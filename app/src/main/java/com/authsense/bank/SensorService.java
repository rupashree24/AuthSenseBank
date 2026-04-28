package com.authsense.bank;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtSession;

public class SensorService extends Service implements SensorEventListener, TextToSpeech.OnInitListener {
    private static final String TAG = "AUTH_SENSE_MODEL";
    
    private SensorManager sensorManager;
    private ModelManager modelManager;
    private Vibrator vibrator;
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    
    private float[] lastAccel = new float[3];
    private float[] lastGyro = new float[3];
    
    private List<float[]> dataBuffer = new ArrayList<>();
    private final int WINDOW_SIZE = 300;
    private final int NUM_FEATURES = 6;
    
    private long lastAlertTime = 0;
    private static final long ALERT_COOLDOWN = 8000; // 8 seconds between voice alerts

    // Risk scoring variables
    private double riskScore = 0;
    private static final double RISK_THRESHOLD = 3.0;  // Lock after 3 points of risk
    private static final double RISK_INCREMENT = 1.0;  // Add 1 point per anomaly
    private static final double RISK_DECAY = 0.1;      // Remove 0.1 per normal window
    
    private BehaviorBaseline behaviorBaseline;
    private KeystrokeTracker keystrokeTracker;
    private String currentUserEmail;
    
    // Keystroke receiver
    private BroadcastReceiver keystrokeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.authsense.bank.KEYSTROKE_EVENT".equals(intent.getAction())) {
                float pressure = intent.getFloatExtra("pressure", 0);
                keystrokeTracker.recordKeystroke(pressure);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "🟢 Sensor Service Created");
        
        // Get current logged-in user email
        SharedPreferences prefs = getSharedPreferences("AuthSensePrefs", Context.MODE_PRIVATE);
        currentUserEmail = prefs.getString("user_email", "");
        
        modelManager = new ModelManager(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        tts = new TextToSpeech(this, this);
        
        // Load user's behavior baseline
        behaviorBaseline = new BehaviorBaseline(this, currentUserEmail);
        keystrokeTracker = new KeystrokeTracker();
        
        if (!behaviorBaseline.isBaselineComplete()) {
            Log.w(TAG, "⚠️ No baseline found for user: " + currentUserEmail);
        } else {
            Log.i(TAG, "✅ Baseline loaded: " + behaviorBaseline);
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        
        if (accel != null) sensorManager.registerListener(this, accel, 100_000);
        if (gyro != null) sensorManager.registerListener(this, gyro, 100_000);
        
        // Register keystroke receiver
        IntentFilter filter = new IntentFilter("com.authsense.bank.KEYSTROKE_EVENT");
        registerReceiver(keystrokeReceiver, filter);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
            isTtsReady = true;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            lastAccel = event.values.clone();
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            lastGyro = event.values.clone();
            float[] sample = new float[NUM_FEATURES];
            for (int i = 0; i < 3; i++) sample[i] = modelManager.scaleValue(lastAccel[i], i);
            for (int i = 0; i < 3; i++) sample[i + 3] = modelManager.scaleValue(lastGyro[i], i + 3);
            addDataPoint(sample);
        }
    }

    private void addDataPoint(float[] sample) {
        dataBuffer.add(sample);
        if (dataBuffer.size() >= WINDOW_SIZE) {
            runInference();
            for(int i=0; i<10; i++) if(!dataBuffer.isEmpty()) dataBuffer.remove(0);
        }
    }

    private void runInference() {
        if (modelManager == null || modelManager.getSession() == null) return;
        if (!behaviorBaseline.isBaselineComplete()) {
            Log.w(TAG, "Baseline not ready, skipping inference");
            return;
        }
        
        try {
            float[][][] inputData = new float[1][WINDOW_SIZE][NUM_FEATURES];
            for (int i = 0; i < WINDOW_SIZE; i++) inputData[0][i] = dataBuffer.get(i);

            OnnxTensor inputTensor = OnnxTensor.createTensor(modelManager.getEnvironment(), inputData);
            OrtSession.Result result = modelManager.getSession().run(Collections.singletonMap("input", inputTensor));
            
            float[][][] outputData = (float[][][]) result.get(0).getValue();
            double mse = calculateMSE(inputData[0], outputData[0]);
            double motionThreshold = behaviorBaseline.getMotionThreshold();
            boolean isMotionAnomaly = mse > motionThreshold;
            
            // Calculate keystroke anomaly score
            double keystrokeAnomalyScore = keystrokeTracker.hasEnoughData() ? 
                behaviorBaseline.calculateKeystrokeAnomalyScore(keystrokeTracker) : 0;
            
            // Update risk score
            if (isMotionAnomaly || keystrokeAnomalyScore > 0.5) {
                riskScore += RISK_INCREMENT;
                Log.w(TAG, "⚠️ Anomaly detected | Motion MSE: " + mse + " (threshold: " + motionThreshold + 
                    ") | Keystroke Anomaly: " + String.format("%.2f", keystrokeAnomalyScore) + 
                    " | Risk Score: " + String.format("%.2f", riskScore));
            } else {
                riskScore = Math.max(0, riskScore - RISK_DECAY);
                Log.d(TAG, "✓ Normal | MSE: " + mse + " | Risk Score: " + String.format("%.2f", riskScore));
            }
            
            // Lock account if risk exceeds threshold
            if (riskScore >= RISK_THRESHOLD) {
                Log.e(TAG, "🔴 RISK THRESHOLD EXCEEDED! Locking account...");
                triggerAlert();
                riskScore = 0;  // Reset for next potential threat
            }
            
            inputTensor.close();
            result.close();
        } catch (Exception e) { 
            Log.e(TAG, "Inference error", e); 
        }
    }

    private void triggerAlert() {
        // 1. Vibrate
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(2000);
            }
        }

        // 2. Background Voice Alert
        if (isTtsReady && tts != null) {
            tts.speak("Warning. Intrusion behavior detected.", TextToSpeech.QUEUE_FLUSH, null, "BackgroundAlert");
        }

        // 3. Launch Alert Screen
        Intent intent = new Intent(this, AnomalyActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        
        // 4. Send email notification (using Intent to open email)
        sendEmailNotification();
        
        Log.w(TAG, "🚨 SECURITY ALERT TRIGGERED!");
    }

    /**
     * Send email notification to user when suspicious activity detected
     */
    private void sendEmailNotification() {
        try {
            String userEmail = currentUserEmail;
            String subject = "🚨 Suspicious Activity Detected on Your Account";
            String body = "Hello,\n\n" +
                "We detected unusual activity on your AuthSense Bank account at " + 
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + ".\n\n" +
                "Your account has been temporarily locked for security.\n\n" +
                "If this was you, please ignore this message. Otherwise, change your password immediately.\n\n" +
                "Stay secure!\nAuthSense Bank Security Team";

            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            emailIntent.setType("message/rfc822");
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{userEmail});
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            emailIntent.putExtra(Intent.EXTRA_TEXT, body);
            emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Try to open email client (may not work in background)
            // Better: Use a backend API to send email
            Log.i(TAG, "📧 Email notification prepared for: " + userEmail);
            
            // Uncomment if you have email integration
            // startActivity(Intent.createChooser(emailIntent, "Send Email"));
        } catch (Exception e) {
            Log.e(TAG, "Error preparing email notification", e);
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
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        unregisterReceiver(keystrokeReceiver);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        try { if (modelManager != null) modelManager.close(); } catch (Exception e) {}
    }
}
