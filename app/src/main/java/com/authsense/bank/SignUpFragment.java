package com.authsense.bank;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;

public class SignUpFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_signup, container, false);

        EditText etName     = view.findViewById(R.id.et_fullname);
        EditText etEmail    = view.findViewById(R.id.et_email);
        EditText etPhone    = view.findViewById(R.id.et_phone);
        EditText etDob      = view.findViewById(R.id.et_dob);
        EditText etAadhaar  = view.findViewById(R.id.et_aadhaar);
        EditText etPan      = view.findViewById(R.id.et_pan);
        EditText etPass     = view.findViewById(R.id.et_new_password);
        EditText etConfirm  = view.findViewById(R.id.et_confirm_password);
        CheckBox cbTerms    = view.findViewById(R.id.cb_terms);
        TextView btnCreate  = view.findViewById(R.id.btn_create_account);
        TextView tvGoSignIn = view.findViewById(R.id.tv_go_signin);

        btnCreate.setOnClickListener(v -> {
            String name    = etName.getText().toString().trim();
            String email   = etEmail.getText().toString().trim();
            String phone   = etPhone.getText().toString().trim();
            String dob     = etDob.getText().toString().trim();
            String aadhaar = etAadhaar.getText().toString().trim();
            String pan     = etPan.getText().toString().trim();
            String pass    = etPass.getText().toString().trim();
            String confirm = etConfirm.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() ||
                    dob.isEmpty() || aadhaar.isEmpty() || pan.isEmpty() ||
                    pass.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(getContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!pass.equals(confirm)) {
                Toast.makeText(getContext(), "Passwords do not match!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (pass.length() < 8) {
                Toast.makeText(getContext(), "Password must be at least 8 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            if (aadhaar.length() != 12) {
                Toast.makeText(getContext(), "Enter a valid 12-digit Aadhaar number", Toast.LENGTH_SHORT).show();
                return;
            }
            if (pan.length() != 10) {
                Toast.makeText(getContext(), "Enter a valid 10-character PAN number", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!cbTerms.isChecked()) {
                Toast.makeText(getContext(), "Please accept Terms & Conditions", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save user info to SharedPreferences for local authentication
            SharedPreferences prefs = getActivity().getSharedPreferences("AuthSensePrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("user_name", name);
            editor.putString("user_email", email);
            editor.putString("user_password", pass);
            editor.apply();

            Toast.makeText(getContext(),
                    "Account created successfully! Please Sign In. 🎉",
                    Toast.LENGTH_LONG).show();
            
            // Switch to Sign In screen
            if (getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).switchToSignIn();
            }
        });

        tvGoSignIn.setOnClickListener(v -> {
            if (getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).switchToSignIn();
            }
        });

        return view;
    }
}