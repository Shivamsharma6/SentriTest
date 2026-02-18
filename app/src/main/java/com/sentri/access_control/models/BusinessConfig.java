package com.sentri.access_control.models;

public class BusinessConfig {
    private final int openHour;
    private final int closeHour;
    private final int maxSeats;

    public BusinessConfig(int openHour, int closeHour, int maxSeats) {
        this.openHour = openHour;
        this.closeHour = closeHour;
        this.maxSeats = maxSeats;
    }

    public int getOpenHour() {
        return openHour;
    }

    public int getCloseHour() {
        return closeHour;
    }

    public int getMaxSeats() {
        return maxSeats;
    }
}
