MarsLogger Android

# TODOs

* Swap the width and height of frames in the saved video.
* Adaptively choose camera frame size referring to the 
[camera2video](https://github.com/android/camera-samples/tree/master/Camera2VideoJava) sample.
* Correct warnings listed in /mobile-ar-sensor-logger/android-mars-logger/app/build/reports/lint-results.html which is produced by 
calling "./gradlew check" from the project dir
* Associate camera frames and frame metadata for the Android app, refer to 
[here](https://android.googlesource.com/platform/packages/apps/Camera2/+/9c94ab3/src/com/android/camera/one/v2?autodive=0%2F%2F/).
* Add [git hooks](https://github.com/harliedharma/android-git-hooks) for the Android project. 
* Test the Android app on a physical Android device with API 21, 
to make sure the program runs OK even if the advanced features in API 23 are not available.
* Long press to unlock focal length and exposure duration.
