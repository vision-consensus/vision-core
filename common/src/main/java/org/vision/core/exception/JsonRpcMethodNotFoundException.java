package org.vision.core.exception;

public class JsonRpcMethodNotFoundException extends VisionException {

  public JsonRpcMethodNotFoundException() {
    super();
  }

  public JsonRpcMethodNotFoundException(String msg) {
    super(msg);
  }

  public JsonRpcMethodNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}