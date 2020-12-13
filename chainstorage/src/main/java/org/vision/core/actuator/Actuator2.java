package org.vision.core.actuator;

import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;

public interface Actuator2 {

  void execute(Object object) throws ContractExeException;

  void validate(Object object) throws ContractValidateException;
}