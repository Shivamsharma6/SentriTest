package com.sentri.access_control.services;

import com.google.firebase.firestore.DocumentSnapshot;
import com.sentri.access_control.utils.CurrencyUtils;
import com.sentri.access_control.utils.DateUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class DashboardMetricsCalculator {

    public DashboardMetrics calculate(List<DocumentSnapshot> customers) {
        Calendar nowCal = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
        Calendar startToday = resetToDayStart((Calendar) nowCal.clone());
        Calendar endToday = (Calendar) startToday.clone();
        endToday.set(Calendar.HOUR_OF_DAY, 23);
        endToday.set(Calendar.MINUTE, 59);
        endToday.set(Calendar.SECOND, 59);
        endToday.set(Calendar.MILLISECOND, 999);

        Calendar startOfMonth = (Calendar) startToday.clone();
        startOfMonth.set(Calendar.DAY_OF_MONTH, 1);

        Calendar thirtyDaysAgo = (Calendar) startToday.clone();
        thirtyDaysAgo.add(Calendar.DAY_OF_MONTH, -30);

        Calendar tenDaysAgo = (Calendar) startToday.clone();
        tenDaysAgo.add(Calendar.DAY_OF_MONTH, -10);

        int totalActiveCustomers = 0;
        int newCustomersIn10Days = 0;
        int subscriptionsEndingToday = 0;
        double pendingPayments = 0.0;
        double expectedPaymentsThisMonth = 0.0;

        for (DocumentSnapshot customer : customers) {
            totalActiveCustomers++;

            Date createdAt = DateUtils.parseFlexibleDate(customer.get("created_at"));
            if (createdAt != null && !createdAt.before(tenDaysAgo.getTime())) {
                newCustomersIn10Days++;
            }

            Date subscriptionEndDate = DateUtils.parseFlexibleDate(customer.get("customer_subscription_end_date"));
            if (isDateInRange(subscriptionEndDate, startToday.getTime(), endToday.getTime())) {
                subscriptionsEndingToday++;
            }

            Date lastPaymentDate = DateUtils.parseFlexibleDate(customer.get("customer_last_payment_date"));
            double rate = CurrencyUtils.parseAmount(customer.get("customer_current_payment_rate"));
            if (rate == 0.0) {
                rate = CurrencyUtils.parseAmount(customer.get("current_payment_rate"));
            }

            if (lastPaymentDate == null || !lastPaymentDate.after(thirtyDaysAgo.getTime())) {
                pendingPayments += rate;
            }
            if (lastPaymentDate == null || lastPaymentDate.before(startOfMonth.getTime())) {
                expectedPaymentsThisMonth += rate;
            }
        }

        return new DashboardMetrics(
                totalActiveCustomers,
                newCustomersIn10Days,
                subscriptionsEndingToday,
                pendingPayments,
                expectedPaymentsThisMonth
        );
    }

    private Calendar resetToDayStart(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private boolean isDateInRange(Date date, Date start, Date end) {
        if (date == null) {
            return false;
        }
        return !date.before(start) && !date.after(end);
    }
}
