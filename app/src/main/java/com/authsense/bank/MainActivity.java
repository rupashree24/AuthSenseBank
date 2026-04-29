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
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        // Display User Email in Profile section
        String userEmail = prefs.getString("user_email", "Guest");
        TextView tvProfile = findViewById(R.id.tv_user_profile);
        tvProfile.setText(userEmail);

        Log.i(TAG, "🏠 MainActivity Started - Starting Security Service");
        
        startService(new Intent(this, SensorService.class));
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

        findViewById(R.id.btn_logout).setOnClickListener(v -> {
            // Logout logic
            prefs.edit().putBoolean("is_logged_in", false).apply();
            stopService(new Intent(this, SensorService.class));
            
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
            
            Intent intent = new Intent(this, AuthActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences("AuthSensePrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("is_logged_in", false)) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            recordKeystrokeEvent(event.getPressure());
        }
        return super.dispatchTouchEvent(event);
    }

    private void recordKeystrokeEvent(float pressure) {
        long currentTime = System.currentTimeMillis();
        Intent intent = new Intent("com.authsense.bank.KEYSTROKE_EVENT");
        intent.setPackage(getPackageName());
        intent.putExtra("pressure", pressure);
        intent.putExtra("timestamp", currentTime);
        sendBroadcast(intent);
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
            } catch (Exception e) {}
        }
    }
}
