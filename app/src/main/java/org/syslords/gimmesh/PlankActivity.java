package org.syslords.gimmesh;

import android.content.SharedPreferences;
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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.preference.PreferenceManager;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.nio.ByteBuffer;
import java.util.List;

public class PlankActivity extends AppCompatActivity
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


                System.out.println(inferenceEnd - inferenceStart);
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


                ByteBuffer buffer = image.getPlanes()[0].getBuffer();

                // Copy the data from the buffer into a byte array
                byte[] byteArray = new byte[buffer.remaining()];
                buffer.get(byteArray);

                // Decode the byte array into a Bitmap
                Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);


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

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int inferenceMode = sharedPreferences.getInt("inference_mode_values", 0);

        System.out.println(inferenceMode);

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
                inferenceMode,
                RunningMode.LIVE_STREAM,
                this,
                landmarkerListener
        );

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback()
        {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder)
            {
                CameraUtils cameraUtils = new CameraUtils(PlankActivity.this, surfaceView, imageAvailableListener);
                cameraUtils.startBackgroundThread();
                cameraUtils.openCamera();

                ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) surfaceView.getLayoutParams();

                overlayView.setLayoutParams(params);
                overlayView.requestLayout();
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