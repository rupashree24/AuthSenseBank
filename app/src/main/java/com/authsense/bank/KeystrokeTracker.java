package com.authsense.bank;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks keystroke/touch patterns for behavioral biometrics
 * Updated to use a sliding window for more responsive detection.
 */
public class KeystrokeTracker {
    private static final String TAG = "KeystrokeTracker";
    private static final int WINDOW_SIZE = 30; // Only keep last 30 strokes for monitoring
    
    private List<Long> keystrokeTimes = new ArrayList<>();
    private List<Float> pressures = new ArrayList<>();
    private List<Float> swipeVelocities = new ArrayList<>();
    private long lastTouchTime = 0;
    private long sessionStartTime = 0;

    public KeystrokeTracker() {
        this.sessionStartTime = System.currentTimeMillis();
    }

    public void recordKeystroke(float pressure) {
        if (pressure <= 0f) pressure = 0.5f;
        long currentTime = System.currentTimeMillis();
        
        if (lastTouchTime > 0) {
            long interKeystrokeInterval = currentTime - lastTouchTime;
            // Ignore very long breaks (e.g. user put phone down) to not skew averages
            if (interKeystrokeInterval < 3000) {
                keystrokeTimes.add(interKeystrokeInterval);
                if (keystrokeTimes.size() > WINDOW_SIZE) keystrokeTimes.remove(0);
            }
        }
        
        pressures.add(pressure);
        if (pressures.size() > WINDOW_SIZE) pressures.remove(0);
        
        lastTouchTime = currentTime;
        Log.d(TAG, "Keystroke recorded | Window Size: " + pressures.size());
    }

    public double getMeanKeystrokeInterval() {
        if (keystrokeTimes.isEmpty()) return 0;
        return keystrokeTimes.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    public double getKeystrokeIntervalStdDev() {
        if (keystrokeTimes.size() < 2) return 0;
        double mean = getMeanKeystrokeInterval();
        double variance = keystrokeTimes.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0);
        return Math.sqrt(variance);
    }

    public double getMeanPressure() {
        if (pressures.isEmpty()) return 0;
        return pressures.stream().mapToDouble(Float::doubleValue).average().orElse(0);
    }

    public double getPressureStdDev() {
        if (pressures.size() < 2) return 0;
        double mean = getMeanPressure();
        double variance = pressures.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0);
        return Math.sqrt(variance);
    }

    public double getPressureRange() {
        if (pressures.isEmpty()) return 0;
        float min = pressures.stream().min(Float::compareTo).orElse(0f);
        float max = pressures.stream().max(Float::compareTo).orElse(0f);
        return max - min;
    }

    public double getMeanSwipeVelocity() {
        if (swipeVelocities.isEmpty()) return 0;
        return swipeVelocities.stream().mapToDouble(Float::doubleValue).average().orElse(0);
    }

    public int getKeystrokeCount() {
        return pressures.size();
    }

    public boolean hasEnoughData() {
        // We need at least 15 samples in the current window to be confident
        return keystrokeTimes.size() >= 15; 
    }

    public void reset() {
        keystrokeTimes.clear();
        pressures.clear();
        swipeVelocities.clear();
        lastTouchTime = 0;
    }
}
