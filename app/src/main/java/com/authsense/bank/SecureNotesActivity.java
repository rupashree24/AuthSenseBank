package com.authsense.bank;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SecureNotesActivity extends AppCompatActivity {
    private EditText etNoteContent;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_secure_notes);

        etNoteContent = findViewById(R.id.et_note_content);
        
        SharedPreferences authPrefs = getSharedPreferences("AuthSensePrefs", Context.MODE_PRIVATE);
        userEmail = authPrefs.getString("user_email", "guest");

        // Load existing note
        SharedPreferences notePrefs = getSharedPreferences("SecureNotes", Context.MODE_PRIVATE);
        String savedNote = notePrefs.getString("note_" + userEmail, "");
        etNoteContent.setText(savedNote);

        findViewById(R.id.btn_save_note).setOnClickListener(v -> {
            String content = etNoteContent.getText().toString();
            notePrefs.edit().putString("note_" + userEmail, content).apply();
            Toast.makeText(this, "Note saved securely!", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            recordKeystrokeEvent(event.getPressure());
        }
        return super.dispatchTouchEvent(event);
    }

    private void recordKeystrokeEvent(float pressure) {
        Intent intent = new Intent("com.authsense.bank.KEYSTROKE_EVENT");
        intent.setPackage(getPackageName());
        intent.putExtra("pressure", pressure);
        intent.putExtra("timestamp", System.currentTimeMillis());
        sendBroadcast(intent);
    }
}
