# MARSLogger: Mobile-AR-Sensor-Logger

Android and iOS apps for logging visual and inertial data with mobile phones

The "AR" in the repo name refers to that visual inertial data are essential to augmented reality on smartphones.

**Authors**: Jianzhu Huai, Yujia Zhang

# Related paper

If you use MARSEL for your research, please consider citing the above paper.

# Acknowledgements

The iOS app is developed based off the [rosywriter](https://developer.apple.com/library/archive/samplecode/RosyWriter/Introduction/Intro.html) in objective C released by Apple.

The Android app is developed based off the [android-dataset-recorder](https://github.com/rpng/android-dataset-recorder) released by the Robot Perception & Navigation Group at Univ. of Delaware.

# Contributions

* synchronized data by the back camera and the inertial sensor
* 25+ Fps of the camera and 100+ Hz of the inerital sensor for off-the-shelf smartphones priced $300+ 
* record the varying focal length and exposure time
* tap to lock or unlock auto focus and auto exposure control

# TODOs

* adaptively choose default camera frame size referring to camera2video android sample
* correct warnings listed in /mobile-ar-sensor-logger/android-ar-sensor-logger/app/build/reports/lint-results.html which is produced by calling "./gradlew check" from the project dir
* associate camera frames and frame metadata for the android app, refer to [here](https://android.googlesource.com/platform/packages/apps/Camera2/+/9c94ab3/src/com/android/camera/one/v2?autodive=0%2F%2F/).
* add git hooks for the android project. https://github.com/harliedharma/android-git-hooks

* Android ar logger should use MediaCodec to encode the output frames and MediaMuxer to writeSampleData for disk I/O. Reference implementation of feeding images from ImageAvailableListener to MediaCodec is at $HOME/sandbox/AndroidStudioProjects/Camera2PreviewStreamMediaCodecVideoRecording which uses an async MediaCodec encoder.

The reference implementation of performing disk I/O with MediaMuxer is at $HOME/sandbox/AndroidStudioProjects/grafika/app/src/main/java/com/android/grafika/VideoEncoderCore.java which is used by the sandbox/AndroidStudioProjects/grafika/app/src/main/java/com/android/grafika/CameraCaptureActivity.java.

# Better solution to record camera images, and saving to a mp4 video

* Camera2 API (setRepeatingRequest, onCaptureCompleted) +
* TextureView (onFrameAvailable) + 
* OpenGL ES GLSurfaceView and GLSurfaceView.Renderer
* MediaCodec with Surface + MediaMuxer

Reference implementation:
* grafika/CameraCaptureActivity but with Camera API, does not use onCaptureCompleted
* GLSurfaceView + Camera2. https://github.com/afei-cn/CameraDemo/tree/master/app/src/main/java/com/afei/camerademo/glsurfaceview. Only shows how to preview and capture pictures.
* GPUVideo-Android: https://github.com/MasayukiSuda/GPUVideo-android. Synchronous MediaCodec encoder, GLSurfaceView, setRepeatingRequest

Coding strategy:
Start with GPUVideo-Android, then adapt to recording inertial data

Tutorials
* Android Camera使用OpenGL ES 2.0和GLSurfaceView对预览进行实时二次处理（黑白滤镜）. https://blog.csdn.net/lb377463323/article/details/77071054. Its project app on github displays a blank screen.
* Android初始化OpenGL ES，并且分析Renderer子线程原理. https://blog.csdn.net/lb377463323/article/details/63263015
* 自定义Camera系列之：GLSurfaceView + Camera2. https://blog.csdn.net/afei__/article/details/87220013. Its github project app works like a charm.
* camera2 opengl实现滤镜效果录制视频 四 录像. https://blog.csdn.net/u010302327/article/details/77839583. Provides source code but not the project configuration, GLSurfaceView is not used.
