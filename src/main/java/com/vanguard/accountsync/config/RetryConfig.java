package com.vanguard.accountsync.config;

import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Retry behavior itself (max attempts, exponential backoff) is configured declaratively
 * under {@code resilience4j.retry.instances} in application.yml for the "eodDataFetch" and
 * "dynamoDataFetch" instances. This class wires logging into every retry attempt so
 * transient failures and exhausted retries are visible without extra instrumentation
 * at each call site.
 */
@Configuration
@Slf4j
public class RetryConfig {

    @Bean
    public RegistryEventConsumer<Retry> accountSyncRetryEventLoggingConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<Retry> entryAddedEvent) {
                entryAddedEvent.getAddedEntry().getEventPublisher()
                        .onRetry(event -> log.warn("Retry attempt #{} for '{}': {}",
                                event.getNumberOfRetryAttempts(),
                                event.getName(),
                                describe(event.getLastThrowable())))
                        .onError(event -> log.error("Retry exhausted for '{}' after {} attempts: {}",
                                event.getName(),
                                event.getNumberOfRetryAttempts(),
                                describe(event.getLastThrowable())))
                        .onSuccess(event -> {
                            if (event.getNumberOfRetryAttempts() > 0) {
                                log.info("Retry succeeded for '{}' after {} attempt(s)",
                                        event.getName(), event.getNumberOfRetryAttempts());
                            }
                        });
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<Retry> entryRemovedEvent) {
                // no-op
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<Retry> entryReplacedEvent) {
                // no-op
            }
        };
    }

    private String describe(Throwable throwable) {
        return throwable != null ? throwable.getMessage() : "unknown cause";
    }
}
