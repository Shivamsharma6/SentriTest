package com.sentri.access_control;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class CustomerEnquiry extends AppCompatActivity {

    private static final int REQUEST_CODE_AADHAR = 201;

    private EditText etName, etParentName, etDOB, etEmail, etWhatsapp, etEmergency,
            etCurrentAddress, etPermanentAddress, etStudyStream, etInstitution;
    private LinearLayout btnUploadAadhar;
    private Uri aadharUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_enquiry);

        ImageView back = findViewById(R.id.ivBack);
        back.setOnClickListener(v -> finish());

        etName = findViewById(R.id.etName);
        etParentName = findViewById(R.id.etParentName);
        etDOB = findViewById(R.id.etDOB);
        etEmail = findViewById(R.id.etEmail);
        etWhatsapp = findViewById(R.id.etWhatsapp);
        etEmergency = findViewById(R.id.etEmergency);
        etCurrentAddress = findViewById(R.id.etCurrentAddress);
        etPermanentAddress = findViewById(R.id.etPermanentAddress);
        etStudyStream = findViewById(R.id.etStudyStream);
        etInstitution = findViewById(R.id.etInstitution);
        btnUploadAadhar = findViewById(R.id.btnUploadAadhar);

        btnUploadAadhar.setOnClickListener(v -> openFileChooser());
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select Aadhar PDF"), REQUEST_CODE_AADHAR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_AADHAR) {
            aadharUri = data.getData();
            Toast.makeText(this, "Aadhar Uploaded", Toast.LENGTH_SHORT).show();
        }
    }
}
