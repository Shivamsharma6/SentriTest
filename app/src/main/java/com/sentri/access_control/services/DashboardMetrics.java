package com.sentri.access_control.services;

public final class DashboardMetrics {
    private final int totalActiveCustomers;
    private final int newCustomersInLast10Days;
    private final int subscriptionsEndingToday;
    private final double pendingPayments;
    private final double expectedPaymentsThisMonth;

    public DashboardMetrics(int totalActiveCustomers,
                            int newCustomersInLast10Days,
                            int subscriptionsEndingToday,
                            double pendingPayments,
                            double expectedPaymentsThisMonth) {
        this.totalActiveCustomers = totalActiveCustomers;
        this.newCustomersInLast10Days = newCustomersInLast10Days;
        this.subscriptionsEndingToday = subscriptionsEndingToday;
        this.pendingPayments = pendingPayments;
        this.expectedPaymentsThisMonth = expectedPaymentsThisMonth;
    }

    public int getTotalActiveCustomers() {
        return totalActiveCustomers;
    }

    public int getNewCustomersInLast10Days() {
        return newCustomersInLast10Days;
    }

    public int getSubscriptionsEndingToday() {
        return subscriptionsEndingToday;
    }

    public double getPendingPayments() {
        return pendingPayments;
    }

    public double getExpectedPaymentsThisMonth() {
        return expectedPaymentsThisMonth;
    }
}
