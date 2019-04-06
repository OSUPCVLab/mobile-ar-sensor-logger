package io.rpng.recorder.utils;

import android.os.Environment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileHelper {
    private String mStorageDir;

    public FileHelper(String basename) {
        mStorageDir = Environment.getExternalStorageDirectory().
                getAbsolutePath() + "/dataset_recorder/" + basename;
        (new File(mStorageDir)).mkdirs();
    }

    public String getStorageDir() {
        return mStorageDir;
    }

    public String getTimeBaseAbsPath() {
        return mStorageDir + "/" + "edge_epochs.txt";
    }

    public String getCameraInfoAbsPath() {
        return mStorageDir + "/" + "frame_info.csv";
    }
    
    public static BufferedWriter createBufferedWriter(String filename) {
        File dest = new File(filename);
        try {
            // If the file does not exist yet, create it
            if (!dest.exists())
                dest.createNewFile();
            return new BufferedWriter(new FileWriter(dest, true));
        } catch (IOException ioe) {
            System.err.println("IOException: " + ioe.getMessage());
        }
        return null;
    }

    public static void closeBufferedWriter(BufferedWriter writer) {
        try {
            writer.flush();
            writer.close();
        } catch (IOException ioe) {
            System.err.println("IOException: " + ioe.getMessage());
        }
    }
}
