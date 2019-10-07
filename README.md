# Mobile AR Sensor (MARS) Logger

log data of the camera and the IMU on iOS and Android mobile devices

The "AR" in the repo name refers to that visual inertial data are essential to augmented reality on smartphones.

# Related paper

If you use MARS logger for your research, please consider citing the paper.
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


# Acknowledgments

The iOS app is developed based off the [rosywriter](https://developer.apple.com/library/archive/samplecode/RosyWriter/Introduction/Intro.html) in objective C released by Apple.
It makes use of the following components.
* Inertial data recorded on a background NSOperationQueue.

The MARS logger for iOS records camera frames, their timestamps, and intrinsic parameters, and the raw accelerometer and gyroscope readings. 
Except for the camera frames which are saved into a video, other pieces of data are saved into csv files.
To control the quality of the images, you may long press the screen before a recording session to lock auto focus and auto exposure, 
and/or tap to unlock auto focus and auto exposure if appropriate. 


The Android app is developed based off the [CameraCaptureActivity](https://github.com/google/grafika/blob/master/app/src/main/java/com/android/grafika/CameraCaptureActivity.java) of the grafika project.
It makes use of the following components.
* Camera2 API (setRepeatingRequest, onCaptureCompleted)
* SurfaceTexture (onFrameAvailable)
* OpenGL ES GLSurfaceView and GLSurfaceView.Renderer
* MediaCodec with Surface + MediaMuxer
* Inertial data recorded on a background HandlerThread.

# Contributions

* synchronized data by the rear camera and the inertial sensor
* 25+ Fps of the camera and 100+ Hz of the inerital sensor for off-the-shelf smartphones priced $300+ 
* record the varying focal length in pixels and exposure time
* tap to lock or unlock auto focus and auto exposure control

# Dataset formats
For android datasets, we have an extra frame_timestamps.txt because entries of movie_metadata.csv do not match exactly with the video frames.
For iOS datasets, the entries of moview_metadata.csv corresponds to every frames in the video.
 

# TODOs

* adaptively choose camera frame size for android referring to camera2video android sample.
* correct warnings listed in /mobile-ar-sensor-logger/android-ar-sensor-logger/app/build/reports/lint-results.html which is produced by calling "./gradlew check" from the project dir
* associate camera frames and frame metadata for the android app, refer to [here](https://android.googlesource.com/platform/packages/apps/Camera2/+/9c94ab3/src/com/android/camera/one/v2?autodive=0%2F%2F/).
* add git hooks for the android project. https://github.com/harliedharma/android-git-hooks
* test the android app on API 21, make sure the advanced features in API 23 can be accessed coherently.

