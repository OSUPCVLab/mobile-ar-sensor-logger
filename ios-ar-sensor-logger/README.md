**Install and improve RosyWriter**

To install and use RosyWriter, you will need Xcode, iOS.


*Ideally, RosyWriter records camera frames, their timestamps, and intrinsic parameters, and the raw accelerometer and gyroscope readings. Except for the camera frames which are saved into a video footage, other pieces of data are saved into csv files. To control the quality of the images, you may long press the screen before a recording session to lock auto focus and auto exposure, and/or tap to unlock auto focus and auto exposure if appropriate. *

---

## Implementation steps

Here is a roadmap or change log for achieving the goals outlined above.

1. Recognize long press gesture, depict a square of thin boundary lines which increases its side length on a preset size as the press time grows. If auto focus and auto exposure is locked, display a banner of yellow background reading AE/AF locked. If another tap is recognized while the AE/AF is locked, enable AE/AF, and dismiss the banner.
2. Save the frame **timestamps and intrinsic parameters** into csv files and mail them to a chosen mailbox.
3. Save the **raw accelerometer and gyroscope readings** into csv files and mail them to a chosen mailbox.
4. Save the **camera frames** with OpenGL or OpenCV into video files under the picture album.

---

## Deal with warnings

One warning is “All interface orientations must be supported unless the app requires full screen” for a universal app

A short answer is given [here](https://stackoverflow.com/questions/37168888/ios-9-warning-all-interface-orientations-must-be-supported-unless-the-app-req)


