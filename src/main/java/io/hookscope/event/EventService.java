package io.hookscope.event;

import io.hookscope.config.HookScopeProperties;
import io.hookscope.endpoint.EndpointNotFoundException;
import io.hookscope.endpoint.WebhookEndpoint;
import io.hookscope.endpoint.WebhookEndpointRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
  private static final Set<String> DEFAULT_SENSITIVE_HEADERS =
      Set.of(
          "authorization",
          "proxy-authorization",
          "cookie",
          "set-cookie",
          "x-api-key",
          "api-key",
          "x-auth-token");
  private final WebhookEndpointRepository endpointRepository;
  private final WebhookEventRepository eventRepository;
  private final Set<String> sensitiveHeaders;

  public EventService(
      WebhookEndpointRepository endpointRepository,
      WebhookEventRepository eventRepository,
      HookScopeProperties properties) {
    this.endpointRepository = endpointRepository;
    this.eventRepository = eventRepository;
    this.sensitiveHeaders = new java.util.HashSet<>(DEFAULT_SENSITIVE_HEADERS);
    properties
        .getAdditionalSensitiveHeaders()
        .forEach(value -> sensitiveHeaders.add(value.toLowerCase(Locale.ROOT)));
  }

  @Transactional
  public void ingest(String publicKey, HttpServletRequest request, byte[] body) {
    WebhookEndpoint endpoint =
        endpointRepository.findByPublicKey(publicKey).orElseThrow(EndpointNotFoundException::new);
    eventRepository.save(
        new WebhookEvent(
            UUID.randomUUID(),
            endpoint.getId(),
            request.getMethod(),
            headers(request),
            queryParameters(request),
            request.getContentType(),
            body,
            body.length,
            sha256(body),
            request.getRemoteAddr(),
            request
                .getRequestURI()
                .replaceFirst("^" + java.util.regex.Pattern.quote(request.getContextPath()), ""),
            Instant.now()));
  }

  @Transactional(readOnly = true)
  public WebhookEvent get(UUID endpointId, UUID eventId) {
    endpointRepository.findById(endpointId).orElseThrow(EndpointNotFoundException::new);
    return eventRepository
        .findById(eventId)
        .filter(event -> event.getEndpointId().equals(endpointId))
        .orElseThrow(EventNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public Page<EventListProjection> list(UUID endpointId, int page, int size) {
    endpointRepository.findById(endpointId).orElseThrow(EndpointNotFoundException::new);
    validatePage(page, size);
    return eventRepository.findSummaryByEndpointId(
        endpointId,
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("receivedAt"), Sort.Order.desc("id"))));
  }

  private void validatePage(int page, int size) {
    if (page < 0) {
      throw new io.hookscope.endpoint.EndpointValidationException(
          "The page must be zero or greater.");
    }
    if (size < 1) {
      throw new io.hookscope.endpoint.EndpointValidationException("The size must be at least 1.");
    }
    if (size > 100) {
      throw new io.hookscope.endpoint.EndpointValidationException("The size must not exceed 100.");
    }
  }

  private Map<String, List<String>> headers(HttpServletRequest request) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    Enumeration<String> names = request.getHeaderNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      String normalized = name.toLowerCase(Locale.ROOT);
      if (sensitiveHeaders.contains(normalized)) {
        result.put(normalized, List.of("[REDACTED]"));
      } else {
        List<String> values = new ArrayList<>();
        Enumeration<String> headers = request.getHeaders(name);
        while (headers.hasMoreElements()) {
          values.add(headers.nextElement());
        }
        result.put(normalized, values);
      }
    }
    return result;
  }

  /**
   * Parses only the raw URL query using application/x-www-form-urlencoded decoding: '+' becomes a
   * space and percent escapes decode as UTF-8. Bare and empty-value parameters both map to "".
   */
  private Map<String, List<String>> queryParameters(HttpServletRequest request) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    String rawQuery = request.getQueryString();
    if (rawQuery == null || rawQuery.isEmpty()) {
      return result;
    }
    for (String part : rawQuery.split("&", -1)) {
      if (part.isEmpty()) {
        continue;
      }
      int separator = part.indexOf('=');
      String rawName = separator < 0 ? part : part.substring(0, separator);
      String rawValue = separator < 0 ? "" : part.substring(separator + 1);
      String name = decodeQueryComponent(rawName);
      String value = decodeQueryComponent(rawValue);
      result.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
    }
    return result;
  }

  private String decodeQueryComponent(String rawComponent) {
    try {
      String decoded = URLDecoder.decode(rawComponent, StandardCharsets.UTF_8);
      if (decoded.indexOf('\u0000') >= 0) {
        throw new MalformedWebhookRequestException();
      }
      return decoded;
    } catch (IllegalArgumentException exception) {
      throw new MalformedWebhookRequestException();
    }
  }

  private String sha256(byte[] body) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
      StringBuilder value = new StringBuilder(64);
      for (byte current : digest) {
        value.append(String.format("%02x", current));
      }
      return value.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }
}
