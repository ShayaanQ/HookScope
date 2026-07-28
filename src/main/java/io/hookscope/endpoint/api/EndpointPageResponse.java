package io.hookscope.endpoint.api;

import java.util.List;
import org.springframework.data.domain.Page;

public record EndpointPageResponse(
    int page, int size, long totalElements, int totalPages, List<EndpointResponse> content) {

  static EndpointPageResponse from(Page<EndpointResponse> endpointPage) {
    return new EndpointPageResponse(
        endpointPage.getNumber(),
        endpointPage.getSize(),
        endpointPage.getTotalElements(),
        endpointPage.getTotalPages(),
        endpointPage.getContent());
  }
}
