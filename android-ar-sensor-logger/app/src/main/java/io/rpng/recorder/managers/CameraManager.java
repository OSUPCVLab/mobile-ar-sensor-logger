package io.rpng.recorder.managers;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.rpng.recorder.R;
import io.rpng.recorder.activities.MainActivity;
import io.rpng.recorder.dialogs.ErrorDialog;
import io.rpng.recorder.utils.CameraUtil;
import io.rpng.recorder.utils.FileHelper;
import io.rpng.recorder.views.AutoFitTextureView;

public class CameraManager {

    // Our permissions we need to function
    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
    };

    private static final String TAG = "CameraManager";
    private static final String FRAGMENT_DIALOG = "dialog";
    private Activity activity;
    private PermissionManager permissionManager;
    private AutoFitTextureView mTextureView;

    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private Size mPreviewSize;
    private Size mVideoSize;
    private Integer mSensorOrientation;

    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSession;

    private float[] intrinsic = new float[5];
    private float[] distortion = new float[4];

    public volatile String mTimeBaseHint;
    private BufferedWriter mCameraInfoWriter;
    private String mCameraInfoAbsPath;

    /**
     * Default constructor
     * Sets our activity, and texture view we should be updating
     */
    public CameraManager(Activity act, AutoFitTextureView txt) {
        this.activity = act;
        this.mTextureView = txt;
        this.permissionManager = new PermissionManager(activity, VIDEO_PERMISSIONS, 1);
        this.mCameraInfoWriter = null;
        // http://qaru.site/questions/13032870/camera2-api-touch-to-focus
        // https://github.com/duanhong169/Camera/blob/53afdaa3efdb4c6ad9034641db22865e7db5c733/camera/src/main/java/top/defaults/camera/CameraView.java
        // https://gist.github.com/royshil/8c760c2485257c85a11cafd958548482
        this.mTextureView.setOnTouchListener(null);
    }

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a {@link TextureView}.
     */
    public final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            // TODO: Handle screen resizing/rotations
            //configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            //mCameraOpenCloseLock.release();
            //if (null != mTextureView) {
            //    configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            //}
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            //mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            //mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

    };

    @TargetApi(23)
    private void getLensParams(TotalCaptureResult result) {
        float[] intrinsic = result.get(CaptureResult.LENS_INTRINSIC_CALIBRATION);
        if (intrinsic != null)
            Log.d(TAG, "lens intrinsics fx " + intrinsic[0] +
                    " fy " + intrinsic[1] +
                    " cx " + intrinsic[2] +
                    " cy " + intrinsic[3] +
                    " s " + intrinsic[4]);
        float[] distort = result.get(CaptureResult.LENS_RADIAL_DISTORTION);
        if (distort != null)
            Log.d(TAG, "lens distortion k1 " + distort[0] +
                    " k2 " + distort[1] +
                    " k3 " + distort[2] +
                    " k4 " + distort[3] +
                    " \nk5 " + distort[4] +
                    " k6 " + distort[5]);
    }
    @TargetApi(23)
    private void getLensParams(CameraCharacteristics result) {
        float[] intrinsic = result.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
        if (intrinsic != null)
            Log.d(TAG, "char lens intrinsics fx " + intrinsic[0] +
                    " fy " + intrinsic[1] +
                    " cx " + intrinsic[2] +
                    " cy " + intrinsic[3] +
                    " s " + intrinsic[4]);
        float[] distort = result.get(CameraCharacteristics.LENS_RADIAL_DISTORTION);
        if (distort != null)
            Log.d(TAG, "char lens distortion k1 " + distort[0] +
                    " k2 " + distort[1] +
                    " k3 " + distort[2] +
                    " k4 " + distort[3] +
                    " \nk5 " + distort[4] +
                    " k6 " + distort[5]);
    }

    private String getTimestampSource(CameraCharacteristics cc) {
        Integer value = cc.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
        String warn_msg = "The camera timestamp source is unreliable to synchronize with motion sensors";
        if(value != null) {
            if(value.intValue() == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN) {
                String src_type = "unknown";
                Log.d(TAG, warn_msg + src_type);
                return src_type;
            } else if(value.intValue() == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) {
                return "realtime";
            } else {
                String src_type = "unknown (" + value.intValue() + ")";
                Log.d(TAG, warn_msg + src_type);
                return src_type;
            }
        }
        String src_type = "unknown";
        Log.d(TAG, warn_msg + src_type);
        return src_type;
    }

    public void prepareInfoWriter() {
        mCameraInfoAbsPath = MainActivity.mFileHelper.getCameraInfoAbsPath();
        mCameraInfoWriter = FileHelper.createBufferedWriter(mCameraInfoAbsPath);
        String header = "Timestamp[ns],frame No.,exposureTime[ns]," +
                "sensorFrameDuration[ns],frameReadoutTime[ns]," +
                "ISO,focal length,focus dist,AF mode";
        try {
            mCameraInfoWriter.write(header + "\n");
        } catch(IOException ioe) {
            System.err.println("IOException: " + ioe.getMessage());
        }
    }

    public void invalidateInfoWriter() {
        FileHelper.closeBufferedWriter(mCameraInfoWriter);
        mCameraInfoWriter = null;
        mCameraInfoAbsPath = null;
    }

    /**
     * Tries to open a {@link CameraDevice}.
     * The result is listened by `mStateCallback`.
     * We should already have permissions, so just try to open it
     */
    public void openCamera(int width, int height) {

        // Make sure we have permissions
        if(permissionManager.handle_permissions())
            return;

        // Note this is bad naming on my part...
        // There is an android class with the same name as this class
        // Thus we import this explicit instead of an import statement
        android.hardware.camera2.CameraManager manager = (android.hardware.camera2.CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            //Log.d(TAG, "tryAcquire");
            //if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
            //    throw new RuntimeException("Time out waiting to lock camera opening.");
            //}
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
            String cameraId = sharedPreferences.getString("prefCamera", "0");

            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            mVideoSize = CameraUtil.chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = CameraUtil.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, mVideoSize);

            // If we can get the intrinsics, great!
            // https://developer.android.com/reference/android/hardware/camera2/CaptureResult.html#LENS_INTRINSIC_CALIBRATION
            // https://developer.android.com/reference/android/hardware/camera2/CaptureResult.html#LENS_RADIAL_DISTORTION
            if(android.os.Build.VERSION.SDK_INT >= 23) {
                intrinsic = characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
                distortion = characteristics.get(CameraCharacteristics.LENS_RADIAL_DISTORTION);
            }

            getLensParams(characteristics);
            String time_src = getTimestampSource(characteristics);
            String hint = "#The CameraCharacteristics.SENSOR_INFO_" +
                    "TIMESTAMP_SOURCE is " + time_src + "\n" +
                    "#If SENSOR_INFO_TIMESTAMP_SOURCE is not realtime," +
                    " the camera CaptureResult.SENSOR_TIMESTAMP is " +
                    "assumed to be on the base of nanoTime() and " +
                    "SensorEvent.timestamp on the" +
                    " base of elapsedRealtimeNanos().\n" +
                    "#For syncing the camera frame timestamps to " +
                    "the inertial sensors, we record the timestamps" +
                    " from the two time basis at the start and the end" +
                    " of a recording session.";
            mTimeBaseHint = hint;
            Log.d(TAG, hint);

            // TODO(jhuai): disable the DISTORTION_CORRECTION_MODE_HIGH_QUALITY mode if needed, see https://medium.com/androiddevelopers/getting-the-most-from-the-new-multi-camera-api-5155fb3d77d9

            int orientation = activity.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }

            // Get image size from prefs
            String imageSize = sharedPreferences.getString("prefSizeRaw", "640x480");
            int widthRaw = Integer.parseInt(imageSize.substring(0,imageSize.lastIndexOf("x")));
            int heightRaw = Integer.parseInt(imageSize.substring(imageSize.lastIndexOf("x")+1));

            // Create the image reader which will be called back to, to get image frames
            mImageReader = ImageReader.newInstance(widthRaw, heightRaw, ImageFormat.YUV_420_888, 3);
            mImageReader.setOnImageAvailableListener(MainActivity.imageAvailableListener, null);


            // The orientation is a multiple of 90 value that rotates into the native orientation
            // This should fix cameras that are rotated in landscape
