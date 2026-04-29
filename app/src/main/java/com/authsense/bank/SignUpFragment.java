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

        EditText etEmail    = view.findViewById(R.id.et_email);
        EditText etPass     = view.findViewById(R.id.et_new_password);
        EditText etConfirm  = view.findViewById(R.id.et_confirm_password);
        CheckBox cbTerms    = view.findViewById(R.id.cb_terms);
        TextView btnCreate  = view.findViewById(R.id.btn_create_account);

        btnCreate.setOnClickListener(v -> {
            String email   = etEmail.getText().toString().trim().toLowerCase();
            String pass    = etPass.getText().toString().trim();
            String confirm = etConfirm.getText().toString().trim();

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(getContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!pass.equals(confirm)) {
                Toast.makeText(getContext(), "Passwords do not match!", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences prefs = getActivity().getSharedPreferences("AuthSensePrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            // Store password keyed by email to allow multiple users
            editor.putString("pass_" + email, pass);
            // Also save this as the primary user for now
            editor.putString("user_email", email); 
            editor.apply();

            Toast.makeText(getContext(), "Account created for " + email + "! Please Sign In.", Toast.LENGTH_LONG).show();
            if (getActivity() instanceof AuthActivity) ((AuthActivity) getActivity()).switchToSignIn();
        });

        return view;
    }
}