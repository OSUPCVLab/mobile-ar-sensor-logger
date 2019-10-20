
RosyWriter

This sample demonstrates how to use AVCaptureVideoDataOutput to bring frames from the camera into various processing pipelines, including CPU-based, OpenGL (i.e. on the GPU), CoreImage filters, and OpenCV. It also demonstrates best practices for writing the processed output of these pipelines to a movie file using AVAssetWriter.

The project includes a different target for each of the different processing pipelines.

Classes
RosyWriterViewController
-- This file contains the view controller logic, including support for the Record button and video preview.
RosyWriterCapturePipeline
-- This file manages the audio and video capture pipelines, including the AVCaptureSession, the various queues, and resource management.

Renderers
RosyWriterRenderer
-- This file defines a generic protocol for renderer objects used by RosyWriterCapturePipeline.
RosyWriterOpenGLRenderer
-- This file manages the OpenGL (GPU) processing for the "rosy" effect and delivers rendered buffers.
RosyWriterCPURenderer
-- This file manages the CPU processing for the "rosy" effect and delivers rendered buffers.
RosyWriterCIFilterRenderer
-- This file manages the CoreImage processing for the "rosy" effect and delivers rendered buffers.
RosyWriterOpenCVRenderer
-- This file manages the delivery of frames to an OpenCV processing block and delivers rendered buffers.

RosyWriterAppDelegate
-- This file is a standard application delegate class.

Shaders
myFilter
-- OpenGL shader code for the "rosy" effect

Utilities
MovieRecorder
-- Illustrates real-time use of AVAssetWriter to record the displayed effect.
OpenGLPixelBufferView
-- This is a view that displays pixel buffers on the screen using OpenGL.

GL
-- Utilities used by the GL processing pipeline.

This program has been found to be able to record 12 minutes of 1920 x 1080 video at 30Hz and inertial data at 100Hz on an iPhone 6S.

TODOs
1. Customize the video dimension with the user input.
Currently the video frame size is preset with
captionSession.sessionPreset = AVCaptureSessionPreset1280x720
2. Warning “All interface orientations must be supported unless the app requires full screen”
for a universal app
To resolve this warning, refer to [here](https://stackoverflow.com/questions/37168888/ios-9-warning-all-interface-orientations-must-be-supported-unless-the-app-req).

===============================================================
Copyright © 2016 Apple Inc. All rights reserved.
