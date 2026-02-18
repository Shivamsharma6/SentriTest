package com.sentri.access_control.repositories;

import java.util.Map;
import java.util.function.Consumer;

public interface LeaveRepository {
    void fetchNextLeaveId(String businessId, String businessPrefix, Consumer<String> onSuccess, Consumer<Exception> onError);

    void createLeave(String businessId, String leaveId, Map<String, Object> leaveData, Runnable onSuccess, Consumer<Exception> onError);

    void applyLeaveAdjustments(String businessId, String customerId, int leaveDays, Runnable onSuccess, Consumer<Exception> onError);
}
