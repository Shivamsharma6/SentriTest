package com.sentri.access_control.data;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Centralized Firestore collection/document paths and common field names.
 */
public final class FirestorePaths {
    public static final String COLLECTION_BUSINESSES = "businessess";
    public static final String COLLECTION_USERS = "users";

    public static final String SUB_CUSTOMERS = "customer";
    public static final String SUB_PAYMENTS = "payments";
    public static final String SUB_COMMENTS = "comments";
    public static final String SUB_SHIFTS = "customer_shifts";
    public static final String SUB_LEAVES = "customer_leaves";
    public static final String SUB_CARDS = "cards";
    public static final String SUB_BUSINESS_USERS = "business_users";
    public static final String SUB_BUSINESS_DEVICES = "business_devices";

    public static final String FIELD_BUSINESS_PREFIX = "business_prefix";
    public static final String FIELD_CREATED_AT = "created_at";
    public static final String FIELD_UPDATED_AT = "updated_at";

    private FirestorePaths() {
    }

    public static CollectionReference businesses(FirebaseFirestore db) {
        return db.collection(COLLECTION_BUSINESSES);
    }

    public static DocumentReference business(FirebaseFirestore db, String businessId) {
        return businesses(db).document(businessId);
    }

    public static CollectionReference users(FirebaseFirestore db) {
        return db.collection(COLLECTION_USERS);
    }

    public static DocumentReference user(FirebaseFirestore db, String email) {
        return users(db).document(email);
    }
}
