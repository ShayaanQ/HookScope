package io.hookscope.delivery;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryAttemptRepository
    extends JpaRepository<WebhookDeliveryAttempt, UUID> {
  List<WebhookDeliveryAttempt> findByDeliveryIdOrderByAttemptNumberAsc(UUID deliveryId);
}
