package org.vision.core.utils;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.vision.core.actuator.AbstractActuator;

@Slf4j(topic = "TransactionRegister")
public class TransactionRegister {

  public static void registerActuator() {
    Reflections reflections = new Reflections("org.vision");
    Set<Class<? extends AbstractActuator>> subTypes = reflections
        .getSubTypesOf(AbstractActuator.class);
    for (Class _class : subTypes) {
      try {
        _class.newInstance();
      } catch (Exception e) {
        logger.error("{} contract actuator register fail!", _class, e);
      }
    }
  }

}
