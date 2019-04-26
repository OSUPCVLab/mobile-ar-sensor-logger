package io.rpng.recorder.managers;

import android.Manifest;
import android.app.Activity;

import android.content.SharedPreferences;

import android.location.Location;

import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;

import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;

import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import io.rpng.recorder.activities.MainActivity;

public class GPSManager {

    private FusedLocationProviderClient fusedLocationClient;
    private Boolean requestingLocationUpdates;
    private LocationCallback locationCallback;
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

    private LocationRequest mLocationRequest;

    public GPSManager(Activity activity) {
        // Set activity
        this.activity = activity;
        // Create permission manager
        this.permissionManager = new PermissionManager(activity, PERMISSIONS, 2);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
        requestingLocationUpdates = true;

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    onLocationChanged(location);
                }
            };
        };
    }

    private void onLocationChanged(Location location) {
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
        if (requestingLocationUpdates) {
            startLocationUpdates();
            requestingLocationUpdates = false;
        }
    }

    /**
     * This will unregister all GPS listeners
     */
    public void unregister() {
        stopLocationUpdates();
        requestingLocationUpdates = true;
    }

    private void startLocationUpdates() {
        try {
            // Get the latest location
            Task<Location> task = fusedLocationClient.getLastLocation();
            task.addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if(location!=null) {
                        onLocationChanged(location);
                        Log.d("AndroidClarified",location.getLatitude()+" "+location.getLongitude());      }
                }
            });

            fusedLocationClient.requestLocationUpdates(mLocationRequest,
                    locationCallback,
                    mBackgroundThread.getLooper());

        }
        // This right here is just so the IDE will stop giving me errors
        // The permissionManager will handle this permission problem
        catch(SecurityException e) {
            e.printStackTrace();
        }
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }


    /**
     * Starts a background thread and its {@link Handler}.
     */
    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("GPSBackground");
        mBackgroundThread.start();
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    public void stopBackgroundThread() {
        try {
            mBackgroundThread.quitSafely();
            mBackgroundThread.join();
            mBackgroundThread = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
