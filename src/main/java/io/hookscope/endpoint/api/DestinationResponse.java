package io.hookscope.endpoint.api;

import io.hookscope.endpoint.WebhookDestination;
import java.time.Instant;
import java.util.UUID;

public record DestinationResponse(UUID id, UUID endpointId, String url, Instant createdAt) {
  public static DestinationResponse from(WebhookDestination d) {
    return new DestinationResponse(d.getId(), d.getEndpointId(), d.getUrl(), d.getCreatedAt());
  }
}
