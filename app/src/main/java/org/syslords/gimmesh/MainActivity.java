package org.syslords.gimmesh;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_selection);

        // Setup toolbar with settings menu
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.main_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                // Open Settings Activity
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            }
            return false;
        });

        MaterialCardView cardPlank = findViewById(R.id.card_plank);
        MaterialCardView cardSquat = findViewById(R.id.card_squat);
        MaterialCardView cardBicepCurls = findViewById(R.id.card_bicep_curls);
        MaterialCardView cardPushup = findViewById(R.id.card_pushup);
        MaterialCardView cardSuryanamaskara = findViewById(R.id.card_suryanamaskara);

        cardPlank.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCameraActivity("Plank");
            }
        });

        cardSquat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCameraActivity("Squat");
            }
        });

        cardBicepCurls.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCameraActivity("BicepCurls");
            }
        });

        cardPushup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCameraActivity("Pushup");
            }
        });

        cardSuryanamaskara.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCameraActivity("Suryanamaskara");
            }
        });

        // Add ripple effect to cards
        cardPlank.setClickable(true);
        cardPlank.setFocusable(true);

        cardSquat.setClickable(true);
        cardSquat.setFocusable(true);

        cardBicepCurls.setClickable(true);
        cardBicepCurls.setFocusable(true);

        cardPushup.setClickable(true);
        cardPushup.setFocusable(true);
    }

    private void startCameraActivity(String yogaSet) {
        Intent intent = new Intent(this, CameraActivity.class);
        intent.putExtra("EXERCISE", yogaSet);
        startActivity(intent);
    }
}