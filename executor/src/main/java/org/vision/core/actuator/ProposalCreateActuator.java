package org.vision.core.actuator;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.DecodeUtil;
import org.vision.common.utils.StringUtil;
import org.vision.core.capsule.ProposalCapsule;
import org.vision.core.capsule.TransactionResultCapsule;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.utils.ProposalUtil;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.protos.contract.ProposalContract.ProposalCreateContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.vision.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;
import static org.vision.core.actuator.ActuatorConstant.NOT_EXIST_STR;
import static org.vision.core.actuator.ActuatorConstant.WITNESS_EXCEPTION_STR;
import static org.vision.core.utils.ProposalUtil.ProposalType.ALLOW_FREEZE_ACCOUNT;
import static org.vision.core.utils.ProposalUtil.ProposalType.FREEZE_ACCOUNT_LIST;

@Slf4j(topic = "actuator")
public class ProposalCreateActuator extends AbstractActuator {

  public ProposalCreateActuator() {
    super(ContractType.ProposalCreateContract, ProposalCreateContract.class);
  }

  @Override
  public boolean execute(Object result) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) result;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();

    try {
      final ProposalCreateContract proposalCreateContract = this.any
          .unpack(ProposalCreateContract.class);
      long id = chainBaseManager.getDynamicPropertiesStore().getLatestProposalNum() + 1;
      ProposalCapsule proposalCapsule =
          new ProposalCapsule(proposalCreateContract.getOwnerAddress(), id);

      proposalCapsule.setParameters(proposalCreateContract.getParametersMap());
      proposalCapsule.setStringParameters(proposalCreateContract.getStringParametersMap());

      long now = chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
      long maintenanceTimeInterval = chainBaseManager.getDynamicPropertiesStore()
          .getMaintenanceTimeInterval();
      proposalCapsule.setCreateTime(now);

      long currentMaintenanceTime =
          chainBaseManager.getDynamicPropertiesStore().getNextMaintenanceTime();
      long now3 = now + getProposalExpiredTime(proposalCreateContract);
      long round = (now3 - currentMaintenanceTime) / maintenanceTimeInterval;
      long expirationTime =
          currentMaintenanceTime + (round + 1) * maintenanceTimeInterval;
      proposalCapsule.setExpirationTime(expirationTime);

      chainBaseManager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
      chainBaseManager.getDynamicPropertiesStore().saveLatestProposalNum(id);

      ret.setStatus(fee, code.SUCESS);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.any == null) {
      throw new ContractValidateException(ActuatorConstant.CONTRACT_NOT_EXIST);
    }
    if (chainBaseManager == null) {
      throw new ContractValidateException("No dbManager!");
    }
    if (!this.any.is(ProposalCreateContract.class)) {
      throw new ContractValidateException(
          "contract type error,expected type [ProposalCreateContract],real type[" + any
              .getClass() + "]");
    }
    final ProposalCreateContract contract;
    try {
      contract = this.any.unpack(ProposalCreateContract.class);
    } catch (InvalidProtocolBufferException e) {
      throw new ContractValidateException(e.getMessage());
    }

    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    if (!chainBaseManager.getAccountStore().has(ownerAddress)) {
      throw new ContractValidateException(
          ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
    }

    if (!chainBaseManager.getWitnessStore().has(ownerAddress)) {
      throw new ContractValidateException(
          WITNESS_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
    }

    if (chainBaseManager.getDynamicPropertiesStore().getSeparateProposalStringParameters() == 1L){
      boolean existParameters = false;
      List<Long> proposalIds = new ArrayList<>();
      if (contract.getParametersMap().size() != 0) {
        existParameters = true;
        for (Map.Entry<Long, Long> entry : contract.getParametersMap().entrySet()) {
          validateValue(entry);
          proposalIds.add(entry.getKey());
        }
      }
      if (contract.getStringParametersMap().size() != 0) {
        existParameters = true;
        for (Map.Entry<Long, String> entry : contract.getStringParametersMap().entrySet()) {
          validateStringValue(entry);
          proposalIds.add(entry.getKey());
        }
      }

      if (!existParameters){
        throw new ContractValidateException("This proposal has no parameter or string parameter.");
      }

      if (checkFreezeAccountBlockNumber() && checkFreezeAccountProposal(proposalIds)) {
        for (Long id: proposalIds) {
          if (id != ALLOW_FREEZE_ACCOUNT.getCode() && id != FREEZE_ACCOUNT_LIST.getCode()) {
            throw new ContractValidateException("This proposal has wrong parameter or string parameter[67 or 69].");
          }
        }
      }

    }else {
      if (contract.getParametersMap().size() != 0) {
        for (Map.Entry<Long, Long> entry : contract.getParametersMap().entrySet()) {
          validateValue(entry);
        }
      } else if (contract.getStringParametersMap().size() != 0) {
        for (Map.Entry<Long, String> entry : contract.getStringParametersMap().entrySet()) {
          validateStringValue(entry);
        }
      } else {
        throw new ContractValidateException("This proposal has no parameter or string parameter.");
      }
    }

    return true;
  }

  private void validateValue(Map.Entry<Long, Long> entry) throws ContractValidateException {
    ProposalUtil
        .validator(chainBaseManager.getDynamicPropertiesStore(), forkController, entry.getKey(),
            entry.getValue());
  }

  private void validateStringValue(Map.Entry<Long, String> entry) throws ContractValidateException {
    ProposalUtil
            .validatorString(chainBaseManager.getDynamicPropertiesStore(), chainBaseManager.getFreezeAccountStore(), forkController, entry.getKey(),
                    entry.getValue());
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(ProposalCreateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

  public boolean checkFreezeAccountProposal(List<Long> proposalIds){
    return !proposalIds.isEmpty() && (proposalIds.contains(ALLOW_FREEZE_ACCOUNT.getCode()) || proposalIds.contains(FREEZE_ACCOUNT_LIST.getCode()));
  }

  public boolean checkFreezeAccountBlockNumber() {
    return chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber() > CommonParameter.getInstance().getProposalEffectiveBlockNumber();
  }

  public long getProposalExpiredTime(ProposalCreateContract proposalCreateContract) {
    long proposalExpiredTime = CommonParameter.getInstance().getProposalExpireTime();
    if (checkFreezeAccountBlockNumber()) {
      List<Long> proposalIds = new ArrayList<>();
      if (proposalCreateContract.getParametersMap().size() != 0) {
        for (Map.Entry<Long, Long> entry : proposalCreateContract.getParametersMap().entrySet()) {
          proposalIds.add(entry.getKey());
        }
      }
      if (proposalCreateContract.getStringParametersMap().size() != 0) {
        for (Map.Entry<Long, String> entry : proposalCreateContract.getStringParametersMap().entrySet()) {
          proposalIds.add(entry.getKey());
        }
      }
      if (checkFreezeAccountProposal(proposalIds)) {
        proposalExpiredTime = CommonParameter.getInstance().getProposalEffectiveExpireTime();
      }
    }
    return proposalExpiredTime;
  }

}
