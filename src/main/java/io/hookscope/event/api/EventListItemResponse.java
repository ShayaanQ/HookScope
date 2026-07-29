package io.hookscope.event.api;

import io.hookscope.event.EventListProjection;
import java.time.Instant;
import java.util.UUID;

public record EventListItemResponse(
    UUID id,
    String method,
    String path,
    String contentType,
    long bodySize,
    String sourceIp,
    Instant receivedAt) {
  static EventListItemResponse from(EventListProjection event) {
    return new EventListItemResponse(
        event.getId(),
        event.getMethod(),
        event.getPath(),
        event.getContentType(),
        event.getBodySize(),
        event.getSourceIp(),
        event.getReceivedAt());
  }
}
