package io.hookscope.delivery;

import io.hookscope.endpoint.WebhookDestination;
import io.hookscope.event.WebhookEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryPersistenceService {
  private final WebhookDeliveryRepository deliveries;
  private final WebhookDeliveryAttemptRepository attempts;

  public DeliveryPersistenceService(
      WebhookDeliveryRepository d, WebhookDeliveryAttemptRepository a) {
    deliveries = d;
    attempts = a;
  }

  @Transactional
  public WebhookDelivery pending(WebhookEvent e, WebhookDestination d) {
    return pending(e, d, "INITIAL");
  }

  @Transactional
  public WebhookDelivery pending(WebhookEvent e, WebhookDestination d, String kind) {
    return deliveries.saveAndFlush(
        new WebhookDelivery(
            UUID.randomUUID(), e.getId(), d.getId(), kind, "PENDING", Instant.now()));
  }

  @Transactional
  public void complete(
      WebhookDelivery d, OutboundDeliveryClient.Result r, Instant started, long duration) {
    complete(d, r, 1, started, duration);
  }

  @Transactional
  public void complete(
      WebhookDelivery d,
      OutboundDeliveryClient.Result r,
      int attemptNumber,
      Instant started,
      long duration) {
    Instant done = Instant.now();
    d.complete(r.succeeded() ? "SUCCEEDED" : "FAILED", done);
    deliveries.save(d);
    attempts.save(
        new WebhookDeliveryAttempt(
            UUID.randomUUID(),
            d.getId(),
            attemptNumber,
            started,
            done,
            r.succeeded() ? "SUCCEEDED" : "FAILED",
            r.status(),
            r.errorCode(),
            duration));
  }

  @Transactional(readOnly = true)
  public WebhookDelivery find(UUID id) {
    return deliveries.findById(id).orElseThrow();
  }
}
