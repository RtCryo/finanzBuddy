package com.finanzbuddy.api.cleanup.port.out;

import java.time.Instant;

/** Supplies the current instant so the staleness window is testable without wall-clock time. */
public interface ClockPort {

    Instant now();
}
