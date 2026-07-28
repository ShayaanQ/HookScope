package io.hookscope.endpoint;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class EndpointPersistenceTest {

  @Test
  void translatesOnlyTheNamedPublicKeyUniqueConstraint() {
    WebhookEndpointRepository repository = mock(WebhookEndpointRepository.class);
    DataIntegrityViolationException violation =
        integrityViolation(PublicKeyConstraintViolationDetector.PUBLIC_KEY_UNIQUE_CONSTRAINT);
    when(repository.saveAndFlush(any(WebhookEndpoint.class))).thenThrow(violation);
    EndpointPersistence persistence =
        new EndpointPersistence(repository, new PublicKeyConstraintViolationDetector());

    assertThatThrownBy(() -> persistence.save(endpoint()))
        .isInstanceOf(EndpointPublicKeyCollisionException.class)
        .hasCause(violation);
  }

  @Test
  void preservesOtherIntegrityViolationsWithoutRetryTranslation() {
    WebhookEndpointRepository repository = mock(WebhookEndpointRepository.class);
    DataIntegrityViolationException violation = integrityViolation("webhook_endpoints_name_check");
    when(repository.saveAndFlush(any(WebhookEndpoint.class))).thenThrow(violation);
    EndpointPersistence persistence =
        new EndpointPersistence(repository, new PublicKeyConstraintViolationDetector());

    assertThatThrownBy(() -> persistence.save(endpoint())).isSameAs(violation);
  }

  private DataIntegrityViolationException integrityViolation(String constraintName) {
    return new DataIntegrityViolationException(
        "constraint violation",
        new ConstraintViolationException(
            "constraint violation", new SQLException("duplicate", "23505"), constraintName));
  }

  private WebhookEndpoint endpoint() {
    return new WebhookEndpoint(UUID.randomUUID(), "Endpoint", "A".repeat(32), Instant.now());
  }
}
