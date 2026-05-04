package com.authsense.bank;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.authsense.bank.adapters.ChatAdapter;
import java.util.ArrayList;
import java.util.List;

public class ChatbotActivity extends AppCompatActivity {
    private RecyclerView rvChat;
    private ChatAdapter adapter;
    private List<ChatMessage> messages;
    private EditText etMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        rvChat = findViewById(R.id.rv_chat);
        etMessage = findViewById(R.id.et_message);
        messages = new ArrayList<>();
        adapter = new ChatAdapter(messages);

        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        // Initial greeting
        addBotMessage("Hello! I'm your AuthSense Assistant. How can I help you today?");

        findViewById(R.id.btn_send).setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                addUserMessage(text);
                etMessage.setText("");
                processUserMessage(text);
            }
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

    private void addUserMessage(String text) {
        messages.add(new ChatMessage(text, true));
        adapter.notifyItemInserted(messages.size() - 1);
        rvChat.scrollToPosition(messages.size() - 1);
    }

    private void addBotMessage(String text) {
        messages.add(new ChatMessage(text, false));
        adapter.notifyItemInserted(messages.size() - 1);
        rvChat.scrollToPosition(messages.size() - 1);
    }

    private void processUserMessage(String text) {
        String lower = text.toLowerCase();
        new Handler().postDelayed(() -> {
            if (lower.contains("balance")) {
                addBotMessage("Your current account balance is ₹45,230.50.");
            } else if (lower.contains("transfer") || lower.contains("send")) {
                addBotMessage("You can transfer money using the 'Instant Transfers' feature on the Home screen.");
            } else if (lower.contains("card")) {
                addBotMessage("Your virtual card is active. You can view details in the 'Credit Card' section.");
            } else if (lower.contains("secure") || lower.contains("safety")) {
                addBotMessage("AuthSense uses advanced behavioral biometrics to keep your account safe.");
            } else {
                addBotMessage("I'm here to help with your banking needs. You can ask about your balance, transfers, or security.");
            }
        }, 1000);
    }
}
