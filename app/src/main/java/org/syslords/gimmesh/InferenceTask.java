/*
 * Copyright (c) 2016-2018 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */
package org.syslords.gimmesh;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.SystemClock;

import com.qualcomm.qti.snpe.FloatTensor;
import com.qualcomm.qti.snpe.NeuralNetwork;
import com.qualcomm.qti.snpe.Tensor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class InferenceTask extends AsyncTask<Bitmap, Void, Integer>
{

    private static final int FLOAT_SIZE = 4;

    final String mInputLayer;

    final String mOutputLayer;

    final NeuralNetwork mNeuralNetwork;

    final Bitmap mImage;

    ModelController modelController;

    long mJavaExecuteTime = -1;

    FloatTensor tensor;

    public InferenceTask(NeuralNetwork network, Bitmap image, ModelController modelController, FloatTensor tensor)
    {
        this.modelController = modelController;
        mNeuralNetwork = network;
        mImage = image;

        Set<String> inputNames = mNeuralNetwork.getInputTensorsNames();
        Set<String> outputNames = mNeuralNetwork.getOutputTensorsNames();
        if (inputNames.size() != 1 || outputNames.size() != 1)
        {
            throw new IllegalStateException("Invalid network input and/or output tensors.");
        }
        else
        {
            mInputLayer = inputNames.iterator().next();
            mOutputLayer = outputNames.iterator().next();
        }

        // this.tensor = tensor;
        this.tensor = mNeuralNetwork.createFloatTensor(mNeuralNetwork.getInputTensorsShapes().get(mInputLayer));
    }

    @Override
    protected Integer doInBackground(Bitmap... params)
    {

        int j = 0;

        // encode tensor

        final int[] dimensions = tensor.getShape();
        float[] rgbBitmapAsFloat = loadRgbBitmapAsFloat(mImage);

//        for (int i = 0;i < rgbBitmapAsFloat.length;++i)
//        {
//            if (i % 1000 == 0)
//                System.out.println(rgbBitmapAsFloat[i]);
//        }

        mImage.recycle();

        tensor.write(rgbBitmapAsFloat, 0, rgbBitmapAsFloat.length);

        final Map<String, FloatTensor> inputs = new HashMap<>();
        inputs.put(mInputLayer, tensor);

        final long javaExecuteStart = SystemClock.elapsedRealtime();
        final Map<String, FloatTensor> outputs = mNeuralNetwork.execute(inputs);
        final long javaExecuteEnd = SystemClock.elapsedRealtime();
        mJavaExecuteTime = javaExecuteEnd - javaExecuteStart;

        Float[][] coordinates = new Float[17][2];

        // decode tensor

        int x = -1;

        for (Map.Entry<String, FloatTensor> output : outputs.entrySet())
        {
            if (output.getKey().equals(mOutputLayer))
            {
                FloatTensor outputTensor = output.getValue();

                final float[] array = new float[outputTensor.getSize()];
                outputTensor.read(array, 0, array.length);

//                for (int i : tensor.getShape())
//                {
//                    System.out.println(i);
//                }

//                System.out.println(" " + array.length);


                float max = Float.MIN_VALUE;

                for (int i = 0; i < 16; ++i)
                {
                    if (max < array[i])
                    {
                        x = i;
                        max = array[i];
                    }
                }

            }

        }

        return x;
    }



//                int height = 64;
//                int width = 48;
//                int channels = 17;

//                float[][][] heatmap = new float[height][width][channels];

//// Fill the 3D heatmap with values from the flattened array
//                int index = 0;
//                for (int h = 0; h < height; h++) {
//                    for (int w = 0; w < width; w++) {
//                        for (int c = 0; c < channels; c++) {
//                            heatmap[h][w][c] = array[index++];
//                        }
//                    }
//                }
//
//                // Loop over each channel
//                for (int c = 0; c < channels; c++) {
//                    float maxVal = Float.MIN_VALUE;
//                    int maxX = -1;
//                    int maxY = -1;
//
//                    // Loop over the height and width of the heatmap
//                    for (int h = 0; h < height; h++) {
//                        for (int w = 0; w < width; w++) {
//                            if (heatmap[h][w][c] > maxVal) {
//                                maxVal = heatmap[h][w][c];
//                                maxX = w;  // x-coordinate
//                                maxY = h;  // y-coordinate
//                            }
//                        }
//                    }
//
//                    System.out.println(maxX + " " + maxY);
//
//                    // Store the (x, y) coordinates of the most probable point for this channel
//                    coordinates[c][0] = maxX > 0 ? ((float) maxX / 48) : -1;
//                    coordinates[c][1] = maxX > 0 ? ((float) maxY / 64) : -1;
//                }
//
//                for (int i = 0; i < coordinates.length; i++) {
//                    System.out.println("Channel " + i + ": x = " + coordinates[i][0] + ", y = " + coordinates[i][1]);
//                }


//                for (int i = 0; i < 17; ++i) {
////                    System.out.println("x: " + array[i * 4] + " y: " + array[i * 4 + 1]);
////                    if (array[i * 4 + 2] > 0.5) {
//                    coordinates[i][0] = array[i * 4];
//                    coordinates[i][1] = array[i * 4 + 1];
////                    }
////                    else
////                    {
////                        coordinates[i][0] = 0f;
////                        coordinates[i][1] = 0f;
////                    }
//                }

//                float max = array[j];
//
//                for (int i = 0;i < array.length;++i)
//                {
//                    System.out.println(array[i]);
//
//                    if (max < array[i])
//                    {
//                        max = array[i];
//                        j = i;
//                    }
//                }

//                Toast.makeText();

//                System.out.println(yogaPoses[j]);

//                for (Pair<Integer, Float> pair : topK(1, array)) {
//                    result.add(mModel.labels[pair.first]);
//                    result.add(String.valueOf(pair.second));
//                }
//        }
//        }

//        releaseTensors(inputs, outputs);
//
//        return coordinates;

    @Override
    protected void onPostExecute(Integer selected)
    {
        super.onPostExecute(selected);
        modelController.onClassificationResult(selected, mJavaExecuteTime);
    }

    @SafeVarargs
    private final void releaseTensors(Map<String, ? extends Tensor>... tensorMaps)
    {
        for (Map<String, ? extends Tensor> tensorMap : tensorMaps)
        {
            for (Tensor tensor : tensorMap.values())
            {
                tensor.release();
            }
        }
    }

    float[] loadRgbBitmapAsFloat(Bitmap image)
    {
        final int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0,
                image.getWidth(), image.getHeight());

//        for (int i = 0;i < pixels.length;++i)
//        {
////            System.out.println(pixels[i]);
//        }

        final float[] pixelsBatched = new float[pixels.length * 3];
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                final int idx = y * image.getWidth() + x;
                final int batchIdx = idx * 3;
                int pixel = pixels[idx];

                float grayscale = ((pixel >> 16) & 0xFF);
                pixelsBatched[batchIdx] = grayscale;
                grayscale = ((pixel >> 8) & 0xFF);
                pixelsBatched[batchIdx + 1] = grayscale;
                grayscale = (pixel & 0xFF);
                pixelsBatched[batchIdx + 2] = grayscale;
            }
        }
        return pixelsBatched;
    }
}
