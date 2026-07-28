package io.hookscope.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/** Factory and servlet writer for the locked application Problem Details format. */
public final class ProblemDetails {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private ProblemDetails() {}

  public static ProblemDetail create(
      HttpStatus status, String code, String title, String detail, HttpServletRequest request) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setType(
        URI.create("urn:hookscope:error:" + code.toLowerCase().replace('_', '-')));
    problemDetail.setTitle(title);
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    problemDetail.setProperty("code", code);
    return problemDetail;
  }

  public static void write(
      HttpServletResponse response,
      HttpServletRequest request,
      HttpStatus status,
      String code,
      String title,
      String detail)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    ProblemDetail problemDetail = create(status, code, title, detail, request);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", problemDetail.getType());
    body.put("title", problemDetail.getTitle());
    body.put("status", problemDetail.getStatus());
    body.put("detail", problemDetail.getDetail());
    body.put("instance", problemDetail.getInstance());
    body.put("code", code);
    OBJECT_MAPPER.writeValue(response.getOutputStream(), body);
  }
}
