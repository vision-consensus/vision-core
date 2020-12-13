package org.vision.core.capsule;

public interface ProtoCapsule<T> {

  byte[] getData();

  T getInstance();
}
