package io.rpng.recorder.managers;

import android.Manifest;
import android.app.Activity;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import io.rpng.recorder.activities.MainActivity;

public class GPSManager implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {


    // Our permissions we need to function
    private static final String[] PERMISSIONS = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    };

    // Define a request code to send to Google Play services
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    // Activity
    private Activity activity;
    private PermissionManager permissionManager;

    // Sensor listeners
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    public GPSManager(Activity activity) {
        // Set activity
        this.activity = activity;
        // Create permission manager
        this.permissionManager = new PermissionManager(activity, PERMISSIONS);
        // Create the google api client
        mGoogleApiClient = new GoogleApiClient.Builder(activity)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        // Create the location request
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds
    }


    @Override
    public void onLocationChanged(Location location) {
        // Get accuracy
        float accuracy = location.getAccuracy();
        // Get altitude
        double altitude = location.getAltitude();
        // Lat Lon
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        Log.e("GPS MANAGER", location.toString());
        //System.out.println(location.getTime() + "," + lat + "," + lon + "," + altitude + "," + accuracy);
        //System.err.println(location.getTime() + "," + lat + "," + lon + "," + altitude + "," + accuracy);

        // Write the data to file if we are recording
        if (MainActivity.is_recording) {

            // Create folder name
            String filename = "gps_data.txt";
            String path = Environment.getExternalStorageDirectory().getAbsolutePath()
                    + "/dataset_recorder/" + MainActivity.folder_name + "/";

            // Create export file
            new File(path).mkdirs();
            File dest = new File(path + filename);

            try {
                // If the file does not exist yet, create it
                if (!dest.exists())
                    dest.createNewFile();

                // The true will append the new data
                BufferedWriter writer = new BufferedWriter(new FileWriter(dest, true));

                // Master string of information
                String data = location.getTime() + "," + lat + "," + lon + "," + altitude + "," + accuracy;

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

    }

    /**
     * This will register all GPS listeners
     */
    public void register() {
        // Make sure we have permissions
        permissionManager.handle_permissions();
        // Connect to the google play services
        mGoogleApiClient.connect();
    }

    /**
     * This will unregister all GPS listeners
     */
    public void unregister() {
        // Remove the listener if connected
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        // Make sure we have permissions
        if(permissionManager.handle_permissions())
            return;

        // Get the latest location
        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        // If we do not have locations, we should request an update
        if (location == null) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        } else {
            onLocationChanged(location);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(activity, CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
                 * Thrown if Google Play services canceled the original
                 * PendingIntent
                 */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }

            // Debug
            Log.i("GPS MANGER", "Location services connection failed with code " + connectionResult.getErrorCode());
        } else {
            /*
             * If no resolution is available, display a dialog to the
             * user with the error.
             */
            Log.i("GPS MANGER", "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

}
