package com.finanzbuddy.parser.processing.port.out;

import java.util.function.Supplier;

/** Outbound port: processing counters and the processing-duration timer. */
public interface ProcessingMetricsPort {

    void recordProcessed();

    void recordFailed();

    void recordDuplicate();

    <T> T timeProcessing(Supplier<T> processing);
}
