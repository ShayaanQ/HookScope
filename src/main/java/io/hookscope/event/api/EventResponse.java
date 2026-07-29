package io.hookscope.event.api;

import io.hookscope.event.WebhookEvent;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EventResponse(
    UUID id,
    String method,
    String path,
    String contentType,
    long bodySize,
    String sourceIp,
    Instant receivedAt,
    Map<String, List<String>> headers,
    Map<String, List<String>> queryParameters,
    String bodyBase64,
    String bodySha256) {
  static EventResponse from(WebhookEvent event) {
    return new EventResponse(
        event.getId(),
        event.getMethod(),
        event.getPath(),
        event.getContentType(),
        event.getBodySize(),
        event.getSourceIp(),
        event.getReceivedAt(),
        event.getHeaders(),
        event.getQueryParameters(),
        Base64.getEncoder().encodeToString(event.getBody()),
        event.getBodySha256());
  }
}
