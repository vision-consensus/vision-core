package org.vision.common.runtime;

import org.vision.core.db.TransactionContext;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;


public interface Runtime {

  void execute(TransactionContext context)
      throws ContractValidateException, ContractExeException;

  ProgramResult getResult();

  String getRuntimeError();

}
