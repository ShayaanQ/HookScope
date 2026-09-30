package io.hookscope.delivery.api;

import io.hookscope.delivery.WebhookDelivery;
import io.hookscope.delivery.WebhookDeliveryAttempt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeliveryResponse(
    UUID id,
    UUID eventId,
    UUID destinationId,
    String kind,
    String status,
    Instant createdAt,
    Instant completedAt,
    int attemptCount,
    List<AttemptResponse> attempts) {
  static DeliveryResponse summary(WebhookDelivery d) {
    return new DeliveryResponse(
        d.getId(),
        d.getEventId(),
        d.getDestinationId(),
        d.getKind(),
        d.getStatus(),
        d.getCreatedAt(),
        d.getCompletedAt(),
        0,
        List.of());
  }

  static DeliveryResponse detail(WebhookDelivery d, List<WebhookDeliveryAttempt> a) {
    return new DeliveryResponse(
        d.getId(),
        d.getEventId(),
        d.getDestinationId(),
        d.getKind(),
        d.getStatus(),
        d.getCreatedAt(),
        d.getCompletedAt(),
        a.size(),
        a.stream().map(AttemptResponse::from).toList());
  }

  public record AttemptResponse(
      int attemptNumber,
      String outcome,
      Integer httpStatus,
      String errorCode,
      Instant startedAt,
      Instant completedAt,
      long durationMs) {
    static AttemptResponse from(WebhookDeliveryAttempt a) {
      return new AttemptResponse(
          a.getAttemptNumber(),
          a.getOutcome(),
          a.getHttpStatus(),
          a.getErrorCode(),
          a.getStartedAt(),
          a.getCompletedAt(),
          a.getDurationMs());
    }
  }
}
