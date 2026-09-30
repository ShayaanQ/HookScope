package io.hookscope.delivery;

import io.hookscope.endpoint.WebhookDestinationRepository;
import io.hookscope.event.EventNotFoundException;
import io.hookscope.event.WebhookEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReplayService {
  private final WebhookEventRepository events;
  private final WebhookDestinationRepository destinations;
  private final DeliveryPersistenceService persistence;
  private final OutboundDeliveryClient client;
  private final DeliveryRetryQueue retries;

  public ReplayService(
      WebhookEventRepository e,
      WebhookDestinationRepository d,
      DeliveryPersistenceService p,
      OutboundDeliveryClient c,
      DeliveryRetryQueue r) {
    events = e;
    destinations = d;
    persistence = p;
    client = c;
    retries = r;
  }

  public WebhookDelivery replay(UUID eventId, UUID destinationId) {
    var e = events.findById(eventId).orElseThrow(EventNotFoundException::new);
    var d =
        destinations
            .findById(destinationId)
            .filter(x -> x.getEndpointId().equals(e.getEndpointId()))
            .orElseThrow(EventNotFoundException::new);
    var delivery = persistence.pending(e, d, "REPLAY");
    var s = Instant.now();
    var result = client.send(e, d.getUrl(), delivery);
    persistence.complete(delivery, result, s, 0);
    if (!result.succeeded()) {
      retries.enqueue(delivery.getId(), 2);
    }
    return persistence.find(delivery.getId());
  }
}
