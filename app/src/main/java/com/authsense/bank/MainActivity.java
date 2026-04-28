package com.authsense.bank;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AUTH_SENSE_MAIN";
    private ModelManager modelManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check if user is logged in
        SharedPreferences prefs = getSharedPreferences("AuthSensePrefs", Context.MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("is_logged_in", false);

        if (!isLoggedIn) {
            // Redirect to AuthActivity if not logged in
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        Log.i(TAG, "🏠 MainActivity Started - Starting Security Service");
        
        // Start the sensor monitoring service immediately on Main Activity start
        startService(new Intent(this, SensorService.class));

        // Initialize the ModelManager
        modelManager = new ModelManager(this);
        
        loadFragment(new HomeFragment());

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (id == R.id.nav_credit) {
                fragment = new CreditCardFragment();
            } else if (id == R.id.nav_features) {
                fragment = new FeaturesFragment();
            } else if (id == R.id.nav_news) {
                fragment = new NewsFragment();
            } else {
                return false;
            }
            loadFragment(fragment);
            return true;
        });

        findViewById(R.id.btn_contact).setOnClickListener(v ->
                Toast.makeText(this, "Contact: 1800-AUTH-BANK", Toast.LENGTH_SHORT).show());

        findViewById(R.id.btn_signup).setOnClickListener(v -> {
            // Logout logic
            prefs.edit().putBoolean("is_logged_in", false).apply();
            stopService(new Intent(this, SensorService.class));
            startActivity(new Intent(this, AuthActivity.class));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reactive Lock: If background service locked the app, kick user out
        SharedPreferences prefs = getSharedPreferences("AuthSensePrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("is_logged_in", false)) {
            Log.e(TAG, "🔒 SYSTEM LOCKED - Redirecting to Login");
            startActivity(new Intent(this, AuthActivity.class));
            finish();
        }
    }

    /**
     * Capture touch events to track keystroke/swipe patterns
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Only record the initial touch (ACTION_DOWN) as a keystroke
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            Log.d(TAG, "Touch detected at: " + event.getX() + ", " + event.getY());
            recordKeystrokeEvent(event.getPressure());
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Record keystroke/touch event (sends to service)
     */
    private void recordKeystrokeEvent(float pressure) {
        long currentTime = System.currentTimeMillis();
        
        // Send explicit broadcast to SensorService
        Intent intent = new Intent("com.authsense.bank.KEYSTROKE_EVENT");
        intent.setPackage(getPackageName()); // CRITICAL: Makes the broadcast explicit
        intent.putExtra("pressure", pressure);
        intent.putExtra("timestamp", currentTime);
        
        sendBroadcast(intent);
        Log.d(TAG, "Broadcast sent for tap");
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (modelManager != null) {
            try {
                modelManager.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing ModelManager", e);
            }
        }
    }
}
