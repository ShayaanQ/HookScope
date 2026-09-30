package io.hookscope.delivery;

import io.hookscope.endpoint.WebhookDestinationRepository;
import io.hookscope.event.WebhookEvent;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class InitialDeliveryOrchestrator {
  private final WebhookDestinationRepository destinations;
  private final DeliveryPersistenceService persistence;
  private final OutboundDeliveryClient client;
  private final DeliveryRetryQueue retries;

  public InitialDeliveryOrchestrator(
      WebhookDestinationRepository d,
      DeliveryPersistenceService p,
      OutboundDeliveryClient c,
      DeliveryRetryQueue r) {
    destinations = d;
    persistence = p;
    client = c;
    retries = r;
  }

  public void deliver(WebhookEvent event) {
    for (var d : destinations.findAllByEndpointId(event.getEndpointId())) {
      try {
        var delivery = persistence.pending(event, d);
        Instant started = Instant.now();
        var result = client.send(event, d.getUrl(), delivery);
        persistence.complete(
            delivery,
            result,
            started,
            java.time.Duration.between(started, Instant.now()).toMillis());
        if (!result.succeeded()) {
          retries.enqueue(delivery.getId(), 2);
        }
      } catch (RuntimeException ignored) {
        // Failure for one destination must not prevent subsequent destinations.
      }
    }
  }
}
