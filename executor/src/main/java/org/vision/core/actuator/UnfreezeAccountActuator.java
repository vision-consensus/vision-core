package org.vision.core.actuator;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.DecodeUtil;
import org.vision.common.utils.StringUtil;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.FreezeAccountCapsule;
import org.vision.core.capsule.TransactionResultCapsule;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.store.AccountStore;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.core.store.FreezeAccountStore;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.protos.contract.AccountContract;
import org.vision.protos.contract.AccountContract.UnfreezeAccountContract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.vision.core.actuator.ActuatorConstant.NOT_EXIST_STR;

@Slf4j(topic = "actuator")
public class UnfreezeAccountActuator extends AbstractActuator {

  public UnfreezeAccountActuator() {
    super(ContractType.UnfreezeAccountContract, UnfreezeAccountContract.class);
  }

  @Override
  public boolean execute(Object result) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) result;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();
    final UnfreezeAccountContract unfreezeAccountContract;

    try {
      unfreezeAccountContract = any.unpack(UnfreezeAccountContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }

    List<ByteString> frozenAddressList = unfreezeAccountContract.getAccountsList();

    FreezeAccountStore freezeAccountStore = chainBaseManager.getFreezeAccountStore();
    FreezeAccountCapsule freezeAccountCapsule = freezeAccountStore.get(freezeAccountStore.createFreezeAccountDbKey());
    List<ByteString> oldFreezeAddresses = freezeAccountCapsule.getAddressesList();

    List<ByteString> newFreezeAccountList = new ArrayList<>();
    for (ByteString address: oldFreezeAddresses) {
      if (frozenAddressList.contains(address)){
        continue;
      }
      newFreezeAccountList.add(address);
    }

    if (newFreezeAccountList.isEmpty()) {
      freezeAccountStore.delete(freezeAccountStore.createFreezeAccountDbKey());
    }else {
      freezeAccountCapsule.setAllAddresses(newFreezeAccountList);
      freezeAccountStore.put(freezeAccountStore.createFreezeAccountDbKey(), freezeAccountCapsule);
    }

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

    if (!any.is(AccountContract.UnfreezeAccountContract.class)) {
      throw new ContractValidateException(
              "contract type error,expected type [UnfreezeAccountContract],real type[" + any
                      .getClass() + "]");
    }

    if (!dynamicStore.supportFreezeAccount()) {
      throw new ContractValidateException("Not support UnfreezeAccountContract transaction,"
              + " need to be opened by the committee");
    }

    final UnfreezeAccountContract unfreezeAccountContract;
    try {
      unfreezeAccountContract = this.any.unpack(UnfreezeAccountContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    byte[] ownerAddress = unfreezeAccountContract.getOwnerAddress().toByteArray();
    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }
    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    if (accountCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
              ActuatorConstant.ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
    }

    if (!Arrays.equals(ownerAddress, ByteArray.fromHexString(dynamicStore.getFreezeAccountOwner()))) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
              ActuatorConstant.ACCOUNT_EXCEPTION_STR + readableOwnerAddress + "] no permission.");
    }


    List<ByteString> freezeAccountList = unfreezeAccountContract.getAccountsList();
    if (freezeAccountList.isEmpty()){
      throw new ContractValidateException("addresses is empty");
    }

    FreezeAccountStore freezeAccountStore = chainBaseManager.getFreezeAccountStore();
    FreezeAccountCapsule freezeAccountCapsule = freezeAccountStore.get(freezeAccountStore.createFreezeAccountDbKey());
    if (freezeAccountCapsule == null) {
      throw new ContractValidateException("No account has been frozen yet");
    }

    List<ByteString> oldFreezeAccountList = freezeAccountCapsule.getAddressesList();
    if (oldFreezeAccountList.isEmpty()) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException("The freeze list for " +
              ActuatorConstant.ACCOUNT_EXCEPTION_STR + readableOwnerAddress + "] is empty.");
    }

    for (ByteString address: freezeAccountList) {
      byte[] freezeAddress = address.toByteArray();
      if (!DecodeUtil.addressValid(freezeAddress)) {
        throw new ContractValidateException("Invalid address");
      }
      if (!oldFreezeAccountList.isEmpty() && !oldFreezeAccountList.contains(address)) {
        String freezeReadableOwnerAddress = StringUtil.createReadableString(freezeAddress);
        throw new ContractValidateException(
                ActuatorConstant.ACCOUNT_EXCEPTION_STR + freezeReadableOwnerAddress + "] has not in freeze list.");
      }
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(UnfreezeAccountContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}
