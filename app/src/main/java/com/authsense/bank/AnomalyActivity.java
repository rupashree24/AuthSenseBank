package com.authsense.bank;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class AnomalyActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private static final String TAG = "AnomalyActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        
        setContentView(R.layout.activity_anomaly);

        tts = new TextToSpeech(this, this);

        TextView btnLock = findViewById(R.id.btn_lock_account);
        btnLock.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("AuthSensePrefs", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("is_logged_in", false).apply();
            stopService(new Intent(this, SensorService.class));
            Intent intent = new Intent(this, AuthActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btn_ignore).setOnClickListener(v -> {
            if (tts != null) tts.stop();
            finish();
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            tts.setAudioAttributes(attributes);
            speak();
        }
    }

    private void speak() {
        String text = "Warning. Unusual activity detected. Please secure your account or dismiss if it is you.";
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AnomalyAlert");
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
