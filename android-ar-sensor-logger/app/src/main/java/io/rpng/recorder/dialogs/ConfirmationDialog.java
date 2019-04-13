package io.rpng.recorder.dialogs;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v13.app.ActivityCompat;


import io.rpng.recorder.R;

public class ConfirmationDialog extends DialogFragment {

    private int mRequestPermission;
    private Activity mMainActivity;
    private String[] mPermissions;
    public ConfirmationDialog() {

    }

    public DialogFragment setArguments(Activity activity, String[] permissions, int request_code) {
        mMainActivity = activity;
        mPermissions = permissions;
        mRequestPermission = request_code;
        return this;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int strref = R.string.request_none_permission;
        switch (mRequestPermission) {
            case 1:
                strref = R.string.request_cam_permission;
                break;
            case 2:
                strref = R.string.request_gps_permission;
                break;
        }

        return new AlertDialog.Builder(getActivity())
                .setMessage(strref)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(mMainActivity,
                                mPermissions, mRequestPermission);
                    }
                })
                .setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mMainActivity.finish();
                            }
                        })
                .create();
    }

}
