**Install and improve RosyWriter**

To install and use RosyWriter, you will need Xcode, iOS.


*Ideally, RosyWriter records camera frames, their timestamps, and intrinsic parameters, and the raw accelerometer and gyroscope readings. Except for the camera frames which are saved into a video, other pieces of data are saved into csv files. To control the quality of the images, you may long press the screen before a recording session to lock auto focus and auto exposure, and/or tap to unlock auto focus and auto exposure if appropriate. *

---

## Todos
1. change the video dimensions with the user input.
    Currently the video frame size is preset with captionSession.sessionPreset = AVCaptureSessionPreset1280x720


---

## Deal with warnings

One warning is “All interface orientations must be supported unless the app requires full screen” for a universal app

A short answer is given [here](https://stackoverflow.com/questions/37168888/ios-9-warning-all-interface-orientations-must-be-supported-unless-the-app-req)


