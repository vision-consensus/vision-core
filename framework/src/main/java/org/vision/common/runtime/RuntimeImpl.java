package org.vision.common.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.vision.common.parameter.CommonParameter;
import org.vision.core.actuator.Actuator;
import org.vision.core.actuator.Actuator2;
import org.vision.core.actuator.ActuatorCreator;
import org.vision.core.actuator.VMActuator;
import org.vision.core.db.TransactionContext;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.vm.program.Program;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result.contractResult;

@Slf4j(topic = "VM")
public class RuntimeImpl implements Runtime {

  TransactionContext context;
  private List<Actuator> actuatorList = null;

  @Getter
  private Actuator2 actuator2 = null;

  @Override
  public void execute(TransactionContext context)
      throws ContractValidateException, ContractExeException {
    this.context = context;

    ContractType contractType = context.getTrxCap().getInstance().getRawData().getContract(0)
        .getType();
    switch (contractType.getNumber()) {
      case ContractType.TriggerSmartContract_VALUE:
      case ContractType.CreateSmartContract_VALUE:
        Set<String> actuatorSet = CommonParameter.getInstance().getActuatorSet();
        if (!actuatorSet.isEmpty() && !actuatorSet.contains(VMActuator.class.getSimpleName())) {
          throw new ContractValidateException("not exist contract " + "SmartContract");
        }
        actuator2 = new VMActuator(context.isStatic());
        break;
      default:
        actuatorList = ActuatorCreator.getINSTANCE().createActuator(context.getTrxCap());
    }
    if (actuator2 != null) {
      actuator2.validate(context);
      actuator2.execute(context);
    } else {
      for (Actuator act : actuatorList) {
        act.validate();
        act.execute(context.getProgramResult().getRet());
      }
    }

    setResultCode(context.getProgramResult());

  }

  @Override
  public ProgramResult getResult() {
    return context.getProgramResult();
  }

  @Override
  public String getRuntimeError() {
    return context.getProgramResult().getRuntimeError();
  }


  private void setResultCode(ProgramResult result) {
    RuntimeException exception = result.getException();
    if (Objects.isNull(exception) && StringUtils
        .isEmpty(result.getRuntimeError()) && !result.isRevert()) {
      result.setResultCode(contractResult.SUCCESS);
      return;
    }
    if (result.isRevert()) {
      result.setResultCode(contractResult.REVERT);
      return;
    }
    if (exception instanceof Program.IllegalOperationException) {
      result.setResultCode(contractResult.ILLEGAL_OPERATION);
      return;
    }
    if (exception instanceof Program.OutOfEnergyException) {
      result.setResultCode(contractResult.OUT_OF_ENERGY);
      return;
    }
    if (exception instanceof Program.BadJumpDestinationException) {
      result.setResultCode(contractResult.BAD_JUMP_DESTINATION);
      return;
    }
    if (exception instanceof Program.OutOfTimeException) {
      result.setResultCode(contractResult.OUT_OF_TIME);
      return;
    }
    if (exception instanceof Program.OutOfMemoryException) {
      result.setResultCode(contractResult.OUT_OF_MEMORY);
      return;
    }
    if (exception instanceof Program.PrecompiledContractException) {
      result.setResultCode(contractResult.PRECOMPILED_CONTRACT);
      return;
    }
    if (exception instanceof Program.StackTooSmallException) {
      result.setResultCode(contractResult.STACK_TOO_SMALL);
      return;
    }
    if (exception instanceof Program.StackTooLargeException) {
      result.setResultCode(contractResult.STACK_TOO_LARGE);
      return;
    }
    if (exception instanceof Program.JVMStackOverFlowException) {
      result.setResultCode(contractResult.JVM_STACK_OVER_FLOW);
      return;
    }
    if (exception instanceof Program.TransferException) {
      result.setResultCode(contractResult.TRANSFER_FAILED);
      return;
    }
    result.setResultCode(contractResult.UNKNOWN);
  }

}

