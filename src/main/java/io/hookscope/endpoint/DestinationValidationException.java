package io.hookscope.endpoint;

public class DestinationValidationException extends RuntimeException {
  public DestinationValidationException(String detail) {
    super(detail);
  }

  public String getDetail() {
    return getMessage();
  }
}
