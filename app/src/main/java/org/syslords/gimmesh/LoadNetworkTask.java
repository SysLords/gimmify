/*
 * Copyright (c) 2016-2018, 2023 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */
package org.syslords.gimmesh;

import android.app.Application;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.qualcomm.qti.snpe.NeuralNetwork;
import com.qualcomm.qti.snpe.SNPE;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class LoadNetworkTask extends AsyncTask<File, Void, NeuralNetwork> {

    private static final String LOG_TAG = LoadNetworkTask.class.getSimpleName();

    private final Application mApplication;

    private final NeuralNetwork.Runtime mTargetRuntime;

    private long mLoadTime = -1;

    private final InputStream mInFile;

    long mSize;

    ModelController modelController;

    public LoadNetworkTask(final Application application,
                           ModelController modelController,
                           final InputStream inFile,
                           long size,
                           final NeuralNetwork.Runtime targetRuntime) {
        mApplication = application;
        this.modelController = modelController;
        mInFile = inFile;
        mSize = size;
        mTargetRuntime = targetRuntime;
    }

    @Override
    protected NeuralNetwork doInBackground(File... params) {
        NeuralNetwork network = null;
        try {

            File dir = new File(mApplication.getFilesDir(), "model");
            if(!dir.exists()){
                dir.mkdir();
            }

            File file = new File(dir, "model");
            FileUtils.copyInputStreamToFile(mInFile, file);

            final SNPE.NeuralNetworkBuilder builder = new SNPE.NeuralNetworkBuilder(mApplication)
                    .setDebugEnabled(false)
                    .setRuntimeOrder(mTargetRuntime)
                    .setModel(file)
                    .setCpuFallbackEnabled(true)
                    .setUseUserSuppliedBuffers(false)
                    .setUnsignedPD(true);
//                    .setCpuFixedPointMode(false);

            builder.setRuntimeCheckOption(NeuralNetwork.RuntimeCheckOption.UNSIGNEDPD_CHECK);


            final long start = SystemClock.elapsedRealtime();
            network = builder.build();
            final long end = SystemClock.elapsedRealtime();

            mLoadTime = end - start;
        } catch (IllegalStateException | IOException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return network;
    }

    @Override
    protected void onPostExecute(NeuralNetwork neuralNetwork) {
        super.onPostExecute(neuralNetwork);
        if (neuralNetwork != null) {
            if (!isCancelled()) {
                modelController.onNetworkLoaded(neuralNetwork, mLoadTime);
                System.out.println("Model Loaded");
            } else {
                neuralNetwork.release();
            }
        } else {
            if (!isCancelled()) {
                System.out.println("Model Loading Failed");
                // mController.onNetworkLoadFailed();
            }
        }
    }
}
