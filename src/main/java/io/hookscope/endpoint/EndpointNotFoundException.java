package io.hookscope.endpoint;

public class EndpointNotFoundException extends RuntimeException {

  public EndpointNotFoundException() {
    super("Endpoint not found");
  }
}
