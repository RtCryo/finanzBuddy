package com.finanzbuddy.parser.processing.adapter.out.status;

public class GatewayStatusException extends RuntimeException {

    public GatewayStatusException(String message) {
        super(message);
    }

    public GatewayStatusException(String message, Throwable cause) {
        super(message, cause);
    }
}
