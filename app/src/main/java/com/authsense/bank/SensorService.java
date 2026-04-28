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
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

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
    private boolean isLocked = false;
    private boolean warningSent = false;
    private static final double RISK_WARNING = 2.0;
    private static final double RISK_CRITICAL = 3.0;
    private static final double RISK_INCREMENT = 1.0;
    private static final double RISK_DECAY = 0.1;
    
    private BehaviorBaseline behaviorBaseline;
    private KeystrokeTracker keystrokeTracker;
    private String currentUserEmail;
    
    // Background collection variables
    private boolean isCollectingBaseline = false;
    private List<Double> collectionMSEs = new ArrayList<>();
    private static final int REQUIRED_MSE_SAMPLES = 50; // About 2-3 mins of background data
    
    // Keystroke receiver
    private BroadcastReceiver keystrokeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.authsense.bank.KEYSTROKE_EVENT".equals(intent.getAction())) {
                float pressure = intent.getFloatExtra("pressure", 0.5f); // Default to 0.5 for emulator
                if (pressure == 0) pressure = 0.5f; // Fix for emulator mouse clicks
                keystrokeTracker.recordKeystroke(pressure);
                Log.d(TAG, "Keystroke received in service. Total: " + keystrokeTracker.getKeystrokeCount());
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
            Log.w(TAG, "⚠️ No baseline found. Starting background collection...");
            isCollectingBaseline = true;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(keystrokeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(keystrokeReceiver, filter);
        }
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
        if (modelManager == null || modelManager.getSession() == null || isLocked) return;
        
        try {
            float[][][] inputData = new float[1][WINDOW_SIZE][NUM_FEATURES];
            for (int i = 0; i < WINDOW_SIZE; i++) inputData[0][i] = dataBuffer.get(i);

            OnnxTensor inputTensor = OnnxTensor.createTensor(modelManager.getEnvironment(), inputData);
            OrtSession.Result result = modelManager.getSession().run(Collections.singletonMap("input", inputTensor));
            
            float[][][] outputData = (float[][][]) result.get(0).getValue();
            double mse = calculateMSE(inputData[0], outputData[0]);

            if (isCollectingBaseline) {
                handleBackgroundCollection(mse);
            } else {
                handleMonitoring(mse);
            }
            
            inputTensor.close();
            result.close();
        } catch (Exception e) { 
            Log.e(TAG, "Inference error", e); 
        }
    }

    /**
     * Phase 1: Quietly collect data while user interacts with the app
     */
    private void handleBackgroundCollection(double mse) {
        collectionMSEs.add(mse);
        int keystrokeCount = keystrokeTracker.getKeystrokeCount();
        Log.d(TAG, String.format("Learning... Motion: %d/%d | Taps: %d/20", 
            collectionMSEs.size(), REQUIRED_MSE_SAMPLES, keystrokeCount));

        // Once we have enough data (motion samples + at least 20 keystrokes)
        if (collectionMSEs.size() >= REQUIRED_MSE_SAMPLES && keystrokeTracker.hasEnoughData()) {
            double meanMSE = collectionMSEs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double mseStdDev = computeStdDev(collectionMSEs, meanMSE);
            
            behaviorBaseline.setBaseline(keystrokeTracker, meanMSE, mseStdDev);
            isCollectingBaseline = false;
            Log.i(TAG, "🎉 Background Learning Complete! Protection is now ACTIVE.");
        }
    }

    /**
     * Phase 2: Actively monitor for intruders and adapt
     */
    private void handleMonitoring(double mse) {
        double motionThreshold = behaviorBaseline.getMotionThreshold();
        boolean isMotionAnomaly = mse > motionThreshold;
        
        // Calculate keystroke anomaly score
        double keystrokeAnomalyScore = keystrokeTracker.hasEnoughData() ? 
            behaviorBaseline.calculateKeystrokeAnomalyScore(keystrokeTracker) : 0;
        
        // Update risk score
        if (isMotionAnomaly || keystrokeAnomalyScore > 0.5) {
            riskScore += RISK_INCREMENT;
            Log.w(TAG, "⚠️ Anomaly detected! Total Risk: " + String.format("%.2f", riskScore));
            
            // Tiered Response Logic
            if (riskScore >= RISK_CRITICAL) {
                sendEmailNotification(); // Send critical alert email
                lockSystem();
            } else if (riskScore >= RISK_WARNING && !warningSent) {
                sendEmailNotification(); // Send warning email
                warnUser();
            }
        } else {
            riskScore = Math.max(0, riskScore - RISK_DECAY);
            
            // Adaptive Learning: Slightly update baseline if behavior is normal
            if (keystrokeTracker.hasEnoughData()) {
                behaviorBaseline.updateBaseline(keystrokeTracker, mse, 0.005);
                Log.d(TAG, "✓ Normal | MSE: " + String.format("%.4f", mse) + " | Baseline Adapted/Fixed");
            } else {
                Log.d(TAG, "✓ Normal | MSE: " + String.format("%.4f", mse) + " | Waiting for more keystrokes to adapt...");
            }
        }
    }

    private double computeStdDev(List<Double> values, double mean) {
        if (values.size() < 2) return 0;
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0);
        return Math.sqrt(variance);
    }

    /**
     * Tier 1: Warn User (Risk 2.0)
     */
    private void warnUser() {
        warningSent = true;
        Log.w(TAG, "🔔 Tier 1: Warning User...");

        // 1. Vibrate briefly
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        }

        // 2. Voice Alert
        if (isTtsReady && tts != null) {
            tts.speak("Warning. Unusual behavior detected. Please verify your identity.", TextToSpeech.QUEUE_FLUSH, null, "WarningAlert");
        }

        // 3. Launch Alert Screen (Warning Mode)
        Intent intent = new Intent(this, AnomalyActivity.class);
        intent.putExtra("hard_lock", false);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);

        // 4. Send Background Email (Network Call)
        sendEmailNotification();
    }

    /**
     * Tier 2: Lock System (Risk 3.0)
     */
    private void lockSystem() {
        if (isLocked) return;
        isLocked = true;
        Log.e(TAG, "🚨 Tier 2: CRITICAL RISK - LOCKING SYSTEM");

        // 1. Heavy Vibration
        if (vibrator != null) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500}, -1));
        }

        // 2. Launch Alert/Lock Screen (Hard Lock Mode)
        Intent intent = new Intent(this, AnomalyActivity.class);
        intent.putExtra("hard_lock", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        
        // 3. Force Logout & Increment Strikes (User-Specific)
        SharedPreferences prefs = getSharedPreferences("AuthSensePrefs", Context.MODE_PRIVATE);
        String strikeKey = "strikes_" + currentUserEmail;
        String blockKey = "blocked_" + currentUserEmail;
        
        int strikes = prefs.getInt(strikeKey, 0) + 1;
        
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("is_logged_in", false);
        editor.putInt(strikeKey, strikes);
        
        if (strikes >= 3) {
            Log.e(TAG, "🚫 ACCOUNT PERMANENTLY BLOCKED: " + currentUserEmail);
            editor.putBoolean(blockKey, true);
        }
        editor.apply();
        
        // 4. Stop service
        stopSelf();
    }

    /**
     * Send email automatically in the background using JavaMail
     */
    private void sendEmailNotification() {
        // 🔑 CONFIGURATION: Set this once. This is the "Bank's" email.
        final String senderEmail = "your-email@gmail.com"; 
        final String senderPassword = "your-app-password"; 

        new Thread(() -> {
            try {
                if (senderEmail.contains("your-email")) {
                    Log.w(TAG, "📧 Email skipped: Placeholder credentials detected. Please set real ones in SensorService.java");
                    return;
                }

                Log.i(TAG, "📧 Sending alert from " + senderEmail + " to " + currentUserEmail);

                // SMTP Properties
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");

                // Create Session
                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(senderEmail, senderPassword);
                    }
                });

                // Create Message
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(senderEmail));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(currentUserEmail));
                message.setSubject("🚨 Security Alert: Suspicious Activity Detected");
                
                String time = new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date());
                String body = "Hello,\n\nUnusual behavior was detected on your AuthSense Bank account. " +
                        "As a precaution, the system has been locked.\n\n" +
                        "Event Time: " + time + "\n" +
                        "User ID: " + currentUserEmail + "\n\n" +
                        "If this was not you, please contact support immediately.";
                
                message.setText(body);

                // Send Email
                Transport.send(message);
                Log.i(TAG, "✅ BACKGROUND EMAIL SENT SUCCESSFULLY to " + currentUserEmail);

            } catch (MessagingException e) {
                Log.e(TAG, "❌ SMTP Error: Failed to send background email. Check credentials/network.", e);
            } catch (Exception e) {
                Log.e(TAG, "❌ General Error in background email", e);
            }
        }).start();
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
