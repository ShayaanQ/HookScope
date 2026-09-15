package io.hookscope.endpoint;

import io.hookscope.config.HookScopeProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DestinationService {
  private static final String INVALID = "The destination URL is invalid.";
  private static final Pattern AUTHORITY_PORT =
      Pattern.compile("^(?:[^@]*@)?(?:\\[[^]]+\\]|[^:]*)(?::([^:]*))?$");
  private final WebhookDestinationRepository destinations;
  private final WebhookEndpointRepository endpoints;
  private final HookScopeProperties properties;
  private final Clock clock = Clock.systemUTC();

  public DestinationService(
      WebhookDestinationRepository destinations,
      WebhookEndpointRepository endpoints,
      HookScopeProperties properties) {
    this.destinations = destinations;
    this.endpoints = endpoints;
    this.properties = properties;
  }

  @Transactional
  public WebhookDestination create(UUID endpointId, String url) {
    endpoint(endpointId);
    validateUrl(url);
    URI parsed = URI.create(url);
    if (!properties.getDelivery().getAllowedHosts().stream()
        .anyMatch(h -> hostMatches(h, parsed.getHost()))) {
      throw new DestinationValidationException("The destination host is not allowed.");
    }
    UUID id = UUID.randomUUID();
    Instant createdAt = Instant.now(clock);
    if (destinations.insertIfAbsent(id, endpointId, url, createdAt) == 0) {
      throw new DestinationDuplicateException();
    }
    return new WebhookDestination(id, endpointId, url, createdAt);
  }

  private boolean hostMatches(String allowed, String parsedHost) {
    if (allowed.equalsIgnoreCase(parsedHost)) {
      return true;
    }
    return stripIpv6Brackets(allowed).equalsIgnoreCase(stripIpv6Brackets(parsedHost));
  }

  private String stripIpv6Brackets(String host) {
    return host != null && host.startsWith("[") && host.endsWith("]")
        ? host.substring(1, host.length() - 1)
        : host;
  }

  @Transactional(readOnly = true)
  public WebhookDestination get(UUID endpointId, UUID id) {
    endpoint(endpointId);
    return destinations
        .findByIdAndEndpointId(id, endpointId)
        .orElseThrow(EndpointNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public Page<WebhookDestination> list(UUID endpointId, int page, int size) {
    endpoint(endpointId);
    if (page < 0) {
      throw new DestinationValidationException("The page must be zero or greater.");
    }
    if (size < 1) {
      throw new DestinationValidationException("The size must be at least 1.");
    }
    if (size > 100) {
      throw new DestinationValidationException("The size must not exceed 100.");
    }
    Pageable p =
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    return destinations.findByEndpointId(endpointId, p);
  }

  private void endpoint(UUID id) {
    if (!endpoints.existsById(id)) {
      throw new EndpointNotFoundException();
    }
  }

  private void validateUrl(String value) {
    if (value == null) {
      throw new DestinationValidationException(INVALID);
    }
    try {
      URI u = URI.create(value);
      if (!u.isAbsolute()
          || u.getHost() == null
          || u.getUserInfo() != null
          || u.getFragment() != null
          || !(u.getScheme().equalsIgnoreCase("http") || u.getScheme().equalsIgnoreCase("https"))
          || !validPort(u)) {
        throw new DestinationValidationException(INVALID);
      }
    } catch (IllegalArgumentException e) {
      throw new DestinationValidationException(INVALID);
    }
  }

  private boolean validPort(URI uri) {
    Matcher matcher =
        AUTHORITY_PORT.matcher(uri.getRawAuthority() == null ? "" : uri.getRawAuthority());
    if (!matcher.matches() || matcher.group(1) == null) {
      return matcher.matches();
    }
    try {
      int port = Integer.parseInt(matcher.group(1));
      return port >= 0 && port <= 65535;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
