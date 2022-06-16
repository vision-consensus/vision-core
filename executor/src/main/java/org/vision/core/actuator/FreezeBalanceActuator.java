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
    try {
      freezeBalanceContract = any.unpack(FreezeBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }

    byte[] ownerAddress = freezeBalanceContract.getOwnerAddress().toByteArray();
    if(freezeBalanceContract.getResource().equals(Common.ResourceCode.SPREAD)){
      chainBaseManager.getMortgageService().withdrawSpreadMintReward(ownerAddress);
    }

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

    long expireTime = now + 180000L;

    byte[] receiverAddress = freezeBalanceContract.getReceiverAddress().toByteArray();
    byte[] parentAddress = freezeBalanceContract.getParentAddress().toByteArray();

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
      case SPREAD:
        if (!ArrayUtils.isEmpty(parentAddress)){
          spreadRelationShip(ownerAddress, parentAddress, frozenBalance, expireTime);
        }

        long newFrozenBalanceForSpreadMint =
                frozenBalance + accountCapsule.getAccountResource()
                        .getFrozenBalanceForSpread().getFrozenBalance();
        accountCapsule.setFrozenForSpread(newFrozenBalanceForSpreadMint, expireTime);

        dynamicStore.addTotalSpreadMintWeight(frozenBalance / VS_PRECISION);
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
      logger.debug(e.getMessage(), e);
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
            && freezeBalanceContract.getResource() == Common.ResourceCode.SPREAD
            && frozenDuration != dynamicStore.getSpreadFreezePeriodLimit()) {
      throw new ContractValidateException(
              "[SPREAD] frozenDuration must be " + dynamicStore.getSpreadFreezePeriodLimit() + " days");
    }

    if (needCheckFrozeTime
            && freezeBalanceContract.getResource() == Common.ResourceCode.FVGUARANTEE
            && frozenDuration != dynamicStore.getFvGuaranteeFreezePeriodLimit()) {
      throw new ContractValidateException(
              "[FVGUARANTEE] frozenDuration must be " + dynamicStore.getFvGuaranteeFreezePeriodLimit() + " days");
    }

    byte[] parentAddress = freezeBalanceContract.getParentAddress().toByteArray();
    long frozenBalance = freezeBalanceContract.getFrozenBalance();

    boolean isUnlimitedPledge = dynamicStore.getLatestBlockHeaderNumber() >= CommonParameter.PARAMETER.spreadMintUnlimitedPledgeEffectBlockNum;
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
      case SPREAD:
        if (!dynamicStore.supportSpreadMint()){
          throw new ContractValidateException("It's not support SPREAD type of frozen.");
        }

        if (ArrayUtils.isEmpty(parentAddress)){
          throw new ContractValidateException("parentAddress can not be empty");
        }
        if (!DecodeUtil.addressValid(parentAddress)) {
          throw new ContractValidateException("Invalid parentAddress");
        }

        if (frozenBalance < 0) {
          throw new ContractValidateException("frozenBalance must be positive");
        } else if (frozenBalance > 0 && frozenBalance < VS_PRECISION) {
          throw new ContractValidateException("frozenBalance must be more than 1VS");
        }

        SpreadRelationShipCapsule spreadRelationShipCapsule = chainBaseManager.getSpreadRelationShipStore().get(ownerAddress);
        String oldParent = "";
        String newParent = Hex.toHexString(ByteString.copyFrom(parentAddress).toByteArray());
        if(spreadRelationShipCapsule != null){
          oldParent = Hex.toHexString(spreadRelationShipCapsule.getParent().toByteArray());
        }

        if (!dynamicStore.supportModifySpreadMintParent()){
          if (!oldParent.isEmpty() && !oldParent.equals(newParent)){
            throw new ContractValidateException("It's not allowed to modify the parentAddress");
          }
          if (frozenBalance == 0){
            throw new ContractValidateException("frozenBalance must be more than 1VS");
          }
        } else { // supportModifySpreadMintParent == true
          if (frozenBalance == 0){ // frozenBalance == 0 and exist spreadRelationShip, update Spread parentAddress
            if (spreadRelationShipCapsule == null){
              throw new ContractValidateException("the address has not yet set a parentAddress, frozenBalance must be more than 1VS");
            }
            if (!oldParent.isEmpty() && oldParent.equals(newParent)) {
              throw new ContractValidateException("The new and old parentAddress cannot be the same address");
            }
          }
        }

        if (spreadRelationShipCapsule != null){
          if (isUnlimitedPledge){
            if (!oldParent.equals(newParent) && spreadRelationShipCapsule.getExpireTimeForSpread() > dynamicStore.getLatestBlockHeaderTimestamp()){
              throw new ContractValidateException("It's not time to modify parentAddress. Time left: "+
                  Time.formatMillisInterval(spreadRelationShipCapsule.getExpireTimeForSpread() - dynamicStore.getLatestBlockHeaderTimestamp()));
            }
          }else{
            if (spreadRelationShipCapsule.getExpireTimeForSpread() > dynamicStore.getLatestBlockHeaderTimestamp()){
              throw new ContractValidateException("It's not time to re-freeze. Time left: "+
                  Time.formatMillisInterval(spreadRelationShipCapsule.getExpireTimeForSpread() - dynamicStore.getLatestBlockHeaderTimestamp()));
            }
          }
        }

        if (isUnlimitedPledge && Arrays.equals(ownerAddress, parentAddress)){
          throw new ContractValidateException("Illegal parentAddress, it's not allowed to set yourself as a parentAddress");
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
      case SPREAD: // check the parentAddress is a valid account
        AccountCapsule parentCapsule = accountStore.get(parentAddress);
        if (parentCapsule == null) {
          String readableParentAddress = StringUtil.createReadableString(parentAddress);
          throw new ContractValidateException(
                  ActuatorConstant.ACCOUNT_EXCEPTION_STR
                          + readableParentAddress + NOT_EXIST_STR);
        }

        boolean isCycle = isUnlimitedPledge ? checkSpreadMintCycleNoSelf(ownerAddress, parentAddress) : checkSpreadMintCycle(ownerAddress, parentAddress);
        if (isCycle){
          throw new ContractValidateException("Illegal parentAddress, the parentAddress will generate a cycle");
        }
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

  private void spreadRelationShip(byte[] ownerAddress, byte[] parentAddress, long balance, long expireTime){
    SpreadRelationShipStore spreadRelationShipStore = chainBaseManager.getSpreadRelationShipStore();
    SpreadRelationShipCapsule spreadRelationShipCapsule = spreadRelationShipStore
            .get(ownerAddress);

    long cycle = chainBaseManager.getDynamicPropertiesStore().getCurrentCycleNumber();
    boolean isUpdate = false;

    if (spreadRelationShipCapsule != null) {
      String oldParent = Hex.toHexString(spreadRelationShipCapsule.getParent().toByteArray());
      String newParent = Hex.toHexString(ByteString.copyFrom(parentAddress).toByteArray());
      if (oldParent.equals(newParent)) {
        spreadRelationShipCapsule.addFrozenBalanceForSpread(balance, expireTime, cycle);
      }else{ // cover spreadRelationShip parentAddress
        isUpdate = true; // update parentAddress
        long frozenBalanceForSpread = spreadRelationShipCapsule.getFrozenBalanceForSpread();
        spreadRelationShipCapsule = new SpreadRelationShipCapsule(
                ByteString.copyFrom(ownerAddress),
                ByteString.copyFrom(parentAddress));
        spreadRelationShipCapsule.setFrozenBalanceForSpread(frozenBalanceForSpread + balance, expireTime, cycle);
      }
    } else {
      spreadRelationShipCapsule = new SpreadRelationShipCapsule(
              ByteString.copyFrom(ownerAddress),
              ByteString.copyFrom(parentAddress));
      spreadRelationShipCapsule.addFrozenBalanceForSpread(balance, expireTime, cycle);
    }
    spreadRelationShipStore.put(ownerAddress, spreadRelationShipCapsule, isUpdate);
  }

  /**
   * check if the spreadRelationship is a cycle
   * @param ownerAddress
   * @param parentAddress
   * @return
   */
  private boolean checkSpreadMintCycle(byte[] ownerAddress, byte[] parentAddress){
    boolean isCycle = false;
    try {
      SpreadRelationShipStore spreadRelationShipStore = chainBaseManager.getSpreadRelationShipStore();
      AccountStore accountStore = chainBaseManager.getAccountStore();
      String spreadLevelProp = chainBaseManager.getDynamicPropertiesStore().getSpreadMintLevelProp();
      int level = spreadLevelProp.split(",").length;

      ArrayList<String> addressList = new ArrayList<>();
      addressList.add(Hex.toHexString(ByteString.copyFrom(ownerAddress).toByteArray()));

      AccountCapsule parentCapsule = accountStore.get(parentAddress);
      for (int i = 1; i < level; i++) {
        SpreadRelationShipCapsule spreadRelationShipCapsule = spreadRelationShipStore.get(parentCapsule.getAddress().toByteArray());
        if (spreadRelationShipCapsule == null){
          break;
        }

        addressList.add(Hex.toHexString(spreadRelationShipCapsule.getOwner().toByteArray()));
        if (addressList.contains(Hex.toHexString(spreadRelationShipCapsule.getParent().toByteArray()))) { // deal loop parent address
          isCycle = true;
          break;
        }
        parentCapsule = accountStore.get(spreadRelationShipCapsule.getParent().toByteArray());
      }
    }catch (Exception e){
      logger.error("checkSpreadMintCycle error: {},{}", Hex.toHexString(ByteString.copyFrom(ownerAddress).toByteArray()),
              Hex.toHexString(ByteString.copyFrom(parentAddress).toByteArray()),e);
    }

    return isCycle;
  }

  /**
   *  check if the spreadRelationship is a no self cycle
   * @param ownerAddress
   * @param parentAddress
   * @return
   */
  private boolean checkSpreadMintCycleNoSelf(byte[] ownerAddress, byte[] parentAddress){
    boolean isCycle = false;
    try {
      if (Arrays.equals(ownerAddress, parentAddress)){
        return true;
      }

      SpreadRelationShipStore spreadRelationShipStore = chainBaseManager.getSpreadRelationShipStore();
      AccountStore accountStore = chainBaseManager.getAccountStore();
      String spreadLevelProp = chainBaseManager.getDynamicPropertiesStore().getSpreadMintLevelProp();
      int level = spreadLevelProp.split(",").length;

      AccountCapsule parentCapsule = accountStore.get(parentAddress);
      for (int i = 1; i < level; i++) {
        SpreadRelationShipCapsule spreadRelationShipCapsule = spreadRelationShipStore.get(parentCapsule.getAddress().toByteArray());
        if (spreadRelationShipCapsule == null){
          break;
        }

        if (Arrays.equals(ownerAddress, spreadRelationShipCapsule.getParent().toByteArray())) { // deal loop parent address
          isCycle = true;
          break;
        }
        parentCapsule = accountStore.get(spreadRelationShipCapsule.getParent().toByteArray());
      }
    }catch (Exception e){
      logger.error("checkSpreadMintCycleNoSelf error: {},{}", Hex.toHexString(ByteString.copyFrom(ownerAddress).toByteArray()),
              Hex.toHexString(ByteString.copyFrom(parentAddress).toByteArray()),e);
    }

    return isCycle;
  }

  private void accountFrozenStageResource(byte[] ownerAddress, List<FreezeBalanceStage> stages, boolean isPhoton, AccountCapsule account, long frozenBalance) {
    DynamicPropertiesStore dynamicPropertiesStore = chainBaseManager.getDynamicPropertiesStore();
    Map<Long, List<Long>> stageWeight = dynamicPropertiesStore.getVPFreezeStageWeights();
    AccountFrozenStageResourceStore accountFrozenStageResourceStore = chainBaseManager.getAccountFrozenStageResourceStore();
    long now = dynamicPropertiesStore.getLatestBlockHeaderTimestamp();
    for (FreezeBalanceStage stage : stages) {
      long expireTime = now + stageWeight.get(stage.getStage()).get(0) * FROZEN_PERIOD;
      expireTime = now + stage.getStage() * 180000L;
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
        expireTime = now + 180000L;
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
