package io.hookscope.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "hookscope.delivery",
    name = "retry-worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DeliveryRetrySchedulingConfiguration {}
