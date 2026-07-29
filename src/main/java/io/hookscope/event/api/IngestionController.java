package io.hookscope.event.api;

import io.hookscope.config.HookScopeProperties;
import io.hookscope.event.BoundedBodyReader;
import io.hookscope.event.EventService;
import io.hookscope.event.MethodNotAllowedException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IngestionController {
  private final EventService service;
  private final BoundedBodyReader reader;
  private final HookScopeProperties properties;

  public IngestionController(
      EventService service, BoundedBodyReader reader, HookScopeProperties properties) {
    this.service = service;
    this.reader = reader;
    this.properties = properties;
  }

  @RequestMapping(
      value = "/hooks/{publicKey}",
      method = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.PATCH,
        RequestMethod.DELETE
      })
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void ingest(@PathVariable String publicKey, HttpServletRequest request)
      throws IOException {
    service.ingest(
        publicKey,
        request,
        reader.read(request.getInputStream(), properties.getIngestion().getMaximumBodySize()));
  }

  @RequestMapping(
      value = "/hooks/{publicKey}",
      method = {RequestMethod.HEAD, RequestMethod.OPTIONS})
  public void unsupported() {
    throw new MethodNotAllowedException();
  }
}
