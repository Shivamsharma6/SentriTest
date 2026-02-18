package com.sentri.access_control.repositories;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.sentri.access_control.data.FirestorePaths;
import com.sentri.access_control.models.BusinessConfig;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class FirestoreBusinessRepository implements BusinessRepository {
    private final FirebaseFirestore db;

    public FirestoreBusinessRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void fetchBusinessPrefix(String businessId, Consumer<String> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .get()
                .addOnSuccessListener(doc -> onSuccess.accept(doc.getString(FirestorePaths.FIELD_BUSINESS_PREFIX)))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchBusinessConfig(String businessId, Consumer<BusinessConfig> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .get()
                .addOnSuccessListener(doc -> onSuccess.accept(parseBusinessConfig(doc)))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchActiveCustomers(String businessId, Consumer<List<DocumentSnapshot>> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .whereEqualTo("customer_status", true)
                .get()
                .addOnSuccessListener(snap -> onSuccess.accept(snap.getDocuments()))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchNextBusinessId(int year, Consumer<String> onSuccess, Consumer<Exception> onError) {
        String prefix = "business_id_" + year + "_";
        FirestorePaths.businesses(db)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.accept(generateNextBusinessId(prefix, snapshot)))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void createBusiness(String businessId, Map<String, Object> businessData, Runnable onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .set(businessData)
                .addOnSuccessListener(ignored -> {
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(exception -> {
                    if (onError != null) {
                        onError.accept(exception);
                    }
                });
    }

    @Override
    public void initializeBusinessCollections(String businessId, Runnable onSuccess, Consumer<Exception> onError) {
        List<String> subCollections = new ArrayList<>();
        subCollections.add(FirestorePaths.SUB_COMMENTS);
        subCollections.add(FirestorePaths.SUB_CUSTOMERS);
        subCollections.add(FirestorePaths.SUB_LEAVES);
        subCollections.add(FirestorePaths.SUB_SHIFTS);
        subCollections.add(FirestorePaths.SUB_PAYMENTS);
        subCollections.add(FirestorePaths.SUB_CARDS);

        WriteBatch batch = db.batch();
        Map<String, Object> placeholder = new HashMap<>();
        placeholder.put("initialized", true);

        for (String subCollection : subCollections) {
            batch.set(
                    FirestorePaths.business(db, businessId)
                            .collection(subCollection)
                            .document("_init"),
                    placeholder
            );
        }

        batch.commit()
                .addOnSuccessListener(ignored -> {
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(exception -> {
                    if (onError != null) {
                        onError.accept(exception);
                    }
                });
    }

    private BusinessConfig parseBusinessConfig(DocumentSnapshot doc) {
        int openHour = readHourField(doc.get("business_open_time"), 0);
        int closeHour = readHourField(doc.get("business_close_time"), 24);
        int maxSeats = readInt(doc, "business_max_seats", 10);
        return new BusinessConfig(openHour, closeHour, maxSeats);
    }

    private String generateNextBusinessId(String prefix, QuerySnapshot snapshot) {
        int maxId = 0;
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            String docId = doc.getId();
            if (docId == null || !docId.startsWith(prefix)) {
                continue;
            }
            try {
                int suffix = Integer.parseInt(docId.substring(prefix.length()));
                if (suffix > maxId) {
                    maxId = suffix;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return prefix + (maxId + 1);
    }

    private int readInt(DocumentSnapshot doc, String fieldName, int defaultValue) {
        Object raw = doc.get(fieldName);
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw instanceof String) {
            try {
                return Integer.parseInt(((String) raw).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private int readHourField(Object raw, int defaultValue) {
        if (raw instanceof Timestamp) {
            return extractHour(((Timestamp) raw).toDate(), defaultValue);
        }
        if (raw instanceof Date) {
            return extractHour((Date) raw, defaultValue);
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw instanceof String) {
            String text = ((String) raw).trim();
            if (text.isEmpty()) {
                return defaultValue;
            }
            if (text.contains(":")) {
                String[] parts = text.split(":");
                if (parts.length > 0) {
                    try {
                        return Integer.parseInt(parts[0].trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private int extractHour(Date date, int defaultValue) {
        if (date == null) {
            return defaultValue;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }
}
