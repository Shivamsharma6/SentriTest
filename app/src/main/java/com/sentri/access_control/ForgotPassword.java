package com.sentri.access_control;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Simple forgot-password activity using Firebase Auth:
 * - Validates email
 * - Sends password reset email with FirebaseAuth.sendPasswordResetEmail()
 * - Shows progress and friendly messages
 */
public class ForgotPassword extends AppCompatActivity {

    private EditText etForgotEmail;
    private Button btnSendReset, btnBack;
    private FirebaseAuth mAuth;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Views
        etForgotEmail = findViewById(R.id.etForgotEmail);
        btnSendReset = findViewById(R.id.btnSendReset);
        btnBack = findViewById(R.id.btnBackToSignIn);

        // Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Sending reset email...");
        progressDialog.setCancelable(false);

        btnSendReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { attemptSendReset(); }
        });

        // Back button - returns to previous screen (Sign-in)
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });
    }

    private void attemptSendReset() {
        final String email = etForgotEmail.getText().toString().trim();

        // Validate email
        if (TextUtils.isEmpty(email)) {
            etForgotEmail.setError("Email required");
            etForgotEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etForgotEmail.setError("Enter a valid email");
            etForgotEmail.requestFocus();
            return;
        }

        // Show progress
        progressDialog.show();

        // Send reset email using Firebase Auth
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        progressDialog.dismiss();
                        if (task.isSuccessful()) {
                            // Success - show a friendly message
                            Toast.makeText(ForgotPassword.this,
                                    "Reset email sent. Check your inbox (or spam).",
                                    Toast.LENGTH_LONG).show();

                            // Optionally finish and return to sign-in screen
                            finish();
                        } else {
                            // Failure - show helpful message from exception if available
                            String msg = task.getException() != null ? task.getException().getMessage() : "Failed to send reset email.";
                            Toast.makeText(ForgotPassword.this, "Error: " + msg, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}

