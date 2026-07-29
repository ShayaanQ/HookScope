package io.hookscope.event.api;

import java.util.List;
import org.springframework.data.domain.Page;

public record EventPageResponse(
    int page, int size, long totalElements, int totalPages, List<EventListItemResponse> content) {
  static EventPageResponse from(Page<EventListItemResponse> page) {
    return new EventPageResponse(
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getContent());
  }
}
