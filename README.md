# Mobile AR Sensor (MARS) Logger

record camera frames at ~30Hz and inertial measurement unit (IMU) measurements at ~100Hz synced to one clock source on Android (API 21+) and iOS (SDK 8.0+) mobile devices.

**New features**

The Android app of MARS logger is upgraded in the user interface.
Now the user can specify the exposure time, ISO, camera frame size, camera ID.
By navigating between tabs, the user can capture videos + IMU data, 
or capture images of a constant focus distance.
Also the tap to focus function is corrected.
The app is released at [here](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/releases)

An Android app to capture data with the fisheye camera and the IMU on tango devices, 
e.g., Lenovo Phab2 Pro, Asus ZenFone AR, has been released with source code at 
[here](https://github.com/JzHuai0108/tango-examples-c/releases/).

# Description

## Android
The Android app is developed from the 
[CameraCaptureActivity](https://github.com/google/grafika/blob/master/app/src/main/java/com/android/grafika/CameraCaptureActivity.java)
 of the grafika project.

* Camera frames are saved into H.264/MP4 videos by using the Camera2 API (setRepeatingRequest, onCaptureCompleted), OpenGL ES (GLSurfaceView and GLSurfaceView.Renderer), and MediaCodec and MediaMuxer.
* The metadata for camera frames are saved to a csv.
* The timestamps for each camera frame are saved to a txt.
* Inertial data are recorded on a background HandlerThread.

## iOS
The iOS app is developed from the 
[rosywriter](https://developer.apple.com/library/archive/samplecode/RosyWriter/Introduction/Intro.html) 
in objective C with iOS SDK 8.0 released by Apple.

* Camera frames are saved into H.264/MP4 videos by using 
AVCaptureVideoDataOutput and AVAssetWriter.
* Timestamps, camera projection intrinsic parameters,
exposure duration of the camera frame are saved into a csv file. 
* Inertial data are saved into a csv by a background NSOperationQueue 
receiving data from the CMMotionManager.

For user guide, please visit the [wiki](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki).

# Features

* 25+ Hz camera frames and 100+ Hz IMU measurements for off-the-shelf smartphones priced $200+.  
* The visual and inertial data are synchronized to one clock.
* The focal length in pixels and exposure duration are recorded.
* Tap to lock auto focus and auto exposure so as to fix focus distance and exposure duration.

# Get started

The installation, data format, recording and exporting data are explained in the following wiki pages.

## Android
* [Installation](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Installation-Android)
* [Record data](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Home)
* [Data format](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Format-description)
* [Example data](https://drive.google.com/open?id=1AeAd4J9yW8lvAaeSxZAECQEeNQlLzoxx)
* [Transfer data to Windows](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Transfer-Android-Windows)
* [Transfer data to Ubuntu](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Transfer-Android-Ubuntu)
* [Clear data](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Home)
* [Bag data](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Home)

## iOS
* [Installation](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Installation-iOS)
* [Record data](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Home)
* [Data format](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Format-description)
* [Example data](https://drive.google.com/open?id=101K0bQcADHNNLu3OiMdoU1ukGvw_UwT7)
* [Transfer data to Windows](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Transfer-iOS-Windows)
* [Transfer data to MacOS](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Transfer-iOS-Mac)
* [Clear data](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Home)
* [Bag data](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Home)

# Citing

If you use the logger for your research, please consider citing the paper.
```
@INPROCEEDINGS{huai2019mars, 
author={Jianzhu {Huai} and Yujia {Zhang} and Alper {Yilmaz}}, 
booktitle={2019 IEEE SENSORS}, 
title={The mobile AR sensor logger for Android and iOS devices}, 
year={2019}, 
volume={}, 
number={}, 
pages={},
ISSN={}, 
month={Oct},}
```

# Contributions
We always look forward to enhancing the capability and portability of MarsLogger 
so that it may better serve the community.
Consequently, we also look forward to community contributions.
If you are willing to extend help, one way is to raise an issue and discuss with the authors about how to carry out the task.
J. Huai will be happy to write up the pseudocode as assistance.
Even better, another way is to code up the functionality and make a pull request (in the Android or iOS submodule).

The following enhancements are on the roadmap of MarsLogger.
1. For the Android app, record data of a variety of other sensors or modules, 
magnetometer, GNSS, WiFi, Bluetooth, among others.
Refer to the GetSensorData app source code at [here](https://github.com/lopsi/GetSensorData_Android).

2. For the iOS app, record data of a variety of other sensors or modules,
magnetometer, GNSS, WiFi, Bluetooth, ArKit, among others.
Refer to the [ios_logger](https://github.com/Varvrar/ios_logger).

3. For the Android app, support recording with multiple cameras.
4. For the iOS app, support recording with multiple cameras.
5. For the iOS app, migrate it to swift, refer to the [swift version of rosywriter](https://github.com/ooper-shlab/RosyWriter2.1-Swift).
