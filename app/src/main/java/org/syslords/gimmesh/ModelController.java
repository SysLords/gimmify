package org.syslords.gimmesh;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.AsyncTask;

import com.qualcomm.qti.snpe.FloatTensor;
import com.qualcomm.qti.snpe.NeuralNetwork;

import java.io.InputStream;
import java.util.Set;

public class ModelController {
    String MODEL_NAME = "model.dlc";

    public Context context;

    NeuralNetwork.Runtime runtime;

    NeuralNetwork mNeuralNetwork;

    OverlayView overlayView;

    FloatTensor tensor;

    public boolean isInferencing = true;

    ModelController(Context context, OverlayView overlayView) {
        this.context = context;
        this.overlayView = overlayView;
    }

    public void loadModel() {
        InputStream stream;
        long size;

        try {
            AssetFileDescriptor fd = context.getAssets().openFd(MODEL_NAME);
            size = fd.getLength();
            fd.close();
            stream = context.getAssets().open(MODEL_NAME);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        runtime = NeuralNetwork.Runtime.CPU;

        LoadNetworkTask mLoadTask = new LoadNetworkTask((Application) context.getApplicationContext(), this, stream, size, runtime);

        mLoadTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    void onNetworkLoaded(NeuralNetwork neuralNetwork, long mLoadTime) {
        this.mNeuralNetwork = neuralNetwork;

        String mInputLayer;

        Set<String> inputNames = mNeuralNetwork.getInputTensorsNames();
        if (inputNames.size() != 1) {
            throw new IllegalStateException("Invalid network input and/or output tensors.");
        } else {
            mInputLayer = inputNames.iterator().next();
        }


        tensor = mNeuralNetwork.createFloatTensor(mNeuralNetwork.getInputTensorsShapes().get(mInputLayer));
        System.out.println(mLoadTime);
        isInferencing = false;
    }

    public static Bitmap resizeBitmap(Bitmap bitmap, int width, int height, int degrees) {
        // Load the bitmap from the given path

        // Resize the bitmap to 256x256
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);

        Bitmap rotatedBitmap;

//        int width = resizedBitmap.getWidth();
//        int height = resizedBitmap.getHeight();

        // Iterate over every pixel in the original bitmap and map it to the new position in the rotated bitmap

        switch (degrees)
        {
            case 0:
                return resizedBitmap;
            case 180:
                rotatedBitmap = Bitmap.createBitmap(width, height, resizedBitmap.getConfig());

                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        // Get the pixel from the original bitmap
                        int pixel = resizedBitmap.getPixel(x, y);

                        // Set the pixel to the new position in the rotated bitmap
                        // 90 degrees counterclockwise: new x is the old y, new y is width - old x - 1
                        rotatedBitmap.setPixel(width - x - 1, height - y - 1, pixel);
                    }
                }
                return rotatedBitmap;
            case 90:
                rotatedBitmap = Bitmap.createBitmap(height, width, resizedBitmap.getConfig());

                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        // Get the pixel from the original bitmap
                        int pixel = resizedBitmap.getPixel(x, y);

                        // Set the pixel to the new position in the rotated bitmap
                        // 90 degrees counterclockwise: new x is the old y, new y is width - old x - 1
                        rotatedBitmap.setPixel(y, width - x - 1, pixel);
                    }
                }
                return rotatedBitmap;
            case -90:
                rotatedBitmap = Bitmap.createBitmap(height, width, resizedBitmap.getConfig());

                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        // Get the pixel from the original bitmap
                        int pixel = resizedBitmap.getPixel(x, y);

                        // Set the pixel to the new position in the rotated bitmap
                        // 90 degrees counterclockwise: new x is the old y, new y is width - old x - 1
                        rotatedBitmap.setPixel(y, x, pixel);
                    }
                }
            default:
                return resizedBitmap;
        }

    }

    public void classify(final Bitmap bitmap) {
        if (mNeuralNetwork != null) {

            isInferencing = true;

            InferenceTask task = new InferenceTask(mNeuralNetwork, bitmap, this, tensor);
//            bitmap.recycle();
            task.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        } else {
            System.out.println("No neural Network");
        }
    }

    @SuppressLint("NewApi")
    public void onClassificationResult(Integer selected, long javaExecuteTime) {
//        System.out.println(javaExecuteTime);
        isInferencing = false;
//        overlayView.drawCoordinates(coordinates);
        ((YogaActivity) context).inferenceTimeBox.setText(javaExecuteTime + "ms");
        ((YogaActivity) context).setClassificationResult(selected);
//            view.setJavaExecuteStatistics(javaExecuteTime);
    }
}
