package io.hookscope.endpoint.api;

import io.hookscope.endpoint.EndpointService;
import java.util.UUID;
import org.springframework.data.domain.Page;
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
@RequestMapping("/api/v1/endpoints")
public class EndpointController {

  private final EndpointService endpointService;

  public EndpointController(EndpointService endpointService) {
    this.endpointService = endpointService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public EndpointResponse create(@RequestBody CreateEndpointRequest request) {
    return EndpointResponse.from(endpointService.create(request.name()));
  }

  @GetMapping("/{endpointId}")
  public EndpointResponse get(@PathVariable UUID endpointId) {
    return EndpointResponse.from(endpointService.get(endpointId));
  }

  @GetMapping
  public EndpointPageResponse list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    Page<EndpointResponse> endpointPage =
        endpointService.list(page, size).map(EndpointResponse::from);
    return EndpointPageResponse.from(endpointPage);
  }
}
