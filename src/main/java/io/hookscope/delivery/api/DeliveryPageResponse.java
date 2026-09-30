package io.hookscope.delivery.api;

import io.hookscope.delivery.WebhookDelivery;
import java.util.List;
import org.springframework.data.domain.Page;

public record DeliveryPageResponse(
    int page, int size, long totalElements, int totalPages, List<DeliveryResponse> content) {
  static DeliveryPageResponse from(Page<WebhookDelivery> p) {
    return new DeliveryPageResponse(
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        p.getContent().stream().map(DeliveryResponse::summary).toList());
  }
}
