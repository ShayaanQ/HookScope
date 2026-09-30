package io.hookscope.delivery.api;

import io.hookscope.delivery.WebhookDeliveryAttemptRepository;
import io.hookscope.delivery.WebhookDeliveryRepository;
import io.hookscope.event.EventNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DeliveryController {
  private final WebhookDeliveryRepository deliveries;
  private final WebhookDeliveryAttemptRepository attempts;
  private final io.hookscope.delivery.ReplayService replay;

  public DeliveryController(
      WebhookDeliveryRepository d,
      WebhookDeliveryAttemptRepository a,
      io.hookscope.delivery.ReplayService r) {
    deliveries = d;
    attempts = a;
    replay = r;
  }

  public record ReplayRequest(UUID destinationId) {}

  @org.springframework.web.bind.annotation.PostMapping("/events/{eventId}/replay")
  @org.springframework.web.bind.annotation.ResponseStatus(
      org.springframework.http.HttpStatus.CREATED)
  public DeliveryResponse replay(
      @PathVariable UUID eventId,
      @org.springframework.web.bind.annotation.RequestBody ReplayRequest request) {
    return DeliveryResponse.detail(
        replay.replay(eventId, request.destinationId()), java.util.List.of());
  }

  @GetMapping("/events/{eventId}/deliveries")
  public DeliveryPageResponse list(
      @PathVariable UUID eventId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new io.hookscope.endpoint.EndpointValidationException("Invalid pagination.");
    }
    return DeliveryPageResponse.from(
        deliveries.findByEventId(
            eventId,
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))));
  }

  @GetMapping("/deliveries/{id}")
  public DeliveryResponse get(@PathVariable UUID id) {
    var d = deliveries.findById(id).orElseThrow(EventNotFoundException::new);
    return DeliveryResponse.detail(d, attempts.findByDeliveryIdOrderByAttemptNumberAsc(id));
  }
}
