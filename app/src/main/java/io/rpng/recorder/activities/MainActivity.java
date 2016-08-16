package io.rpng.recorder.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.rpng.recorder.managers.CameraManager;
import io.rpng.recorder.R;
import io.rpng.recorder.managers.GPSManager;
import io.rpng.recorder.managers.IMUManager;
import io.rpng.recorder.views.AutoFitTextureView;


public class MainActivity extends AppCompatActivity {

    private static String TAG = "MainActivity";
    private static final int RESULT_SETTINGS = 1;
    private static final int RESULT_RESULT = 2;
    private static final int RESULT_INFO = 3;

    private static Intent intentSettings;
    private static Intent intentResults;

    private static ImageView camera2View;
    private AutoFitTextureView mTextureView;

    public static CameraManager mCameraManager;
    public static IMUManager mImuManager;
    public static GPSManager mGpsManager;
    private static SharedPreferences sharedPreferences;


    // Variables for the current state
    public static boolean is_recording;
    public static String folder_name;

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
        camera2View = (ImageView) findViewById(R.id.camera2_preview);
        mTextureView = (AutoFitTextureView) findViewById(R.id.camera2_texture);

        // Create the camera manager
        mCameraManager = new CameraManager(this, mTextureView, camera2View);
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

        // Lets by default launch into the settings view
        startActivityForResult(intentSettings, RESULT_SETTINGS);

    }

    private void addButtonListeners() {

        // We we want to "capture" the current grid, we should record the current corners
        Button button_record = (Button) findViewById(R.id.button_record);
        button_record.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If we are not recording we should start it
                if(!is_recording) {
                    // Set our folder name
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yy-MM-dd HH:mm:ss");
                    folder_name = dateFormat.format(new Date());

                    // Also change the text on the button so that it turns into the stop button
                    Button button_record = (Button) findViewById(R.id.button_record);
                    button_record.setText("Stop Recording");

                    // Trigger the recording by changing the recording boolean
                    is_recording = true;
                }
                // Else we can assume we pressed the "stop recording" button
                else {
                    // Just reset the recording button
                    is_recording = false;

                    // Also change the text on the button so that it turns into the start button
                    Button button_record = (Button) findViewById(R.id.button_record);
                    button_record.setText("Start Recording");

                    // Start the result activity
                    //startActivityForResult(intentResults, RESULT_RESULT);
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
    public final static ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader ir) {

            // Contrary to what is written in Aptina presentation acquireLatestImage is not working as described
            // Google: Also, not working as described in android docs (should work the same as acquireNextImage in
            // our case, but it is not)
            // Image im = ir.acquireLatestImage();

            // Get the next image from the queue
            Image image = ir.acquireNextImage();

            // Collection of bytes of the image
            byte[] rez;

            // Convert to NV21 format
            // https://github.com/bytedeco/javacv/issues/298#issuecomment-169100091
            ByteBuffer buffer0 = image.getPlanes()[0].getBuffer();
            ByteBuffer buffer2 = image.getPlanes()[2].getBuffer();
            int buffer0_size = buffer0.remaining();
            int buffer2_size = buffer2.remaining();
            rez = new byte[buffer0_size + buffer2_size];

            // Load the final data var with the actual bytes
            buffer0.get(rez, 0, buffer0_size);
            buffer2.get(rez, buffer0_size, buffer2_size);

            // Byte output stream, so we can save the file
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Create YUV image file
            YuvImage yuvImage = new YuvImage(rez, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 90, out);
            byte[] imageBytes = out.toByteArray();

            // Display for the end user
            Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            MainActivity.camera2View.setImageBitmap(bmp);

            // Save the file (if enabled)
            // http://stackoverflow.com/a/9006098
            if(MainActivity.is_recording) {

                // Create folder name
                String filename = image.getTimestamp() + ".jpeg";
                String path = Environment.getExternalStorageDirectory().getAbsolutePath()
                        + "/dataset_recorder/" + MainActivity.folder_name + "/images/";

                // Create export file
                new File(path).mkdirs();
                File dest = new File(path + filename);

                // Export the file to disk
                try {
                    FileOutputStream output = new FileOutputStream(dest);
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, output);
                    output.flush();
                    output.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Make sure we close the image
            image.close();
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
            button_record.setText("Start Recording");

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
            button_record.setText("Start Recording");

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
