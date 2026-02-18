package com.sentri.access_control.models;

public class ShiftItem {
    private final String dateAdmin;
    private final String timeSlot;
    private final String subStartDate;
    private final String subEndDate;
    private final String seatInfo;

    public ShiftItem(String dateAdmin,
                     String timeSlot,
                     String subStartDate,
                     String subEndDate,
                     String seatInfo) {
        this.dateAdmin = dateAdmin;
        this.timeSlot = timeSlot;
        this.subStartDate = subStartDate;
        this.subEndDate = subEndDate;
        this.seatInfo = seatInfo;
    }

    public String getDateAdmin() {
        return dateAdmin;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public String getSubStartDate() {
        return subStartDate;
    }

    public String getSubEndDate() {
        return subEndDate;
    }

    public String getSeatInfo() {
        return seatInfo;
    }
}
