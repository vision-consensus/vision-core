/*
 * vision-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * vision-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.vision.core.capsule;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.vision.core.Constant;
import org.vision.protos.Protocol.Transaction;
import org.vision.protos.contract.BalanceContract;
import org.vision.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract.ABI;
import org.vision.protos.contract.SmartContractOuterClass.SmartContractDataWrapper;
import org.vision.protos.contract.SmartContractOuterClass.TriggerSmartContract;
import org.vision.protos.contract.StorageContract;
import org.vision.protos.contract.WitnessContract;

import static java.lang.Math.max;
import static java.lang.Math.min;

@Slf4j(topic = "capsule")
public class ContractCapsule implements ProtoCapsule<SmartContract> {

  private SmartContract smartContract;
  private byte[] runtimecode;

  /**
   * constructor TransactionCapsule.
   */
  public ContractCapsule(SmartContract smartContract) {
    this.smartContract = smartContract;
  }

  public ContractCapsule(byte[] data) {
    try {
      this.smartContract = SmartContract.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      // logger.debug(e.getMessage());
    }
  }

  public static CreateSmartContract getSmartContractFromTransaction(Transaction trx) {
    try {
      Any any = trx.getRawData().getContract(0).getParameter();
      CreateSmartContract createSmartContract = any.unpack(CreateSmartContract.class);
      return createSmartContract;
    } catch (InvalidProtocolBufferException e) {
      return null;
    }
  }

  public static TriggerSmartContract getTriggerContractFromTransaction(Transaction trx) {
    try {
      Any any = trx.getRawData().getContract(0).getParameter();
      TriggerSmartContract contractTriggerContract = any.unpack(TriggerSmartContract.class);
      return contractTriggerContract;
    } catch (InvalidProtocolBufferException e) {
      return null;
    }
  }

  public static CreateSmartContract getCreateSmartContractFromTransaction(Transaction trx) {
    try {
      Any any = trx.getRawData().getContract(0).getParameter();
      CreateSmartContract createSmartContract = any.unpack(CreateSmartContract.class);
      return createSmartContract;
    } catch (InvalidProtocolBufferException e) {
      return null;
    }
  }

  public static BalanceContract.TransferContract getTransferContractFromTransaction(Transaction trx) {
    try {
      Any any = trx.getRawData().getContract(0).getParameter();
      return any.unpack(BalanceContract.TransferContract.class);
    } catch (InvalidProtocolBufferException e) {
      return null;
    }
  }

  public static BalanceContract.WithdrawBalanceContract getWithdrawBalanceContractFromTransaction(Transaction trx) {
    try {
      Any any = trx.getRawData().getContract(0).getParameter();
      return any.unpack(BalanceContract.WithdrawBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      return null;
    }
  }

  public static BalanceContract.FreezeBalanceContract getFreezeBalanceContractFromTransaction(Transaction trx) {
    try {
      Any any = trx.getRawData().getContract(0).getParameter();
      return any.unpack(BalanceContract.FreezeBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      return null;
    }
  }

  public static BalanceContract.UnfreezeBalanceContract getUnfreezeBalanceContractFromTransaction(Transaction trx) {
    try {
      Any any = trx.getRawData().getContract(0).getParameter();
      return any.unpack(BalanceContract.UnfreezeBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      return null;
    }
  }

  public static WitnessContract.VoteWitnessContract getVoteWitnessContractFromTransaction(Transaction trx) {
    try {
      Any any = trx.getRawData().getContract(0).getParameter();
      return any.unpack(WitnessContract.VoteWitnessContract.class);
    } catch (InvalidProtocolBufferException e) {
      return null;
    }
  }

  public static WitnessContract.WitnessCreateContract getWitnessCreateContractFromTransaction(Transaction trx) {
    try {
      Any any = trx.getRawData().getContract(0).getParameter();
      return any.unpack(WitnessContract.WitnessCreateContract.class);
    } catch (InvalidProtocolBufferException e) {
      return null;
    }
  }

  public static WitnessContract.WitnessUpdateContract getWitnessUpdateContractFromTransaction(Transaction trx) {
    try {
      Any any = trx.getRawData().getContract(0).getParameter();
      return any.unpack(WitnessContract.WitnessUpdateContract.class);
    } catch (InvalidProtocolBufferException e) {
      return null;
    }
  }

  public static StorageContract.UpdateBrokerageContract getUpdateBrokerageContractFromTransaction(Transaction trx) {
    try {
      Any any = trx.getRawData().getContract(0).getParameter();
      return any.unpack(StorageContract.UpdateBrokerageContract.class);
    } catch (InvalidProtocolBufferException e) {
      return null;
    }
  }

  public byte[] getCodeHash() {
    return this.smartContract.getCodeHash().toByteArray();
  }

  public void setCodeHash(byte[] codeHash) {
    this.smartContract = this.smartContract.toBuilder().setCodeHash(ByteString.copyFrom(codeHash))
        .build();
  }

  public void setRuntimecode(byte[] bytecode) {
    this.runtimecode = bytecode;
  }

  public SmartContractDataWrapper generateWrapper() {
    return SmartContractDataWrapper.newBuilder().setSmartContract(this.smartContract)
        .setRuntimecode(ByteString.copyFrom(this.runtimecode)).build();
  }

  @Override
  public byte[] getData() {
    return this.smartContract.toByteArray();
  }

  @Override
  public SmartContract getInstance() {
    return this.smartContract;
  }

  @Override
  public String toString() {
    return this.smartContract.toString();
  }

  public byte[] getOriginAddress() {
    return this.smartContract.getOriginAddress().toByteArray();
  }

  public long getConsumeUserResourcePercent() {
    long percent = this.smartContract.getConsumeUserResourcePercent();
    return max(0, min(percent, Constant.ONE_HUNDRED));
  }

  public long getOriginEntropyLimit() {
    long originEntropyLimit = this.smartContract.getOriginEntropyLimit();
    if (originEntropyLimit == Constant.PB_DEFAULT_ENTROPY_LIMIT) {
      originEntropyLimit = Constant.CREATOR_DEFAULT_ENTROPY_LIMIT;
    }
    return originEntropyLimit;
  }

  public void clearABI() {
    this.smartContract = this.smartContract.toBuilder().setAbi(ABI.getDefaultInstance()).build();
  }

  public byte[] getTrxHash() {
    return this.smartContract.getTrxHash().toByteArray();
  }
}
