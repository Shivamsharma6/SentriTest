package com.sentri.access_control.repositories;

import android.text.TextUtils;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.sentri.access_control.data.FirestorePaths;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FirestoreCardRepository implements CardRepository {
    private final FirebaseFirestore db;

    public FirestoreCardRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void fetchBusinessCards(String businessId,
                                   boolean onlyUnassigned,
                                   Consumer<List<DocumentSnapshot>> onSuccess,
                                   Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CARDS)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.accept(filterCards(snapshot, onlyUnassigned)))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchAllCards(Consumer<List<DocumentSnapshot>> onSuccess, Consumer<Exception> onError) {
        db.collectionGroup(FirestorePaths.SUB_CARDS)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.accept(snapshot.getDocuments()))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void assignCardToCustomer(String businessId,
                                     String customerId,
                                     String cardDocId,
                                     String cardId,
                                     Runnable onSuccess,
                                     Consumer<Exception> onError) {
        if (!validateCardAssignmentInput(businessId, customerId, cardDocId, cardId, onError)) {
            return;
        }

        WriteBatch batch = db.batch();
        DocumentReference cardRef = cardReference(businessId, cardDocId);
        DocumentReference customerRef = customerReference(businessId, customerId);

        batch.update(
                cardRef,
                "card_assigned_to", customerId,
                "card_assigned_type", "customer",
                "card_status", true,
                FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
        );

        batch.update(
                customerRef,
                "customer_current_card_id", cardId,
                FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
        );

        commitBatch(batch, onSuccess, onError);
    }

    @Override
    public void replaceCardForCustomer(String businessId,
                                       String customerId,
                                       String newCardDocId,
                                       String newCardId,
                                       Runnable onSuccess,
                                       Consumer<Exception> onError) {
        if (!validateCardAssignmentInput(businessId, customerId, newCardDocId, newCardId, onError)) {
            return;
        }

        DocumentReference customerRef = customerReference(businessId, customerId);
        customerRef.get()
                .addOnSuccessListener(customerDoc -> {
                    String oldCardId = customerDoc.getString("customer_current_card_id");
                    findCardDocumentByCardId(businessId, oldCardId, oldCardDoc -> {
                        WriteBatch batch = db.batch();

                        DocumentReference newCardRef = cardReference(businessId, newCardDocId);
                        batch.update(
                                newCardRef,
                                "card_assigned_to", customerId,
                                "card_assigned_type", "customer",
                                "card_status", true,
                                FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
                        );

                        batch.update(
                                customerRef,
                                "customer_current_card_id", newCardId,
                                FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
                        );

                        if (oldCardDoc != null && !TextUtils.equals(oldCardDoc.getId(), newCardDocId)) {
                            batch.update(
                                    oldCardDoc.getReference(),
                                    "card_assigned_to", "",
                                    "card_assigned_type", "",
                                    "card_status", false,
                                    FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
                            );
                        }

                        commitBatch(batch, onSuccess, onError);
                    }, onError);
                })
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void returnCardFromCustomer(String businessId,
                                       String customerId,
                                       Runnable onSuccess,
                                       Consumer<Exception> onError) {
        if (TextUtils.isEmpty(businessId) || TextUtils.isEmpty(customerId)) {
            if (onError != null) {
                onError.accept(new IllegalArgumentException("Missing business/customer details"));
            }
            return;
        }

        DocumentReference customerRef = customerReference(businessId, customerId);
        customerRef.get()
                .addOnSuccessListener(customerDoc -> {
                    String currentCardId = customerDoc.getString("customer_current_card_id");
                    if (TextUtils.isEmpty(currentCardId)) {
                        if (onError != null) {
                            onError.accept(new IllegalStateException("No card assigned to this customer"));
                        }
                        return;
                    }

                    findCardDocumentByCardId(
                            businessId,
                            currentCardId,
                            currentCardDoc -> {
                                WriteBatch batch = db.batch();
                                batch.update(
                                        customerRef,
                                        "customer_current_card_id", "",
                                        FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
                                );

                                if (currentCardDoc != null) {
                                    batch.update(
                                            currentCardDoc.getReference(),
                                            "card_assigned_to", "",
                                            "card_assigned_type", "",
                                            "card_status", false,
                                            FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
                                    );
                                }
                                commitBatch(batch, onSuccess, onError);
                            },
                            onError
                    );
                })
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void unassignCard(String businessId,
                             String customerId,
                             String cardDocId,
                             Runnable onSuccess,
                             Consumer<Exception> onError) {
        if (TextUtils.isEmpty(businessId) || TextUtils.isEmpty(cardDocId)) {
            if (onError != null) {
                onError.accept(new IllegalArgumentException("Missing business/card details"));
            }
            return;
        }

        WriteBatch batch = db.batch();
        DocumentReference cardRef = cardReference(businessId, cardDocId);
        batch.update(
                cardRef,
                "card_assigned_to", "",
                "card_assigned_type", "",
                "card_status", false,
                FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
        );

        if (!TextUtils.isEmpty(customerId)) {
            DocumentReference customerRef = customerReference(businessId, customerId);
            batch.update(
                    customerRef,
                    "customer_current_card_id", "",
                    FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
            );
        }

        commitBatch(batch, onSuccess, onError);
    }

    private boolean validateCardAssignmentInput(String businessId,
                                                String customerId,
                                                String cardDocId,
                                                String cardId,
                                                Consumer<Exception> onError) {
        if (!TextUtils.isEmpty(businessId)
                && !TextUtils.isEmpty(customerId)
                && !TextUtils.isEmpty(cardDocId)
                && !TextUtils.isEmpty(cardId)) {
            return true;
        }
        if (onError != null) {
            onError.accept(new IllegalArgumentException("Missing business/customer/card details"));
        }
        return false;
    }

    private void findCardDocumentByCardId(String businessId,
                                          String cardId,
                                          Consumer<DocumentSnapshot> onSuccess,
                                          Consumer<Exception> onError) {
        if (TextUtils.isEmpty(cardId)) {
            onSuccess.accept(null);
            return;
        }

        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CARDS)
                .whereEqualTo("card_id", cardId)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.accept(snapshot.isEmpty() ? null : snapshot.getDocuments().get(0)))
                .addOnFailureListener(onError::accept);
    }

    private DocumentReference customerReference(String businessId, String customerId) {
        return FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .document(customerId);
    }

    private DocumentReference cardReference(String businessId, String cardDocId) {
        return FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CARDS)
                .document(cardDocId);
    }

    private void commitBatch(WriteBatch batch, Runnable onSuccess, Consumer<Exception> onError) {
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

    private List<DocumentSnapshot> filterCards(QuerySnapshot snapshot, boolean onlyUnassigned) {
        if (!onlyUnassigned) {
            return snapshot.getDocuments();
        }

        List<DocumentSnapshot> filtered = new ArrayList<>();
        for (DocumentSnapshot cardDoc : snapshot.getDocuments()) {
            Object assignedTo = cardDoc.get("card_assigned_to");
            boolean isUnassigned = assignedTo == null
                    || (assignedTo instanceof String && ((String) assignedTo).trim().isEmpty());
            if (isUnassigned) {
                filtered.add(cardDoc);
            }
        }
        return filtered;
    }
}
