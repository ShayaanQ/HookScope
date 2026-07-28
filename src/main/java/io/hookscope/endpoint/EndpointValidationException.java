package io.hookscope.endpoint;

public class EndpointValidationException extends RuntimeException {

  private final String detail;

  public EndpointValidationException(String detail) {
    super(detail);
    this.detail = detail;
  }

  public String getDetail() {
    return detail;
  }
}
