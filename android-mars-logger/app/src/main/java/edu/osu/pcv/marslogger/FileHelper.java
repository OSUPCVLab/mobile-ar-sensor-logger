package edu.osu.pcv.marslogger;

import android.os.Build;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;

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

    // https://stackoverflow.com/questions/37904739/html-fromhtml-deprecated-in-android-n
    @SuppressWarnings("deprecation")
    public static Spanned fromHtml(String html) {
        if (html == null) {
            // return an empty spannable if the html is null
            return new SpannableString("");
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // FROM_HTML_MODE_LEGACY is the behaviour that was used for versions below android N
            // we are using this flag to give a consistent behaviour
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } else {
            return Html.fromHtml(html);
        }
    }

}
