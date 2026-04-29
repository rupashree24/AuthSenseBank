package com.authsense.bank;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONObject;
import java.util.Locale;

public class BehaviorBaseline {
    private static final String TAG = "BehaviorBaseline";
    private static final String PREFS_NAME = "BehaviorBaseline";

    private String userEmail;
    private double meanKeystrokeInterval;
    private double keystrokeIntervalStdDev;
    private double meanPressure;
    private double baselineMSE;
    private double mseStdDev;
    private boolean isBaselineComplete;

    private SharedPreferences prefs;

    public BehaviorBaseline(Context context, String email) {
        this.userEmail = email;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadFromPrefs();
    }

    public void setBaseline(KeystrokeTracker tracker, double mse, double mseDev) {
        this.meanKeystrokeInterval = tracker.getMeanKeystrokeInterval();
        this.keystrokeIntervalStdDev = tracker.getKeystrokeIntervalStdDev();
        this.meanPressure = tracker.getMeanPressure();
        this.baselineMSE = mse;
        this.mseStdDev = mseDev;
        this.isBaselineComplete = true;
        saveToPrefs();
        Log.i(TAG, "🎯 FINAL BASELINE ESTABLISHED: " + toString());
    }

    public double calculateKeystrokeAnomalyScore(KeystrokeTracker current) {
        if (!isBaselineComplete) return 0;
        
        // Speed check
        double diff = Math.abs(current.getMeanKeystrokeInterval() - this.meanKeystrokeInterval);
        // Sensitive normalization: Score of 1.0 means you are typing roughly 2x faster/slower than base
        double speedScore = diff / (this.keystrokeIntervalStdDev + 15);
        
        // Pressure check
        double pressScore = Math.abs(current.getMeanPressure() - this.meanPressure) / 0.1;
        
        double total = (speedScore * 0.8) + (pressScore * 0.2);
        return Math.min(1.0, total / 1.2); // Cap at 1.0, trigger point is usually 0.5+
    }

    public double getMotionThreshold() {
        // Reduced to 2.0x for much higher sensitivity to tilts
        return baselineMSE + (2.0 * mseStdDev);
    }

    public void updateBaseline(KeystrokeTracker current, double mse, double alpha) {
        if (!isBaselineComplete) return;
        // Slow adaptive learning (0.5% weight to new data)
        this.meanKeystrokeInterval = (alpha * current.getMeanKeystrokeInterval()) + (1 - alpha) * this.meanKeystrokeInterval;
        if (mse < getMotionThreshold() * 1.1) {
            this.baselineMSE = (alpha * mse) + (1 - alpha) * this.baselineMSE;
        }
        saveToPrefs();
    }

    private void saveToPrefs() {
        try {
            JSONObject json = new JSONObject();
            json.put("int", meanKeystrokeInterval);
            json.put("dev", keystrokeIntervalStdDev);
            json.put("press", meanPressure);
            json.put("mse", baselineMSE);
            json.put("mseDev", mseStdDev);
            json.put("complete", isBaselineComplete);
            prefs.edit().putString("baseline_" + userEmail, json.toString()).apply();
        } catch (Exception e) {}
    }

    private void loadFromPrefs() {
        try {
            String json = prefs.getString("baseline_" + userEmail, null);
            if (json != null) {
                JSONObject obj = new JSONObject(json);
                this.meanKeystrokeInterval = obj.getDouble("int");
                this.keystrokeIntervalStdDev = obj.getDouble("dev");
                this.meanPressure = obj.getDouble("press");
                this.baselineMSE = obj.getDouble("mse");
                this.mseStdDev = obj.getDouble("mseDev");
                this.isBaselineComplete = obj.getBoolean("complete");
            }
        } catch (Exception e) { isBaselineComplete = false; }
    }

    public boolean isBaselineComplete() { return isBaselineComplete; }
    
    @Override
    public String toString() {
        return String.format(Locale.US, "Base: Speed=%.0fms, MSE=%.6f", meanKeystrokeInterval, baselineMSE);
    }
}
