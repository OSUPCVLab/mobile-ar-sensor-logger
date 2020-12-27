package edu.osu.pcv.marslogger;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraMetadata;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;

import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import timber.log.Timber;


/**
 * Dependency relations between the key components:
 * CameraSurfaceRenderer onSurfaceCreated depends on mCameraHandler, and eventually mCamera2Proxy
 * mCamera2Proxy initialization depends on onRequestPermissionsResult
 *
 * The order of calls in requesting permission inside onCreate()
 * activity.onCreate() -> requestCameraPermission()
 * activity.onResume()
 * activity.onPause()
 * activity.onRequestPermissionsResult()
 * activity.onResume()
 */
public class PhotoCaptureActivity extends CameraCaptureActivityBase
        implements AdapterView.OnItemSelectedListener {
    private CameraSurfaceRenderer mRenderer = null;
    private TextView mOutputDirText;

    private String mSnapshotOutputDir = null;
    private boolean mSnap = false;
    private int mSnapNumber = 0;

    private CameraHandler mCameraHandler;

    private TextureMovieEncoder sVideoEncoder = new TextureMovieEncoder();
    private IMUManager mImuManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Occasionally some device show landscape views despite the portrait in manifest. See
        // https://stackoverflow.com/questions/47228194/android-8-1-screen-orientation-issue-flipping-to-landscape-a-portrait-screen
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        setContentView(R.layout.activity_photo_capture);
        mSnapshotMode = true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        mCamera2Proxy = new Camera2Proxy(this);
        Size previewSize = mCamera2Proxy.configureCamera();
        setLayoutAspectRatio(previewSize);
        Size videoSize = mCamera2Proxy.getmVideoSize();
        mVideoFrameWidth = videoSize.getWidth();
        mVideoFrameHeight = videoSize.getHeight();

        // Define a handler that receives camera-control messages from other threads.  All calls
        // to Camera must be made on the same thread.  Note we create this before the renderer
        // thread, so we know the fully-constructed object will be visible.
        mCameraHandler = new CameraHandler(this, true);

        // Configure the GLSurfaceView.  This will start the Renderer thread, with an
        // appropriate EGL context.
        mGLView = (SampleGLView) findViewById(R.id.cameraPreview_surfaceView);
        if (mRenderer == null) {
            mRenderer = new CameraSurfaceRenderer(
                    mCameraHandler, sVideoEncoder);
            mGLView.setEGLContextClientVersion(2);     // select GLES 2.0
            mGLView.setRenderer(mRenderer);
            mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
        mGLView.setTouchListener((event, width, height) -> {
            ManualFocusConfig focusConfig =
                    new ManualFocusConfig(event.getX(), event.getY(), width, height);
            Timber.d(focusConfig.toString());
            mCameraHandler.sendMessage(
                    mCameraHandler.obtainMessage(CameraHandler.MSG_MANUAL_FOCUS, focusConfig));
        });
        if (mImuManager == null) {
            mImuManager = new IMUManager(this);
        }
        mKeyCameraParamsText = (TextView) findViewById(R.id.cameraParams_text);
        mCaptureResultText = (TextView) findViewById(R.id.captureResult_text);
        mOutputDirText = (TextView) findViewById(R.id.cameraOutputDir_text);
    }

    @Override
    protected void onResume() {
        Timber.d("onResume -- acquiring camera");
        super.onResume();
        Timber.d("Keeping screen on for previewing recording.");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (mCamera2Proxy == null) {
            mCamera2Proxy = new Camera2Proxy(this);
            Size previewSize = mCamera2Proxy.configureCamera();
            setLayoutAspectRatio(previewSize);
            Size videoSize = mCamera2Proxy.getmVideoSize();
            mVideoFrameWidth = videoSize.getWidth();
            mVideoFrameHeight = videoSize.getHeight();
        }

        mGLView.onResume();
        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                mRenderer.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight);
                mRenderer.setVideoFrameSize(mVideoFrameWidth, mVideoFrameHeight);
            }
        });
        mImuManager.register();
    }

    @Override
    protected void onPause() {
        Timber.d("onPause -- releasing camera");
        super.onPause();
        // no more frame metadata will be saved during pause
        if (mCamera2Proxy != null) {
            mCamera2Proxy.releaseCamera();
            mCamera2Proxy = null;
        }

        mSnapshotOutputDir = null;
        mSnap = false;
        mSnapNumber = 0;

        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                // Tell the renderer that it's about to be paused so it can clean up.
                mRenderer.notifyPausing();
            }
        });
        mGLView.onPause();
        mImuManager.unregister();
        Timber.d("onPause complete");
    }

    @Override
    protected void onDestroy() {
        Timber.d("onDestroy");
        super.onDestroy();
        mCameraHandler.invalidateHandler();     // paranoia
    }

    // spinner selected
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        Spinner spinner = (Spinner) parent;
        final int filterNum = spinner.getSelectedItemPosition();

        Timber.d("onItemSelected: %d", filterNum);
        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                // notify the renderer that we want to change the encoder's state
                mRenderer.changeFilterMode(filterNum);
            }
        });
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    //    https://github.com/almalence/OpenCamera/blob/master/src/com/almalence/opencam/cameracontroller/Camera2Controller.java#L3455
//    https://stackoverflow.com/questions/34664131/camera2-imagereader-freezes-repeating-capture-request
    public final ImageReader.OnImageAvailableListener mImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader ir) {
                    if (mSnap) {
                        Image image = ir.acquireNextImage();
                        Long timestamp = image.getTimestamp();
                        String outputFile = mSnapshotOutputDir + File.separator + timestamp.toString() + ".jpg";
                        File dest = new File(outputFile);
                        Timber.d("Saving image to %s", outputFile);
                        new ImageSaver(image, dest).run();
                        mSnap = false;
                        ++mSnapNumber;
                        mCamera2Proxy.pauseRecordingCaptureResult();
                    } else {
                        Image image = ir.acquireLatestImage();
                        image.close();
                    }
                }
            };

    public void clickSnapshot(@SuppressWarnings("unused") View unused) {
        if (mSnapshotOutputDir != null) {
            mCamera2Proxy.resumeRecordingCaptureResult();
            mSnap = true;
        } else {
            mSnapshotOutputDir = renewOutputDir();
            String basename = mSnapshotOutputDir.substring(mSnapshotOutputDir.lastIndexOf("/") + 1);
            mOutputDirText.setText(basename);
            mSnapNumber = 0;
            mCamera2Proxy.startRecordingCaptureResult(
                    mSnapshotOutputDir + File.separator + "movie_metadata.csv");
            mSnap = true;
        }
        TextView numSnapshotView = (TextView) findViewById(R.id.numSnapshot_text);
        numSnapshotView.setText(String.valueOf(mSnapNumber + 1));
    }

}