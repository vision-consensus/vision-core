package org.vision.core.actuator;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.spongycastle.util.encoders.Hex;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.DecodeUtil;
import org.vision.common.utils.StringUtil;
import org.vision.core.capsule.*;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.service.MortgageService;
import org.vision.core.store.*;
import org.vision.protos.Protocol.AccountType;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.protos.contract.BalanceContract.FreezeBalanceContract;
import org.vision.protos.contract.Common;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
    long expireTime = now + duration;

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
          long newFrozenBalanceForPhoton =
              frozenBalance + accountCapsule.getFrozenBalance();
          accountCapsule.setFrozenForPhoton(newFrozenBalanceForPhoton, expireTime);
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
          long newFrozenBalanceForEntropy =
              frozenBalance + accountCapsule.getAccountResource()
                  .getFrozenBalanceForEntropy()
                  .getFrozenBalance();
          accountCapsule.setFrozenForEntropy(newFrozenBalanceForEntropy, expireTime);
        }
        dynamicStore
            .addTotalEntropyWeight(frozenBalance / VS_PRECISION);
        break;
      case SRGUARANTEE:
        long srGuaranteeExpireTime = now + UN_FREEZE_SRGUARANTEE_LIMIT;
        long newFrozenBalanceForSRGuarantee =
                frozenBalance + accountCapsule.getAccountResource()
                        .getFrozenBalanceForSrguarantee()
                        .getFrozenBalance();
        accountCapsule.setFrozenForSRGuarantee(newFrozenBalanceForSRGuarantee, srGuaranteeExpireTime);
        dynamicStore
                .addTotalSRGuaranteeWeight(frozenBalance / VS_PRECISION);
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
    long minFrozenTime = dynamicStore.getMinFrozenTime();
    long maxFrozenTime = dynamicStore.getMaxFrozenTime();

    boolean needCheckFrozeTime = CommonParameter.getInstance()
            .getCheckFrozenTime() == 1;//for test
    if (needCheckFrozeTime && !(frozenDuration >= minFrozenTime
            && frozenDuration <= maxFrozenTime)) {
      throw new ContractValidateException(
              "frozenDuration must be less than " + maxFrozenTime + " days "
                      + "and more than " + minFrozenTime + " days");
    }

    byte[] parentAddress = freezeBalanceContract.getParentAddress().toByteArray();
    long frozenBalance = freezeBalanceContract.getFrozenBalance();
    if (freezeBalanceContract.getResource() == Common.ResourceCode.SPREAD){
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
      if (frozenBalance == 0){ // frozenBalance == 0 and exist spreadRelationShip, update Spread parentAddress
        if (spreadRelationShipCapsule == null){
          throw new ContractValidateException("the address has not yet set a parentAddress, frozenBalance must be more than 1VS");
        } else {
          String oldParent = Hex.toHexString(spreadRelationShipCapsule.getParent().toByteArray());
          String newParent = Hex.toHexString(ByteString.copyFrom(parentAddress).toByteArray());
          if (oldParent.equals(newParent)) {
            throw new ContractValidateException("The new and old parentAddress cannot be the same address");
          }
        }
      }

      if (spreadRelationShipCapsule != null){
        long duration = freezeBalanceContract.getFrozenDuration() * FROZEN_PERIOD;
        long now = dynamicStore.getLatestBlockHeaderTimestamp();
        long frozenSpreadExpiredTime = spreadRelationShipCapsule.getExpireTimeForSpread();
        long frozenBalanceForSpread = spreadRelationShipCapsule.getFrozenBalanceForSpread();
        if (frozenBalanceForSpread > 0 && frozenSpreadExpiredTime - duration + dynamicStore.getSpreadFreezePeriodLimit() * FROZEN_PERIOD - now > dynamicStore.getMaxFrozenTime() * FROZEN_PERIOD - 180000L){
          throw new ContractValidateException("It's not time to re-freeze.");
        }
      }
    }else{
      if (frozenBalance <= 0) {
        throw new ContractValidateException("frozenBalance must be positive");
      }
      if (frozenBalance < VS_PRECISION) {
        throw new ContractValidateException("frozenBalance must be more than 1VS");
      }
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
      case SRGUARANTEE:
        break;
      case SPREAD: // check the parentAddress is a valid account
          AccountCapsule parentCapsule = accountStore.get(parentAddress);
          if (parentCapsule == null) {
            String readableParentAddress = StringUtil.createReadableString(parentAddress);
            throw new ContractValidateException(
                    ActuatorConstant.ACCOUNT_EXCEPTION_STR
                            + readableParentAddress + NOT_EXIST_STR);
          }

          boolean isCycle = checkSpreadMintCycle(ownerAddress, parentAddress);
          if (isCycle){
            throw new ContractValidateException("Illegal parentAddress, the parentAddress will generate a cycle");
          }
        break;
      default:
        throw new ContractValidateException(
            "ResourceCode error,valid ResourceCode[PHOTON、ENTROPY、SRGUARANTEE、SPREAD]");
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
      logger.error("checkSpreadMintCycle error: {},{}", Hex.toHexString(ByteString.copyFrom(ownerAddress).toByteArray()), e);
    }

    return isCycle;
  }

}
