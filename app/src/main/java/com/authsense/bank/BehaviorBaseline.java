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
    
    // Keystroke Baseline Stats
    private double meanKeyRaw;      // Your average raw score (speed/pressure combo)
    private double meanKeyError;    // Your average deviation during training
    private double keyErrorStdDev;  // Variation of your deviation
    
    // Motion Baseline Stats
    private double baselineMSE;
    private double mseStdDev;
    
    private boolean isBaselineComplete;
    private SharedPreferences prefs;

    public BehaviorBaseline(Context context, String email) {
        this.userEmail = email;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadFromPrefs();
    }

    public void setBaseline(KeystrokeTracker tracker, double mse, double mseDev, 
                            double keyRaw, double keyErr, double keyErrDev) {
        this.meanKeyRaw = keyRaw;
        this.meanKeyError = keyErr;
        this.keyErrorStdDev = keyErrDev;
        
        this.baselineMSE = mse;
        this.mseStdDev = mseDev;
        
        this.isBaselineComplete = true;
        saveToPrefs();
        Log.i(TAG, "🎯 DYNAMIC BASELINE ESTABLISHED: " + toString());
    }

    public double calculateKeystrokeAnomalyScore(KeystrokeTracker current) {
        if (!isBaselineComplete) return 0;
        // Current raw score
        double normalizedInterval = Math.min(current.getMeanKeystrokeInterval(), 1000.0) / 10.0;
        double currentRaw = (normalizedInterval * 0.7) + (current.getMeanPressure() * 100 * 0.3);
        // Current error (distance from training mean)
        return Math.abs(currentRaw - meanKeyRaw);
    }

    public double getKeystrokeThreshold() {
        // SAME FORMULA AS MSE: Mean Error + 3.0 * StdDev
        return meanKeyError + (3.0 * keyErrorStdDev);
    }

    public double getMotionThreshold() {
        // Mean MSE + 3.0 * StdDev
        return baselineMSE + (3.0 * mseStdDev);
    }

    public void updateBaseline(KeystrokeTracker current, double mse, double alpha) {
        if (!isBaselineComplete) return;
        // Slow adaptive learning for the raw mean
        double normalizedInterval = Math.min(current.getMeanKeystrokeInterval(), 1000.0) / 10.0;
        double currentRaw = (normalizedInterval * 0.7) + (current.getMeanPressure() * 100 * 0.3);
        this.meanKeyRaw = (alpha * currentRaw) + (1 - alpha) * this.meanKeyRaw;
        
        if (mse < getMotionThreshold() * 1.1) {
            this.baselineMSE = (alpha * mse) + (1 - alpha) * this.baselineMSE;
        }
        saveToPrefs();
    }

    private void saveToPrefs() {
        try {
            JSONObject json = new JSONObject();
            json.put("meanRaw", meanKeyRaw);
            json.put("meanErr", meanKeyError);
            json.put("devErr", keyErrorStdDev);
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
                this.meanKeyRaw = obj.getDouble("meanRaw");
                this.meanKeyError = obj.getDouble("meanErr");
                this.keyErrorStdDev = obj.getDouble("devErr");
                this.baselineMSE = obj.getDouble("mse");
                this.mseStdDev = obj.getDouble("mseDev");
                this.isBaselineComplete = obj.getBoolean("complete");
            }
        } catch (Exception e) { isBaselineComplete = false; }
    }

    public boolean isBaselineComplete() { return isBaselineComplete; }
    
    @Override
    public String toString() {
        return String.format(Locale.US, "KeyBase=%.1f, KeyThresh=%.1f, MSEThresh=%.4f", 
            meanKeyRaw, getKeystrokeThreshold(), getMotionThreshold());
    }
}
