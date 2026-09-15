package io.hookscope.endpoint.api;

import io.hookscope.endpoint.DestinationService;
import io.hookscope.endpoint.DestinationValidationException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/endpoints/{endpointId}/destinations")
public class DestinationController {
  private final DestinationService service;

  public DestinationController(DestinationService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DestinationResponse create(
      @PathVariable UUID endpointId, @RequestBody CreateDestinationRequest request) {
    if (request == null) {
      throw new DestinationValidationException("The destination URL is required.");
    }
    return DestinationResponse.from(service.create(endpointId, request.url()));
  }

  @GetMapping("/{destinationId}")
  public DestinationResponse get(@PathVariable UUID endpointId, @PathVariable UUID destinationId) {
    return DestinationResponse.from(service.get(endpointId, destinationId));
  }

  @GetMapping
  public DestinationPageResponse list(
      @PathVariable UUID endpointId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return DestinationPageResponse.from(
        service.list(endpointId, page, size).map(DestinationResponse::from));
  }
}
