package edu.osu.pcv.marslogger;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;

import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;

import android.util.Range;
import android.util.Size;

import android.widget.Toast;

/**
 * Activities that contain this fragment must implement the
 * {@link SettingsFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link SettingsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */

public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private OnFragmentInteractionListener mListener;
    private Range<Integer> isoRange = null;
    private Range<Float> exposureTimeRangeMs = null;

    public SettingsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment SettingsFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static SettingsFragment newInstance(String param1, String param2) {
        SettingsFragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);

        PreferenceManager.getDefaultSharedPreferences(
                getActivity()).registerOnSharedPreferenceChangeListener(this);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                getActivity());

        ListPreference cameraList = (ListPreference)
                getPreferenceManager().findPreference("prefCamera");
        ListPreference cameraRez = (ListPreference)
                getPreferenceManager().findPreference("prefSizeRaw");
//        ListPreference cameraFocus = (ListPreference)
//                getPreferenceManager().findPreference("prefFocusDistance");

        EditTextPreference prefISO = (EditTextPreference)
                getPreferenceScreen().findPreference("prefISO");
        EditTextPreference prefExposureTime = (EditTextPreference)
                getPreferenceScreen().findPreference("prefExposureTime");

        prefISO.setOnPreferenceChangeListener(checkISOListener);
        try {
            Activity activity = getActivity();
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            int cameraSize = manager.getCameraIdList().length;
            CharSequence[] entries = new CharSequence[cameraSize];
            CharSequence[] entriesValues = new CharSequence[cameraSize];
            for (int i = 0; i < manager.getCameraIdList().length; i++) {
                String cameraId = manager.getCameraIdList()[i];
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                try {
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                            CameraMetadata.LENS_FACING_BACK) {
                        entries[i] = cameraId + " - Lens Facing Back";
                    } else if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                            CameraMetadata.LENS_FACING_FRONT) {
                        entries[i] = cameraId + " - Lens Facing Front";
                    } else {
                        entries[i] = cameraId + " - Lens External";
                    }
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    entries[i] = cameraId + " - Lens Facing Unknown";
                }
                entriesValues[i] = cameraId;
            }

            // Update our settings entry
            cameraList.setEntries(entries);
            cameraList.setEntryValues(entriesValues);
            cameraList.setDefaultValue(entriesValues[0]);
            // Do not call "cameraList.setValueIndex(0)" which will invoke onSharedPreferenceChanged
            // if the previous camera is not 0, and cause null pointer exception.

            // Right now we have selected the first camera, so lets populate the resolution list
            // We should just use the default if there is not a shared setting yet
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(
                    sharedPreferences.getString("prefCamera", entriesValues[0].toString()));
            StreamConfigurationMap streamConfigurationMap = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamConfigurationMap.getOutputSizes(MediaRecorder.class);

            int rezSize = sizes.length;
            CharSequence[] rez = new CharSequence[rezSize];
            CharSequence[] rezValues = new CharSequence[rezSize];
            int defaultIndex = 0;
            for (int i = 0; i < sizes.length; i++) {
                rez[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                rezValues[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                if (sizes[i].getWidth() + sizes[i].getHeight() ==
                        DesiredCameraSetting.mDesiredFrameWidth +
                                DesiredCameraSetting.mDesiredFrameHeight) {
                    defaultIndex = i;
                }
            }

            cameraRez.setEntries(rez);
            cameraRez.setEntryValues(rezValues);
            cameraRez.setDefaultValue(rezValues[defaultIndex]);

            isoRange = characteristics.get(
                    CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            if (isoRange != null) {
                String rangeStr = "[" + isoRange.getLower() + "," + isoRange.getUpper() + "] (1)";
                prefISO.setDialogTitle("Adjust ISO in range " + rangeStr);
            }

            Range<Long> exposureTimeRangeNs = characteristics.get(
                    CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            if (exposureTimeRangeNs != null) {
                exposureTimeRangeMs = new Range<Float>(
                        new Float(exposureTimeRangeNs.getLower().floatValue() / 1e6),
                        new Float(exposureTimeRangeNs.getUpper().floatValue() / 1e6));
                String rangeStr = "[" + exposureTimeRangeMs.getLower() + "," +
                        exposureTimeRangeMs.getUpper() + "] (ms)";
                prefExposureTime.setDialogTitle("Adjust exposure time in range " + rangeStr);
            }

            // Get the possible focus lengths, on non-optical devices this only has one value
            // https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics.html#LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            float[] focus_lengths = characteristics.get(
                    CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            CharSequence[] focuses = new CharSequence[focus_lengths.length];
            for (int i = 0; i < focus_lengths.length; i++) {
                focuses[i] = focus_lengths[i] + "";
            }

//            cameraFocus.setEntries(focuses);
//            cameraFocus.setEntryValues(focuses);
//            cameraFocus.setDefaultValue(focuses[0]);

        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks that a preference is a valid numerical value
     */
    Preference.OnPreferenceChangeListener checkISOListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            //Check that the string is an integer.
            return checkIso(newValue);
        }
    };

    private boolean checkIso(Object newValue) {
        if (!newValue.toString().equals("") && newValue.toString().matches("\\d*")) {
            return true;
        } else {
            Toast.makeText(getActivity(),
                    newValue + " is not a valid number!", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("prefCamera")) {
            try {
                String cameraId = sharedPreferences.getString("prefCamera", "0");

                Activity activity = getActivity();
                CameraManager manager = (CameraManager)
                        activity.getSystemService(Context.CAMERA_SERVICE);

                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap streamConfigurationMap = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] sizes = streamConfigurationMap.getOutputSizes(MediaRecorder.class);

                int rezSize = sizes.length;
                CharSequence[] rez = new CharSequence[rezSize];
                CharSequence[] rezValues = new CharSequence[rezSize];
                int defaultIndex = 0;
                for (int i = 0; i < sizes.length; i++) {
                    rez[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                    rezValues[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                    if (sizes[i].getWidth() + sizes[i].getHeight() ==
                            DesiredCameraSetting.mDesiredFrameWidth +
                                    DesiredCameraSetting.mDesiredFrameHeight) {
                        defaultIndex = i;
                    }
                }

                ListPreference cameraRez = (ListPreference)
                        getPreferenceManager().findPreference("prefSizeRaw");
                cameraRez.setEntries(rez);
                cameraRez.setEntryValues(rezValues);
                cameraRez.setValueIndex(defaultIndex);

                float[] focus_lengths = characteristics.get(
                        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                CharSequence[] focuses = new CharSequence[focus_lengths.length];
                for (int i = 0; i < focus_lengths.length; i++) {
                    focuses[i] = focus_lengths[i] + "";
                }

//                ListPreference cameraFocus = (ListPreference)getPreferenceManager().findPreference("prefFocusDistance");
//                cameraFocus.setEntries(focuses);
//                cameraFocus.setEntryValues(focuses);
//                cameraFocus.setValueIndex(0);

            } catch (CameraAccessException | NullPointerException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(Uri uri);
    }
}
