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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        
        setContentView(R.layout.activity_anomaly);

        refreshUI();

        findViewById(R.id.btn_lock_account).setOnClickListener(v -> {
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

    private void refreshUI() {
        boolean isHardLock = getIntent().getBooleanExtra("hard_lock", false);
        boolean isUrgent = getIntent().getBooleanExtra("urgent", false);
        int attemptCount = getIntent().getIntExtra("attempt_count", 0);

        TextView tvTitle = findViewById(R.id.tv_anomaly_title);
        TextView tvDesc = findViewById(R.id.tv_anomaly_desc);
        TextView btnLock = findViewById(R.id.btn_lock_account);
        TextView btnIgnore = findViewById(R.id.btn_ignore);

        if (isHardLock) {
            tvTitle.setText("ACCOUNT LOCKED");
            tvDesc.setText("Too many unusual activity attempts. Please re-authenticate.");
            btnIgnore.setVisibility(android.view.View.GONE);
            btnLock.setText("RE-AUTHENTICATE");
        } else if (isUrgent) {
            tvTitle.setText("FINAL WARNING");
            tvDesc.setText("Suspicious behavior persists. Attempt " + attemptCount + " of 3. Next one will lock your account!");
            btnIgnore.setVisibility(android.view.View.VISIBLE);
            btnLock.setText("LOCK NOW");
        } else {
            tvTitle.setText("SECURITY ALERT");
            tvDesc.setText("Unusual activity detected. Is this you?");
            btnIgnore.setVisibility(android.view.View.VISIBLE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        refreshUI();
        speak();
    }

    private void speak() {
        boolean isHardLock = getIntent().getBooleanExtra("hard_lock", false);
        boolean isUrgent = getIntent().getBooleanExtra("urgent", false);
        int attemptCount = getIntent().getIntExtra("attempt_count", 0);

        String text;
        if (isHardLock) {
            text = "Critical Security Alert. Your system is now locked due to too many suspicious attempts.";
        } else if (isUrgent) {
            text = "Final Warning. Suspicious behavior detected. This is attempt " + attemptCount + ". Please secure your account.";
        } else {
            text = "Warning. Unusual activity detected. Please secure your account or dismiss if it is you.";
        }

        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AnomalyAlert");
        }
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
