package io.rpng.recorder.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraMetadata;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.rpng.recorder.R;
import io.rpng.recorder.managers.CameraManager;
import io.rpng.recorder.managers.GPSManager;
import io.rpng.recorder.managers.IMUManager;
import io.rpng.recorder.managers.TimeBaseManager;
import io.rpng.recorder.utils.FileHelper;
import io.rpng.recorder.utils.ImageSaver;
import io.rpng.recorder.views.AutoFitTextureView;

public class MainActivity extends AppCompatActivity {

    private static String TAG = "MainActivity";
    private static final int RESULT_SETTINGS = 1;
    private static final int RESULT_RESULT = 2;
    private static final int RESULT_INFO = 3;

    private static Intent intentSettings;
    private static Intent intentResults;

    private AutoFitTextureView mTextureView;

    private TextView mFpsFocalLength;
    private TextView mwxh;
    private TextView mExposureTime;
    private TextView mAEAFstate;

    public static CameraManager mCameraManager;
    public static IMUManager mImuManager;
    public static GPSManager mGpsManager;
    private static SharedPreferences sharedPreferences;


    // Variables for the current state
    public static boolean is_recording;
    private static String folder_name;

    private String mTimeBaseAbsPath;
    private TimeBaseManager mTimeBaseManager;

    public static FileHelper mFileHelper;

    private Long mLastFrameTimeNs;
    private Float mFrameRate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Pass to super
        super.onCreate(savedInstanceState);

        // Create our layout
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Add our listeners
        this.addButtonListeners();

        // Get our surfaces
        mTextureView = (AutoFitTextureView) findViewById(R.id.camera2_texture);

        mFpsFocalLength = (TextView) findViewById(R.id.fps_focal_length);
        mwxh = (TextView) findViewById(R.id.wxh);
        mExposureTime = (TextView) findViewById(R.id.exposure_time);
        mAEAFstate = (TextView) findViewById(R.id.AE_AF_state);

        // Create the camera manager
        mCameraManager = new CameraManager(this, mTextureView);
        mImuManager = new IMUManager(this);
        mGpsManager = new GPSManager(this);

        // Set our shared preferences
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Build the result activities for later
        intentSettings = new Intent(this, SettingsActivity.class);
        intentResults = new Intent(this, ResultsActivity.class);

        // Set the state so that we are not recording
        folder_name = "";
        is_recording = false;

        mTimeBaseManager = new TimeBaseManager();

