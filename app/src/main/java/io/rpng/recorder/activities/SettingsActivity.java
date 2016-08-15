package io.rpng.recorder.activities;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Size;

import io.rpng.recorder.R;
import io.rpng.recorder.dialogs.ErrorDialog;

// Taken from => http://stackoverflow.com/a/13441715
public class SettingsActivity extends PreferenceActivity
{
    @Override
    protected void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener
    {
        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            // Call super
            super.onCreate(savedInstanceState);

            // Load the pref file from our xml folder
            addPreferencesFromResource(R.xml.settings);

            // Make it so that we listen to change events
            PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);

            // Get our exit button
            Preference button = (Preference)getPreferenceManager().findPreference("exitlink");

            // Add a listener for when the finish button is pressed
            if (button != null) {
                button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference arg0) {
                        // When we click this button, exit the pref activity
                        getActivity().finish();
                        // Return success
                        return true;
                    }
                });
            }

            // Current prefs, if this is called mid usage, we would want to use this to get our active settings
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

            // Get our camera id list preference
            ListPreference cameraList = (ListPreference)getPreferenceManager().findPreference("prefCamera");
            ListPreference cameraRez = (ListPreference)getPreferenceManager().findPreference("prefSizeRaw");
            ListPreference cameraFocus = (ListPreference)getPreferenceManager().findPreference("prefFocusLength");

            try {
                // Load our camera settings
                Activity activity = getActivity();
                CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
                // Our two values we need to update
                int cameraSize = manager.getCameraIdList().length;
                CharSequence[] entries = new CharSequence[cameraSize];
                CharSequence[] entriesValues = new CharSequence[cameraSize];
                // Loop through our camera list
                for (int i=0; i<manager.getCameraIdList().length; i++) {
                    // Get the camera
                    String cameraId = manager.getCameraIdList()[i];
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                    // Try to find what direction it is pointing
                    try {
                        // Check to see if the camera is facing the back, front, or external
                        if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK) {
                            entries[i] = cameraId + " - Lens Facing Back";
                        } else if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT) {
                            entries[i] = cameraId + " - Lens Facing Front";
                        } else {
                            entries[i] = cameraId + " - Lens External";
                        }
                    } catch(NullPointerException e) {
                        e.printStackTrace();
                        entries[i] = cameraId + " - Lens Facing Unknown";
                    }
                    // Set the value to just the camera id
                    entriesValues[i] = cameraId;
                }

                // Update our settings entry
                cameraList.setEntries(entries);
                cameraList.setEntryValues(entriesValues);
                cameraList.setDefaultValue(entriesValues[0]);;


                // Right now we have selected the first camera, so lets populate the resolution list
                // We should just use the default if there is not a shared setting yet
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(sharedPreferences.getString("prefCamera", entriesValues[0].toString()));
                StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] sizes = streamConfigurationMap.getOutputSizes(MediaRecorder.class);

                // Our new rez entries
                int rezSize = sizes.length;
                CharSequence[] rez = new CharSequence[rezSize];
                CharSequence[] rezValues = new CharSequence[rezSize];

                // Loop through and create our entries
                for(int i=0; i<sizes.length; i++) {
                    rez[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                    rezValues[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                }

                // Update our settings entry
                cameraRez.setEntries(rez);
                cameraRez.setEntryValues(rezValues);
                cameraRez.setDefaultValue(rezValues[0]);

                // Get the possible focus lengths, on non-optical devices this only has one value
                // https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics.html#LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                float[] focus_lengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                CharSequence[] focuses = new CharSequence[focus_lengths.length];
                for(int i=0; i<focus_lengths.length; i++) {
                    focuses[i]  = focus_lengths[i] + "";
                }

                cameraFocus.setEntries(focuses);
                cameraFocus.setEntryValues(focuses);
                cameraFocus.setDefaultValue(focuses[0]);
                cameraFocus.setValueIndex(0);


            } catch (CameraAccessException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                // Currently an NPE is thrown when the Camera2API is used but not supported on the device this code runs.
                ErrorDialog.newInstance(getString(R.string.camera_error)).show(getActivity().getFragmentManager(), "dialog");
            }


        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

            // Add the listener to the camera pref, so we can update the camera resolution field
            if(key.equals("prefCamera")) {
                try {

                    // Get what camera we have selected
                    String cameraId = sharedPreferences.getString("prefCamera", "0");

                    // Load our camera settings
                    Activity activity = getActivity();
                    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

                    // Right now we have selected the first camera, so lets populate the resolution list
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                    StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    Size[] sizes = streamConfigurationMap.getOutputSizes(MediaRecorder.class);

                    // Our new rez entries
                    int rezSize = sizes.length;
                    CharSequence[] rez = new CharSequence[rezSize];
                    CharSequence[] rezValues = new CharSequence[rezSize];

                    // Loop through and create our entries
                    for(int i=0; i<sizes.length; i++) {
                        rez[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                        rezValues[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                    }

                    // Update our settings entry
                    ListPreference cameraRez = (ListPreference)getPreferenceManager().findPreference("prefSizeRaw");
                    cameraRez.setEntries(rez);
                    cameraRez.setEntryValues(rezValues);
                    cameraRez.setDefaultValue(rezValues[0]);
                    cameraRez.setValueIndex(0);

                    // Get the possible focus lengths, on non-optical devices this only has one value
                    // https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics.html#LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                    float[] focus_lengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    CharSequence[] focuses = new CharSequence[focus_lengths.length];
                    for(int i=0; i<focus_lengths.length; i++) {
                        focuses[i]  = focus_lengths[i] + "";
                    }

                    ListPreference cameraFocus = (ListPreference)getPreferenceManager().findPreference("prefFocusLength");
                    cameraFocus.setEntries(focuses);
                    cameraFocus.setEntryValues(focuses);
                    cameraFocus.setDefaultValue(focuses[0]);
                    cameraFocus.setValueIndex(0);


                } catch (CameraAccessException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    // Currently an NPE is thrown when the Camera2API is used but not supported on the device this code runs.
                    ErrorDialog.newInstance(getString(R.string.camera_error)).show(getActivity().getFragmentManager(), "dialog");
                }

            }

        }
    }
}