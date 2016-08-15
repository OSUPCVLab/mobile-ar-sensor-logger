package io.rpng.recorder.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import io.rpng.recorder.R;

public class InfoActivity  extends AppCompatActivity {

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Pass to super
        super.onCreate(savedInstanceState);

        // Create our layout
        setContentView(R.layout.activity_info);

        // Add our button listeners
        addButtonListeners();

        // Get shared pref config
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    }

    private void addButtonListeners() {

        // When the done button is pressed we should end the result activity
        Button button_done = (Button) findViewById(R.id.button_done);
        button_done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InfoActivity.this.finish();
            }
        });
    }
}