        mFrameRate = 15.0f;
        mLastFrameTimeNs = null;
        // Lets by default launch into the settings view
        startActivityForResult(intentSettings, RESULT_SETTINGS);

    }

    private void addButtonListeners() {

        // We we want to "capture" the current grid, we should record the current corners
        Button button_record = (Button) findViewById(R.id.button_record);
        button_record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If we are not recording we should start it
                if (!is_recording) {
                    // Set our folder name
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yy_MM_dd_HH_mm_ss");
                    folder_name = dateFormat.format(new Date());

                    // Also change the text on the button so that it turns into the stop button
                    Button button_record = (Button) findViewById(R.id.button_record);
                    button_record.setText(R.string.stop_record);

                    mFileHelper = new FileHelper(folder_name);
                    mTimeBaseAbsPath = mFileHelper.getTimeBaseAbsPath();

                    mTimeBaseManager.startRecording(mTimeBaseAbsPath, mCameraManager.mTimeSourceValue);

                    mCameraManager.prepareInfoWriter();
                    // Trigger the recording by changing the recording boolean
                    is_recording = true;

                } else { // Else we can assume we pressed the "stop recording" button
                    // Just reset the recording button
                    is_recording = false;

                    // Also change the text on the button so that it turns into the start button
                    Button button_record = (Button) findViewById(R.id.button_record);
                    button_record.setText(R.string.start_record);

                    // Start the result activity
                    //startActivityForResult(intentResults, RESULT_RESULT);

                    mTimeBaseManager.stopRecording();
                    mTimeBaseAbsPath = null;

                    mCameraManager.invalidateInfoWriter();
                }
            }
        });
    }

    @Override
    public void onResume() {
        // Pass to our super
        super.onResume();
        // Start the background thread
        mCameraManager.startBackgroundThread();
        // Open the camera
        // This should take care of the permissions requests
        if (mTextureView.isAvailable()) {
            mCameraManager.openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mCameraManager.mSurfaceTextureListener);
        }

        // Register the listeners
        mImuManager.register();

        // Start background thread
        mGpsManager.startBackgroundThread();
        // Register google services
        mGpsManager.register();
    }

    static void saveImageTimestamp(Long timestamp, String output_dir) {

        // Create folder name
        String filename = "data_image.txt";

        // Create export file
        File dest = new File(output_dir + filename);

        try {
            // If the file does not exist yet, create it
            if (!dest.exists())
                dest.createNewFile();

            // The true will append the new data
            BufferedWriter writer = new BufferedWriter(new FileWriter(dest, true));

            // Master string of information
            String data = timestamp + ",images/" + timestamp + ".jpeg";

            // Appends the string to the file and closes
            writer.write(data + "\n");
            writer.flush();
            writer.close();
        }
        // Ran into a problem writing to file
        catch (IOException ioe) {
            System.err.println("IOException: " + ioe.getMessage());
        }
    }

    public void updateStatsPanel(
            final Long timestamp, final Float fl,
            final Long exposureTimeNs, final Integer afMode) {
        if (mLastFrameTimeNs != null) {
            Long gapNs = timestamp - mLastFrameTimeNs;
            mFrameRate = mFrameRate * 0.3f +
                    (float) (1000000000.0 / gapNs * 0.7);
        }
        mLastFrameTimeNs = timestamp;
        final String sfps = String.format(Locale.getDefault(), "%.1f FPS", mFrameRate);
        final String sfl = String.format(Locale.getDefault(), "%.3f", fl);
        final String sexpotime =
                exposureTimeNs == null ?
                        "null ms" :
                        String.format(Locale.getDefault(), "%.2f ms",
                                exposureTimeNs / 1000000.0);
        String safMode;
        switch (afMode) {
            case CameraMetadata.CONTROL_AF_MODE_OFF:
                safMode = "AF locked";
                break;
            default:
                safMode = "AF unlocked";
                break;
        }
        final String saf = safMode;
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mFpsFocalLength.setText(sfps + " " + sfl);
                mExposureTime.setText(sexpotime);
                mAEAFstate.setText(saf);
            }
        });
    }

    @Override
    public void onPause() {

        // Stop background thread
        mCameraManager.stopBackgroundThread();
        // Close our camera, note we will get permission errors if we try to reopen
        // And we have not closed the current active camera
        mCameraManager.closeCamera();

        // Unregister the listeners
        mImuManager.unregister();

        // Stop background thread
        mGpsManager.stopBackgroundThread();
        // Remove gps listener
        mGpsManager.unregister();

        // Call the super
        super.onPause();
    }

    // Taken from OpenCamera project
    // URL: https://github.com/almalence/OpenCamera/blob/master/src/com/almalence/opencam/cameracontroller/Camera2Controller.java#L3455
    public final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader ir) {
            Image image = ir.acquireNextImage();
            Integer w = image.getWidth();
            Integer h = image.getHeight();
            mwxh.setText(w + " x " + h);
            // Save the file (if enabled)
            // http://stackoverflow.com/a/9006098
            if (MainActivity.is_recording) {
                Long timestamp = image.getTimestamp();
                String output_dir = mFileHelper.getStorageDir() + "/";
                saveImageTimestamp(timestamp, output_dir);

                // Create folder name
                String filename = timestamp + ".jpeg";
                output_dir = mFileHelper.getStorageDir() + "/images/";

                // Create export file
                new File(output_dir).mkdirs();
                File dest = new File(output_dir + filename);
                // TODO(jhuai): the offline method occasionally suffers from the
                // exception of the buffer size which is an arg to the
                // ImageReader constructor
                mCameraManager.mBackgroundHandler.post(
                        new ImageSaver(image, dest));
                // Alternative
//                new ImageSaver(image, dest).run();
            } else {
                image.close();
            }

        }
    };


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {

            // Disable the current recording session
            is_recording = false;

            // Also change the text on the button so that it turns into the start button
            Button button_record = (Button) findViewById(R.id.button_record);
            button_record.setText(R.string.start_record);

            // Start the settings activity
            Intent i = new Intent(this, SettingsActivity.class);
            startActivityForResult(i, RESULT_SETTINGS);

            return true;
        }

        if (id == R.id.action_info) {

            // Disable the current recording session
            is_recording = false;

            // Also change the text on the button so that it turns into the start button
            Button button_record = (Button) findViewById(R.id.button_record);
            button_record.setText(R.string.start_record);

            // Start the settings activity
            Intent i = new Intent(this, InfoActivity.class);
            startActivityForResult(i, RESULT_INFO);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {

            // Call back from end of settings activity
            case RESULT_SETTINGS:

                // The settings have changed, so reset the calibrator
                //mCameraCalibrator.clearCorners();

                // Update the textview with starting values
                //camera2Captured.setText("Capture Success: 0\nCapture Tries: 0");

                break;

            // Call back from end of settings activity
            case RESULT_RESULT:

                // The settings have changed, so reset the calibrator
                //mCameraCalibrator.clearCorners();

                // Update the textview with starting values
                //camera2Captured.setText("Capture Success: 0\nCapture Tries: 0");

                break;

        }

    }
}