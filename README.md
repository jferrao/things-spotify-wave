## About

Simple Spotify player on a RaspberryPi using Android Things and Kotlin.

Currently very limited functionality as it only allows to set a list of pre-defined playlists already accessible from your Spotify account

## Requirements

* [Raspberry Pi 3](https://www.raspberrypi.org/)
* [Rainbow HAT](https://shop.pimoroni.com/products/rainbow-hat-for-android-things) (Optional but recommended)
* [Android Things](https://developer.android.com/things/index.html) (Developers Preview 6 used to built it originally and also tested on 6.1)
* A speaker or headphones with a 3.5mm jack

## Components

If you’re using the Rainbow HAT you can skip this section though the Rainbow HAT is not strictly necessary you’ll need it to control the volume and get volume information.

If you don’t have a Rainbow HAT or if you don’t want to use one but still want to have volume control you can connect components to the Raspberry Pi GPIO:

* 3 buttons
	* Simple button press for volume control (volume up, down and mute)
	* Long button press for changing playlist and skip to next or previous song
* 3 leds 
* 7 led strip for volume visualisation

### Connections:

* Volume up button connected to BCM16
* Volume down button connected to BCM20
* Sound mute button connected to BCM21
* 4 digit display connect to I2C1 bus
* 7 led strip connected to SPI0.0 bus

You may find [Raspberry Pi GPIO Pinout](https://pinout.xyz) useful to get the connections right for each component.

## Known Issues

### Audio output using the Pi’s 3.5 mm output

Especially if using a HDMI screen while testing or setting up this project and to make use of the Raspberry Pi’s 3.5mm audio output you might need to tweak the [Pi’s config.txt](https://elinux.org/RPiconfig) boot file.

Seems that by default the Pi with Android Things Developers Preview 6, probably other older versions as well, will use the HDMI output for audio output. In order to separate the video and audio outputs and set the audio output to the internal 3.5mm audio port add the following line to the Pi’s boot config.txt:

```
hdmi_ignore_hotplug=1
```

Using this setting it will use composite mode even if HDMI monitor is detected.

Thanks to [Burngate](https://www.raspberrypi.org/forums/memberlist.php?mode=viewprofile&u=2314) for pointing me in the right direction in the [Force pi to Analog/Digital output - Raspberry Pi Forums](https://www.raspberrypi.org/forums/viewtopic.php?t=23407) post from back in 2012. It took me quite a bit of time to understand why I could not get sound through the Pi’s audio output port:
> hdmi_ignore_hotplug Pretends HDMI hotplug signal is not asserted so it appears a HDMI display is not attached

## Usage

With the Raspberry Pi connected to a computer in debug mode flashed with Android Things either run it from Android Studio or use the gradle wrapper to install the application:
```
./gradlew installDebug
```

::Note that due to some of the required Android permissions for this application, chances are that you’ll need to restart the Raspberry Pi so that those permissions can be accepted by the device.:: 
