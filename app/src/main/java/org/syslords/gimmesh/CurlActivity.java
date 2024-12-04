package org.syslords.gimmesh;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
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

public class CurlActivity extends AppCompatActivity {
    private int curlCount = 0;
    private int totalCurls = 0;
    private CurlState currentState = CurlState.STARTING;
    private List<String> formViolations = new ArrayList<>();

    private TextView curlCountBox;
    private TextView formFeedbackBox;
    private TextView performanceBox;
    private TextToSpeech textToSpeech;

    private enum CurlState {
        STARTING,         // Initial position
        DESCENDING,       // Lowering the weight
        BOTTOM_POSITION,  // Lowest point of curl
        ASCENDING,        // Lifting the weight
        TOP_POSITION      // Fully contracted bicep
    }

    private long lastStateChangeTime = 0;
    private static final long MIN_CURL_DURATION = 500;  // Minimum time between state changes
    private static final long MAX_CURL_DURATION = 3000;
    ModelController modelController;

    SurfaceView surfaceView;
    OverlayView overlayView;
    ImageView imageView;
    TextView inferenceTimeBox;

    PoseLandmarkerHelper poseLandmarkerHelper;

    int i = 0;
    long inferenceStart;
    long inferenceEnd;

    PoseLandmarkerHelper.LandmarkerListener landmarkerListener = new PoseLandmarkerHelper.LandmarkerListener() {
        @Override
        public void onError(@NonNull String error, int errorCode) {
            // Error handling
        }

        @Override
        public void onResults(@NonNull PoseLandmarkerHelper.ResultBundle resultBundle) {
            Float[][] coordinates = new Float[33][2];

            int x = 0;
            for (List<NormalizedLandmark> result : resultBundle.getResults().get(0).landmarks()) {
                for (NormalizedLandmark landmark : result) {
                    coordinates[x][0] = landmark.x();
                    coordinates[x][1] = landmark.y();
                    ++x;
                }
            }

            try {
                analyzeBicepCurlForm(coordinates);
                modelController.isInferencing = false;
                overlayView.drawCoordinates(coordinates);

                inferenceEnd = System.currentTimeMillis();

                runOnUiThread(() -> {
                    inferenceTimeBox.setText(Long.toString(inferenceEnd - inferenceStart) + "ms");
                });
            } catch (Exception e) {
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

    private double calculateAngle(Float[] point1, Float[] point2, Float[] point3) {
        double vector1x = point1[0] - point2[0];
        double vector1y = point1[1] - point2[1];
        double vector2x = point3[0] - point2[0];
        double vector2y = point3[1] - point2[1];

        double dotProduct = (vector1x * vector2x) + (vector1y * vector2y);
        double magnitude1 = Math.sqrt(vector1x * vector1x + vector1y * vector1y);
        double magnitude2 = Math.sqrt(vector2x * vector2x + vector2y * vector2y);

        double cosineAngle = dotProduct / (magnitude1 * magnitude2);
        double angleRadians = Math.acos(Math.max(-1, Math.min(1, cosineAngle)));
        return Math.toDegrees(angleRadians);
    }

    private void analyzeBicepCurlForm(Float[][] coordinates) {
        // Key body landmarks for bicep curl
        Float[] leftShoulder = coordinates[11];
        Float[] leftElbow = coordinates[13];
        Float[] leftWrist = coordinates[15];
        Float[] rightShoulder = coordinates[12];
        Float[] rightElbow = coordinates[14];
        Float[] rightWrist = coordinates[16];
        Float[] leftHip = coordinates[23];
        Float[] rightHip = coordinates[24];

        formViolations.clear();

        // Angle calculations
        double leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist);
        double rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist);
        double bodyAlignmentAngle = calculateHorizontalAlignment(leftShoulder, rightShoulder, leftHip, rightHip);

        checkBicepCurlFormViolations(leftElbowAngle, rightElbowAngle, bodyAlignmentAngle);
        updateCurlState(leftElbowAngle, rightElbowAngle);
        updateUserInterface();
    }

    private void checkBicepCurlFormViolations(double leftElbowAngle, double rightElbowAngle, double bodyAlignmentAngle) {
        formViolations.clear();

        // Allow a larger difference for uneven elbows
        if (Math.abs(leftElbowAngle - rightElbowAngle) > 45) {
            formViolations.add("Uneven elbow movement");
        }

        // Allow more torso sway before flagging
        if (Math.abs(bodyAlignmentAngle) > 20) {
            formViolations.add("Torso swaying");
        }

        // Warn against overextension
        if (leftElbowAngle > 170 || rightElbowAngle > 170) {
            formViolations.add("Avoid fully extending elbow");
        }

        // Warn against insufficient contraction
        if (leftElbowAngle < 45 || rightElbowAngle < 45) {
            formViolations.add("Ensure full bicep contraction");
        }
    }


    private void updateCurlState(double leftElbowAngle, double rightElbowAngle) {
        long currentTime = System.currentTimeMillis();

        // More lenient angle checks
        boolean isInitialPosition = leftElbowAngle > 120 && rightElbowAngle > 120;
        boolean isLoweringWeight = leftElbowAngle >= 90 && leftElbowAngle <= 160 &&
                rightElbowAngle >= 90 && rightElbowAngle <= 160;
        boolean isBottomPosition = leftElbowAngle <= 90 && rightElbowAngle <= 90;
        boolean isLiftingWeight = leftElbowAngle >= 90 && leftElbowAngle <= 160 &&
                rightElbowAngle >= 90 && rightElbowAngle <= 160;
        boolean isContractedPosition = leftElbowAngle > 120 && rightElbowAngle > 120;

        // Prevent rapid transitions and ensure minimum duration between states
        if (currentTime - lastStateChangeTime < MIN_CURL_DURATION) {
            return;
        }

        switch (currentState) {
            case STARTING:
                if (isLoweringWeight) {
                    currentState = CurlState.DESCENDING;
                    lastStateChangeTime = currentTime;
                }
                break;

            case DESCENDING:
                if (isBottomPosition) {
                    currentState = CurlState.BOTTOM_POSITION;
                    lastStateChangeTime = currentTime;
                }
                break;

            case BOTTOM_POSITION:
                if (isLiftingWeight) {
                    currentState = CurlState.ASCENDING;
                    lastStateChangeTime = currentTime;
                }
                break;

            case ASCENDING:
                if (isContractedPosition) {
                    currentState = CurlState.TOP_POSITION;
                    curlCount++;
                    totalCurls++;
                    lastStateChangeTime = currentTime;
                    speakText("Curl completed!");  // Optional audio feedback
                }
                break;

            case TOP_POSITION:
                if (isInitialPosition) {
                    currentState = CurlState.STARTING;
                    lastStateChangeTime = currentTime;
                }
                break;
        }
    }



    private long lastFeedbackTime = 0;
    private static final long FEEDBACK_INTERVAL = 5000;

    private void updateUserInterface() {
        long currentTime = System.currentTimeMillis();

        runOnUiThread(() -> {
            curlCountBox.setText("Bicep Curls: " + curlCount);

            if (currentTime - lastFeedbackTime >= FEEDBACK_INTERVAL) {
                if (formViolations.isEmpty()) {
                    formFeedbackBox.setText("Perfect Form!");
                    speakText("Perfect form!");
                    formFeedbackBox.setTextColor(Color.GREEN);
                } else {
                    String feedback = "Form Issues:\n" + String.join("\n", formViolations);
                    speakText(String.join(". ", formViolations));
                    formFeedbackBox.setText(feedback);
                    formFeedbackBox.setTextColor(Color.RED);
                }
                lastFeedbackTime = currentTime;
            }

            performanceBox.setText(String.format(
                    "State: %s\nTotal Curls: %d",
                    currentState.toString(),
                    totalCurls
            ));
        });
    }

    private double calculateHorizontalAlignment(Float[] leftShoulder, Float[] rightShoulder,
                                                Float[] leftHip, Float[] rightHip) {
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

    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireLatestImage();

                ++i;

                if (image == null || (image != null && modelController.isInferencing)) {
                    if (image != null) image.close();
                    return;
                }

                modelController.isInferencing = true;

                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] byteArray = new byte[buffer.remaining()];
                buffer.get(byteArray);

                Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);

                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(CurlActivity.this);
                String degreesString = sharedPreferences.getString("orientation", "0");

