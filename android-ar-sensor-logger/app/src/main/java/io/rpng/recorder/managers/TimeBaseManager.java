package io.rpng.recorder.managers;

import android.hardware.camera2.CameraCharacteristics;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.IOException;

import io.rpng.recorder.utils.FileHelper;

public class TimeBaseManager {
    private static final String TAG = TimeBaseManager.class.getName();

    public String mTimeBaseHint;
    
    private BufferedWriter mDataWriter = null;

    public TimeBaseManager() {

    }
    
    public void startRecording(String captureResultFile, Integer timeSourceValue) {
        mDataWriter = FileHelper.createBufferedWriter(captureResultFile);
        long sysElapsedNs = SystemClock.elapsedRealtimeNanos();
        long sysNs = System.nanoTime();
        long diff = sysElapsedNs - sysNs;
        setCameraTimestampSource(timeSourceValue);
        try {
            mDataWriter.write(mTimeBaseHint + "\n");
            mDataWriter.write("#IMU data clock\tCamera SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN clock\tDifference\n");
            mDataWriter.write("#elapsedRealtimeNanos()\tnanoTime()\tDifference\n");
            mDataWriter.write(sysElapsedNs + "\t" + sysNs + "\t" + diff + "\n");
        } catch (IOException ioe) {
            System.err.println("IOException: " + ioe.getMessage());
        }
    }

    public void stopRecording() {
        long sysElapsedNs = SystemClock.elapsedRealtimeNanos();
        long sysNs = System.nanoTime();
        long diff = sysElapsedNs - sysNs;
        try {
            mDataWriter.write(sysElapsedNs + "\t" + sysNs + "\t" + diff + "\n");
        } catch (IOException ioe) {
            System.err.println("IOException: " + ioe.getMessage());
        }
        FileHelper.closeBufferedWriter(mDataWriter);
        mDataWriter = null;
    }

    private void createHeader(String timestampSource) {
        String hint = "#Camera frame timestamp base according to CameraCharacteristics.SENSOR_INFO_" +
                "TIMESTAMP_SOURCE is " + timestampSource + ".\n#" +
                "If SENSOR_INFO_TIMESTAMP_SOURCE is SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME, then " +
                "camera frame timestamps of the attribute CaptureResult.SENSOR_TIMESTAMP\n#" +
                "and IMU reading timestamps of the field SensorEvent.timestamp " +
                "are on the same timebase CLOCK_BOOTTIME which is " +
                "used by elapsedRealtimeNanos().\n#" +
                "In this case, no offline sync is necessary.\n#" +
                "Otherwise, the camera frame timestamp is " +
                "assumed to be on the timebase of CLOCK_MONOTONIC" +
                " which is generally used by nanoTime().\n#" +
                "In this case, offline sync is usually necessary unless the difference " +
                "is really small, e.g., <1000 nanoseconds.\n#" +
                "To help sync camera frames to " +
                "the IMU offline, the timestamps" +
                " according to the two time basis at the start and end" +
                " of a recording session are recorded.";
        mTimeBaseHint = hint;
        Log.d(TAG, hint);
    }

    private void setCameraTimestampSource(Integer timestampSource) {
        String warn_msg = "The camera timestamp source is unreliable to synchronize with motion sensors";
        String src_type = "SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN";
        if (timestampSource != null) {
            if (timestampSource.intValue() == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN) {
                src_type = "SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN";
                Log.d(TAG, warn_msg + src_type);
            } else if (timestampSource.intValue() == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) {
                src_type = "SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME";
            } else {
                src_type = "SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN (" + timestampSource + ")";
                Log.d(TAG, warn_msg + src_type);
            }
        }
        createHeader(src_type);
    }
}
