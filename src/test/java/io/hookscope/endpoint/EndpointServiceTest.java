package io.hookscope.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class EndpointServiceTest {

  @Test
  void retriesAKeyCollisionAndPersistsTheNextGeneratedKey() {
    WebhookEndpointRepository repository = mock(WebhookEndpointRepository.class);
    EndpointPersistence persistence = mock(EndpointPersistence.class);
    PublicKeyGenerator generator = mock(PublicKeyGenerator.class);
    when(generator.generate()).thenReturn("A".repeat(32), "B".repeat(32));
    when(persistence.save(any(WebhookEndpoint.class)))
        .thenThrow(new EndpointPublicKeyCollisionException(new RuntimeException()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    EndpointService service = service(repository, persistence, generator);

    WebhookEndpoint endpoint = service.create("  Payments  ");

    assertThat(endpoint.getName()).isEqualTo("Payments");
    assertThat(endpoint.getPublicKey()).isEqualTo("B".repeat(32));
    verify(persistence, times(2)).save(any(WebhookEndpoint.class));
  }

  @Test
  void failsAfterTheDocumentedBoundedCollisionRetryLimit() {
    WebhookEndpointRepository repository = mock(WebhookEndpointRepository.class);
    EndpointPersistence persistence = mock(EndpointPersistence.class);
    PublicKeyGenerator generator = mock(PublicKeyGenerator.class);
    when(generator.generate()).thenReturn("A".repeat(32));
    when(persistence.save(any(WebhookEndpoint.class)))
        .thenThrow(new EndpointPublicKeyCollisionException(new RuntimeException()));
    EndpointService service = service(repository, persistence, generator);

    assertThatThrownBy(() -> service.create("Payments"))
        .isInstanceOf(EndpointKeyGenerationException.class);
    verify(persistence, times(EndpointService.MAX_KEY_GENERATION_ATTEMPTS))
        .save(any(WebhookEndpoint.class));
  }

  @Test
  void rejectsBlankAndOverlongNamesAfterTrimming() {
    EndpointService service =
        service(
            mock(WebhookEndpointRepository.class),
            mock(EndpointPersistence.class),
            mock(PublicKeyGenerator.class));

    assertThatThrownBy(() -> service.create("   ")).isInstanceOf(EndpointValidationException.class);
    assertThatThrownBy(() -> service.create("x".repeat(121)))
        .isInstanceOf(EndpointValidationException.class);
    assertThatThrownBy(() -> service.create("\u2003\u2002"))
        .isInstanceOf(EndpointValidationException.class);
  }

  @Test
  void doesNotRetryAnUnrelatedIntegrityViolation() {
    WebhookEndpointRepository repository = mock(WebhookEndpointRepository.class);
    EndpointPersistence persistence = mock(EndpointPersistence.class);
    PublicKeyGenerator generator = mock(PublicKeyGenerator.class);
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException("other failure");
    when(generator.generate()).thenReturn("A".repeat(32));
    when(persistence.save(any(WebhookEndpoint.class))).thenThrow(violation);
    EndpointService service = service(repository, persistence, generator);

    assertThatThrownBy(() -> service.create("Endpoint")).isSameAs(violation);
    verify(persistence).save(any(WebhookEndpoint.class));
  }

  private EndpointService service(
      WebhookEndpointRepository repository,
      EndpointPersistence persistence,
      PublicKeyGenerator generator) {
    return new EndpointService(
        repository,
        persistence,
        generator,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }
}