//            mTextureView.setRotation(mSensorOrientation);

            // Finally actually open the camera
            manager.openCamera(cameraId, mStateCallback, null);


        } catch (CameraAccessException e) {
            e.printStackTrace();
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            activity.finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the device this code runs.
            ErrorDialog.newInstance(activity.getString(R.string.camera_error)).show(activity.getFragmentManager(), FRAGMENT_DIALOG);
        } catch (SecurityException e) {
            e.printStackTrace();
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            activity.finish();
        }
        //catch (InterruptedException e) {
        //    throw new RuntimeException("Interrupted while trying to lock camera opening.");
        //}
    }


    private void startPreview() {
        if (mCameraDevice == null ||!mTextureView.isAvailable() || mPreviewSize == null || mImageReader == null) {
            return;
        }
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            // mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            // Set control elements, we want auto exposure and white balance
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);

            // Get the focal length
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
            String focus = sharedPreferences.getString("prefFocusLength", "5.0");
            mPreviewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, Float.parseFloat(focus));


            // Create the surface we want to render to (this preview surface is required)
            List surfaces = new ArrayList<>();
            Surface readerSurface = mImageReader.getSurface();
            surfaces.add(readerSurface);
            mPreviewBuilder.addTarget(readerSurface);

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            mPreviewSession = cameraCaptureSession;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession){
                            Log.w(TAG, "Create capture session failed");
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request,
                                               TotalCaptureResult result) {

                    long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
                    long number = result.getFrameNumber();
                    long exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    long frmDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION);
                    long frmReadoutNs = result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW);
                    Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);

                    float fl = result.get(CaptureResult.LENS_FOCAL_LENGTH);
                    float fd = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
                    Integer afMode = result.get(CaptureResult.CONTROL_AF_MODE);
                    char delimiter = ',';
                    String frame_info = String.valueOf(timestamp) + delimiter
                            + String.valueOf(number) + delimiter
                            + String.valueOf(exposureTimeNs) + delimiter
                            + String.valueOf(frmDurationNs) + delimiter
                            + String.valueOf(frmReadoutNs)  + delimiter
                            + String.valueOf(iso) + delimiter
                            + Float.toString(fl) + delimiter
                            + Float.toString(fd) + delimiter
                            + String.valueOf(afMode);
                    if (MainActivity.is_recording) {
                        try {
                            mCameraInfoWriter.write(frame_info + "\n");
                        } catch (IOException ioe) {
                            System.err.println("IOException: " + ioe.getMessage());
                        }
                    }
                    getLensParams(result);
                }

                @Override
                public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                                CaptureResult partialResult) {
                    Log.d(TAG, "mSessionCaptureCallback,  onCaptureProgressed");
                }

            };


    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(
                    mPreviewBuilder.build(),
                    mSessionCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void closeCamera() {
        //try {
        //mCameraOpenCloseLock.acquire();

        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }

        //}
        //catch (InterruptedException e) {
        //    throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        //} finally {
        //    mCameraOpenCloseLock.release();
        //}
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
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

    public float[] getIntrinsic() {
        if(intrinsic != null)
            return intrinsic;
        return new float[5];
    }

    public float[] getDistortion() {
        if(distortion != null)
            return distortion;
        return new float[4];
    }

}
