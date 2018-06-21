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

## Create a file

Next, you’ll add a new file to this repository.

1. Click the **New file** button at the top of the **Source** page.
2. Give the file a filename of **contributors.txt**.
3. Enter your name in the empty file space.
4. Click **Commit** and then **Commit** again in the dialog.
5. Go back to the **Source** page.

Before you move on, go ahead and explore the repository. You've already seen the **Source** page, but check out the **Commits**, **Branches**, and **Settings** pages.

