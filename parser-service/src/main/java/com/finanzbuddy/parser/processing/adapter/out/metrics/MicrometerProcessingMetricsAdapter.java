package com.finanzbuddy.parser.processing.adapter.out.metrics;

import com.finanzbuddy.parser.processing.port.out.ProcessingMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class MicrometerProcessingMetricsAdapter implements ProcessingMetricsPort {

    private final MeterRegistry meterRegistry;
    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Counter duplicateCounter;
    private final Timer processingTimer;

    public MicrometerProcessingMetricsAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.processedCounter =
                Counter.builder("parser.documents.processed")
                        .description("Number of successfully processed documents")
                        .register(meterRegistry);
        this.failedCounter =
                Counter.builder("parser.documents.failed")
                        .description("Number of failed document processing attempts")
                        .register(meterRegistry);
        this.duplicateCounter =
                Counter.builder("parser.documents.duplicate")
                        .description("Number of duplicate document events ignored")
                        .register(meterRegistry);
        this.processingTimer =
                Timer.builder("parser.documents.processing.duration")
                        .description("Document processing duration")
                        .register(meterRegistry);
    }

    @Override
    public void recordProcessed() {
        processedCounter.increment();
    }

    @Override
    public void recordFailed() {
        failedCounter.increment();
    }

    @Override
    public void recordDuplicate() {
        duplicateCounter.increment();
    }

    @Override
    public <T> T timeProcessing(Supplier<T> processing) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return processing.get();
        } finally {
            sample.stop(processingTimer);
        }
    }
}
