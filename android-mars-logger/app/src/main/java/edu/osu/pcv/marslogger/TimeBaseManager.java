package edu.osu.pcv.marslogger;

import android.hardware.camera2.CameraCharacteristics;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.IOException;

import timber.log.Timber;

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
            mDataWriter.write("#IMU data clock\tSENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN camera clock\tDifference\n");
            mDataWriter.write("#elapsedRealtimeNanos()\tnanoTime()\tDifference\n");
            mDataWriter.write(sysElapsedNs + "\t" + sysNs + "\t" + diff + "\n");
        } catch (IOException ioe) {
            Timber.e(ioe);
        }
    }

    public void stopRecording() {
        long sysElapsedNs = SystemClock.elapsedRealtimeNanos();
        long sysNs = System.nanoTime();
        long diff = sysElapsedNs - sysNs;
        try {
            mDataWriter.write(sysElapsedNs + "\t" + sysNs + "\t" + diff + "\n");
        } catch (IOException ioe) {
            Timber.e(ioe);
        }
        FileHelper.closeBufferedWriter(mDataWriter);
        mDataWriter = null;
    }

    private void createHeader(String timestampSource) {
        mTimeBaseHint = "#Camera frame timestamp source according to CameraCharacteristics.SENSOR_INFO_" +
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
    }

    private void setCameraTimestampSource(Integer timestampSource) {
        String warn_msg = "The camera timestamp source is unreliable to synchronize with motion sensors";
        String src_type = "SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN";
        if (timestampSource != null) {
            if (timestampSource == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN) {
                src_type = "SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN";
                Timber.d("%s:%s", warn_msg, src_type);
            } else if (timestampSource == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) {
                src_type = "SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME";
            } else {
                src_type = "SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN (" + timestampSource + ")";
                Timber.d("%s:%s", warn_msg, src_type);
            }
        }
        createHeader(src_type);
    }
}
