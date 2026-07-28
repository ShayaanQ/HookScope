package io.hookscope.endpoint;

public class EndpointKeyGenerationException extends RuntimeException {

  public EndpointKeyGenerationException() {
    super("Endpoint key generation retry limit reached");
  }
}
