# Mobile AR Sensor (MARS) Logger

record camera frames at ~30Hz and inertial measurement unit (IMU) measurements at ~100Hz synced to one clock source on Android (API 21+) and iOS (SDK 8.0+) mobile devices.

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
* [Installation](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Installation_Android)
* [Record data](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Home)
* [Data format](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Format_Description)
* [Example data](https://drive.google.com/open?id=1AeAd4J9yW8lvAaeSxZAECQEeNQlLzoxx)
* [Transfer data to Windows](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Transfer_Android_Windows)
* [Transfer data to Ubuntu](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Transfer_Android_Ubuntu)
* [Clear data](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Home)
* [Bag data](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Home)

## iOS
* [Installation](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Installation_iOS)
* [Record data](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Home)
* [Data format](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Format_Description)
* [Example data](https://drive.google.com/open?id=101K0bQcADHNNLu3OiMdoU1ukGvw_UwT7)
* [Transfer data to Windows](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Transfer_iOS_Windows)
* [Transfer data to MacOS](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Transfer_iOS_Mac)
* [Clear data](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Home)
* [Bag data](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Home)

# Citing

If you use the MARS logger for your research, please consider citing the paper.
```
@INPROCEEDINGS{huai2019mars, 
author={Jianzhu {Huai} and Yujia {Zhang} and Alper {Yilmaz}}, 
booktitle={2019 IEEE SENSORS}, 
title={The Mobile AR Sensor Logger for Android and iOS Devices}, 
year={2019}, 
volume={}, 
number={}, 
pages={},
ISSN={}, 
month={Oct},}
```

