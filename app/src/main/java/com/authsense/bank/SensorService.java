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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

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
    private int sensorEventCount = 0;
    
    private List<float[]> dataBuffer = new ArrayList<>();
    private final int WINDOW_SIZE = 300;
    private final int NUM_FEATURES = 6;
    
    private double riskScore = 0;
    private boolean isLocked = false;
    private boolean warningSent = false;
    private static final double RISK_WARNING = 2.0;    
    private static final double RISK_CRITICAL = 5.0;   
    private static final double RISK_INCREMENT = 1.0;
    private static final double RISK_DECAY = 0.1;

    private int criticalAnomalyCount = 0;
    private static final int MAX_CRITICAL_ATTEMPTS = 3;
    
    private BehaviorBaseline behaviorBaseline;
    private KeystrokeTracker keystrokeTracker;
    private String currentUserEmail;
    
    private boolean isCollectingBaseline = false;
    private List<Double> collectionMSEs = new ArrayList<>();
    private long learningStartTime = 0;
    private static final long LEARNING_DURATION_MS = 300_000; // 5 MINUTES
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private BroadcastReceiver keystrokeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.authsense.bank.KEYSTROKE_EVENT".equals(intent.getAction())) {
                float pressure = intent.getFloatExtra("pressure", 0.5f);
                if (pressure == 0) pressure = 0.5f;
                keystrokeTracker.recordKeystroke(pressure);
                Log.d(TAG, "👆 Keystroke received. Count: " + keystrokeTracker.getKeystrokeCount());
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "🟢 STEP 1: Sensor Service Created");
        
        SharedPreferences prefs = getSharedPreferences("AuthSensePrefs", Context.MODE_PRIVATE);
        currentUserEmail = prefs.getString("user_email", "unknown_user");
        Log.d(TAG, "🟢 STEP 2: Current User: " + currentUserEmail);

        try {
            Log.d(TAG, "🟢 STEP 3: Loading AI Model...");
            modelManager = new ModelManager(this);
            Log.d(TAG, "🟢 STEP 4: Model Loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ STEP 3 FAILED: Model error: " + e.getMessage());
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        
        Log.d(TAG, "🟢 STEP 5: Initializing Voice Engine...");
        tts = new TextToSpeech(this, this);
        
        behaviorBaseline = new BehaviorBaseline(this, currentUserEmail);
        keystrokeTracker = new KeystrokeTracker();
        
        if (!behaviorBaseline.isBaselineComplete()) {
            isCollectingBaseline = true;
            learningStartTime = System.currentTimeMillis();
            Log.w(TAG, "📝 MODE: LEARNING started. Need 5 mins of data.");
            mainHandler.post(() -> Toast.makeText(this, "Learning: 5-minute countdown started.", Toast.LENGTH_LONG).show());
        } else {
            isCollectingBaseline = false;
            Log.i(TAG, "🛡️ MODE: MONITORING active.");
        }
        
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        
        if (accel != null) {
            sensorManager.registerListener(this, accel, 100_000);
            Log.d(TAG, "🟢 STEP 6: Accelerometer Registered");
        } else {
            Log.e(TAG, "❌ STEP 6 FAILED: No Accelerometer!");
        }

        if (gyro != null) {
            sensorManager.registerListener(this, gyro, 100_000);
            Log.d(TAG, "🟢 STEP 7: Gyroscope Registered");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vm.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        
        IntentFilter filter = new IntentFilter("com.authsense.bank.KEYSTROKE_EVENT");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(keystrokeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(keystrokeReceiver, filter);
        }
        Log.i(TAG, "🟢 STEP 8: Initialization Complete. Sensors listening...");
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
            isTtsReady = true;
            Log.d(TAG, "✅ Voice engine ready");
        } else {
            Log.e(TAG, "❌ Voice engine failed to init!");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        sensorEventCount++;
        // Print hardware heartbeat every 100 events
        if (sensorEventCount % 100 == 0) {
            Log.v(TAG, "📡 Hardware Pulse: Received 100 events. Current Sensor: " + event.sensor.getType());
        }

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            lastAccel = event.values.clone();
            float[] sample = new float[NUM_FEATURES];
            for (int i = 0; i < 3; i++) sample[i] = modelManager.scaleValue(lastAccel[i], i);
            for (int i = 0; i < 3; i++) sample[i + 3] = modelManager.scaleValue(lastGyro[i], i + 3);
            addDataPoint(sample);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            lastGyro = event.values.clone();
        }
    }

    private void addDataPoint(float[] sample) {
        dataBuffer.add(sample);
        
        // Show progress while filling initial buffer
        if (dataBuffer.size() < WINDOW_SIZE && dataBuffer.size() % 50 == 0) {
            Log.d(TAG, "📥 Filling AI Buffer: " + dataBuffer.size() + "/" + WINDOW_SIZE + " samples...");
        }

        if (dataBuffer.size() >= WINDOW_SIZE) {
            runInference();
            // Slide window by 5 seconds
            for(int i=0; i<50; i++) if(!dataBuffer.isEmpty()) dataBuffer.remove(0);
        }
    }

    private void runInference() {
        if (modelManager == null || modelManager.getSession() == null || isLocked) {
            if (modelManager != null && modelManager.getSession() == null) {
                Log.e(TAG, "⚠️ Inference skipped: OrtSession is NULL");
            }
            return;
        }
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
        } catch (Exception e) { Log.e(TAG, "Inference error", e); }
    }

    private void handleBackgroundCollection(double mse) {
        long elapsed = System.currentTimeMillis() - learningStartTime;
        long remaining = (LEARNING_DURATION_MS - elapsed) / 1000;
        collectionMSEs.add(mse);
        Log.i(TAG, String.format(Locale.US, "⏳ Learning: %ds left | MSE: %.4f", Math.max(0, remaining), mse));

        if (elapsed >= LEARNING_DURATION_MS && keystrokeTracker.hasEnoughData()) {
            double meanMSE = collectionMSEs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double mseStdDev = computeStdDev(collectionMSEs, meanMSE);
            behaviorBaseline.setBaseline(keystrokeTracker, meanMSE, mseStdDev);
            isCollectingBaseline = false;
            Log.i(TAG, "🎉 Learning Complete!");
            mainHandler.post(() -> Toast.makeText(this, "🎉 Profile Created! Monitoring Active.", Toast.LENGTH_LONG).show());
        }
    }

    private void handleMonitoring(double mse) {
        double motionThreshold = behaviorBaseline.getMotionThreshold();
        boolean isMotionAnomaly = mse > motionThreshold;
        double keystrokeScore = behaviorBaseline.calculateKeystrokeAnomalyScore(keystrokeTracker);
        
        Log.d(TAG, String.format(Locale.US, "🛡️ Risk: %.1f | MSE: %.4f (Th: %.4f) | Key: %.2f", riskScore, mse, motionThreshold, keystrokeScore));

        if (isMotionAnomaly || keystrokeScore > 0.5) {
            riskScore += RISK_INCREMENT;
            if (riskScore >= RISK_WARNING && !warningSent) {
                sendEmailAlert("SECURITY WARNING", "Suspicious activity detected on your account.");
                warnUser(false);
            }
            if (riskScore >= RISK_CRITICAL) {
                criticalAnomalyCount++;
                sendEmailAlert("URGENT: ACCOUNT LOCKED", "Persistent suspicious behavior. Account locked for safety.");
                if (criticalAnomalyCount >= MAX_CRITICAL_ATTEMPTS) {
                    lockSystem();
                } else {
                    riskScore = 0;
                    warningSent = false;
                    warnUser(true);
                }
            }
        } else {
            riskScore = Math.max(0, riskScore - RISK_DECAY);
            if (keystrokeTracker.hasEnoughData() && mse < motionThreshold * 1.1) {
                behaviorBaseline.updateBaseline(keystrokeTracker, mse, 0.005);
            }
        }
    }

    private void sendEmailAlert(String subject, String body) {
        final String senderEmail = "authsensebank@gmail.com";
        final String senderPass = "bguq djnp vuiu nxnb";
        final String senderName = "AuthSense Bank Security";

        new Thread(() -> {
            try {
                Log.i(TAG, "📧 Attempting to send alert to: " + currentUserEmail);
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");

                Session session = Session.getInstance(props, new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(senderEmail, senderPass);
                    }
                });

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(senderEmail, senderName));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(currentUserEmail));
                message.setSubject("🚨 " + senderName + ": " + subject);
                message.setText("Hello,\n\n" + body + "\n\nUser ID: " + currentUserEmail + "\nTime: " + new java.util.Date());
                
                Transport.send(message);
                Log.i(TAG, "✅ Email sent successfully to " + currentUserEmail);
            } catch (Exception e) {
                Log.e(TAG, "❌ Email Failed: " + e.getMessage());
            }
        }).start();
    }

    private double computeStdDev(List<Double> values, double mean) {
        if (values.size() < 2) return 0;
        double var = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        return Math.sqrt(var);
    }

    private void warnUser(boolean isUrgent) {
        warningSent = true;
        if (vibrator != null) vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        if (isTtsReady && tts != null) {
            String text = isUrgent ? "Critical alert. Account monitored." : "Warning. Unusual behavior.";
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "Alert");
        }
        Intent intent = new Intent(this, AnomalyActivity.class);
        intent.putExtra("hard_lock", false);
        intent.putExtra("urgent", isUrgent);
        intent.putExtra("attempt_count", criticalAnomalyCount);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void lockSystem() {
        if (isLocked) return;
        isLocked = true;
        Log.e(TAG, "🚨 LOCKING SYSTEM");
        if (vibrator != null) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500}, -1));
        Intent intent = new Intent(this, AnomalyActivity.class);
        intent.putExtra("hard_lock", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        SharedPreferences prefs = getSharedPreferences("AuthSensePrefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("is_logged_in", false).apply();
        stopSelf();
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

    @Override public void onAccuracyChanged(Sensor s, int a) {}
    @Override public IBinder onBind(Intent i) { return null; }
    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        unregisterReceiver(keystrokeReceiver);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
