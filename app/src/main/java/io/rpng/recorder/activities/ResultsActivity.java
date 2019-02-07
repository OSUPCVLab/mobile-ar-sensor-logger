package io.rpng.recorder.activities;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import io.rpng.recorder.R;


public class ResultsActivity extends AppCompatActivity {

    private TextView mTextResults;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Pass to super
        super.onCreate(savedInstanceState);

        // Create our layout
        setContentView(R.layout.activity_results);

        // Add our button listeners
        addButtonListeners();

        // Get the text view we will display our results on
        mTextResults = (TextView) findViewById(R.id.text_results);

        // Get shared pref config
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Run the async calibration
        run_calibration();

    }

    private void addButtonListeners() {

        // When the done button is pressed we should end the result activity
        Button button_done = (Button) findViewById(R.id.button_done);
        button_done.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ResultsActivity.this.finish();
            }
        });

        // When this is clicked we should save the settings file
        Button button_save = (Button) findViewById(R.id.button_save);
        button_save.setEnabled(false);
        button_save.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
    }

    // Taken from the calibration example, will run in async tast
    // https://github.com/Itseez/opencv/blob/master/samples/android/camera-calibration/src/org/opencv/samples/cameracalibration/CameraCalibrationActivity.java#L154-L188
    private void run_calibration() {

        final Resources res = getResources();

        new AsyncTask<Void, Void, Void>() {
            private ProgressDialog calibrationProgress;

            @Override
            protected void onPreExecute() {
                calibrationProgress = new ProgressDialog(ResultsActivity.this);
                calibrationProgress.setTitle("Calibrating");
                calibrationProgress.setMessage("Please Wait");
                calibrationProgress.setCancelable(false);
                calibrationProgress.setIndeterminate(true);
                calibrationProgress.show();
            }

            @Override
            protected Void doInBackground(Void... arg0) {
                try {
                    //MainActivity.mCameraCalibrator.calibrate();
                } catch(Exception e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {

                // Dismiss the processing popup
                calibrationProgress.dismiss();

                // Reset everything
                //MainActivity.mCameraCalibrator.clearCorners();

            }
        }.execute();
    }

}
