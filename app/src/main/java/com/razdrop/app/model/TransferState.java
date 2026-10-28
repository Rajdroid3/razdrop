package com.razdrop.app.model;

public enum TransferState {
    IDLE("Idle"),
    CONNECTING("Connecting"),
    NEGOTIATING("Negotiating"),
    WAITING_ACCEPT("Waiting for acceptance"),
    TRANSFERRING("Transferring"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    FAILED("Failed");

    private final String description;

    TransferState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
