package com.authsense.bank;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks keystroke/touch patterns for behavioral biometrics
 */
public class KeystrokeTracker {
    private static final String TAG = "KeystrokeTracker";
    private List<Long> keystrokeTimes = new ArrayList<>();
    private List<Float> pressures = new ArrayList<>();
    private List<Float> swipeVelocities = new ArrayList<>();
    private long lastTouchTime = 0;
    private long sessionStartTime = 0;

    public KeystrokeTracker() {
        this.sessionStartTime = System.currentTimeMillis();
    }

    /**
     * Record a keystroke event
     */
    public void recordKeystroke(float pressure) {
        long currentTime = System.currentTimeMillis();
        
        if (lastTouchTime > 0) {
            long interKeystrokeInterval = currentTime - lastTouchTime;
            keystrokeTimes.add(interKeystrokeInterval);
        }
        
        pressures.add(pressure);
        lastTouchTime = currentTime;
        
        Log.d(TAG, "Keystroke recorded | Pressure: " + pressure + " | Interval: " + 
                (lastTouchTime > 0 ? (currentTime - lastTouchTime) : 0) + "ms");
    }

    /**
     * Record a swipe event with velocity
     */
    public void recordSwipe(float velocityX, float velocityY) {
        float velocity = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
        swipeVelocities.add(velocity);
        Log.d(TAG, "Swipe recorded | Velocity: " + velocity);
    }

    /**
     * Get mean inter-keystroke interval (dwell time)
     */
    public double getMeanKeystrokeInterval() {
        if (keystrokeTimes.isEmpty()) return 0;
        return keystrokeTimes.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    /**
     * Get keystroke interval standard deviation
     */
    public double getKeystrokeIntervalStdDev() {
        if (keystrokeTimes.size() < 2) return 0;
        double mean = getMeanKeystrokeInterval();
        double variance = keystrokeTimes.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0);
        return Math.sqrt(variance);
    }

    /**
     * Get mean pressure
     */
    public double getMeanPressure() {
        if (pressures.isEmpty()) return 0;
        return pressures.stream().mapToDouble(Float::doubleValue).average().orElse(0);
    }

    /**
     * Get pressure range (max - min)
     */
    public double getPressureRange() {
        if (pressures.isEmpty()) return 0;
        float min = pressures.stream().min(Float::compareTo).orElse(0f);
        float max = pressures.stream().max(Float::compareTo).orElse(0f);
        return max - min;
    }

    /**
     * Get mean swipe velocity
     */
    public double getMeanSwipeVelocity() {
        if (swipeVelocities.isEmpty()) return 0;
        return swipeVelocities.stream().mapToDouble(Float::doubleValue).average().orElse(0);
    }

    /**
     * Get total keystroke count
     */
    public int getKeystrokeCount() {
        return pressures.size();
    }

    /**
     * Get session duration in seconds
     */
    public long getSessionDuration() {
        return (System.currentTimeMillis() - sessionStartTime) / 1000;
    }

    /**
     * Reset tracker
     */
    public void reset() {
        keystrokeTimes.clear();
        pressures.clear();
        swipeVelocities.clear();
        lastTouchTime = 0;
        sessionStartTime = System.currentTimeMillis();
    }

    /**
     * Check if there's enough data for baseline
     */
    public boolean hasEnoughData() {
        return keystrokeTimes.size() >= 20;  // At least 20 keystrokes
    }

    @Override
    public String toString() {
        return String.format(
            "KeystrokeProfile { MeanInterval: %.0f ms, StdDev: %.0f ms, MeanPressure: %.2f, " +
            "PressureRange: %.2f, MeanVelocity: %.2f, KeystrokeCount: %d, SessionDuration: %d s }",
            getMeanKeystrokeInterval(),
            getKeystrokeIntervalStdDev(),
            getMeanPressure(),
            getPressureRange(),
            getMeanSwipeVelocity(),
            getKeystrokeCount(),
            getSessionDuration()
        );
    }
}
