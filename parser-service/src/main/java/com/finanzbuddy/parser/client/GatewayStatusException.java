package com.finanzbuddy.parser.client;

public class GatewayStatusException extends RuntimeException {

    public GatewayStatusException(String message) {
        super(message);
    }

    public GatewayStatusException(String message, Throwable cause) {
        super(message, cause);
    }
}
