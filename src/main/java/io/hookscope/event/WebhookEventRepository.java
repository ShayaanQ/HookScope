package io.hookscope.event;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
  @Query(
      value =
          "select e.id as id, e.method as method, e.path as path, e.contentType as contentType, "
              + "e.bodySize as bodySize, e.sourceIp as sourceIp, e.receivedAt as receivedAt "
              + "from WebhookEvent e where e.endpointId = :endpointId",
      countQuery = "select count(e) from WebhookEvent e where e.endpointId = :endpointId")
  Page<EventListProjection> findSummaryByEndpointId(
      @Param("endpointId") UUID endpointId, Pageable pageable);

  @Query(
      "select e.id as id, e.method as method, e.path as path, e.contentType as contentType, e.bodySize as bodySize, e.sourceIp as sourceIp, e.receivedAt as receivedAt from WebhookEvent e where e.endpointId = :endpointId and (:method = '' or e.method = :method) and e.receivedAt > :after and e.receivedAt < :before")
  Page<EventListProjection> findFiltered(
      @Param("endpointId") UUID endpointId,
      @Param("method") String method,
      @Param("after") java.time.Instant after,
      @Param("before") java.time.Instant before,
      Pageable pageable);
}
