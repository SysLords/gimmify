package org.syslords.gimmesh;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class CameraActivity extends AppCompatActivity {

    ModelController modelController;

    SurfaceView surfaceView;
    OverlayView overlayView;

    TextView inferenceTimeBox;

    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireLatestImage();

                Image.Plane[] planes = image.getPlanes();

                // The Y (luminance) plane
                ByteBuffer yBuffer = planes[0].getBuffer();
                // The U (chrominance) plane
                ByteBuffer uBuffer = planes[1].getBuffer();
                // The V (chrominance) plane
                ByteBuffer vBuffer = planes[2].getBuffer();

                int ySize = yBuffer.remaining();
                int uSize = uBuffer.remaining();
                int vSize = vBuffer.remaining();

                // Create byte arrays to hold the data
                byte[] yBytes = new byte[ySize];
                byte[] uBytes = new byte[uSize];
                byte[] vBytes = new byte[vSize];

                // Read the buffers into the byte arrays
                yBuffer.get(yBytes);
                uBuffer.get(uBytes);
                vBuffer.get(vBytes);

                // Convert YUV to RGB
                int width = image.getWidth();
                int height = image.getHeight();
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                // You can use a library like YuvImage to simplify YUV to Bitmap conversion
                YuvImage yuvImage = new YuvImage(yBytes, ImageFormat.NV21, width, height, null);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, baos);
                byte[] jpegData = baos.toByteArray();
                bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);


                modelController.classify(bitmap);

                // Process the image here
//                processImage(image);
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
//        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_camera);
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });


        surfaceView = findViewById(R.id.surfaceView);
        overlayView = findViewById(R.id.overlayView);

        inferenceTimeBox = findViewById(R.id.inference_time_box);

        modelController = new ModelController(this, overlayView);
        modelController.loadModel();

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                CameraUtils cameraUtils = new CameraUtils(CameraActivity.this, surfaceView, imageAvailableListener);
                cameraUtils.startBackgroundThread();
                cameraUtils.openCamera();
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

            }
        });

    }


}