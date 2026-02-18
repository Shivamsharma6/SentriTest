package com.sentri.access_control;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.sentri.access_control.repositories.BusinessRepository;
import com.sentri.access_control.repositories.CustomerRepository;
import com.sentri.access_control.repositories.FirestoreBusinessRepository;
import com.sentri.access_control.repositories.FirestoreCustomerRepository;
import com.sentri.access_control.utils.CommentHelper;
import com.sentri.access_control.utils.ImageUploadHelper;
import com.sentri.access_control.utils.PrefsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddCustomer extends AppCompatActivity {

    private static final int PICK_AADHAR = 101;
    private static final int CAPTURE_AADHAR = 102;
    private static final int PICK_PHOTO = 103;
    private static final int CAPTURE_PHOTO = 104;

    private EditText etName;
    private EditText etEmail;
    private EditText etWhatsapp;
    private EditText etDob;
    private EditText etEmergency;
    private EditText etParents;
    private EditText etCurrAddr;
    private EditText etPermAddr;
    private EditText etStudyStream;
    private EditText etInstitution;
    private EditText etComments;

    private Button btnAadharPhoto;
    private Button btnCustomerPhoto;
    private Button btnSave;
    private ImageView ivBack;

    private Uri aadharUri;
    private Uri photoUri;
    private Uri tempImageUri;

    private String aadharUrl = "";
    private String photoUrl = "";
    private String businessId;
    private String businessPrefix;
    private String userEmail;

    private ProgressDialog progressDialog;
    private PrefsManager prefsManager;
    private CustomerRepository customerRepository;
    private BusinessRepository businessRepository;
    private CommentHelper commentHelper;

    private boolean isEditMode = false;
    private String editCustomerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_customer);

        prefsManager = new PrefsManager(this);

        Intent intent = getIntent();
        isEditMode = intent.getBooleanExtra("isEdit", false);
        if (isEditMode) {
            businessId = intent.getStringExtra("businessDocId");
            editCustomerId = intent.getStringExtra("customerDocId");
        } else {
            businessId = prefsManager.getCurrentBizId();
        }
        userEmail = prefsManager.getUserEmail();

        if (businessId == null || businessId.trim().isEmpty()) {
            Toast.makeText(this, "Business not specified in session", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        customerRepository = new FirestoreCustomerRepository(db);
        businessRepository = new FirestoreBusinessRepository(db);
        commentHelper = new CommentHelper(db);

        bindViews();
        setupKeyboardScrollBehavior();
        setupActions();

        if (isEditMode) {
            loadCustomerForEdit();
        }

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Saving...");
        progressDialog.setCancelable(false);
    }

    private void bindViews() {
        etName = findViewById(R.id.etCustomerName);
        etEmail = findViewById(R.id.etCustomerEmail);
        etWhatsapp = findViewById(R.id.etCustomerWhatsapp);
        etDob = findViewById(R.id.etCustomerDob);
        etEmergency = findViewById(R.id.etCustomerEmergency);
        etParents = findViewById(R.id.etCustomerParents);
        etCurrAddr = findViewById(R.id.etCustomerCurrAddr);
        etPermAddr = findViewById(R.id.etCustomerPermAddr);
        etStudyStream = findViewById(R.id.etCustomerStream);
        etInstitution = findViewById(R.id.etCustomerInstitution);
        etComments = findViewById(R.id.etComments);

        btnAadharPhoto = findViewById(R.id.btnAadharPhoto);
        btnCustomerPhoto = findViewById(R.id.btnCustomerPhoto);
        btnSave = findViewById(R.id.btnSaveCustomer);
        ivBack = findViewById(R.id.ivBack);
    }

    private void setupActions() {
        ivBack.setOnClickListener(v -> finish());
        btnAadharPhoto.setOnClickListener(v -> showChooserDialog(PICK_AADHAR, CAPTURE_AADHAR));
        btnCustomerPhoto.setOnClickListener(v -> showChooserDialog(PICK_PHOTO, CAPTURE_PHOTO));
        btnSave.setText(isEditMode ? "Update Customer" : "Save Customer");
        btnSave.setOnClickListener(v -> {
            if (isEditMode) {
                validateAndUpdate();
            } else {
                validateAndSave();
            }
        });

        etDob.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(
                    this,
                    (DatePicker view, int year, int month, int dayOfMonth) -> {
                        String mm = String.format("%02d", month + 1);
                        String dd = String.format("%02d", dayOfMonth);
                        etDob.setText(year + "-" + mm + "-" + dd);
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            ).show();
        });
    }

    private void setupKeyboardScrollBehavior() {
        ScrollView scrollView = findViewById(R.id.scrollView);
        List<EditText> fields = Arrays.asList(etInstitution, etComments);

        for (EditText field : fields) {
            field.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    return;
                }
                scrollView.postDelayed(() -> scrollView.smoothScrollTo(0, v.getBottom()), 200);
            });
        }
    }

    private void loadCustomerForEdit() {
        if (editCustomerId == null || editCustomerId.trim().isEmpty()) {
            Toast.makeText(this, "Missing customer id for edit", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        customerRepository.fetchCustomer(
                businessId,
                editCustomerId,
                doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Customer not found", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }

                    etName.setText(safeString(doc.getString("customer_name")));
                    etEmail.setText(safeString(doc.getString("customer_email")));
                    etWhatsapp.setText(safeString(doc.getString("customer_whatsapp")));
                    etDob.setText(safeString(doc.getString("customer_dob")));
                    etEmergency.setText(safeString(doc.getString("customer_emergency_contact")));
                    etParents.setText(safeString(doc.getString("customer_parents_name")));
                    etCurrAddr.setText(safeString(doc.getString("customer_current_address")));
                    etPermAddr.setText(safeString(doc.getString("customer_permanent_address")));
                    etStudyStream.setText(safeString(doc.getString("customer_study_stream")));
                    etInstitution.setText(safeString(doc.getString("customer_institution")));
                },
                e -> Toast.makeText(this, "Failed to load customer: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private void validateAndUpdate() {
        if (getText(etName).isEmpty()) {
            etName.setError("Required");
            etName.requestFocus();
            return;
        }

        btnSave.setEnabled(false);
        progressDialog.show();

        Map<String, Object> updates = new HashMap<>();
        updates.put("customer_name", getText(etName));
        updates.put("customer_email", getText(etEmail));
        updates.put("customer_whatsapp", getText(etWhatsapp));
        updates.put("customer_dob", getText(etDob));
        updates.put("customer_emergency_contact", getText(etEmergency));
        updates.put("customer_parents_name", getText(etParents));
        updates.put("customer_current_address", getText(etCurrAddr));
        updates.put("customer_permanent_address", getText(etPermAddr));
        updates.put("customer_study_stream", getText(etStudyStream));
        updates.put("customer_institution", getText(etInstitution));

        customerRepository.updateCustomerProfile(
                businessId,
                editCustomerId,
                updates,
                () -> {
                    progressDialog.dismiss();
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Customer updated", Toast.LENGTH_SHORT).show();
                    finish();
                },
                e -> {
                    progressDialog.dismiss();
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Error updating: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
        );
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private void showChooserDialog(int pickCode, int cameraCode) {
        new AlertDialog.Builder(this)
                .setTitle("Select Option")
                .setItems(new String[]{"Camera", "Choose from Gallery"}, (dialog, which) -> {
                    if (which == 0) {
                        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        tempImageUri = createImageUri();
                        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, tempImageUri);
                        startActivityForResult(cameraIntent, cameraCode);
                    } else {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        startActivityForResult(Intent.createChooser(intent, "Select Image"), pickCode);
                    }
                })
                .show();
    }

    private Uri createImageUri() {
        String fileName = "temp_" + System.currentTimeMillis() + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, fileName);
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        if (requestCode == PICK_AADHAR && data != null && data.getData() != null) {
            aadharUri = data.getData();
            setUploadButtonState(btnAadharPhoto, "Aadhar Selected");
            return;
        }

        if (requestCode == CAPTURE_AADHAR && tempImageUri != null) {
            aadharUri = tempImageUri;
            setUploadButtonState(btnAadharPhoto, "Aadhar Captured");
            return;
        }

        if (requestCode == PICK_PHOTO && data != null && data.getData() != null) {
            photoUri = data.getData();
            setUploadButtonState(btnCustomerPhoto, "Photo Selected");
            return;
        }

        if (requestCode == CAPTURE_PHOTO && tempImageUri != null) {
            photoUri = tempImageUri;
            setUploadButtonState(btnCustomerPhoto, "Photo Captured");
        }
    }

    private void setUploadButtonState(Button button, String text) {
        button.setText(text);
        button.setBackgroundColor(Color.parseColor("#317FFF"));
    }

    private void validateAndSave() {
        if (getText(etName).isEmpty()) {
            etName.setError("Required");
            etName.requestFocus();
            return;
        }

        if (aadharUri == null) {
            Toast.makeText(this, "Please upload Aadhar photo", Toast.LENGTH_SHORT).show();
            return;
        }

        if (photoUri == null) {
            Toast.makeText(this, "Please upload Customer photo", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);
        progressDialog.show();
        uploadImagesAndCreateCustomer();
    }

    private void uploadImagesAndCreateCustomer() {
        String storageKey = buildStorageKey(getText(etEmail));
        StorageReference baseRef = FirebaseStorage.getInstance()
                .getReference("customer_photos")
                .child(storageKey);

        ImageUploadHelper.uploadImage(
                aadharUri,
                baseRef.child("aadhar.jpg"),
                aadharDownloadUrl -> {
                    aadharUrl = aadharDownloadUrl;
                    ImageUploadHelper.uploadImage(
                            photoUri,
                            baseRef.child("photo.jpg"),
                            photoDownloadUrl -> {
                                photoUrl = photoDownloadUrl;
                                fetchBusinessPrefixAndCreateCustomer();
                            },
                            this::onSaveFailure
                    );
                },
                this::onSaveFailure
        );
    }

    private String buildStorageKey(String email) {
        String normalized = (email == null || email.trim().isEmpty()) ? "unknown_email" : email.trim();
        return normalized.replace("@", "_").replace(".", "_");
    }

    private void fetchBusinessPrefixAndCreateCustomer() {
        businessRepository.fetchBusinessPrefix(
                businessId,
                prefix -> {
                    if (prefix == null || prefix.trim().isEmpty()) {
                        onSaveFailure(new IllegalStateException("Business prefix missing"));
                        return;
                    }
                    businessPrefix = prefix;
                    prefsManager.setBusinessPrefix(prefix);
                    createCustomerRecord();
                },
                this::onSaveFailure
        );
    }

    private void createCustomerRecord() {
        customerRepository.fetchNextCustomerId(
                businessId,
                businessPrefix,
                newCustomerId -> {
                    Map<String, Object> customerData = buildCustomerPayload(newCustomerId);
                    customerRepository.saveCustomer(
                            businessId,
                            newCustomerId,
                            customerData,
                            () -> onCustomerSaved(newCustomerId),
                            this::onSaveFailure
                    );
                },
                this::onSaveFailure
        );
    }

    private Map<String, Object> buildCustomerPayload(String customerId) {
        Map<String, Object> data = new HashMap<>();
        data.put("created_at", FieldValue.serverTimestamp());
        data.put("updated_at", FieldValue.serverTimestamp());
        data.put("created_by", userEmail);
        data.put("customer_business_id", businessId);
        data.put("customer_id", customerId);
        data.put("customer_name", getText(etName));
        data.put("customer_email", getText(etEmail));
        data.put("customer_whatsapp", getText(etWhatsapp));
        data.put("customer_dob", getText(etDob));
        data.put("customer_emergency_contact", getText(etEmergency));
        data.put("customer_parents_name", getText(etParents));
        data.put("customer_current_address", getText(etCurrAddr));
        data.put("customer_permanent_address", getText(etPermAddr));
        data.put("customer_study_stream", getText(etStudyStream));
        data.put("customer_institution", getText(etInstitution));
        data.put("customer_subscription_start_date", new ArrayList<>());
        data.put("customer_subscription_end_date", new ArrayList<>());
        data.put("customer_last_payment_date", "");
        data.put("customer_current_payment_rate", "");
        data.put("customer_current_card_id", "");
        data.put("customer_current_card_data", "");
        data.put("customer_current_seat", new ArrayList<>());
        data.put("customer_current_shift_id", new ArrayList<>());
        data.put("customer_status", false);
        data.put("customer_aadhar_photo", aadharUrl);
        data.put("customer_photo", photoUrl);
        return data;
    }

    private void onCustomerSaved(String customerId) {
        String commentText = getText(etComments);
        if (!commentText.isEmpty()) {
            commentHelper.addComment(
                    businessId,
                    customerId,
                    businessPrefix,
                    "customer",
                    commentText,
                    userEmail,
                    () -> {
                    }
            );
        }

        progressDialog.dismiss();
        Toast.makeText(this, "Added customer: " + customerId, Toast.LENGTH_SHORT).show();
        startShiftFlow(customerId);
    }

    private void startShiftFlow(String customerId) {
        Intent intent = new Intent(this, CustomerShift.class);
        intent.putExtra("businessDocId", businessId);
        intent.putExtra("customerDocId", customerId);
        startActivity(intent);
        finish();
    }

    private void onSaveFailure(Exception exception) {
        progressDialog.dismiss();
        btnSave.setEnabled(true);
        Toast.makeText(this, "Error saving customer: " + exception.getMessage(), Toast.LENGTH_LONG).show();
    }

    private String getText(EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}
