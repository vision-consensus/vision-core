package org.vision.core.actuator;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.spongycastle.util.encoders.Hex;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.DecodeUtil;
import org.vision.common.utils.StringUtil;
import org.vision.common.utils.Time;
import org.vision.core.capsule.*;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.store.*;
import org.vision.protos.Protocol;
import org.vision.protos.Protocol.AccountType;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.protos.contract.BalanceContract.FreezeBalanceContract;
import org.vision.protos.contract.BalanceContract.FreezeBalanceStage;
import org.vision.protos.contract.Common;

import java.util.*;
import java.util.stream.Collectors;

import static org.vision.core.actuator.ActuatorConstant.NOT_EXIST_STR;
import static org.vision.core.config.Parameter.ChainConstant.*;

@Slf4j(topic = "actuator")
public class FreezeBalanceActuator extends AbstractActuator {

  public FreezeBalanceActuator() {
    super(ContractType.FreezeBalanceContract, FreezeBalanceContract.class);
  }

  @Override
  public boolean execute(Object result) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) result;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();
    final FreezeBalanceContract freezeBalanceContract;
    AccountStore accountStore = chainBaseManager.getAccountStore();
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    AccountFrozenStageResourceStore accountFrozenStageResourceStore = chainBaseManager.getAccountFrozenStageResourceStore();
    try {
      freezeBalanceContract = any.unpack(FreezeBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }

    byte[] ownerAddress = freezeBalanceContract.getOwnerAddress().toByteArray();

    AccountCapsule accountCapsule = accountStore
            .get(freezeBalanceContract.getOwnerAddress().toByteArray());

    long now = dynamicStore.getLatestBlockHeaderTimestamp();
    long duration = freezeBalanceContract.getFrozenDuration() * FROZEN_PERIOD;
    long newBalance = accountCapsule.getBalance() - freezeBalanceContract.getFrozenBalance();

    long frozenBalance = freezeBalanceContract.getFrozenBalance();
    List<FreezeBalanceStage> stages = freezeBalanceContract.getFreezeBalanceStageList();
    switch (freezeBalanceContract.getResource()) {
      case PHOTON:
      case ENTROPY:
        stages = stages.stream().sorted(Comparator.comparingLong(FreezeBalanceStage::getStage)).collect(Collectors.toList());
        for(FreezeBalanceStage stage : stages){
          duration = dynamicStore.getVPFreezeDurationByStage(stage.getStage()) * FROZEN_PERIOD;
          frozenBalance += stage.getFrozenBalance();
          newBalance -= stage.getFrozenBalance();
        }
        break;
    }

    long expireTime = now + duration;

    byte[] receiverAddress = freezeBalanceContract.getReceiverAddress().toByteArray();

    switch (freezeBalanceContract.getResource()) {
      case PHOTON:
        if (!ArrayUtils.isEmpty(receiverAddress)
                && dynamicStore.supportDR()) {
          delegateResource(ownerAddress, receiverAddress, true,
                  frozenBalance, expireTime);
          accountCapsule.addDelegatedFrozenBalanceForPhoton(frozenBalance);
        } else {
          if (dynamicStore.getAllowVPFreezeStageWeight() == 1) {
            accountFrozenStageResource(ownerAddress, stages, true, accountCapsule, freezeBalanceContract.getFrozenBalance());
            Map<Long, List<Long>> stageWeights = dynamicStore.getVPFreezeStageWeights();
            for (Map.Entry<Long, List<Long>> entry : stageWeights.entrySet()) {
              byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, entry.getKey());
              AccountFrozenStageResourceCapsule capsule = accountFrozenStageResourceStore.get(key);
              if (capsule == null || capsule.getInstance().getFrozenBalanceForPhoton() == 0) {
                continue;
              }
              expireTime = Math.max(expireTime, capsule.getInstance().getExpireTimeForPhoton());
            }
          }

          long newFrozenBalanceForPhoton =
                  frozenBalance + accountCapsule.getFrozenBalance();
          accountCapsule.setFrozenForPhoton(newFrozenBalanceForPhoton, expireTime);

          if (dynamicStore.getAllowVPFreezeStageWeight() == 1) {
            long weightMerge = AccountCapsule.calcAccountFrozenStageWeightMerge(
                accountCapsule, accountFrozenStageResourceStore, dynamicStore);
            accountCapsule.setFrozenStageWeightMerge(weightMerge);
          }
        }
        dynamicStore
            .addTotalPhotonWeight(frozenBalance / VS_PRECISION);
        break;
      case ENTROPY:
        if (!ArrayUtils.isEmpty(receiverAddress)
                && dynamicStore.supportDR()) {
          delegateResource(ownerAddress, receiverAddress, false,
                  frozenBalance, expireTime);
          accountCapsule.addDelegatedFrozenBalanceForEntropy(frozenBalance);
        } else {
          if (dynamicStore.getAllowVPFreezeStageWeight() == 1) {
            accountFrozenStageResource(ownerAddress, stages, false, accountCapsule, freezeBalanceContract.getFrozenBalance());

            Map<Long, List<Long>> stageWeights = dynamicStore.getVPFreezeStageWeights();
            for (Map.Entry<Long, List<Long>> entry : stageWeights.entrySet()) {
              byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, entry.getKey());
              AccountFrozenStageResourceCapsule capsule = accountFrozenStageResourceStore.get(key);
              if (capsule == null || capsule.getInstance().getFrozenBalanceForEntropy() == 0) {
                continue;
              }
              expireTime = Math.max(expireTime, capsule.getInstance().getExpireTimeForEntropy());
            }
          }
          long newFrozenBalanceForEntropy =
                  frozenBalance + accountCapsule.getAccountResource()
                          .getFrozenBalanceForEntropy()
                          .getFrozenBalance();
          accountCapsule.setFrozenForEntropy(newFrozenBalanceForEntropy, expireTime);

          if (dynamicStore.getAllowVPFreezeStageWeight() == 1) {
            long weightMerge = AccountCapsule.calcAccountFrozenStageWeightMerge(
                accountCapsule, accountFrozenStageResourceStore, dynamicStore);
            accountCapsule.setFrozenStageWeightMerge(weightMerge);
          }
        }
        dynamicStore
                .addTotalEntropyWeight(frozenBalance / VS_PRECISION);
        break;
      case FVGUARANTEE:
        long newFrozenBalanceForFVGuarantee =
                frozenBalance + accountCapsule.getAccountResource()
                        .getFrozenBalanceForFvguarantee()
                        .getFrozenBalance();
        accountCapsule.setFrozenForFVGuarantee(newFrozenBalanceForFVGuarantee, expireTime);
        dynamicStore
                .addTotalFVGuaranteeWeight(frozenBalance / VS_PRECISION);
        break;
      default:
        logger.debug("Resource Code Error.");
    }

    accountCapsule.setBalance(newBalance);
    accountStore.put(accountCapsule.createDbKey(), accountCapsule);

    ret.setStatus(fee, code.SUCESS);

    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.any == null) {
      throw new ContractValidateException(ActuatorConstant.CONTRACT_NOT_EXIST);
    }
    if (chainBaseManager == null) {
      throw new ContractValidateException(ActuatorConstant.STORE_NOT_EXIST);
    }
    AccountStore accountStore = chainBaseManager.getAccountStore();
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    if (!any.is(FreezeBalanceContract.class)) {
      throw new ContractValidateException(
              "contract type error,expected type [FreezeBalanceContract],real type[" + any
                      .getClass() + "]");
    }

    final FreezeBalanceContract freezeBalanceContract;
    try {
      freezeBalanceContract = this.any.unpack(FreezeBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      throw new ContractValidateException(e.getMessage());
    }
    byte[] ownerAddress = freezeBalanceContract.getOwnerAddress().toByteArray();
    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }
    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    if (accountCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
              ActuatorConstant.ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
    }

    long frozenDuration = freezeBalanceContract.getFrozenDuration();

    boolean needCheckFrozeTime = CommonParameter.getInstance()
            .getCheckFrozenTime() == 1;
    long days = dynamicStore.getSpecialFreezePeriodLimit();
    if (dynamicStore.getAllowVPFreezeStageWeight() == 1) {
      if (freezeBalanceContract.getFreezeBalanceStageCount() > 5) {
        throw new ContractValidateException(
                "[PHOTON、ENTROPY] frozen stage's length must be lte 5");
      }
      Map<Long, List<Long>> stageWeight = dynamicStore.getVPFreezeStageWeights();
      Set<Long> stages = new HashSet<>();
      for(FreezeBalanceStage stage : freezeBalanceContract.getFreezeBalanceStageList()) {
        if(!stageWeight.containsKey(stage.getStage())){
          throw new ContractValidateException(
                  "[PHOTON、ENTROPY] frozen stage must be one of " + stageWeight.keySet());
        }
        stages.add(stage.getStage());
      }
      if (stages.size() != freezeBalanceContract.getFreezeBalanceStageCount()) {
        throw new ContractValidateException("[PHOTON、ENTROPY] frozen stage must be not repeated");
      }
      days = dynamicStore.getVPFreezeDurationByStage(1L);
    } else {
      if (freezeBalanceContract.getFreezeBalanceStageCount() > 0) {
        throw new ContractValidateException("freeze stages is not allowed yet");
      }
    }

    if (needCheckFrozeTime
        && (freezeBalanceContract.getResource() == Common.ResourceCode.PHOTON || freezeBalanceContract.getResource() == Common.ResourceCode.ENTROPY)
        && frozenDuration != days) {
      if (dynamicStore.getAllowVPFreezeStageWeight() != 1){
        throw new ContractValidateException(
            "[PHOTON、ENTROPY] frozenDuration must be " + days + " days");
      } else {
        if (freezeBalanceContract.getFrozenBalance() > 0) {
          throw new ContractValidateException(
              "[PHOTON、ENTROPY] frozenDuration must be " + days + " days");
        } else {
          if (freezeBalanceContract.getFreezeBalanceStageCount() <=0 ){
            throw new ContractValidateException(
                "[PHOTON、ENTROPY] freeze stages must not by empty when freeze balance is zero");
          }
        }
      }
    }

    if (needCheckFrozeTime
            && freezeBalanceContract.getResource() == Common.ResourceCode.FVGUARANTEE
            && frozenDuration != dynamicStore.getFvGuaranteeFreezePeriodLimit()) {
      throw new ContractValidateException(
              "[FVGUARANTEE] frozenDuration must be " + dynamicStore.getFvGuaranteeFreezePeriodLimit() + " days");
    }

    byte[] parentAddress = freezeBalanceContract.getParentAddress().toByteArray();
    long frozenBalance = freezeBalanceContract.getFrozenBalance();

    switch (freezeBalanceContract.getResource()) {
      case PHOTON:
      case ENTROPY:
        if (dynamicStore.getAllowVPFreezeStageWeight() == 1) {
          if (frozenBalance < 0) {
            throw new ContractValidateException("frozenBalance must be positive");
          }
          if (frozenBalance > 0 && frozenBalance < VS_PRECISION) {
            throw new ContractValidateException("frozenBalance must be more than 1VS");
          }
          if (frozenBalance == 0 && freezeBalanceContract.getFreezeBalanceStageCount() == 0) {
            throw new ContractValidateException("frozenBalance must be positive");
          }
          for (FreezeBalanceStage stage : freezeBalanceContract.getFreezeBalanceStageList()) {
            if (stage.getFrozenBalance() <= 0) {
              throw new ContractValidateException("frozenBalance must be positive");
            }
            if (stage.getFrozenBalance() < VS_PRECISION) {
              throw new ContractValidateException("frozenBalance must be more than 1VS");
            }
            frozenBalance += stage.getFrozenBalance();
          }
        } else {
          if (frozenBalance <= 0) {
            throw new ContractValidateException("frozenBalance must be positive");
          }
          if (frozenBalance < VS_PRECISION) {
            throw new ContractValidateException("frozenBalance must be more than 1VS");
          }
        }
        break;
      case FVGUARANTEE:
        if (frozenBalance <= 0) {
          throw new ContractValidateException("frozenBalance must be positive");
        }
        if (frozenBalance < VS_PRECISION) {
          throw new ContractValidateException("frozenBalance must be more than 1VS");
        }
        break;
    }

    int frozenCount = accountCapsule.getFrozenCount();
    if (!(frozenCount == 0 || frozenCount == 1)) {
      throw new ContractValidateException("frozenCount must be 0 or 1");
    }
    if (frozenBalance > accountCapsule.getBalance()) {
      throw new ContractValidateException("frozenBalance must be less than accountBalance");
    }

    switch (freezeBalanceContract.getResource()) {
      case PHOTON:
        break;
      case ENTROPY:
        break;
      case FVGUARANTEE:
        break;
      default:
        throw new ContractValidateException(
                "ResourceCode error,valid ResourceCode[PHOTON、ENTROPY、FVGUARANTEE、SPREAD]");
    }

    //todo：need version control and config for delegating resource
    byte[] receiverAddress = freezeBalanceContract.getReceiverAddress().toByteArray();
    //If the receiver is included in the contract, the receiver will receive the resource.
    if (!ArrayUtils.isEmpty(receiverAddress) && dynamicStore.supportDR()) {
      if (Arrays.equals(receiverAddress, ownerAddress)) {
        throw new ContractValidateException(
                "receiverAddress must not be the same as ownerAddress");
      }

      if (!DecodeUtil.addressValid(receiverAddress)) {
        throw new ContractValidateException("Invalid receiverAddress");
      }

      AccountCapsule receiverCapsule = accountStore.get(receiverAddress);
      if (receiverCapsule == null) {
        String readableOwnerAddress = StringUtil.createReadableString(receiverAddress);
        throw new ContractValidateException(
                ActuatorConstant.ACCOUNT_EXCEPTION_STR
                        + readableOwnerAddress + NOT_EXIST_STR);
      }

      if (dynamicStore.getAllowVvmConstantinople() == 1
              && receiverCapsule.getType() == AccountType.Contract) {
        throw new ContractValidateException(
                "Do not allow delegate resources to contract addresses");

      }

    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(FreezeBalanceContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

  private void delegateResource(byte[] ownerAddress, byte[] receiverAddress, boolean isPhoton,
                                long balance, long expireTime) {
    AccountStore accountStore = chainBaseManager.getAccountStore();
    DelegatedResourceStore delegatedResourceStore = chainBaseManager.getDelegatedResourceStore();
    DelegatedResourceAccountIndexStore delegatedResourceAccountIndexStore = chainBaseManager
            .getDelegatedResourceAccountIndexStore();
    byte[] key = DelegatedResourceCapsule.createDbKey(ownerAddress, receiverAddress);
    //modify DelegatedResourceStore
    DelegatedResourceCapsule delegatedResourceCapsule = delegatedResourceStore
            .get(key);
    if (delegatedResourceCapsule != null) {
      if (isPhoton) {
        delegatedResourceCapsule.addFrozenBalanceForPhoton(balance, expireTime);
      } else {
        delegatedResourceCapsule.addFrozenBalanceForEntropy(balance, expireTime);
      }
    } else {
      delegatedResourceCapsule = new DelegatedResourceCapsule(
              ByteString.copyFrom(ownerAddress),
              ByteString.copyFrom(receiverAddress));
      if (isPhoton) {
        delegatedResourceCapsule.setFrozenBalanceForPhoton(balance, expireTime);
      } else {
        delegatedResourceCapsule.setFrozenBalanceForEntropy(balance, expireTime);
      }

    }
    delegatedResourceStore.put(key, delegatedResourceCapsule);

    //modify DelegatedResourceAccountIndexStore
    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = delegatedResourceAccountIndexStore
              .get(ownerAddress);
      if (delegatedResourceAccountIndexCapsule == null) {
        delegatedResourceAccountIndexCapsule = new DelegatedResourceAccountIndexCapsule(
                ByteString.copyFrom(ownerAddress));
      }
      List<ByteString> toAccountsList = delegatedResourceAccountIndexCapsule.getToAccountsList();
      if (!toAccountsList.contains(ByteString.copyFrom(receiverAddress))) {
        delegatedResourceAccountIndexCapsule.addToAccount(ByteString.copyFrom(receiverAddress));
      }
      delegatedResourceAccountIndexStore
              .put(ownerAddress, delegatedResourceAccountIndexCapsule);
    }

    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = delegatedResourceAccountIndexStore
              .get(receiverAddress);
      if (delegatedResourceAccountIndexCapsule == null) {
        delegatedResourceAccountIndexCapsule = new DelegatedResourceAccountIndexCapsule(
                ByteString.copyFrom(receiverAddress));
      }
      List<ByteString> fromAccountsList = delegatedResourceAccountIndexCapsule
              .getFromAccountsList();
      if (!fromAccountsList.contains(ByteString.copyFrom(ownerAddress))) {
        delegatedResourceAccountIndexCapsule.addFromAccount(ByteString.copyFrom(ownerAddress));
      }
      delegatedResourceAccountIndexStore
              .put(receiverAddress, delegatedResourceAccountIndexCapsule);
    }

    //modify AccountStore
    AccountCapsule receiverCapsule = accountStore.get(receiverAddress);
    if (isPhoton) {
      receiverCapsule.addAcquiredDelegatedFrozenBalanceForPhoton(balance);
    } else {
      receiverCapsule.addAcquiredDelegatedFrozenBalanceForEntropy(balance);
    }

    accountStore.put(receiverCapsule.createDbKey(), receiverCapsule);
  }

  private void accountFrozenStageResource(byte[] ownerAddress, List<FreezeBalanceStage> stages, boolean isPhoton, AccountCapsule account, long frozenBalance) {
    DynamicPropertiesStore dynamicPropertiesStore = chainBaseManager.getDynamicPropertiesStore();
    Map<Long, List<Long>> stageWeight = dynamicPropertiesStore.getVPFreezeStageWeights();
    AccountFrozenStageResourceStore accountFrozenStageResourceStore = chainBaseManager.getAccountFrozenStageResourceStore();
    long now = dynamicPropertiesStore.getLatestBlockHeaderTimestamp();
    for (FreezeBalanceStage stage : stages) {
      long expireTime = now + stageWeight.get(stage.getStage()).get(0) * FROZEN_PERIOD;
      long balance = stage.getFrozenBalance();
      if (stage.getStage() == 1L) {
        if (isPhoton) {
          balance += frozenBalance + account.getFrozenBalance();
          balance -= AccountFrozenStageResourceCapsule.getTotalStageBalanceForPhoton(ownerAddress, 0L, accountFrozenStageResourceStore, dynamicPropertiesStore);
        } else {
          balance += frozenBalance + account.getEntropyFrozenBalance();
          balance -= AccountFrozenStageResourceCapsule.getTotalStageBalanceForEntropy(ownerAddress, 0L, accountFrozenStageResourceStore, dynamicPropertiesStore);
        }
      }
      AccountFrozenStageResourceCapsule.freezeBalance(
          ownerAddress,
          stage.getStage(),
          balance,
          expireTime,
          isPhoton,
          accountFrozenStageResourceStore);

      if (isPhoton) {
        dynamicPropertiesStore
            .addTotalStagePhotonWeight(Collections.singletonList(stage.getStage()),
                balance / VS_PRECISION);
      } else {
        dynamicPropertiesStore
            .addTotalStageEntropyWeight(Collections.singletonList(stage.getStage()),
                balance / VS_PRECISION);
      }
    }

    Set<Long> stagesKey = stages.stream().map(FreezeBalanceStage::getStage).collect(Collectors.toSet());
    if (!stagesKey.contains(1L)) {
      long expireTime = account.getFrozenExpireTime();
      if (!isPhoton) {
        expireTime = account.getEntropyFrozenExpireTime();
      }
      if (frozenBalance > 0) {
        expireTime = now + stageWeight.get(1L).get(0) * FROZEN_PERIOD;
      }

      long balance = frozenBalance;
      if (isPhoton) {
        balance += account.getFrozenBalance();
      } else {
        balance += account.getEntropyFrozenBalance();
      }
      AccountFrozenStageResourceCapsule.freezeBalance(
          ownerAddress,
          1L,
          balance,
          expireTime,
          isPhoton,
          accountFrozenStageResourceStore);
      if (isPhoton) {
        dynamicPropertiesStore
            .addTotalStagePhotonWeight(Collections.singletonList(1L),
                balance / VS_PRECISION);
      } else {
        dynamicPropertiesStore
            .addTotalStageEntropyWeight(Collections.singletonList(1L),
                balance / VS_PRECISION);
      }
    }

    AccountFrozenStageResourceCapsule.dealReFreezeConsideration(
        account, accountFrozenStageResourceStore, dynamicPropertiesStore);
  }
}
