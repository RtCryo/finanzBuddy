package com.finanzbuddy.api.cleanup.adapter.out.clock;

import com.finanzbuddy.api.cleanup.port.out.ClockPort;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SystemClockAdapter implements ClockPort {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
