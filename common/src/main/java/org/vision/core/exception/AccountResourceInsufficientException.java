package org.vision.core.exception;

public class AccountResourceInsufficientException extends VisionException {

  public AccountResourceInsufficientException() {
    super("Insufficient photon and balance to create new account");
  }

  public AccountResourceInsufficientException(String message) {
    super(message);
  }
}

