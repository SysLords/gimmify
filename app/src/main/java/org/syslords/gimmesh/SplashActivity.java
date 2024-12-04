package org.syslords.gimmesh;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;

public class SplashActivity extends Activity {

    private MediaPlayer mediaPlayer;

    public SplashActivity() {
        // Default constructor
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash); // Ensure this matches your layout file name

        mediaPlayer = MediaPlayer.create(this, R.raw.splash_sound); // Place the sound file in res/raw folder
        mediaPlayer.start();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {

                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    mediaPlayer = null;
                }

                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                finish(); // Close this activity
            }
        }, 2300); // 2-second delay
    }
}
