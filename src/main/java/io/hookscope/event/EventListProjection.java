package io.hookscope.event;

import java.time.Instant;
import java.util.UUID;

/** Summary-only event view used by paginated listing without hydrating event bodies or JSON. */
public interface EventListProjection {
  UUID getId();

  String getMethod();

  String getPath();

  String getContentType();

  long getBodySize();

  String getSourceIp();

  Instant getReceivedAt();
}
