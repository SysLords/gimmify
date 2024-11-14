package org.syslords.gimmesh;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.qualcomm.qti.snpe.NeuralNetwork;
import com.qualcomm.qti.snpe.SNPE;


public class MainActivity extends AppCompatActivity {

    ModelController modelController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

//        SNPE.logger.initializeLogging((Application) getApplicationContext(), NeuralNetwork.LogLevel.LOG_VERBOSE);
//        SNPE.logger.setLogLevel(NeuralNetwork.LogLevel.LOG_INFO);


//        modelController = new ModelController(this);
//        modelController.loadModel();

        Intent intent = new Intent(MainActivity.this, CameraActivity.class);
        startActivity(intent);
    }
}