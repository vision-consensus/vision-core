package org.vision.core.exception;

public class TooBigTransactionResultException extends VisionException {

  public TooBigTransactionResultException() {
    super("too big transaction result");
  }

  public TooBigTransactionResultException(String message) {
    super(message);
  }
}
