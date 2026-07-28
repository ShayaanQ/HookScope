package io.hookscope.endpoint;

class EndpointPublicKeyCollisionException extends RuntimeException {

  EndpointPublicKeyCollisionException(Throwable cause) {
    super("Endpoint public key collided");
    initCause(cause);
  }
}
