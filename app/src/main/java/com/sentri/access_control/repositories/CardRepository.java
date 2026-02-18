package com.sentri.access_control.repositories;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.List;
import java.util.function.Consumer;

public interface CardRepository {
    void fetchBusinessCards(String businessId,
                            boolean onlyUnassigned,
                            Consumer<List<DocumentSnapshot>> onSuccess,
                            Consumer<Exception> onError);

    void fetchAllCards(Consumer<List<DocumentSnapshot>> onSuccess, Consumer<Exception> onError);

    void assignCardToCustomer(String businessId,
                              String customerId,
                              String cardDocId,
                              String cardId,
                              Runnable onSuccess,
                              Consumer<Exception> onError);

    void replaceCardForCustomer(String businessId,
                                String customerId,
                                String newCardDocId,
                                String newCardId,
                                Runnable onSuccess,
                                Consumer<Exception> onError);

    void returnCardFromCustomer(String businessId,
                                String customerId,
                                Runnable onSuccess,
                                Consumer<Exception> onError);

    void unassignCard(String businessId,
                      String customerId,
                      String cardDocId,
                      Runnable onSuccess,
                      Consumer<Exception> onError);
}
