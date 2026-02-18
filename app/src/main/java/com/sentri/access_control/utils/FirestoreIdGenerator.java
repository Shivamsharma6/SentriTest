package com.sentri.access_control.utils;

import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Calendar;

/**
 * Generates sequential Firestore IDs in the format: PREFIX_ENTITYTYPE_YEAR_N
 */
public final class FirestoreIdGenerator {

    private FirestoreIdGenerator() {} // Prevent instantiation

    /**
     * Generates the next sequential ID based on existing documents.
     *
     * @param businessPrefix The business prefix (e.g., "SEN")
     * @param entityType     The entity type code (e.g., "PAY", "COM", "SHIFT")
     * @param idFieldName    The field name containing the ID in documents
     * @param existingDocs   QuerySnapshot of existing documents to find max ID
     * @return The next ID in format: PREFIX_ENTITYTYPE_YEAR_N
     */
    public static String generateNextId(String businessPrefix, String entityType, 
                                        String idFieldName, QuerySnapshot existingDocs) {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        String prefix = businessPrefix + "_" + entityType + "_" + year + "_";
        
        int maxNum = 0;
        if (existingDocs != null) {
            for (QueryDocumentSnapshot doc : existingDocs) {
                String id = doc.getString(idFieldName);
                if (id != null && id.startsWith(prefix)) {
                    try {
                        int num = Integer.parseInt(id.substring(prefix.length()));
                        if (num > maxNum) maxNum = num;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        
        return prefix + (maxNum + 1);
    }

    /**
     * Generates the next customer ID based on existing customers.
     * Format: PREFIX_YEAR_N (no entity type for customers)
     *
     * @param businessPrefix The business prefix
     * @param existingDocs   QuerySnapshot of existing customer documents
     * @return The next customer ID
     */
    public static String generateNextCustomerId(String businessPrefix, QuerySnapshot existingDocs) {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        String prefix = businessPrefix + "_" + year + "_";
        
        int maxNum = 0;
        if (existingDocs != null) {
            for (QueryDocumentSnapshot doc : existingDocs) {
                String id = doc.getString("customer_id");
                if (id != null && id.startsWith(prefix)) {
                    try {
                        int num = Integer.parseInt(id.substring(prefix.length()));
                        if (num > maxNum) maxNum = num;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        
        return prefix + (maxNum + 1);
    }

    /**
     * Gets the current year.
     */
    public static int getCurrentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    /**
     * Builds an ID prefix for querying.
     * Format: PREFIX_ENTITYTYPE_YEAR_
     */
    public static String buildPrefix(String businessPrefix, String entityType) {
        return businessPrefix + "_" + entityType + "_" + getCurrentYear() + "_";
    }
}
