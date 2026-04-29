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

        btnSignIn.setOnClickListener(v -> {
            String inputEmail = etEmail.getText().toString().trim().toLowerCase();
            String inputPass = etPass.getText().toString().trim();

            if (inputEmail.isEmpty() || inputPass.isEmpty()) {
                Toast.makeText(getContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences prefs = getActivity().getSharedPreferences("AuthSensePrefs", Context.MODE_PRIVATE);
            
            // MULTI-USER CHECK: Get password specifically for this email
            String savedPass = prefs.getString("pass_" + inputEmail, "");
            String blockKey = "blocked_" + inputEmail;

            if (prefs.getBoolean(blockKey, false)) {
                Toast.makeText(getContext(), "ACCOUNT BLOCKED. Contact support.", Toast.LENGTH_LONG).show();
                return;
            }

            if (!savedPass.isEmpty() && inputPass.equals(savedPass)) {
                Log.i(TAG, "✅ Login Success: " + inputEmail);
                
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("is_logged_in", true);
                editor.putString("user_email", inputEmail); // MARK THIS AS THE ACTIVE USER
                editor.apply();

                Intent intent = new Intent(getActivity(), MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "Invalid Credentials", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.tv_go_signup).setOnClickListener(v -> {
            if (getActivity() instanceof AuthActivity) ((AuthActivity) getActivity()).switchToSignUp();
        });

        return view;
    }
}
