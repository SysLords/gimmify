package org.syslords.gimmesh;

import static android.content.ContentValues.TAG;
import static android.content.Context.CAMERA_SERVICE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

public class CameraUtils
{
    static CameraDevice cameraDevice;
    CaptureRequest.Builder captureRequestBuilder;
    static CameraCaptureSession cameraCaptureSession;

    Context context;
    CameraManager manager;

    String lid;
    SurfaceView surfaceView;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler2;
    private HandlerThread mBackgroundThread2;

    ImageReader mImageReader;

    ImageReader.OnImageAvailableListener imageAvailableListener;

    CameraUtils(Context context, SurfaceView surfaceView, ImageReader.OnImageAvailableListener imageAvailableListener)
    {
        this.context = context;
        this.surfaceView = surfaceView;
        this.imageAvailableListener = imageAvailableListener;
    }

    void openCamera()
    {
        CameraManager manager = (CameraManager) context.getSystemService(CAMERA_SERVICE);

        try {
            lid = "0";

            CameraCharacteristics camera = manager.getCameraCharacteristics(lid);
            Log.d(TAG, "Camera " + lid + " capabilities: " + Arrays.toString(camera.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)));
            Log.d(TAG, "Is logical multi-camera: " + camera.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES));

            for (int i : camera.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)) {
                if (i == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) {
                    System.out.println("Logical camera confirmed");
                }
            }


            CameraCharacteristics chars = manager.getCameraCharacteristics(lid);
            int[] capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            for (int capability : capabilities) {
                Log.d(TAG, "Camera capability: " + capability);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                System.out.println(manager.getConcurrentCameraIds());
            }

//        (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA));

            float deviation = Float.MAX_VALUE;
            float ideal = 1f;
            Size best = new Size(500, 500);

            String dimension = "";

            for (Size size : camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG)) {
                float ratio = (float) size.getWidth() / size.getHeight();

                if (Math.abs(ratio - ideal) < deviation) {
                    deviation = Math.abs(ratio - ideal);
                    best = size;
                }

                System.out.println("width " + size.getWidth() + " height " + size.getHeight());
                dimension += "width " + size.getWidth() + " height " + size.getHeight() + "\n";
            }

//            int x = camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG).length;
//            best = camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG)[10];
//            size = best;
            surfaceView.getHolder().setFixedSize(best.getWidth(), best.getHeight());

            int screenWidth = surfaceView.getWidth();
            int screenHeight = surfaceView.getHeight();
            float factor = (float) screenWidth / best.getWidth();
            int surfaceHeight = (int) (factor * best.getHeight());

            surfaceView.getLayoutParams().height = surfaceHeight;
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) surfaceView.getLayoutParams();

            params.setMargins(0, (screenHeight - surfaceHeight) / 2, 0, (screenHeight - surfaceHeight) / 2);
            params.height = surfaceHeight;

            surfaceView.setLayoutParams(params);
            surfaceView.requestLayout();

//            s1.getHolder().setFixedSize(best.getWidth(), best.getHeight());

            dimension += "chose " + best.getWidth() + " " + best.getHeight() + "\n";

            System.out.println(dimension);

            // System.out.println(camera.get(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA));

            // showDialog(dimension);
            // Create an ImageReader to handle the preview frames
            // ImageReader imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getWidth(), ImageFormat.PRIVATE, 1);
            // reprocessSurface = imageReader.getSurface();

            if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            Log.d(TAG, "Trying to open camera" + lid);
            manager.openCamera(lid, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.d(TAG, "Opened " + lid);

                    // showDialog("Opened id " + lid);

                    cameraDevice = camera;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    if (error == ERROR_MAX_CAMERAS_IN_USE) {
                        Log.d(TAG, "cant open camera" + lid);
                    }
                    camera.close();
                    cameraDevice = null;
                }
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("NewApi")
    public void createCameraPreviewSession() {
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

//            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//            captureRequestBuilder.addTarget(s1.getHolder().getSurface());
//            captureRequestBuilder.addTarget(s2.getHolder().getSurface());

            CameraManager manager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
            CameraCharacteristics camera = manager.getCameraCharacteristics(lid);

            float deviation = Float.MAX_VALUE;
            float ideal = (float) surfaceView.getWidth() / surfaceView.getHeight();
            Size best = new Size(500, 500);

            String dimension = "";

            for (Size size : camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG)) {
                float ratio = (float) size.getWidth() / size.getHeight();

                if (Math.abs(ratio - ideal) < deviation) {
                    deviation = Math.abs(ratio - ideal);
                    best = size;
                }

                System.out.println("width " + size.getWidth() + " height " + size.getHeight());
                dimension += "width " + size.getWidth() + " height " + size.getHeight() + "\n";
            }

