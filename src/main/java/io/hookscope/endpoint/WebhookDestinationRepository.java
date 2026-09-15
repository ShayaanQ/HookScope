package io.hookscope.endpoint;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookDestinationRepository extends JpaRepository<WebhookDestination, UUID> {
  Page<WebhookDestination> findByEndpointId(UUID endpointId, Pageable pageable);

  java.util.Optional<WebhookDestination> findByIdAndEndpointId(UUID id, UUID endpointId);

  boolean existsByEndpointIdAndUrl(UUID endpointId, String url);

  @Modifying
  @Query(
      value =
          "INSERT INTO webhook_destinations(id, endpoint_id, url, created_at) VALUES (:id, :endpointId, :url, :createdAt) ON CONFLICT ON CONSTRAINT webhook_destinations_endpoint_url_key DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("endpointId") UUID endpointId,
      @Param("url") String url,
      @Param("createdAt") Instant createdAt);
}
