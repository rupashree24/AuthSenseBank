package com.authsense.bank;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtSession;

/**
 * 2-minute baseline collection activity after user signup
 * Collects keystroke + motion data to establish per-user behavior profile
 */
public class BaselineCollectionActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "BaselineCollection";
    private static final long COLLECTION_DURATION_MS = 120000;  // 2 minutes
    private static final int WINDOW_SIZE = 300;
    private static final int NUM_FEATURES = 6;

    private TextView tvStatus;
    private ProgressBar progressBar;
    private KeystrokeTracker keystrokeTracker;
    private SensorManager sensorManager;
    private ModelManager modelManager;
    private String userEmail;

    private float[] lastAccel = new float[3];
    private float[] lastGyro = new float[3];
    private List<float[]> motionBuffer = new ArrayList<>();
    private List<Double> mseValues = new ArrayList<>();
    private List<Double> collectionKeyScores = new ArrayList<>();

    private long startTime;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_baseline_collection);

        tvStatus = findViewById(R.id.tv_status);
        progressBar = findViewById(R.id.progress_baseline);

        SharedPreferences prefs = getSharedPreferences("AuthSensePrefs", Context.MODE_PRIVATE);
        userEmail = prefs.getString("user_email", "");

        keystrokeTracker = new KeystrokeTracker();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        modelManager = new ModelManager(this);

        startTime = System.currentTimeMillis();
        progressBar.setMax((int) (COLLECTION_DURATION_MS / 1000));

        // Start sensor monitoring
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (accel != null) sensorManager.registerListener(this, accel, 100_000);
        if (gyro != null) sensorManager.registerListener(this, gyro, 100_000);

        updateStatus();

        // Auto-finish after 2 minutes
        handler.postDelayed(this::finishBaseline, COLLECTION_DURATION_MS);

        Log.i(TAG, "Baseline collection started for user: " + userEmail);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            lastAccel = event.values.clone();
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            lastGyro = event.values.clone();
            
            // Combine accel + gyro into feature vector
            float[] sample = new float[NUM_FEATURES];
            for (int i = 0; i < 3; i++) sample[i] = modelManager.scaleValue(lastAccel[i], i);
            for (int i = 0; i < 3; i++) sample[i + 3] = modelManager.scaleValue(lastGyro[i], i + 3);
            
            motionBuffer.add(sample);
            
            // Run inference every WINDOW_SIZE samples
            if (motionBuffer.size() >= WINDOW_SIZE) {
                recordMotionMSE();
                for (int i = 0; i < 10; i++) {
                    if (!motionBuffer.isEmpty()) motionBuffer.remove(0);
                }
            }
        }
    }

    /**
     * Record motion MSE
     */
    private void recordMotionMSE() {
        try {
            float[][][] inputData = new float[1][WINDOW_SIZE][NUM_FEATURES];
            for (int i = 0; i < WINDOW_SIZE; i++) {
                inputData[0][i] = motionBuffer.get(i);
            }

            OnnxTensor inputTensor = OnnxTensor.createTensor(
                modelManager.getEnvironment(), inputData);
            OrtSession.Result result = modelManager.getSession()
                .run(Collections.singletonMap("input", inputTensor));

            float[][][] outputData = (float[][][]) result.get(0).getValue();
            double mse = calculateMSE(inputData[0], outputData[0]);
            mseValues.add(mse);

            inputTensor.close();
            result.close();

            Log.d(TAG, "Motion MSE: " + mse);
        } catch (Exception e) {
            Log.e(TAG, "Error computing MSE during baseline", e);
        }
    }

    private double calculateMSE(float[][] original, float[][] reconstructed) {
        double sumError = 0;
        int count = 0;
        for (int i = 0; i < original.length; i++) {
            for (int j = 0; j < original[i].length; j++) {
                float diff = original[i][j] - reconstructed[i][j];
                sumError += (diff * diff);
                count++;
            }
        }
        return sumError / count;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float pressure = event.getPressure();
            if (pressure <= 0f) pressure = 0.5f;
            keystrokeTracker.recordKeystroke(pressure);

            // Sample score on every touch once we have enough data
            if (keystrokeTracker.hasEnoughData()) {
                double normalizedInterval = Math.min(keystrokeTracker.getMeanKeystrokeInterval(), 1000.0) / 10.0;
                double currentRaw = (normalizedInterval * 0.7) + (keystrokeTracker.getMeanPressure() * 100 * 0.3);
                collectionKeyScores.add(currentRaw);
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void updateStatus() {
        long elapsed = System.currentTimeMillis() - startTime;
        int secondsLeft = (int) ((COLLECTION_DURATION_MS - elapsed) / 1000);
        int progress = (int) (elapsed / 1000);

        tvStatus.setText(String.format(
            "Baseline Collection\nPlease use the app normally\n%d seconds remaining\nKeystrokes: %d",
            secondsLeft,
            keystrokeTracker.getKeystrokeCount()
        ));
        progressBar.setProgress(progress);

        if (secondsLeft > 0) {
            handler.postDelayed(this::updateStatus, 1000);
        }
    }

    private void finishBaseline() {
        // Compute Motion MSE Stats
        double meanMSE = mseValues.isEmpty() ? 0 : 
            mseValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double mseStdDev = computeStdDev(mseValues, meanMSE);

        // Compute Keystroke Stats
        double meanKeyRaw = collectionKeyScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        
        List<Double> keyErrors = new ArrayList<>();
        for (Double score : collectionKeyScores) {
            keyErrors.add(Math.abs(score - meanKeyRaw));
        }
        double meanKeyError = keyErrors.isEmpty() ? 0 : 
            keyErrors.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double keyErrorStdDev = computeStdDev(keyErrors, meanKeyError);

        // Save baseline with full 6-parameter set
        BehaviorBaseline baseline = new BehaviorBaseline(this, userEmail);
        baseline.setBaseline(keystrokeTracker, meanMSE, mseStdDev, meanKeyRaw, meanKeyError, keyErrorStdDev);

        Log.i(TAG, "Baseline collection complete: " + baseline);
        Log.i(TAG, "Keystroke samples: " + collectionKeyScores.size());

        tvStatus.setText("Baseline complete! Launching app...");

        // Start MainActivity
        handler.postDelayed(() -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        }, 2000);
    }

    private double computeStdDev(List<Double> values, double mean) {
        if (values.size() < 2) return 0;
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0);
        return Math.sqrt(variance);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        sensorManager.unregisterListener(this);
        if (modelManager != null) {
            try {
                modelManager.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing ModelManager", e);
            }
        }
    }
}
