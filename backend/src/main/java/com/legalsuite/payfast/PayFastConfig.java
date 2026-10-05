package com.legalsuite.payfast;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PayFastConfig {
    @Bean
    public Clock productBillingClock() {
        return Clock.system(ZoneId.of("Africa/Johannesburg"));
    }
}
