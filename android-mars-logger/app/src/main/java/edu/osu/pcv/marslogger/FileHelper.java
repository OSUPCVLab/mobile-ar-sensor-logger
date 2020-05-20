package edu.osu.pcv.marslogger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import timber.log.Timber;

public class FileHelper {
    public static BufferedWriter createBufferedWriter(String filename) {
        File dest = new File(filename);
        try {
            if (!dest.exists())
                dest.createNewFile();
            return new BufferedWriter(new FileWriter(dest, true));
        } catch (IOException ioe) {
            Timber.e(ioe);
        }
        return null;
    }

    public static void closeBufferedWriter(BufferedWriter writer) {
        try {
            writer.flush();
            writer.close();
        } catch (IOException ioe) {
            Timber.e(ioe);
        }
    }
}
