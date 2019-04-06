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
* profile  MainActivity.camera2View.setImageBitmap() versus surfaces.add(previewSurface) for feeding data to preview
* adaptively choose default camera frame size referring to camera2video android sample
* record camera intrinsic parameters for every frame
* record camera frame timestamps 
* will it be more efficient if the images are directly saved to a MediaRecorder?
* correct warnings listed in /mobile-ar-sensor-logger/android-ar-sensor-logger/app/build/reports/lint-results.html which is produced by calling "./gradlew check" from the project dir

