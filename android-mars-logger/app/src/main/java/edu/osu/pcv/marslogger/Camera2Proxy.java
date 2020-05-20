package edu.osu.pcv.marslogger;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SizeF;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import timber.log.Timber;

public class Camera2Proxy {

    private static final String TAG = "Camera2Proxy";

    private Activity mActivity;

    private int mCameraId = CameraCharacteristics.LENS_FACING_BACK;
    private String mCameraIdStr = "";
    private Size mPreviewSize;
    private Size mVideoSize;
    private CameraManager mCameraManager;
    private CameraCharacteristics mCameraCharacteristics;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private Rect sensorArraySize;
    private Integer mTimeSourceValue;

    private CaptureRequest mPreviewRequest;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private ImageReader mImageReader;
    private Surface mPreviewSurface;
    private SurfaceTexture mPreviewSurfaceTexture = null;
    private OrientationEventListener mOrientationEventListener;

    private int mDisplayRotate = 0;
    private int mDeviceOrientation = 0;
    private int mZoom = 1;

    private BufferedWriter mFrameMetadataWriter = null;

    // https://stackoverflow.com/questions/3786825/volatile-boolean-vs-atomicboolean
    private volatile boolean mRecordingMetadata = false;

    private FocalLengthHelper mFocalLengthHelper = new FocalLengthHelper();

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Timber.d("onOpened");
            mCameraDevice = camera;
            initPreviewRequest();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Timber.d("onDisconnected");
            releaseCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Timber.w("Camera Open failed with error %d", error);
            releaseCamera();
        }
    };

    public Integer getmTimeSourceValue() {
        return mTimeSourceValue;
    }

    public void startRecordingCaptureResult(String captureResultFile) {
        try {
            mFrameMetadataWriter = new BufferedWriter(
                    new FileWriter(captureResultFile, false));
            String header = "Timestamp[nanosec],fx[px],fy[px],Frame No.," +
                    "Exposure time[nanosec],Sensor frame duration[nanosec]," +
                    "Frame readout time[nanosec]," +
                    "ISO,Focal length,Focus distance,AF mode";

            mFrameMetadataWriter.write(header + "\n");
            mRecordingMetadata = true;
        } catch (IOException err) {
            Timber.e(err, "IOException in opening frameMetadataWriter at %s",
                    captureResultFile);
        }
    }

    public void stopRecordingCaptureResult() {
        if (mRecordingMetadata) {
            mRecordingMetadata = false;
            try {
                mFrameMetadataWriter.flush();
                mFrameMetadataWriter.close();
            } catch (IOException err) {
                Timber.e(err, "IOException in closing frameMetadataWriter.");
            }
            mFrameMetadataWriter = null;
        }
    }

    public Camera2Proxy(Activity activity) {
        mActivity = activity;
        mCameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        mOrientationEventListener = new OrientationEventListener(mActivity) {
            @Override
            public void onOrientationChanged(int orientation) {
                mDeviceOrientation = orientation;
            }
        };
    }

    public Size configureCamera(int width, int height) {
        try {
            mCameraIdStr = CameraUtils.getRearCameraId(mCameraManager);
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraIdStr);
            sensorArraySize = mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            mTimeSourceValue = mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);

            StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics
                    .SCALER_STREAM_CONFIGURATION_MAP);

            Size[] videoSizeChoices = map.getOutputSizes(MediaRecorder.class);
            mVideoSize = CameraUtils.chooseVideoSize(videoSizeChoices, width, height, width);

            mFocalLengthHelper.setLensParams(mCameraCharacteristics);
            mFocalLengthHelper.setmImageSize(mVideoSize);

            mPreviewSize = CameraUtils.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mVideoSize);
            Timber.d("Video size %s preview size %s.",
                    mVideoSize.toString(), mPreviewSize.toString());

        } catch (CameraAccessException e) {
            Timber.e(e);
        }
        return mPreviewSize;
    }

    @SuppressLint("MissingPermission")
    public void openCamera(int width, int height) {
        Timber.v("openCamera");
        startBackgroundThread();
        mOrientationEventListener.enable();
        if (mCameraIdStr.isEmpty()) {
            configureCamera(width, height);
        }
        try {
            mCameraManager.openCamera(mCameraIdStr, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Timber.e(e);
        }
    }

    public void releaseCamera() {
        Timber.v("releaseCamera");
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        mOrientationEventListener.disable();
        mPreviewSurfaceTexture = null;
        mCameraIdStr = "";
        stopRecordingCaptureResult();
        stopBackgroundThread();
    }

    public void setImageAvailableListener(ImageReader.OnImageAvailableListener
                                                  onImageAvailableListener) {
        if (mImageReader == null) {
            Timber.w("setImageAvailableListener: mImageReader is null");
            return;
        }
        mImageReader.setOnImageAvailableListener(onImageAvailableListener, null);
    }

    public void setPreviewSurface(SurfaceHolder holder) {
        mPreviewSurface = holder.getSurface();
    }

    public void setPreviewSurfaceTexture(SurfaceTexture surfaceTexture) {
        mPreviewSurfaceTexture = surfaceTexture;
    }


    private class NumExpoIso {
        public Long mNumber;
        public Long mExposureNanos;
        public Integer mIso;

        public NumExpoIso(Long number, Long expoNanos, Integer iso) {
            mNumber = number;
            mExposureNanos = expoNanos;
            mIso = iso;
        }
    }

    private final int kMaxExpoSamples = 10;
    private ArrayList<NumExpoIso> expoStats = new ArrayList<>(kMaxExpoSamples);

    private void setExposureAndIso() {
        Long exposureNanos = CameraCaptureActivity.mDesiredExposureTime;
        Long desiredIsoL = 30L * 30000000L / exposureNanos;
        Integer desiredIso = desiredIsoL.intValue();
        if (!expoStats.isEmpty()) {
            int index = expoStats.size() / 2;
            Long actualExpo = expoStats.get(index).mExposureNanos;
            Integer actualIso = expoStats.get(index).mIso;
            if (actualExpo != null && actualIso != null) {
                if (actualExpo <= exposureNanos) {
                    exposureNanos = actualExpo;
                    desiredIso = actualIso;
                } else {
                    desiredIsoL = actualIso * actualExpo / exposureNanos;
                    desiredIso = desiredIsoL.intValue();
                }
            } // else may occur on an emulated device.
        }

        // fix exposure
        mPreviewRequestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
        Range<Long> exposureTimeRange = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        if (exposureTimeRange != null) {
            Timber.d("exposure time range %s", exposureTimeRange.toString());
        }

        mPreviewRequestBuilder.set(
                CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNanos);
        Timber.d("Exposure time set to %d", exposureNanos);

        // fix ISO
        Range<Integer> isoRange = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (isoRange != null) {
            Timber.d("ISO range %s", isoRange.toString());
        }

        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, desiredIso);
        Timber.d("ISO set to %d", desiredIso);
    }

    private void initPreviewRequest() {
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            // Set control elements, we want auto white balance
            mPreviewRequestBuilder.set(
                    CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mPreviewRequestBuilder.set(
                    CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);

            // fix focus distance
//            mPreviewRequestBuilder.set(
//                    CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
//            Float minFocusDistance = mCameraCharacteristics.get(
//                    CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
//            if (minFocusDistance == null)
//                minFocusDistance = 5.0f;
//            mPreviewRequestBuilder.set(
//                    CaptureRequest.LENS_FOCUS_DISTANCE, minFocusDistance);
//            Timber.d("Focus distance set to its min value %f", minFocusDistance);

            if (mPreviewSurfaceTexture != null && mPreviewSurface == null) { // use texture view
                mPreviewSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),
                        mPreviewSize.getHeight());
                mPreviewSurface = new Surface(mPreviewSurfaceTexture);
            }
            mPreviewRequestBuilder.addTarget(mPreviewSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(mPreviewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mCaptureSession = session;
                            mPreviewRequest = mPreviewRequestBuilder.build();
                            startPreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Timber.w("ConfigureFailed. session: mCaptureSession");
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Timber.e(e);
        }
    }


    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request,
                                               TotalCaptureResult result) {
                    Long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
                    Long number = result.getFrameNumber();
                    Long exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);

                    Long frmDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION);
                    Long frmReadoutNs = result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW);
                    Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
                    if (expoStats.size() > kMaxExpoSamples) {
                        expoStats.subList(0, kMaxExpoSamples / 2).clear();
                    }
                    expoStats.add(new NumExpoIso(number, exposureTimeNs, iso));

                    Float fl = result.get(CaptureResult.LENS_FOCAL_LENGTH);

                    Float fd = result.get(CaptureResult.LENS_FOCUS_DISTANCE);

                    Integer afMode = result.get(CaptureResult.CONTROL_AF_MODE);

                    Rect rect = result.get(CaptureResult.SCALER_CROP_REGION);
                    mFocalLengthHelper.setmFocalLength(fl);
                    mFocalLengthHelper.setmFocusDistance(fd);
                    mFocalLengthHelper.setmCropRegion(rect);
                    SizeF sz_focal_length = mFocalLengthHelper.getFocalLengthPixel();
                    String delimiter = ",";
                    StringBuilder sb = new StringBuilder();
                    sb.append(timestamp);
                    sb.append(delimiter + sz_focal_length.getWidth());
                    sb.append(delimiter + sz_focal_length.getHeight());
                    sb.append(delimiter + number);
                    sb.append(delimiter + exposureTimeNs);
                    sb.append(delimiter + frmDurationNs);
                    sb.append(delimiter + frmReadoutNs);
                    sb.append(delimiter + iso);
                    sb.append(delimiter + fl);
                    sb.append(delimiter + fd);
                    sb.append(delimiter + afMode);
                    String frame_info = sb.toString();
                    if (mRecordingMetadata) {
                        try {
                            mFrameMetadataWriter.write(frame_info + "\n");
                        } catch (IOException err) {
                            Timber.e(err, "Error writing captureResult");
                        }
                    }
                    ((CameraCaptureActivity) mActivity).updateCaptureResultPanel(
                            sz_focal_length.getWidth(), exposureTimeNs, afMode);
                }

                @Override
                public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                                CaptureResult partialResult) {
                }
            };


    public void startPreview() {
        Timber.v("startPreview");
        if (mCaptureSession == null || mPreviewRequestBuilder == null) {
            Timber.w("startPreview: mCaptureSession or mPreviewRequestBuilder is null");
            return;
        }
        try {
            mCaptureSession.setRepeatingRequest(
                    mPreviewRequest, mSessionCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Timber.e(e);
        }
    }

    public void stopPreview() {
        Timber.v("stopPreview");
        if (mCaptureSession == null || mPreviewRequestBuilder == null) {
            Timber.w("stopPreview: mCaptureSession or mPreviewRequestBuilder is null");
            return;
        }
        try {
            mCaptureSession.stopRepeating();
        } catch (CameraAccessException e) {
            Timber.e(e);
        }
    }

    // TODO(jhuai): analyze the mechanism behind lock AF upon touch,
    // make sure it won't cause sync issues with other Camera2Proxy methods
    void changeManualFocusPoint(ManualFocusConfig focusConfig) {
        float eventX = focusConfig.mEventX;
        float eventY = focusConfig.mEventY;
        int viewWidth = focusConfig.mViewWidth;
        int viewHeight = focusConfig.mViewHeight;

        final int y = (int) ((eventX / (float) viewWidth) * (float) sensorArraySize.height());
        final int x = (int) ((eventY / (float) viewHeight) * (float) sensorArraySize.width());
        final int halfTouchWidth = 400;
        final int halfTouchHeight = 400;
        MeteringRectangle focusAreaTouch = new MeteringRectangle(Math.max(x - halfTouchWidth, 0),
                Math.max(y - halfTouchHeight, 0),
                halfTouchWidth * 2,
                halfTouchHeight * 2,
                MeteringRectangle.METERING_WEIGHT_MAX - 1);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS,
                new MeteringRectangle[]{focusAreaTouch});
        try {
            mCaptureSession.setRepeatingRequest(
                    mPreviewRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            Timber.e(e);
        }

        setExposureAndIso();

        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                CameraMetadata.CONTROL_MODE_AUTO);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_AUTO);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START);

        try {
            mCaptureSession.setRepeatingRequest(
                    mPreviewRequestBuilder.build(),
                    mSessionCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Timber.e(e);
        }
    }

    private void startBackgroundThread() {
        if (mBackgroundThread == null || mBackgroundHandler == null) {
            Timber.v("startBackgroundThread");
            mBackgroundThread = new HandlerThread("CameraBackground");
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
    }

    private void stopBackgroundThread() {
        Timber.v("stopBackgroundThread");
        try {
            if (mBackgroundThread != null) {
                mBackgroundThread.quitSafely();
                mBackgroundThread.join();
            }
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Timber.e(e);
        }
    }
}
