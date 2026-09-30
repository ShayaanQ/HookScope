package io.hookscope.event.api;

import io.hookscope.event.EventService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/endpoints/{endpointId}/events")
public class EventController {
  private final EventService service;

  public EventController(EventService service) {
    this.service = service;
  }

  @GetMapping
  public EventPageResponse list(
      @PathVariable UUID endpointId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String method,
      @RequestParam(required = false) Instant receivedAfter,
      @RequestParam(required = false) Instant receivedBefore) {
    return EventPageResponse.from(
        service
            .list(endpointId, page, size, method, receivedAfter, receivedBefore)
            .map(EventListItemResponse::from));
  }

  @GetMapping("/{eventId}")
  public EventResponse get(@PathVariable UUID endpointId, @PathVariable UUID eventId) {
    return EventResponse.from(service.get(endpointId, eventId));
  }
}
