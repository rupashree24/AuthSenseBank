package com.authsense.bank;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;

public class SignInFragment extends Fragment {
    private static final String TAG = "AUTH_SENSE_LOGIN";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_signin, container, false);

        EditText etEmail = view.findViewById(R.id.et_customer_id);
        EditText etPass = view.findViewById(R.id.et_password);
        TextView btnSignIn = view.findViewById(R.id.btn_signin);
        TextView tvGoSignUp = view.findViewById(R.id.tv_go_signup);

        btnSignIn.setOnClickListener(v -> {
            String inputEmail = etEmail.getText().toString().trim();
            String inputPass = etPass.getText().toString().trim();
            Log.d(TAG, "Sign-in attempt for: " + inputEmail);

            if (inputEmail.isEmpty() || inputPass.isEmpty()) {
                Toast.makeText(getContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences prefs = getActivity().getSharedPreferences("AuthSensePrefs", Context.MODE_PRIVATE);
            String savedEmail = prefs.getString("user_email", "");
            String savedPass = prefs.getString("user_password", "");

            if (inputEmail.equals(savedEmail) && inputPass.equals(savedPass)) {
                Log.i(TAG, "✅ Sign-in Successful!");
                
                // Mark as logged in
                prefs.edit().putBoolean("is_logged_in", true).apply();

                // Check if baseline exists for this user
                String baselineKey = "baseline_" + inputEmail;
                boolean hasBaseline = prefs.getSharedPreferences("BehaviorBaseline", Context.MODE_PRIVATE)
                    .getString(baselineKey, null) != null;

                // If first login, go to baseline collection; otherwise go to MainActivity
                Intent intent;
                if (!hasBaseline) {
                    Log.i(TAG, "First login detected. Starting baseline collection...");
                    intent = new Intent(getActivity(), BaselineCollectionActivity.class);
                } else {
                    intent = new Intent(getActivity(), MainActivity.class);
                }
                
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else {
                Log.w(TAG, "❌ Invalid credentials. Expected: " + savedEmail);
                Toast.makeText(getContext(), "Invalid Email or Password", Toast.LENGTH_SHORT).show();
            }
        });

        tvGoSignUp.setOnClickListener(v -> {
            if (getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).switchToSignUp();
            }
        });

        return view;
    }
}
