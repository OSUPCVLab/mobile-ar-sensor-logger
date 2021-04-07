/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.osu.pcv.marslogger;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import timber.log.Timber;

/**
 * This class wraps up the core components used for surface-input video encoding.
 * <p>
 * Once created, frames are fed to the input surface.  Remember to provide the presentation
 * time stamp, and always call drainEncoder() before swapBuffers() to ensure that the
 * producer side doesn't get backed up.
 * <p>
 * This class is not thread-safe, with one exception: it is valid to use the input surface
 * on one thread, and drain the output on a different thread.
 */
public class VideoEncoderCore {
    private static final String TAG = CameraCaptureActivity.TAG;
    private static final boolean VERBOSE = false;

    // TODO: these ought to be configurable as well
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    public static final int FRAME_RATE = 30;               // 30fps
    private static final int IFRAME_INTERVAL = 1;           // seconds between I-frames

    private Surface mInputSurface;
    private MediaMuxer mMuxer;
    private MediaCodec mEncoder;
    private boolean mEncoderInExecutingState = false;
    private MediaCodec.BufferInfo mBufferInfo;
    private int mTrackIndex;
    private boolean mMuxerStarted;
    private BufferedWriter mFrameMetadataWriter = null;

    private static String FrameTimeHeader = "Frame timestamp[nanosec],Unix time[nanosec]\n";
    class TimePair {
        public Long sensorTimeMicros;
        public long unixTimeMillis;
        public TimePair(Long sensorTime, long unixTime) {
            sensorTimeMicros = sensorTime;
            unixTimeMillis = unixTime;
        }
        public String toString() {
            String delimiter = ",";
            StringBuilder sb = new StringBuilder();
            sb.append(sensorTimeMicros + "000");
            sb.append(delimiter + unixTimeMillis + "000000");
            return sb.toString();
        }
    }

    private ArrayList<TimePair> mTimeArray = null;
    final int TIMEOUT_USEC = 10000;

    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    public VideoEncoderCore(int width, int height, int bitRate,
                            String outputFile, String metaFile)
            throws IOException {
        mBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        if (VERBOSE) Timber.d("format: %s", format.toString());

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();

        try {
            mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            mEncoderInExecutingState = true;
        } catch (IllegalStateException ise) {
            // This exception occurs with certain devices e.g., Nexus 9 API 22.
            Timber.e(ise);
            mEncoderInExecutingState = false;
        }

        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        mMuxer = new MediaMuxer(outputFile,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        mTrackIndex = -1;
        mMuxerStarted = false;

        try {
            mFrameMetadataWriter = new BufferedWriter(
                    new FileWriter(metaFile, false));
        } catch (IOException err) {
            Timber.e(err, "IOException in opening frameMetadataWriter.");
        }
        mTimeArray = new ArrayList<>();
    }

    /**
     * Returns the encoder's input surface.
     */
    public Surface getInputSurface() {
        return mInputSurface;
    }

    /**
     * Releases encoder resources.
     */
    public void release() {
        if (VERBOSE) Timber.d("releasing encoder objects");
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mMuxer != null) {
            // TODO: stop() throws an exception if you haven't fed it any data.  Keep track
            //       of frames submitted, and don't call stop() if we haven't written anything.
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
        if (mFrameMetadataWriter != null) {
            try {
                mFrameMetadataWriter.write(FrameTimeHeader);
                for (TimePair value : mTimeArray) {
                    mFrameMetadataWriter.write(value.toString() + "\n");
                }
                mFrameMetadataWriter.flush();
                mFrameMetadataWriter.close();
            } catch (IOException err) {
                Timber.e(err, "IOException in closing frameMetadataWriter.");
            }
            mFrameMetadataWriter = null;
        }
    }

    /**
     * Extracts all pending data from the encoder and forwards it to the muxer.
     * <p>
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     * <p>
     * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).  We're
     * not recording audio.
     */
    public void drainEncoder(boolean endOfStream) {
        if (VERBOSE) Timber.d("drainEncoder(%b)", endOfStream);

        if (endOfStream) {
            if (VERBOSE) Timber.d("sending EOS to encoder");
            mEncoder.signalEndOfInputStream();
        }

        while (mEncoderInExecutingState) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break;      // out of while
                } else {
                    if (VERBOSE) Timber.d("no output available, spinning to await EOS");
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                MediaFormat newFormat = mEncoder.getOutputFormat();
                Timber.d("encoder output format changed: %s", newFormat.toString());

                // now that we have the Magic Goodies, start the muxer
                mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
                mMuxerStarted = true;
            } else if (encoderStatus < 0) {
                Timber.w("unexpected result from encoder.dequeueOutputBuffer: %d", encoderStatus);
                // let's ignore it
            } else {
                ByteBuffer encodedData = mEncoder.getOutputBuffer(encoderStatus);
//                MediaFormat bufferFormat = mEncoder.getOutputFormat(encoderStatus);
                // bufferFormat is identical to newFormat
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Timber.d("ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                    mTimeArray.add(new TimePair(mBufferInfo.presentationTimeUs,
                            System.currentTimeMillis()));
                    mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    if (VERBOSE) {
                        Timber.d("sent %d bytes to muxer, ts=%d",
                                mBufferInfo.size, mBufferInfo.presentationTimeUs);
                    }
                }

                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Timber.w("reached end of stream unexpectedly");
                    } else {
                        if (VERBOSE) Timber.d("end of stream reached");
                    }
                    break;      // out of while
                }
            }
        }
    }
}