                System.out.println(degreesString);

                int degrees = Integer.parseInt(degreesString);

                inferenceStart = System.currentTimeMillis();
                poseLandmarkerHelper.detectLiveStream(ModelController.resizeBitmap(bitmap, 192, 256, degrees), false);
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String inferenceModeString = sharedPreferences.getString("inference_mode", "cpu");

        int inferenceMode = inferenceModeString.equals("cpu") ? 0 : 1;

        surfaceView = findViewById(R.id.surfaceView);
        overlayView = findViewById(R.id.overlayView);
        imageView = findViewById(R.id.imageView);
        curlCountBox = findViewById(R.id.pushup_count_box);
        formFeedbackBox = findViewById(R.id.form_feedback_box);
        performanceBox = findViewById(R.id.performance_box);
        inferenceTimeBox = findViewById(R.id.inference_time_box);

        modelController = new ModelController(this, overlayView);
        modelController.isInferencing = false;

        poseLandmarkerHelper = new PoseLandmarkerHelper(
                0.5f, 0.5f, 0.5f,
                PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL,
                inferenceMode,
                RunningMode.LIVE_STREAM,
                this,
                landmarkerListener
        );

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                CameraUtils cameraUtils = new CameraUtils(CurlActivity.this, surfaceView, imageAvailableListener);
                cameraUtils.startBackgroundThread();
                cameraUtils.openCamera();

                ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) surfaceView.getLayoutParams();
                overlayView.setLayoutParams(params);
                overlayView.requestLayout();
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {}
        });

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    System.out.println("Language not supported");
                }
            } else {
                System.out.println("Initialization failed");
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