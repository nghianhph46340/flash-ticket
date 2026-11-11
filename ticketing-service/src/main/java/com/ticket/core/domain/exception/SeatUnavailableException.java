package com.ticket.core.domain.exception;

import lombok.Getter;

@Getter
public class SeatUnavailableException extends RuntimeException {
    private final String conflictingSeatId;

    public SeatUnavailableException(String conflictingSeatId) {
        super("Seat " + conflictingSeatId + " is already reserved or unavailable");
        this.conflictingSeatId = conflictingSeatId;
    }
}
