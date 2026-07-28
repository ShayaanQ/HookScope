package io.hookscope.endpoint.api;

import io.hookscope.endpoint.WebhookEndpoint;
import java.time.Instant;
import java.util.UUID;

public record EndpointResponse(
    UUID id, String name, String publicKey, String ingestionPath, Instant createdAt) {

  static EndpointResponse from(WebhookEndpoint endpoint) {
    String publicKey = endpoint.getPublicKey();
    return new EndpointResponse(
        endpoint.getId(),
        endpoint.getName(),
        publicKey,
        "/hooks/" + publicKey,
        endpoint.getCreatedAt());
  }
}
