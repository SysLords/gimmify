package org.syslords.gimmesh;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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

public class SquatActivity extends AppCompatActivity {
    private int squatCount = 0;
    private int totalSquats = 0;
    private SquatState currentState = SquatState.STARTING;
    private List<String> formViolations = new ArrayList<>();

    private TextView squatCountBox;
    private TextView formFeedbackBox;
    private TextView performanceBox;
    private TextToSpeech textToSpeech;

    private enum SquatState {
        STARTING,
        DESCENDING,
        BOTTOM_POSITION,
        ASCENDING,
        TOP_POSITION
    }

    private long lastStateChangeTime = 0;
    private static final long MIN_SQUAT_DURATION = 500; // Minimum time between state changes
    private static final long MAX_SQUAT_DURATION = 3000;
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
                analyzeSquatForm(coordinates);
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

    private double calculateAngle(Float[] point1, Float[] point2, Float[] point3) throws Exception {
        try {
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
        } catch (Exception e) {
            throw new Exception("Angle calculation error");
        }
    }

    private void analyzeSquatForm(Float[][] coordinates) throws Exception {
        // Key body landmarks
        Float[] leftHip = coordinates[23];
        Float[] rightHip = coordinates[24];
        Float[] leftKnee = coordinates[25];
        Float[] rightKnee = coordinates[26];
        Float[] leftAnkle = coordinates[27];
        Float[] rightAnkle = coordinates[28];

        // Reset form violations
        formViolations.clear();

        // Angle calculations
        double leftKneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle);
        double rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle);
        double bodyAlignmentAngle = calculateHorizontalAlignment(leftHip, rightHip, leftKnee, rightKnee);

        // Advanced form analysis
        checkFormViolations(leftKneeAngle, rightKneeAngle, bodyAlignmentAngle);

        // Squat state machine
        updateSquatState(leftKneeAngle, rightKneeAngle);

        // Update UI
        updateUserInterface();
    }

    private void checkFormViolations(double leftKneeAngle, double rightKneeAngle, double bodyAlignmentAngle) {
        // Check knee symmetry
        if (Math.abs(leftKneeAngle - rightKneeAngle) > 20) {
            formViolations.add("Uneven knee angles");
        }

        // Check body alignment
        if (Math.abs(bodyAlignmentAngle) > 10) {
            formViolations.add("Body not horizontal");
        }

        // Check squat depth
        if (leftKneeAngle > 160 || rightKneeAngle > 160) {
            formViolations.add("Not deep enough");
        }

        // Check knee bending
        if (leftKneeAngle < 70 || rightKneeAngle < 70) {
            formViolations.add("Over-bending knees");
        }
    }

    private void updateSquatState(double leftKneeAngle, double rightKneeAngle) {
        long currentTime = System.currentTimeMillis();

        switch (currentState) {
            case STARTING:
                if (leftKneeAngle < 110 && rightKneeAngle < 110) {
                    currentState = SquatState.DESCENDING;
                    lastStateChangeTime = currentTime;
                }
                break;

            case DESCENDING:
                if (leftKneeAngle <= 70 && rightKneeAngle <= 70) {
                    currentState = SquatState.BOTTOM_POSITION;
                    lastStateChangeTime = currentTime;
                }
                break;

            case BOTTOM_POSITION:
                if (leftKneeAngle > 110 && rightKneeAngle > 110) {
                    currentState = SquatState.ASCENDING;
                    lastStateChangeTime = currentTime;
                }
                break;

            case ASCENDING:
                if (leftKneeAngle > 160 && rightKneeAngle > 160) {
                    currentState = SquatState.TOP_POSITION;
                    squatCount++;
                    totalSquats++;
                    lastStateChangeTime = currentTime;
                }
                break;

            case TOP_POSITION:
                // Reset to starting position after a brief pause
                if (currentTime - lastStateChangeTime > 500) {
                    currentState = SquatState.STARTING;
                }
                break;
        }
    }

    private long lastFeedbackTime = 0;
    private static final long FEEDBACK_INTERVAL = 10000;

    private void updateUserInterface() {
        long currentTime = System.currentTimeMillis();

        runOnUiThread(() -> {
            // Update squat count
            squatCountBox.setText("Squats: " + squatCount);

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
                    "State: %s\nTotal Squats: %d",
                    currentState.toString(),
                    totalSquats
            ));
        });
    }

    private double calculateHorizontalAlignment(Float[] leftHip, Float[] rightHip,
                                                Float[] leftKnee, Float[] rightKnee) {
        // Calculate the angle between hip line and knee line
        double hipAngle = Math.atan2(
                rightHip[1] - leftHip[1],
                rightHip[0] - leftHip[0]
        );

        double kneeAngle = Math.atan2(
                rightKnee[1] - leftKnee[1],
                rightKnee[0] - leftKnee[0]
        );

        return Math.toDegrees(Math.abs(hipAngle - kneeAngle));
    }

    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireLatestImage();

                ++i;

                if (image == null) {
                    return;
                }

                if (image != null && modelController.isInferencing) {
                    image.close();
                    return;
                }

                modelController.isInferencing = true;

                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] byteArray = new byte[buffer.remaining()];
                buffer.get(byteArray);

                Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);

                inferenceStart = System.currentTimeMillis();
                poseLandmarkerHelper.detectLiveStream(ModelController.resizeBitmap(bitmap, 192, 256, 180), false);
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

        System.out.println(inferenceModeString);

        int inferenceMode = 0;

        if (inferenceModeString.equals("cpu")) {
            inferenceMode = 0;
        } else if (inferenceModeString.equals("gpu")) {
            inferenceMode = 1;
        }

        surfaceView = findViewById(R.id.surfaceView);
        overlayView = findViewById(R.id.overlayView);
        imageView = findViewById(R.id.imageView);
        squatCountBox = findViewById(R.id.pushup_count_box);
        formFeedbackBox = findViewById(R.id.form_feedback_box);
        performanceBox = findViewById(R.id.performance_box);
        inferenceTimeBox = findViewById(R.id.inference_time_box);

        modelController = new ModelController(this, overlayView);
        modelController.isInferencing = false;

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

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                CameraUtils cameraUtils = new CameraUtils(SquatActivity.this, surfaceView, imageAvailableListener);
                cameraUtils.startBackgroundThread();
                cameraUtils.openCamera();

                ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) surfaceView.getLayoutParams();
                overlayView.setLayoutParams(params);
                overlayView.requestLayout();
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            }
        });

        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
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