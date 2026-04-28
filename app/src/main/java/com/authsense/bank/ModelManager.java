package com.authsense.bank;

import android.content.Context;
import android.util.Log;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ModelManager {
    private static final String TAG = "ModelManager";
    private OrtEnvironment env;
    private OrtSession session;
    private double threshold;
    private float[] scalerMin;
    private float[] scalerMax;

    public ModelManager(Context context) {
        try {
            // 1. Load Metadata
            String jsonString = loadJSONFromAsset(context, "lstm_meta.json");
            JSONObject jsonObject = new JSONObject(jsonString);
            this.threshold = jsonObject.getDouble("lstm_ae_threshold");
            
            // Load Scaler values
            JSONArray minArray = jsonObject.getJSONArray("scaler_min");
            JSONArray maxArray = jsonObject.getJSONArray("scaler_max");
            scalerMin = new float[minArray.length()];
            scalerMax = new float[maxArray.length()];
            for (int i = 0; i < minArray.length(); i++) {
                scalerMin[i] = (float) minArray.getDouble(i);
                scalerMax[i] = (float) maxArray.getDouble(i);
            }

            // 2. Initialize ONNX
            env = OrtEnvironment.getEnvironment();
            String modelPath = copyAssetToInternalStorage(context, "lstm_ae.onnx");
            try {
                copyAssetToInternalStorage(context, "lstm_ae.onnx.data");
            } catch (Exception ignored) {}

            session = env.createSession(modelPath);
            Log.d(TAG, "Model loaded successfully. Threshold: " + threshold);

        } catch (Exception e) {
            Log.e(TAG, "Initialization error: " + e.getMessage(), e);
        }
    }

    public float scaleValue(float value, int index) {
        if (index >= scalerMin.length) return value;
        // Min-Max Scaling formula: (x - min) / (max - min)
        float denom = scalerMax[index] - scalerMin[index];
        if (denom == 0) return 0;
        return (value - scalerMin[index]) / denom;
    }

    private String copyAssetToInternalStorage(Context context, String fileName) throws Exception {
        File file = new File(context.getFilesDir(), fileName);
        try (InputStream is = context.getAssets().open(fileName);
             FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }
        return file.getAbsolutePath();
    }

    private String loadJSONFromAsset(Context context, String fileName) throws Exception {
        InputStream is = context.getAssets().open(fileName);
        byte[] buffer = new byte[is.available()];
        is.read(buffer);
        is.close();
        return new String(buffer, StandardCharsets.UTF_8);
    }

    public OrtSession getSession() { return session; }
    public OrtEnvironment getEnvironment() { return env; }
    public double getThreshold() { return threshold; }
    public void close() throws Exception {
        if (session != null) session.close();
        if (env != null) env.close();
    }
}
