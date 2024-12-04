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
import android.speech.tts.TextToSpeech;
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
import java.util.Locale;

import android.speech.tts.TextToSpeech;

public class PushupActivity extends AppCompatActivity
{
    private int pushupCount = 0;
    private int totalPushups = 0;
    private PushupState currentState = PushupState.STARTING;
    private List<String> formViolations = new ArrayList<>();

    private TextView pushupCountBox;
    private TextView formFeedbackBox;
    private TextView performanceBox;
    private TextToSpeech textToSpeech;

    private enum PushupState {
        STARTING,
        DESCENDING,
        BOTTOM_POSITION,
        ASCENDING,
        TOP_POSITION
    }

    private long lastStateChangeTime = 0;
    private static final long MIN_PUSHUP_DURATION = 500; // Minimum time between state changes
    private static final long MAX_PUSHUP_DURATION = 3000;
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
                analyzePushupForm(coordinates);
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
                modelController.isInferencing = false;
                e.printStackTrace();
            }
        }
    };

    private void speakText(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
    private double calculateAngle(Float[] point1, Float[] point2, Float[] point3) throws Exception {
        // Calculate vectors

        double vector1x;
        double vector1y;
        double vector2x;
        double vector2y;


        try
        {
            vector1x = point1[0] - point2[0];
            vector1y = point1[1] - point2[1];
            vector2x = point3[0] - point2[0];
            vector2y = point3[1] - point2[1];
        }
        catch (Exception e)
        {
            throw new Exception();
        }

        // Calculate dot product
        double dotProduct = (vector1x * vector2x) + (vector1y * vector2y);

        // Calculate magnitudes
        double magnitude1 = Math.sqrt(vector1x * vector1x + vector1y * vector1y);
        double magnitude2 = Math.sqrt(vector2x * vector2x + vector2y * vector2y);

        // Calculate cosine of the angle
        double cosineAngle = dotProduct / (magnitude1 * magnitude2);

        // Convert to degrees and ensure it's always positive
        double angleRadians = Math.acos(Math.max(-1, Math.min(1, cosineAngle)));
        double angleDegrees = Math.toDegrees(angleRadians);

        return angleDegrees;
    }

    private void analyzePushupForm(Float[][] coordinates) throws Exception {
        // Key body landmarks
        Float[] leftShoulder = coordinates[11];
        Float[] leftElbow = coordinates[13];
        Float[] leftWrist = coordinates[15];
        Float[] rightShoulder = coordinates[12];
        Float[] rightElbow = coordinates[14];
        Float[] rightWrist = coordinates[16];
        Float[] leftHip = coordinates[23];
        Float[] rightHip = coordinates[24];

        // Reset form violations
        formViolations.clear();

        // Angle calculations
        double leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist);
        double rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist);
        double bodyAlignmentAngle = calculateHorizontalAlignment(leftShoulder, rightShoulder, leftHip, rightHip);

        // Advanced form analysis
        checkFormViolations(leftElbowAngle, rightElbowAngle, bodyAlignmentAngle);

        // Push-up state machine
        updatePushupState(leftElbowAngle, rightElbowAngle);

        // Update UI
        updateUserInterface();
    }

    private void checkFormViolations(double leftElbowAngle, double rightElbowAngle, double bodyAlignmentAngle) {
        // Check elbow symmetry
        if (Math.abs(leftElbowAngle - rightElbowAngle) > 20) {
            formViolations.add("Uneven elbow angles");
        }

        // Check body alignment
        if (Math.abs(bodyAlignmentAngle) > 10) {
            formViolations.add("Body not horizontal");
        }

        // Check elbow positioning
        if (leftElbowAngle > 160 || rightElbowAngle > 160) {
            formViolations.add("Elbows too straight");
        }

        // Check depth
        if (leftElbowAngle < 70 || rightElbowAngle < 70) {
            formViolations.add("Not enough depth");
        }
    }

    private void updatePushupState(double leftElbowAngle, double rightElbowAngle) {
        long currentTime = System.currentTimeMillis();

        switch (currentState) {
            case STARTING:
                if (leftElbowAngle < 110 && rightElbowAngle < 110) {
                    currentState = PushupState.DESCENDING;
                    lastStateChangeTime = currentTime;
                }
                break;

            case DESCENDING:
                if (leftElbowAngle <= 70 && rightElbowAngle <= 70) {
                    currentState = PushupState.BOTTOM_POSITION;
                    lastStateChangeTime = currentTime;
                }
                break;

            case BOTTOM_POSITION:
                if (leftElbowAngle > 110 && rightElbowAngle > 110) {
                    currentState = PushupState.ASCENDING;
                    lastStateChangeTime = currentTime;
                }
                break;

            case ASCENDING:
                if (leftElbowAngle > 160 && rightElbowAngle > 160) {
                    currentState = PushupState.TOP_POSITION;
                    pushupCount++;
                    totalPushups++;
                    lastStateChangeTime = currentTime;
                }
                break;

            case TOP_POSITION:
                // Reset to starting position after a brief pause
                if (currentTime - lastStateChangeTime > 500) {
                    currentState = PushupState.STARTING;
                }
                break;
        }
    }

    private long lastFeedbackTime = 0; // Tracks the last feedback time
    private static final long FEEDBACK_INTERVAL = 10000; // Minimum interval in milliseconds

    private void updateUserInterface() {
        long currentTime = System.currentTimeMillis();

        runOnUiThread(() -> {
            // Update push-up count
            pushupCountBox.setText("Push-ups: " + pushupCount);

            // Only provide feedback if the minimum interval has passed
            if (currentTime - lastFeedbackTime >= FEEDBACK_INTERVAL) {
                // Detailed form feedback
                if (formViolations.isEmpty()) {
                    formFeedbackBox.setText("Perfect Form!");
                    speakText("Perfect form!");
                    formFeedbackBox.setTextColor(Color.GREEN);
                } else {
                    String feedback = "Form Issues:\n" + String.join("\n", formViolations);
                    speakText(String.join("\n", formViolations));
                    formFeedbackBox.setText(feedback);
                    formFeedbackBox.setTextColor(Color.RED);
                }
                // Update the last feedback time
                lastFeedbackTime = currentTime;
            }

            // Performance tracking
            performanceBox.setText(String.format(
                    "State: %s\nTotal Pushups: %d",
                    currentState.toString(),
                    totalPushups
            ));
        });
    }

    private double calculateHorizontalAlignment(Float[] leftShoulder, Float[] rightShoulder,
                                                Float[] leftHip, Float[] rightHip) {
        // Calculate the angle between shoulder line and hip line
        double shoulderAngle = Math.atan2(
                rightShoulder[1] - leftShoulder[1],
                rightShoulder[0] - leftShoulder[0]
        );

        double hipAngle = Math.atan2(
                rightHip[1] - leftHip[1],
                rightHip[0] - leftHip[0]
        );

        return Math.toDegrees(Math.abs(shoulderAngle - hipAngle));
    }





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
        String inferenceModeString = sharedPreferences.getString("inference_mode", "cpu");

        System.out.println(inferenceModeString);

        int inferenceMode = 0;

        if (inferenceModeString.equals("cpu"))
        {
            inferenceMode = 0;
        }
        else if (inferenceModeString.equals("gpu"))
        {
            inferenceMode = 1;
        }

        surfaceView = findViewById(R.id.surfaceView);
        overlayView = findViewById(R.id.overlayView);
        imageView = findViewById(R.id.imageView);
        pushupCountBox = findViewById(R.id.pushup_count_box);
        formFeedbackBox = findViewById(R.id.form_feedback_box);
        performanceBox = findViewById(R.id.performance_box);
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
                CameraUtils cameraUtils = new CameraUtils(PushupActivity.this, surfaceView, imageAvailableListener);
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

        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    // TTS is successfully initialized
                    int result = textToSpeech.setLanguage(Locale.US);
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        System.out.println("Language not supported");
                    }
                } else {
                    System.out.println("Initialization failed");
                }
            }
        });

    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }


}