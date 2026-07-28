package io.hookscope.endpoint;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndpointService {

  static final int MAX_KEY_GENERATION_ATTEMPTS = 3;
  static final String INVALID_ENDPOINT_NAME_DETAIL =
      "The endpoint name must contain between 1 and 120 characters after trimming.";
  static final String NEGATIVE_PAGE_DETAIL = "The page must be zero or greater.";
  static final String SIZE_BELOW_MINIMUM_DETAIL = "The size must be at least 1.";
  static final String SIZE_ABOVE_MAXIMUM_DETAIL = "The size must not exceed 100.";

  private final WebhookEndpointRepository repository;
  private final EndpointPersistence endpointPersistence;
  private final PublicKeyGenerator publicKeyGenerator;
  private final Clock clock;

  @Autowired
  public EndpointService(
      WebhookEndpointRepository repository,
      EndpointPersistence endpointPersistence,
      PublicKeyGenerator publicKeyGenerator) {
    this(repository, endpointPersistence, publicKeyGenerator, Clock.systemUTC());
  }

  EndpointService(
      WebhookEndpointRepository repository,
      EndpointPersistence endpointPersistence,
      PublicKeyGenerator publicKeyGenerator,
      Clock clock) {
    this.repository = repository;
    this.endpointPersistence = endpointPersistence;
    this.publicKeyGenerator = publicKeyGenerator;
    this.clock = clock;
  }

  public WebhookEndpoint create(String requestedName) {
    String name = validateAndTrimName(requestedName);
    for (int attempt = 0; attempt < MAX_KEY_GENERATION_ATTEMPTS; attempt++) {
      WebhookEndpoint endpoint =
          new WebhookEndpoint(
              UUID.randomUUID(), name, publicKeyGenerator.generate(), Instant.now(clock));
      try {
        return endpointPersistence.save(endpoint);
      } catch (EndpointPublicKeyCollisionException exception) {
        // The unique public_key constraint is the final collision arbiter; retry only a bounded
        // number.
      }
    }
    throw new EndpointKeyGenerationException();
  }

  @Transactional(readOnly = true)
  public WebhookEndpoint get(UUID endpointId) {
    return repository.findById(endpointId).orElseThrow(EndpointNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public Page<WebhookEndpoint> list(int page, int size) {
    if (page < 0) {
      throw new EndpointValidationException(NEGATIVE_PAGE_DETAIL);
    }
    if (size < 1) {
      throw new EndpointValidationException(SIZE_BELOW_MINIMUM_DETAIL);
    }
    if (size > 100) {
      throw new EndpointValidationException(SIZE_ABOVE_MAXIMUM_DETAIL);
    }
    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    return repository.findAll(pageable);
  }

  private String validateAndTrimName(String requestedName) {
    if (requestedName == null) {
      throw new EndpointValidationException(INVALID_ENDPOINT_NAME_DETAIL);
    }
    String name = requestedName.strip();
    if (name.isBlank() || name.length() > 120) {
      throw new EndpointValidationException(INVALID_ENDPOINT_NAME_DETAIL);
    }
    return name;
  }
}
