package com.authsense.bank;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONObject;

/**
 * Manages per-user behavioral baseline (keystroke + motion patterns)
 */
public class BehaviorBaseline {
    private static final String TAG = "BehaviorBaseline";
    private static final String PREFS_NAME = "BehaviorBaseline";

    private String userEmail;
    private double meanKeystrokeInterval;
    private double keystrokeIntervalStdDev;
    private double meanPressure;
    private double pressureRange;
    private double meanSwipeVelocity;
    private double baselineMSE;
    private double mseStdDev;
    private long baselineTimestamp;
    private boolean isBaselineComplete;

    private SharedPreferences prefs;

    public BehaviorBaseline(Context context, String email) {
        this.userEmail = email;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadFromPrefs();
    }

    /**
     * Save baseline from keystroke tracker and motion data
     */
    public void setBaseline(KeystrokeTracker keystrokeTracker, double mse, double mseStdDev) {
        this.meanKeystrokeInterval = keystrokeTracker.getMeanKeystrokeInterval();
        this.keystrokeIntervalStdDev = keystrokeTracker.getKeystrokeIntervalStdDev();
        this.meanPressure = keystrokeTracker.getMeanPressure();
        this.pressureRange = keystrokeTracker.getPressureRange();
        this.meanSwipeVelocity = keystrokeTracker.getMeanSwipeVelocity();
        this.baselineMSE = mse;
        this.mseStdDev = mseStdDev;
        this.baselineTimestamp = System.currentTimeMillis();
        this.isBaselineComplete = true;

        saveToPrefs();
        Log.i(TAG, "Baseline set for user: " + userEmail + " | " + this);
    }

    /**
     * Calculate keystroke anomaly score (0-1, where 1 is most anomalous)
     */
    public double calculateKeystrokeAnomalyScore(KeystrokeTracker currentTracker) {
        if (!isBaselineComplete) return 0;

        double intervalDeviation = Math.abs(
            currentTracker.getMeanKeystrokeInterval() - this.meanKeystrokeInterval
        ) / (this.keystrokeIntervalStdDev + 1);  // +1 to avoid division by zero

        double pressureDeviation = Math.abs(
            currentTracker.getMeanPressure() - this.meanPressure
        ) / (this.pressureRange + 0.1);

        double velocityDeviation = Math.abs(
            currentTracker.getMeanSwipeVelocity() - this.meanSwipeVelocity
        ) / (this.meanSwipeVelocity + 1);

        // Weighted average (keystroke interval is most important)
        double anomalyScore = (intervalDeviation * 0.5 + pressureDeviation * 0.3 + velocityDeviation * 0.2);
        
        // Normalize to 0-1 range
        return Math.min(1.0, anomalyScore / 3.0);
    }

    /**
     * Calculate motion anomaly threshold (based on baseline MSE)
     */
    public double getMotionThreshold() {
        return baselineMSE + (2 * mseStdDev);
    }

    /**
     * Adapt the baseline to normal behavior over time (Adaptive Learning)
     */
    public void updateBaseline(KeystrokeTracker currentTracker, double currentMSE, double alpha) {
        if (!isBaselineComplete) return;

        // Exponential Moving Average to slowly adapt to user changes
        this.meanKeystrokeInterval = (alpha * currentTracker.getMeanKeystrokeInterval()) + (1 - alpha) * this.meanKeystrokeInterval;
        this.keystrokeIntervalStdDev = (alpha * currentTracker.getKeystrokeIntervalStdDev()) + (1 - alpha) * this.keystrokeIntervalStdDev;
        this.meanPressure = (alpha * currentTracker.getMeanPressure()) + (1 - alpha) * this.meanPressure;
        this.pressureRange = (alpha * currentTracker.getPressureRange()) + (1 - alpha) * this.pressureRange;
        this.meanSwipeVelocity = (alpha * currentTracker.getMeanSwipeVelocity()) + (1 - alpha) * this.meanSwipeVelocity;
        
        this.baselineMSE = (alpha * currentMSE) + (1 - alpha) * this.baselineMSE;
        
        saveToPrefs();
        Log.d(TAG, "Baseline adapted for user: " + userEmail);
    }

    /**
     * Save baseline to SharedPreferences
     */
    private void saveToPrefs() {
        try {
            String key = "baseline_" + userEmail;
            JSONObject json = new JSONObject();
            json.put("meanKeystrokeInterval", meanKeystrokeInterval);
            json.put("keystrokeIntervalStdDev", keystrokeIntervalStdDev);
            json.put("meanPressure", meanPressure);
            json.put("pressureRange", pressureRange);
            json.put("meanSwipeVelocity", meanSwipeVelocity);
            json.put("baselineMSE", baselineMSE);
            json.put("mseStdDev", mseStdDev);
            json.put("baselineTimestamp", baselineTimestamp);
            json.put("isBaselineComplete", isBaselineComplete);

            prefs.edit().putString(key, json.toString()).apply();
            Log.d(TAG, "Baseline saved to SharedPreferences for: " + userEmail);
        } catch (Exception e) {
            Log.e(TAG, "Error saving baseline", e);
        }
    }

    /**
     * Load baseline from SharedPreferences
     */
    private void loadFromPrefs() {
        try {
            String key = "baseline_" + userEmail;
            String json = prefs.getString(key, null);
            if (json != null) {
                JSONObject obj = new JSONObject(json);
                this.meanKeystrokeInterval = obj.getDouble("meanKeystrokeInterval");
                this.keystrokeIntervalStdDev = obj.getDouble("keystrokeIntervalStdDev");
                this.meanPressure = obj.getDouble("meanPressure");
                this.pressureRange = obj.getDouble("pressureRange");
                this.meanSwipeVelocity = obj.getDouble("meanSwipeVelocity");
                this.baselineMSE = obj.getDouble("baselineMSE");
                this.mseStdDev = obj.getDouble("mseStdDev");
                this.baselineTimestamp = obj.getLong("baselineTimestamp");
                this.isBaselineComplete = obj.getBoolean("isBaselineComplete");
                Log.d(TAG, "Baseline loaded from SharedPreferences for: " + userEmail);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading baseline", e);
            isBaselineComplete = false;
        }
    }

    public boolean isBaselineComplete() {
        return isBaselineComplete;
    }

    @Override
    public String toString() {
        return String.format(
            "BehaviorBaseline { User: %s, KeystrokeInterval: %.0f±%.0f ms, " +
            "Pressure: %.2f (range: %.2f), Velocity: %.2f, Motion MSE: %.6f±%.6f, Complete: %s }",
            userEmail, meanKeystrokeInterval, keystrokeIntervalStdDev,
            meanPressure, pressureRange, meanSwipeVelocity,
            baselineMSE, mseStdDev, isBaselineComplete
        );
    }
}
