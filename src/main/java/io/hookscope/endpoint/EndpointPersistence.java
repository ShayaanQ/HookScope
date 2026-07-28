package io.hookscope.endpoint;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Isolates each public-key insertion attempt so a database collision can safely be retried. */
@Component
public class EndpointPersistence {

  private final WebhookEndpointRepository repository;
  private final PublicKeyConstraintViolationDetector constraintViolationDetector;

  public EndpointPersistence(
      WebhookEndpointRepository repository,
      PublicKeyConstraintViolationDetector constraintViolationDetector) {
    this.repository = repository;
    this.constraintViolationDetector = constraintViolationDetector;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public WebhookEndpoint save(WebhookEndpoint endpoint) {
    try {
      return repository.saveAndFlush(endpoint);
    } catch (DataIntegrityViolationException exception) {
      if (constraintViolationDetector.isPublicKeyUniqueViolation(exception)) {
        throw new EndpointPublicKeyCollisionException(exception);
      }
      throw exception;
    }
  }
}
