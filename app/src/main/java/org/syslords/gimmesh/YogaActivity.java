package org.syslords.gimmesh;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.core.RunningMode;

import java.nio.ByteBuffer;
import java.util.List;

public class YogaActivity extends AppCompatActivity
{

    ModelController modelController;

    SurfaceView surfaceView;
    OverlayView overlayView;

    ImageView imageView;

    TextView inferenceTimeBox;
    TextView inferenceResultBox;

    PoseLandmarkerHelper poseLandmarkerHelper;

    int i = 0;

    long inferenceStart;
    long inferenceEnd;


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

                if (modelController.isInferencing)
                {
                    image.close();
                    return;
                }

                ByteBuffer buffer = image.getPlanes()[0].getBuffer();

                // Copy the data from the buffer into a byte array
                byte[] byteArray = new byte[buffer.remaining()];
                buffer.get(byteArray);

                // Decode the byte array into a Bitmap
                Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);

                modelController.classify(bitmap);

                inferenceStart = System.currentTimeMillis();
            }
            finally
            {
                if (image != null)
                {
                    image.close();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_yoga);



        surfaceView = findViewById(R.id.yoga_preview);

        inferenceTimeBox = findViewById(R.id.yoga_inference_time_box);
        inferenceResultBox = findViewById(R.id.yoga_inference_result_box);

        modelController = new ModelController(this, overlayView);
        modelController.isInferencing = false;
        modelController.loadModel();

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback()
        {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder)
            {
                CameraUtils cameraUtils = new CameraUtils(YogaActivity.this, surfaceView, imageAvailableListener);
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


    void setClassificationResult(int result)
    {
        String[] yogaPoses = {
                "Utkatakonasana",
                "Natarajasana",
                "Trikonasana",
                "Veerabhadrasana",
                "Padhahastasana",
                "Ashwasanchalasana",
                "Astangasana",
                "Bhujangasana",
                "ArdhaChandrasana",
                "Parvathasana",
                "BaddhaKonasana",
                "Vrukshasana",
                "Dandasana",
                "Shashangasana",
                "Ardhachakrasana",
                "Pranamasana"
        };

        inferenceResultBox.setText(yogaPoses[result]);
    }

}