package org.syslords.gimmesh;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.nio.ByteBuffer;
import java.util.List;

public class SquatActivity extends AppCompatActivity
{

    ModelController modelController;

    SurfaceView surfaceView;
    OverlayView overlayView;

    ImageView imageView;

    TextView inferenceTimeBox;

    PoseLandmarkerHelper poseLandmarkerHelper;

    int i = 0;

    long inferenceStart;
    long inferenceEnd;


    PoseLandmarkerHelper.LandmarkerListener landmarkerListener = new PoseLandmarkerHelper.LandmarkerListener()
    {
        @Override
        public void onError(@NonNull String error, int errorCode)
        {

        }

        @Override
        public void onResults(@NonNull PoseLandmarkerHelper.ResultBundle resultBundle)
        {
            System.out.println("result got");

            Float[][] coordinates = new Float[33][2];

            int x = 0;

            for (List<NormalizedLandmark> result : resultBundle.getResults().get(0).landmarks())
            {
                for (NormalizedLandmark landmark : result)
                {
//                    System.out.println(x + " " + landmark.x() + " " + landmark.y());
                    coordinates[x][0] = landmark.x();
                    coordinates[x][1] = landmark.y();
                    ++x;
                }
            }

            try
            {
                modelController.isInferencing = false;
                overlayView.drawCoordinates(coordinates);

                inferenceEnd = System.currentTimeMillis();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Code to execute on the UI thread
                        inferenceTimeBox.setText(Long.toString(inferenceEnd - inferenceStart) + "ms");
                    }
                });


//                System.out.println(inferenceEnd - inferenceStart);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    };

    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener()
    {
        @Override
        public void onImageAvailable(ImageReader reader)
        {
            Image image = null;
            try
            {
                image = reader.acquireLatestImage();

                ++i;


//                if (image != null) {
//                    image.close();
//                    return;
//                }
//
                if (image == null)
                {
                    return;
                }

                if (image != null && modelController.isInferencing)
                {
//                    System.out.println("closing");
                    image.close();
                    return;
                }

                modelController.isInferencing = true;

//                System.out.println("processing");

//                int width = image.getWidth();
//                int height = image.getHeight();
//
//                Image.Plane[] planes = image.getPlanes();
//                if (planes.length < 3) {
//                    throw new IllegalStateException("Expected 3 planes for FLEX_RGB_888 format");
//                }
//
//                // Get the byte buffers for each plane
//                ByteBuffer redBuffer = planes[0].getBuffer(); // Red plane
//                ByteBuffer greenBuffer = planes[1].getBuffer(); // Green plane
//                ByteBuffer blueBuffer = planes[2].getBuffer(); // Blue plane
//
//                // Convert byte buffers to byte arrays
//                byte[] redData = new byte[redBuffer.remaining()];
//                byte[] greenData = new byte[greenBuffer.remaining()];
//                byte[] blueData = new byte[blueBuffer.remaining()];
//
//                redBuffer.get(redData);
//                greenBuffer.get(greenData);
//                blueBuffer.get(blueData);
//
//                // Create an array to hold the pixels in ARGB format
//                int[] pixels = new int[width * height];
//
//                // Iterate over the pixel data and convert RGB to ARGB
//                int pixelIndex = 0;
//                for (int i = 0; i < width * height; i++) {
//                    // Get the RGB values from the byte arrays
//                    int r = redData[i] & 0xFF;      // Red value from the red plane
//                    int g = greenData[i] & 0xFF;    // Green value from the green plane
//                    int b = blueData[i] & 0xFF;     // Blue value from the blue plane
//
//                    // Convert to ARGB format (set Alpha to 255 for full opacity)
//                    pixels[pixelIndex] = (255 << 24) | (r << 16) | (g << 8) | b;
//
//                    pixelIndex++;
//                }
//
//                // Create a Bitmap from the pixel data
//                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//                bitmap.setPixels(pixels, 0, width, 0, 0, width, height);


                ByteBuffer buffer = image.getPlanes()[0].getBuffer();

                // Copy the data from the buffer into a byte array
                byte[] byteArray = new byte[buffer.remaining()];
                buffer.get(byteArray);

                // Decode the byte array into a Bitmap
                Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);

//                Image.Plane[] planes = image.getPlanes();
//
//                // The Y (luminance) plane
//                ByteBuffer yBuffer = planes[0].getBuffer();
//                // The U (chrominance) plane
//                ByteBuffer uBuffer = planes[1].getBuffer();
//                // The V (chrominance) plane
//                ByteBuffer vBuffer = planes[2].getBuffer();
//
//                int ySize = yBuffer.remaining();
//                int uSize = uBuffer.remaining();
//                int vSize = vBuffer.remaining();
//
//                // Create byte arrays to hold the data
//                byte[] yBytes = new byte[ySize];
//                byte[] uBytes = new byte[uSize];
//                byte[] vBytes = new byte[vSize];
//
//                // Read the buffers into the byte arrays
//                yBuffer.get(yBytes);
//                uBuffer.get(uBytes);
//                vBuffer.get(vBytes);
//
//                // Convert YUV to RGB
//                int width = image.getWidth();
//                int height = image.getHeight();
//                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//
//                // You can use a library like YuvImage to simplify YUV to Bitmap conversion
//                YuvImage yuvImage = new YuvImage(yBytes, ImageFormat.NV21, width, height, null);
//                ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, baos);
//                byte[] jpegData = baos.toByteArray();
//                bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);



//                modelController.classify(bitmap);

                inferenceStart = System.currentTimeMillis();
                poseLandmarkerHelper.detectLiveStream(ModelController.resizeBitmap(bitmap, 192, 256, 180), false);

//                bitmap.recycle();

                // Process the image here
//                processImage(image);
            }
            finally
            {
                if (image != null)
                {
//                    System.out.println("closing");
                    image.close();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_camera);
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });


        surfaceView = findViewById(R.id.surfaceView);
        overlayView = findViewById(R.id.overlayView);
        imageView = findViewById(R.id.imageView);

        inferenceTimeBox = findViewById(R.id.inference_time_box);

        modelController = new ModelController(this, overlayView);
        modelController.isInferencing = false;
//        modelController.loadModel();

        poseLandmarkerHelper = new PoseLandmarkerHelper(
                0.5f,
                0.5f,
                0.5f,
                PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL,
                PoseLandmarkerHelper.DELEGATE_CPU,
                RunningMode.LIVE_STREAM,
                this,
                landmarkerListener
        );

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback()
        {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder)
            {
                CameraUtils cameraUtils = new CameraUtils(SquatActivity.this, surfaceView, imageAvailableListener);
                cameraUtils.startBackgroundThread();
                cameraUtils.openCamera();
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height)
            {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder)
            {

            }
        });

    }


}