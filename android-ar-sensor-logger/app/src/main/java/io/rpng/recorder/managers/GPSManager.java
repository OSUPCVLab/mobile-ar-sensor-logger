package io.rpng.recorder.managers;

import android.Manifest;
import android.app.Activity;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
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
import java.util.Date;

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

    // Threading for listener
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    // Sensor listeners
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    public GPSManager(Activity activity) {
        // Set activity
        this.activity = activity;
        // Create permission manager
        this.permissionManager = new PermissionManager(activity, PERMISSIONS, 2);
        // Create the google api client
        mGoogleApiClient = new GoogleApiClient.Builder(activity.getBaseContext())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
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

        Log.i("GPS MANAGER", location.toString());
        //System.out.println(location.getTime() + "," + lat + "," + lon + "," + altitude + "," + accuracy);
        //System.err.println(location.getTime() + "," + lat + "," + lon + "," + altitude + "," + accuracy);

        // Write the data to file if we are recording
        if (MainActivity.is_recording) {

            // Create folder name
            String filename = "data_gps.txt";
            String path = MainActivity.mFileHelper.getStorageDir() + "/";

            // Create export file
            File dest = new File(path + filename);

            try {
                // If the file does not exist yet, create it
                if (!dest.exists())
                    dest.createNewFile();

                // The true will append the new data
                BufferedWriter writer = new BufferedWriter(new FileWriter(dest, true));

                // Master string of information
                String data = location.getElapsedRealtimeNanos() + "," + lat + "," + lon + "," + altitude + "," + accuracy
                        + "," + location.getBearing() + "," + location.getSpeed() + "," + location.getProvider();

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
        // Get how fast we want to get GPS updates
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        String gpsFreq = sharedPreferences.getString("perfGPSFreq", "1");
        // Create the location request
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval((long)(Double.parseDouble(gpsFreq) * 1000))         // seconds, in milliseconds
                .setFastestInterval((long)(Double.parseDouble(gpsFreq) * 1000)); // seconds, in milliseconds
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
        if (permissionManager.handle_permissions())
            return;

        try {
            // Get the latest location
            Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

            // If we do not have locations, we should request an update
            if (location != null) {
                onLocationChanged(location);
            }

            // Request a location update
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
        // This right here is just so the IDE will stop giving me errors
        // The permissionManager will handle this permission problem
        catch(SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
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

    /**
     * Starts a background thread and its {@link Handler}.
     */
    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("GPSBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    public void stopBackgroundThread() {
        try {
            mBackgroundThread.quitSafely();
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
