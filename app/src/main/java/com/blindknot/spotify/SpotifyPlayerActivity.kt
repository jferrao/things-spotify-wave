package com.blindknot.spotify

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.KeyEvent
import android.os.Bundle
import android.os.AsyncTask
import android.media.AudioManager
import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.google.android.things.contrib.driver.button.Button
import com.google.android.things.contrib.driver.apa102.Apa102
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay;
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManager
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse
import com.spotify.sdk.android.player.SpotifyPlayer
import com.spotify.sdk.android.player.Spotify
import android.content.Intent
import com.spotify.sdk.android.player.Config
import com.spotify.sdk.android.player.ConnectionStateCallback
import com.spotify.sdk.android.player.Player
import com.spotify.sdk.android.player.PlayerEvent
import java.util.Random


class SpotifyPlayerActivity :
        Activity(),
        Player.NotificationCallback,
        ConnectionStateCallback,
        Player.OperationCallback {

    companion object {
        private val TAG = SpotifyPlayerActivity::class.java.simpleName

        private const val clientId = "your_spotify_client_id"
        private const val redirectUri = "mdkio://callback"
        private const val requestCode = 1337

        private const val stationName = "SPOTIFY"

        private enum class Playlist(val title: String, val uri: String, val size: Int) {
            PLAYLIST1 ("playlist_name", "spotify:uri:of:your:playlist", 309)
        }

        private fun randomSongIndex(playlistSize: Int) : Int {
            val random = Random()
            return random.nextInt(playlistSize)
        }

        private enum class Rpi3(val io: String) {
            BCM6("BCM6"),
            BCM16("BCM16"),
            BCM19("BCM19"),
            BCM20("BCM20"),
            BCM21("BCM21"),
            BCM26("BCM26"),
            I2C1("I2C1"),
            SPI("SPI0.0"),
        }

        private const val displaySize = 4

        private const val ledStripBrightness = 1
        private const val ledStripSize = 7
    }

    private lateinit var buttonVolumeUp: ButtonInputDriver
    private lateinit var buttonVolumeDown: ButtonInputDriver
    private lateinit var buttonSoundMute: ButtonInputDriver

    private lateinit var ledUp: Gpio
    private lateinit var ledDown: Gpio
    private lateinit var ledMute: Gpio

    private var display: AlphanumericDisplay? = null
    private var ledStrip: Apa102? = null

    private lateinit var audio: AudioManager
    private lateinit var mediaPlayer: SpotifyPlayer
    private var scroller: Scroller? = null

    private var selectedPlaylist = Playlist.PLAYLIST1

    private val displayStuff : String
        get() = "$stationName ${selectedPlaylist.title}".trimEnd()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        setupInputButtons()
        setupLeds()
        setupDisplay()
        setupLedStrip()

        val builder = AuthenticationRequest.Builder(clientId, AuthenticationResponse.Type.TOKEN, redirectUri)
        builder.setScopes(arrayOf("user-read-private", "streaming"))
        val request = builder.build()
        AuthenticationClient.openLoginActivity(this, requestCode, request)
    }

    override fun onDestroy() {
        super.onDestroy()
        Spotify.destroyPlayer(this)
        releaseInputButtons()
        releaseLeds()
        releaseDisplay()
        releaseLedStrip()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {
        super.onActivityResult(requestCode, resultCode, intent)

        // Check if result comes from the correct activity
        if (requestCode == requestCode) {
            val response = AuthenticationClient.getResponse(resultCode, intent)
            if (response.type == AuthenticationResponse.Type.TOKEN) {
                val playerConfig = Config(this, response.accessToken, clientId)
                Spotify.getPlayer(playerConfig, this, object : SpotifyPlayer.InitializationObserver {
                    override fun onInitialized(spotifyPlayer: SpotifyPlayer) {
                        mediaPlayer = spotifyPlayer
                        mediaPlayer.addConnectionStateCallback(this@SpotifyPlayerActivity)
                        mediaPlayer.addNotificationCallback(this@SpotifyPlayerActivity)
                    }

                    override fun onError(throwable: Throwable) {
                        Log.e(TAG, "Could not initialize player: ${throwable.message}")
                }
                })
            }
        }
    }

    override fun onPlaybackEvent(playerEvent: PlayerEvent) {
        Log.d(TAG, "Playback event received: $playerEvent")
        when (playerEvent) {
            PlayerEvent.kSpPlaybackNotifyPause -> {
                updateDisplay("PAUSE")
                Log.d(TAG, "Pause")
            }
            PlayerEvent.kSpPlaybackNotifyPlay -> {
                updateDisplay(displayStuff)
                Log.d(TAG, "Resume play")
            }
            PlayerEvent.kSpPlaybackNotifyBecameActive -> {
                // On SDK "24-noconnect-2.20b", setShuffle can only be called after having received the playback event kSpPlaybackNotifyBecameActive
                mediaPlayer.setShuffle(this@SpotifyPlayerActivity, true)
            }
            else -> {
            }
        }
    }

    override fun onPlaybackError(error: com.spotify.sdk.android.player.Error) {
        Log.d(TAG, "Playback error received: $error")
        when (error) {
            // Handle error type as necessary
            else -> {
            }
        }
    }

    override fun onLoggedIn() {
        Log.d(TAG, "User logged in")
        mediaPlayer.playUri(null, selectedPlaylist.uri, randomSongIndex(selectedPlaylist.size), 0)
    }

    override fun onLoggedOut() {
        Log.d(TAG, "User logged out")
    }

    override fun onLoginFailed(var1: com.spotify.sdk.android.player.Error) {
        Log.d(TAG, "Login failed")
    }

    override fun onTemporaryError() {
        Log.d(TAG, "Temporary error occurred")
    }

    override fun onConnectionMessage(message: String) {
        Log.d(TAG, "Received connection message: $message")
    }

    override fun onSuccess() {
        Log.d(TAG, "Successful operation callback")
    }

    override fun onError(var1: com.spotify.sdk.android.player.Error) {
        Log.d(TAG, "Error on operation callback")
    }

    /**
     * Handle on button pressed event where it mostly resets states.
     *
     * @param keyCode The value in event.getKeyCode()
     * @param event Description of the key event
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            event.startTracking()
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> updateLed(true, ledUp)
                KeyEvent.KEYCODE_VOLUME_DOWN -> updateLed(true, ledDown)
                KeyEvent.KEYCODE_VOLUME_MUTE -> updateLed(true, ledMute)
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Handles long button press event.
     *
     * @param keyCode The value in event.getKeyCode()
     * @param event Description of the key event
     */
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mediaPlayer.skipToNext(this@SpotifyPlayerActivity)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            mediaPlayer.skipToPrevious(this@SpotifyPlayerActivity)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            val index = if (selectedPlaylist.ordinal < enumValues<Playlist>().size - 1) selectedPlaylist.ordinal + 1 else 0
            selectedPlaylist = enumValues<Playlist>()[index]
            mediaPlayer.playUri(null, selectedPlaylist.uri, randomSongIndex(selectedPlaylist.size), 0)
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    /**
     * Handle volume controls on button released event.
     *
     * @param keyCode The value in event.getKeyCode()
     * @param event Description of the key event
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> updateLed(false, ledUp)
            KeyEvent.KEYCODE_VOLUME_DOWN -> updateLed(false, ledDown)
            KeyEvent.KEYCODE_VOLUME_MUTE -> updateLed(false, ledMute)
        }

        if ((event.flags and KeyEvent.FLAG_CANCELED_LONG_PRESS) == 0) { // onKeyDown
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                var volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && volume < audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) { // Handle volume up
                    volume++
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                    updateLed(false, ledUp)
                    if (volume == 1) handleMuteStates(audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                    Log.d(TAG, "Volume up: $volume")
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && volume > 0) { // Handle volume down
                    volume--
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                    updateLed(false, ledDown)
                    if (volume == 0) handleMuteStates(audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                    Log.d(TAG, "Volume down: $volume")
                }
                updateLedStrip(audio.getStreamVolume(AudioManager.STREAM_MUSIC), audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) { // Handle pause and resume
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                if (mediaPlayer.playbackState.isPlaying) {
                    mediaPlayer.pause(this@SpotifyPlayerActivity)
                } else {
                    mediaPlayer.resume(this@SpotifyPlayerActivity)
                }
                updateLed(true, ledMute)
                updateLedStrip(audio.getStreamVolume(AudioManager.STREAM_MUSIC), audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                return true
            }
        }

        return return super.onKeyUp(keyCode, event);
    }

    /**
     * Helper method for common operations when handling (un)mute states.
     *
     * @param volume The sound volume value
     */
    private fun handleMuteStates(volume: Int) {
        updateDisplay(if (volume > 0) displayStuff else "MUTE")
        Log.d(TAG, "${if (volume > 0) "Un-mute" else "Mute"} sound")
    }

    /**
     * Register the GPIO buttons on the Rainbow HAT that generates key press actions.
     */
    private fun setupInputButtons() {
        try {
            buttonVolumeUp = ButtonInputDriver(
                    Rpi3.BCM16.io,
                    Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_VOLUME_UP)
            buttonVolumeUp.register()
            buttonVolumeDown = ButtonInputDriver(
                    Rpi3.BCM20.io,
                    Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_VOLUME_DOWN)
            buttonVolumeDown.register()
            buttonSoundMute = ButtonInputDriver(
                    Rpi3.BCM21.io,
                    Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_VOLUME_MUTE)
            buttonSoundMute.register()
            Log.d(TAG, "Initialized GPIO Buttons")
        } catch (e: IOException) {
            throw RuntimeException("Error initializing GPIO buttons", e)
        }
    }

    /**
     * Disable the GPIO buttons.
     */
    private fun releaseInputButtons() {
        try {
            buttonVolumeUp.close()
            buttonVolumeDown.close()
            buttonSoundMute.close()
        } catch (e: IOException) {
            throw RuntimeException("Error releasing GPIO buttons", e)
        }
    }

    /**
     * Setup GPIO leds, button leds on the Rainbow HAT.
     * TODO: make leds optional
     */
    private fun setupLeds() {
        try {
            val pioService = PeripheralManager.getInstance()

            ledUp = pioService.openGpio(Rpi3.BCM26.io).apply {
                setEdgeTriggerType(Gpio.EDGE_NONE)
                setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
                setActiveType(Gpio.ACTIVE_HIGH)
            }

            ledDown = pioService.openGpio(Rpi3.BCM19.io).apply {
                setEdgeTriggerType(Gpio.EDGE_NONE)
                setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
                setActiveType(Gpio.ACTIVE_HIGH)
            }

            ledMute = pioService.openGpio(Rpi3.BCM6.io).apply {
                setEdgeTriggerType(Gpio.EDGE_NONE)
                setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
                setActiveType(Gpio.ACTIVE_HIGH)
            }

            Log.d(TAG, "Initialized GPIO leds")
        } catch (e: IOException) {
            throw RuntimeException("Error initializing leds", e)
        }
    }

    /**
     * Disable the GPIO leds.
     */
    private fun releaseLeds() {
        try {
            ledUp.value = false
            ledUp.close()
            ledDown.value = false
            ledDown.close()
            ledMute.value = false
            ledMute.close()
        } catch (e: IOException) {
            throw RuntimeException("Error releasing leds", e)
        }
    }

    /**
     * Setup I2C1 display, 4 character digital display on the Rainbow HAT.
     */
    private fun setupDisplay() {
        try {
            display = AlphanumericDisplay(Rpi3.I2C1.io)
            display?.let {
                it.setEnabled(true)
                updateDisplay(displayStuff)
            }
            Log.d(TAG, "Initialized I2C Display")
        } catch (e: IOException) {
            Log.e(TAG, "Error initializing I2C display", e)
            Log.d(TAG, "Display disabled")
            display = null // Display is optional
        }
    }

    /**
     * Disable the I2C1 display.
     */
    private fun releaseDisplay() {
        display?.let {
            try {
                it.clear()
                it.setEnabled(false)
                it.close()
            } catch (e: IOException) {
                throw RuntimeException("Error releasing I2C display", e)
            }
        }
    }

    /**
     * Setup the SPI led strip, 7 led strip on the Rainbow HAT.
     */
    private fun setupLedStrip() {
        // Initialize the SPI led strip
        try {
            ledStrip = Apa102(Rpi3.SPI.io, Apa102.Mode.BGR)
            ledStrip?.brightness = ledStripBrightness
            updateLedStrip(audio.getStreamVolume(AudioManager.STREAM_MUSIC),  audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            Log.d(TAG, "Initialized SPI led strip")
        } catch (e: IOException) {
            Log.e(TAG, "Error initializing SPI led strip", e)
            Log.d(TAG, "Led strip disabled")
            ledStrip = null // Led strip is optional
        }
    }

    /**
     * Disable the SPI led strip.
     */
    private fun releaseLedStrip() {
        ledStrip?.let {
            try {
                it.brightness = 0
                it.write(IntArray(ledStripSize))
                it.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error releasing SPI led strip", e)
            }
        }
    }

    /**
     * Update GPIO led state.
     *
     * @param state Boolean signaling whether the led is on (true) or off (false)
     * @param led Which led instance to use
     */
    private fun updateLed(state: Boolean, led: Gpio) {
        try {
            led.value = state
        } catch (e: IOException) {
            Log.e(TAG, "Error updating LED", e)
        }
    }

    /**
     * Update the I2C1 display text. It will apply a text scroll effect if text is larger than
     * display size.
     *
     * @param text Test to display
     */
    private fun updateDisplay(text: String) {
        display?.let {
            it.clear()
            scroller?.cancel (true)
            if (text.length > displaySize) {
                scroller = Scroller()
                scroller?.execute(text)
            } else {
                it.display(text)
            }
        }
    }

    /**
     * Display sound volume information on the led strip.
     *
     * @param value Value to be represented on the led strip
     * @param maxValue Max value supported
     */
    private fun updateLedStrip(value: Int, maxValue: Int) {
        ledStrip?.let {
            val affectedLeds = value.toFloat().div(maxValue.toFloat()).times(ledStripSize).toInt()
            val rainbow = IntArray(ledStripSize, { _ -> 0 })
            for (i in ledStripSize.minus(affectedLeds) until rainbow.size) {
                val hsv = floatArrayOf(i * 360f / rainbow.size, 1.0f, 1.0f)
                rainbow[i] = Color.HSVToColor(255, hsv)
            }
            it.write(rainbow)
        }
    }

    /**
     * The text scroller class as an async task.
     */
    internal inner class Scroller : AsyncTask<String, Void, Boolean>() {
        override fun doInBackground(vararg strings: String): Boolean? {
            val scrollText = "   ${strings[0]} "
            while (true) {
                for (i in 0 until scrollText.length.minus(1)) {
                    display?.display(scrollText.substring(i))
                    TimeUnit.MILLISECONDS.sleep(400)
                }
            }
        }
    }

}
