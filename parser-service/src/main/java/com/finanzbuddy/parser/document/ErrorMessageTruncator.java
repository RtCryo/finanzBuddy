package com.finanzbuddy.parser.document;

public final class ErrorMessageTruncator {

    private ErrorMessageTruncator() {}

    public static String truncate(Throwable error, int maxCodePoints) {
        String message =
                error.getClass().getSimpleName()
                        + ": "
                        + (error.getMessage() == null
                                ? "No details available"
                                : error.getMessage());
        int codePointCount = message.codePointCount(0, message.length());
        if (codePointCount <= maxCodePoints) {
            return message;
        }
        return message.substring(0, message.offsetByCodePoints(0, maxCodePoints));
    }
}
