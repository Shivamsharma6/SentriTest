package com.sentri.access_control;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class Settings extends AppCompatActivity {

    private ImageView backButton, ivAvatar;
    private TextView tvTitle, tvAccessLabel, tvAccessValue;
    private View rowDetails, rowUsers, rowDevices;
    private View rowFaq, rowContact, rowPaymentLink;
    private Button btnLight, btnDark, btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        backButton    = findViewById(R.id.backButton);
        ivAvatar      = findViewById(R.id.ivAvatar);
        tvTitle       = findViewById(R.id.tvTitle);
        tvAccessLabel = findViewById(R.id.tvAccessLabel);
        tvAccessValue = findViewById(R.id.tvAccessValue);

        rowDetails    = findViewById(R.id.rowDetails);
        rowUsers      = findViewById(R.id.rowUsers);
        rowDevices    = findViewById(R.id.rowDevices);
        rowFaq        = findViewById(R.id.rowFaq);
        rowContact    = findViewById(R.id.rowContact);
        rowPaymentLink= findViewById(R.id.rowPaymentLink);

        btnLight      = findViewById(R.id.btnLight);
        btnDark       = findViewById(R.id.btnDark);
        btnLogout     = findViewById(R.id.btnLogout);

        // Back
        backButton.setOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userAccessLevel  = prefs.getString("currentBizAccessLevel","user");
        String currentBizName   = prefs.getString("currentBizName","Business Name");
        tvTitle.setText(currentBizName);
        tvAccessValue.setText(userAccessLevel);
        // TODO: load avatar via URI if provided
        // ivAvatar.setImageURI(...);

        // Navigation actions
        rowDetails.setOnClickListener(v ->
                startActivity(new Intent(this, UserDetails.class)));

        rowUsers.setOnClickListener(v ->
                startActivity(new Intent(this, UserList.class)));

        rowDevices.setOnClickListener(v ->
                startActivity(new Intent(this, DeviceList.class)));

//        rowFaq.setOnClickListener(v ->
//                startActivity(new Intent(this, FAQActivity.class)));
//
//        rowContact.setOnClickListener(v ->
//                startActivity(new Intent(this, ContactSupport.class)));

        rowPaymentLink.setOnClickListener(v ->
                startActivity(new Intent(this, SentriPayment.class)));

        // Theme toggle
        btnLight.setOnClickListener(v -> {
            AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_NO);
            updateThemeButtons();
        });
        btnDark.setOnClickListener(v -> {
            AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_YES);
            updateThemeButtons();
        });
        updateThemeButtons();

        // Logout
        btnLogout.setOnClickListener(v -> {
            // TODO: clear user session, prefs, etc.
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
    }

    private void updateThemeButtons() {
        int mode = AppCompatDelegate.getDefaultNightMode();
        boolean dark = (mode == AppCompatDelegate.MODE_NIGHT_YES);
        btnLight.setEnabled(dark);
        btnDark.setEnabled(!dark);
    }
}
