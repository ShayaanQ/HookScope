package io.hookscope.delivery;

import io.hookscope.event.WebhookEvent;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import javax.net.ssl.SSLException;
import org.springframework.stereotype.Component;

@Component
public class OutboundDeliveryClient {
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(2))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public Result send(WebhookEvent event, String url, WebhookDelivery delivery) {
    HttpRequest.Builder b =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(5))
            .method(event.getMethod(), HttpRequest.BodyPublishers.ofByteArray(event.getBody()));
    if (event.getContentType() != null) {
      b.header("Content-Type", event.getContentType());
    }
    b.header("X-HookScope-Event-Id", event.getId().toString())
        .header("X-HookScope-Delivery-Id", delivery.getId().toString())
        .header("X-HookScope-Delivery-Kind", delivery.getKind());
    try {
      HttpResponse<Void> r = client.send(b.build(), HttpResponse.BodyHandlers.discarding());
      return Result.http(r.statusCode());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return Result.failure("INTERRUPTED");
    } catch (HttpTimeoutException ex) {
      return Result.failure("TIMEOUT");
    } catch (ConnectException ex) {
      return Result.failure("CONNECT_ERROR");
    } catch (SSLException ex) {
      return Result.failure("TLS_ERROR");
    } catch (java.io.IOException ex) {
      return Result.failure("IO_ERROR");
    } catch (RuntimeException ex) {
      return Result.failure("UNEXPECTED_ERROR");
    }
  }

  public record Result(Integer status, String errorCode) {
    public static Result http(int status) {
      return new Result(status, null);
    }

    public static Result failure(String code) {
      return new Result(null, code);
    }

    public boolean succeeded() {
      return status != null && status >= 200 && status <= 299;
    }
  }
}
