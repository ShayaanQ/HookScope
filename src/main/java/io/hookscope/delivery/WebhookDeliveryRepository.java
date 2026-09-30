package io.hookscope.delivery;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
  Page<WebhookDelivery> findByEventId(UUID eventId, Pageable pageable);
}
