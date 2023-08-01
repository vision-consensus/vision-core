package org.vision.core.actuator;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.DecodeUtil;
import org.vision.common.utils.StringUtil;
import org.vision.core.capsule.*;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.store.*;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.protos.contract.AccountContract.FreezeAccountContract;

import java.util.*;

import static org.vision.core.actuator.ActuatorConstant.NOT_EXIST_STR;

@Slf4j(topic = "actuator")
public class FreezeAccountActuator extends AbstractActuator {

  public FreezeAccountActuator() {
    super(ContractType.FreezeAccountContract, FreezeAccountContract.class);
  }

  @Override
  public boolean execute(Object result) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) result;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();
    final FreezeAccountContract freezeAccountContract;
    try {
      freezeAccountContract = any.unpack(FreezeAccountContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }

    List<ByteString> addresses = freezeAccountContract.getAccountsList();

    FreezeAccountStore freezeAccountStore = chainBaseManager.getFreezeAccountStore();
    FreezeAccountCapsule freezeAccountCapsule = freezeAccountStore.get(freezeAccountStore.createFreezeAccountDbKey());
    if (freezeAccountCapsule != null) {
      for (ByteString address: addresses) {
        freezeAccountCapsule.addAddress(address);
      }
    }else {
      freezeAccountCapsule = new FreezeAccountCapsule(addresses);
    }

    freezeAccountStore.put(freezeAccountStore.createFreezeAccountDbKey(), freezeAccountCapsule);

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

    if (!any.is(FreezeAccountContract.class)) {
      throw new ContractValidateException(
              "contract type error,expected type [FreezeAccountContract],real type[" + any
                      .getClass() + "]");
    }

    if (!dynamicStore.supportFreezeAccount()) {
      throw new ContractValidateException("Not support FreezeAccountContract transaction,"
              + " need to be opened by the committee");
    }

    final FreezeAccountContract freezeAccountContract;
    try {
      freezeAccountContract = this.any.unpack(FreezeAccountContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    byte[] ownerAddress = freezeAccountContract.getOwnerAddress().toByteArray();
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

    List<ByteString> freezeAccountList = freezeAccountContract.getAccountsList();
    if (freezeAccountList.isEmpty()){
      throw new ContractValidateException("addresses is empty");
    }

    for (ByteString address: freezeAccountList) {
      byte[] freezeAddress = address.toByteArray();
      if (!DecodeUtil.addressValid(freezeAddress)) {
        throw new ContractValidateException("Invalid address");
      }
    }

    FreezeAccountStore freezeAccountStore = chainBaseManager.getFreezeAccountStore();
    FreezeAccountCapsule freezeAccountCapsule = freezeAccountStore.get(freezeAccountStore.createFreezeAccountDbKey());
    if (freezeAccountCapsule != null) {
      List<ByteString> oldFreezeAccountList = freezeAccountCapsule.getAddressesList();
      for (ByteString address: freezeAccountList) {
        if (!oldFreezeAccountList.isEmpty() && oldFreezeAccountList.contains(address)) {
          byte[] freezeAddress = address.toByteArray();
          String freezeReadableOwnerAddress = StringUtil.createReadableString(freezeAddress);
          throw new ContractValidateException(
                  ActuatorConstant.ACCOUNT_EXCEPTION_STR + freezeReadableOwnerAddress + "] has already been banned.");
        }
      }
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(FreezeAccountContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}
