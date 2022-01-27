package org.vision.core.exception;

public class JsonRpcInvalidParamsException extends VisionException {

  public JsonRpcInvalidParamsException() {
    super();
  }

  public JsonRpcInvalidParamsException(String msg) {
    super(msg);
  }

  public JsonRpcInvalidParamsException(String message, Throwable cause) {
    super(message, cause);
  }
}