//            Size best = camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG)[10];

            System.out.println("jped sige" + best.getWidth() + " " + best.getHeight());


            mImageReader = ImageReader.newInstance(best.getWidth(), best.getHeight(), ImageFormat.JPEG, 3);
            Surface mImageSurface = mImageReader.getSurface();

            mImageReader.setOnImageAvailableListener(imageAvailableListener, mBackgroundHandler2);

            // captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            long exposureTime = 100000000L;  // in nanoseconds (e.g., 100 ms)
            // captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime);

            captureRequestBuilder.addTarget(surfaceView.getHolder().getSurface());
            captureRequestBuilder.addTarget(mImageSurface);

            OutputConfiguration config1 = new OutputConfiguration(surfaceView.getHolder().getSurface());
            OutputConfiguration config2 = new OutputConfiguration(mImageSurface);


            ArrayList<OutputConfiguration> confs = new ArrayList<>();

//            if (pid1.isEmpty() && pid2.isEmpty()) {
//
//            } else {
//                config1.setPhysicalCameraId(pid1);
//                config2.setPhysicalCameraId(pid2);
//            }
//
            confs.add(config1);
            confs.add(config2);

            cameraDevice.createCaptureSession(new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, confs, context.getMainExecutor(), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSession = session;

                    startPreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    // Toast.makeText(context, "Configuration failed", Toast.LENGTH_SHORT).show();
                }
            }));
        } catch (Exception e) {
            // showDialog(Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
    }

    public void startPreview() {
////        showDialog("Success");
//        if (cameraDevice == null) {
////            showDialog("Null");
//            Log.e(TAG, "updatePreview: cameraDevices[id] is null");
//            return;
//        }


//        captureRequestBuilder2.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//             captureRequestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, 4f);
//             captureRequestBuilder2.set(CaptureRequest.CONTROL_ZOOM_RATIO, 2f);
        }

        // System.out.println(Arrays.toString(captureRequestBuilder.build().getKeys().toArray()));

        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    // showDialog("working");
                }

                @Override
                public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);

                    // session.close();

                    // handler.postDelayed(r, 1000);

                    // createCameraPreviewSession;

//                    startPreview();

//                    if (failure.wasImageCaptured())
//                        showDialog("Image was captured");
//                    else
//                        showDialog("image was not captured");

                    System.out.println(failure.getReason());
                    System.out.println("frame " + failure.getFrameNumber());
//                    Log.e(TAG, "Session configuration: " + session.getDeviceStateCallback().toString()); }


                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        System.out.println("failed " + failure.getReason() + " ON " + failure.getPhysicalCameraId());
                    } else {
//                        showDialog("failed " + failure.getReason());
                    }
                }

                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);

                    // showDialog("started at " + timestamp);
                }

            }, mBackgroundHandler);
        } catch (Exception e) {
            // showDialog(Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
    }

    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground1");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

        mBackgroundThread2 = new HandlerThread("CameraBackground1");
        mBackgroundThread2.start();
        mBackgroundHandler2 = new Handler(mBackgroundThread.getLooper());
    }
}
