package com.legalsuite.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled jobs (FICA reminders, directory POPIA purge). Before this class the jobs were
 * declared but never ran. The free Render service sleeps outside 06:00 to 18:00 SAST, so jobs are
 * timed inside that window. LEGALSUITE_SCHEDULING_ENABLED=false switches them all off.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "legalsuite.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {}
