# MARS Logger: Mobile-AR-Sensor-Logger

Android and iOS apps for logging visual and inertial data with mobile phones

The "AR" in the repo name refers to that visual inertial data are essential to augmented reality on smartphones.

**Authors**: Jianzhu Huai, Yujia Zhang
*Ideally, RosyWriter records camera frames, their timestamps, and intrinsic parameters, and the raw accelerometer and gyroscope readings. Except for the camera frames which are saved into a video, other pieces of data are saved into csv files. To control the quality of the images, you may long press the screen before a recording session to lock auto focus and auto exposure, and/or tap to unlock auto focus and auto exposure if appropriate. *

# Related paper

If you use MARSEL for your research, please consider citing the above paper.

# Acknowledgements

The iOS app is developed based off the [rosywriter](https://developer.apple.com/library/archive/samplecode/RosyWriter/Introduction/Intro.html) in objective C released by Apple.
It makes use of the following components.
* Inertial data recorded on a background NSOperationQueue.


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

# TODOs

* adaptively choose camera frame size for android referring to camera2video android sample.
* correct warnings listed in /mobile-ar-sensor-logger/android-ar-sensor-logger/app/build/reports/lint-results.html which is produced by calling "./gradlew check" from the project dir
* associate camera frames and frame metadata for the android app, refer to [here](https://android.googlesource.com/platform/packages/apps/Camera2/+/9c94ab3/src/com/android/camera/one/v2?autodive=0%2F%2F/).
* add git hooks for the android project. https://github.com/harliedharma/android-git-hooks
* test the android app on API 21, make sure the advanced features in API 23 can be accessed coherently.

