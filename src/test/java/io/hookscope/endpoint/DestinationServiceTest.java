package io.hookscope.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.hookscope.config.HookScopeProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class DestinationServiceTest {
  @Test
  void insertSuccessReturnsCreatedDestination() {
    WebhookDestinationRepository destinations = mock(WebhookDestinationRepository.class);
    WebhookEndpointRepository endpoints = mock(WebhookEndpointRepository.class);
    HookScopeProperties properties = properties();
    when(endpoints.existsById(any())).thenReturn(true);
    when(destinations.insertIfAbsent(any(), any(), any(), any())).thenReturn(1);

    WebhookDestination result =
        new DestinationService(destinations, endpoints, properties)
            .create(UUID.randomUUID(), "https://receiver.example.com/hook");

    assertThat(result.getUrl()).isEqualTo("https://receiver.example.com/hook");
  }

  @Test
  void conflictResultBecomesDuplicateException() {
    WebhookDestinationRepository destinations = mock(WebhookDestinationRepository.class);
    WebhookEndpointRepository endpoints = mock(WebhookEndpointRepository.class);
    when(endpoints.existsById(any())).thenReturn(true);
    when(destinations.insertIfAbsent(any(), any(), any(), any())).thenReturn(0);

    assertThatThrownBy(
            () ->
                new DestinationService(destinations, endpoints, properties())
                    .create(UUID.randomUUID(), "https://receiver.example.com/hook"))
        .isInstanceOf(DestinationDuplicateException.class);
  }

  @Test
  void databaseFailurePropagatesUnchanged() {
    WebhookDestinationRepository destinations = mock(WebhookDestinationRepository.class);
    WebhookEndpointRepository endpoints = mock(WebhookEndpointRepository.class);
    DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database");
    when(endpoints.existsById(any())).thenReturn(true);
    when(destinations.insertIfAbsent(any(), any(), any(), any())).thenThrow(failure);

    assertThatThrownBy(
            () ->
                new DestinationService(destinations, endpoints, properties())
                    .create(UUID.randomUUID(), "https://receiver.example.com/hook"))
        .isSameAs(failure);
  }

  private HookScopeProperties properties() {
    HookScopeProperties properties = new HookScopeProperties();
    properties.getDelivery().setAllowedHosts(java.util.List.of("receiver.example.com"));
    return properties;
  }
}
