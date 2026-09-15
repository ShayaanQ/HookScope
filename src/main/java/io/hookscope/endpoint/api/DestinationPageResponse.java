package io.hookscope.endpoint.api;

import java.util.List;
import org.springframework.data.domain.Page;

public record DestinationPageResponse(
    int page, int size, long totalElements, int totalPages, List<DestinationResponse> content) {
  static DestinationPageResponse from(Page<DestinationResponse> p) {
    return new DestinationPageResponse(
        p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), p.getContent());
  }
}
