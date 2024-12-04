package org.syslords.gimmesh;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
import java.util.ArrayList;
import java.util.List;

public class PlankActivity extends AppCompatActivity
{
    private long plankStartTime = 0;
    private boolean holdingPlank = false;
    private String currentSide = "right";
    private List<String> formViolations = new ArrayList<>();

    ModelController modelController;

    SurfaceView surfaceView;
    OverlayView overlayView;

    ImageView imageView;

    TextView inferenceTimeBox;

    TextView plankTimerBox;
    TextView formFeedbackBox;
    TextView performanceBox;


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
//            System.out.println("result got");

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
                analyzePlankForm(coordinates);
                modelController.isInferencing = false;
                overlayView.drawCoordinates(coordinates);

                inferenceEnd = System.currentTimeMillis();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Code to execute on the UI thread
                        inferenceTimeBox.setText(Long.toString(inferenceEnd - inferenceStart) + "ms");
                        updatePlankUI();
                    }
                });


                System.out.println(inferenceEnd - inferenceStart);
            }
            catch (Exception e)
            {
                modelController.isInferencing = false;
//                e.printStackTrace();
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
        plankTimerBox = findViewById(R.id.pushup_count_box);
        formFeedbackBox = findViewById(R.id.form_feedback_box);
        performanceBox = findViewById(R.id.performance_box);
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

    private void analyzePlankForm(Float[][] coordinates) throws Exception
    {
        // Reset form violations
        try
        {

            formViolations.clear();

            // Key landmarks indices
            int rightShoulderIndex = 12;
            int rightHipIndex = 24;
            int rightAnkleIndex = 28;
            int leftShoulderIndex = 11;
            int leftHipIndex = 23;
            int leftAnkleIndex = 27;

            // Determine side based on visibility
            currentSide = coordinates[rightShoulderIndex][1] < coordinates[leftShoulderIndex][1] ? "right" : "left";

            // Select landmarks based on current side
            int shoulderIndex = currentSide.equals("right") ? rightShoulderIndex : leftShoulderIndex;
            int hipIndex = currentSide.equals("right") ? rightHipIndex : leftHipIndex;
            int ankleIndex = currentSide.equals("right") ? rightAnkleIndex : leftAnkleIndex;

            // Check landmark visibility
            if (isLandmarkVisible(coordinates[shoulderIndex]) &&
                    isLandmarkVisible(coordinates[hipIndex]) &&
                    isLandmarkVisible(coordinates[ankleIndex]))
            {

                // Check horizontal alignment
                if (checkHorizontalAlignment(
                        coordinates[shoulderIndex],
                        coordinates[hipIndex]))
                {

                    // Calculate plank angle
                    double plankAngle = calculateAngle(
                            coordinates[shoulderIndex],
                            coordinates[hipIndex],
                            coordinates[ankleIndex]
                    );

                    // Validate plank form
                    validatePlankForm(plankAngle);
                }
                else
                {
                    formViolations.add("Body not horizontally aligned");
                    holdingPlank = false;
                }
            }
            else
            {
                formViolations.add("Ensure full body visibility");
                holdingPlank = false;
            }
        }
        catch (Exception e)
        {
            throw e;
        }
    }

    private boolean isLandmarkVisible(Float[] landmark) {
        // Assuming landmark is visible if its values are not null and within reasonable range
        return landmark != null && landmark[0] >= 0 && landmark[0] <= 1 &&
                landmark[1] >= 0 && landmark[1] <= 1;
    }

    private boolean checkHorizontalAlignment(Float[] shoulder, Float[] hip) {
        // Check if vertical distance between shoulder and hip is within a small threshold
        return Math.abs(shoulder[1] - hip[1]) < 0.1;
    }

    private void validatePlankForm(double plankAngle) {
        if (plankAngle > 190) {
            formViolations.add("Back is too low");
            holdingPlank = false;
        } else if (plankAngle < 170) {
            formViolations.add("Back is too high");
            holdingPlank = false;
        } else {
            // Valid plank position
            if (!holdingPlank) {
                plankStartTime = System.currentTimeMillis();
            }
            holdingPlank = true;
        }
    }

    private double calculateAngle(Float[] point1, Float[] point2, Float[] point3) {
        // Calculate vectors
        double vector1x = point1[0] - point2[0];
        double vector1y = point1[1] - point2[1];
        double vector2x = point3[0] - point2[0];
        double vector2y = point3[1] - point2[1];

        // Calculate dot product
        double dotProduct = (vector1x * vector2x) + (vector1y * vector2y);

        // Calculate magnitudes
        double magnitude1 = Math.sqrt(vector1x * vector1x + vector1y * vector1y);
        double magnitude2 = Math.sqrt(vector2x * vector2x + vector2y * vector2y);

        // Calculate cosine of the angle
        double cosineAngle = dotProduct / (magnitude1 * magnitude2);

        // Convert to degrees and ensure it's always positive
        double angleRadians = Math.acos(Math.max(-1, Math.min(1, cosineAngle)));
        return Math.toDegrees(angleRadians);
    }

    private void updatePlankUI() {
        // Calculate plank duration
        long currentTime = System.currentTimeMillis();
        long elapsedTime = holdingPlank ? (currentTime - plankStartTime) / 1000 : 0;

        // Update plank timer
        plankTimerBox.setText(String.format("Plank Time: %d sec", elapsedTime));

        // Update form feedback
        if (formViolations.isEmpty()) {
            formFeedbackBox.setText("Perfect Plank Form!");
            formFeedbackBox.setTextColor(Color.GREEN);
        } else {
            formFeedbackBox.setText(String.join("\n", formViolations));
            formFeedbackBox.setTextColor(Color.RED);
        }

        // Update performance box
        performanceBox.setText(String.format(
                "Side: %s\nStatus: %s",
                currentSide,
                holdingPlank ? "Holding" : "Invalid"
        ));
    }

}