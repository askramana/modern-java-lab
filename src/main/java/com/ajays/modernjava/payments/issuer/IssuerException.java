package com.ajays.modernjava.payments.issuer;

/// The issuer couldn't be reached or refused a request for a technical reason.
public final class IssuerException extends RuntimeException {

    public IssuerException(String message) {
        super(message);
    }
}
