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

import com.google.common.primitives.Bytes;
import com.google.protobuf.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.BigIntegers;
import org.spongycastle.util.encoders.Hex;
import org.vision.common.crypto.ECKey;
import org.vision.common.crypto.ECKey.ECDSASignature;
import org.vision.common.crypto.Hash;
import org.vision.common.crypto.SignInterface;
import org.vision.common.crypto.SignUtils;
import org.vision.common.ethutils.RLP;
import org.vision.common.ethutils.RLPElement;
import org.vision.common.ethutils.RLPItem;
import org.vision.common.ethutils.RLPList;
import org.vision.common.overlay.message.Message;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.ByteUtil;
import org.vision.common.utils.ReflectUtils;
import org.vision.common.utils.Sha256Hash;
import org.vision.core.Constant;
import org.vision.core.actuator.TransactionFactory;
import org.vision.core.db.TransactionContext;
import org.vision.core.db.TransactionTrace;
import org.vision.core.exception.*;
import org.vision.core.store.AccountStore;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.protos.Protocol.Key;
import org.vision.protos.Protocol.Permission;
import org.vision.protos.Protocol.Permission.PermissionType;
import org.vision.protos.Protocol.Transaction;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result;
import org.vision.protos.Protocol.Transaction.Result.contractResult;
import org.vision.protos.Protocol.Transaction.raw;
import org.vision.protos.contract.*;
import org.vision.protos.contract.AccountContract.AccountCreateContract;
import org.vision.protos.contract.AssetIssueContractOuterClass.AssetIssueContract;
import org.vision.protos.contract.AssetIssueContractOuterClass.ParticipateAssetIssueContract;
import org.vision.protos.contract.AssetIssueContractOuterClass.TransferAssetContract;
import org.vision.protos.contract.BalanceContract.TransferContract;
import org.vision.protos.contract.BalanceContract.FreezeBalanceContract;
import org.vision.protos.contract.BalanceContract.UnfreezeBalanceContract;
import org.vision.protos.contract.BalanceContract.WithdrawBalanceContract;
import org.vision.protos.contract.ShieldContract.ShieldedTransferContract;
import org.vision.protos.contract.ShieldContract.SpendDescription;
import org.vision.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract.ABI;
import org.vision.protos.contract.SmartContractOuterClass.TriggerSmartContract;
import org.vision.protos.contract.WitnessContract.VoteWitnessContract;
import org.vision.protos.contract.WitnessContract.WitnessCreateContract;
import org.vision.protos.contract.WitnessContract.WitnessUpdateContract;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.commons.lang3.ArrayUtils.*;
import static org.vision.common.utils.StringUtil.encode58Check;
import static org.vision.common.utils.WalletUtil.checkPermissionOperations;
import static org.vision.core.exception.P2pException.TypeEnum.PROTOBUF_ERROR;

@Slf4j(topic = "capsule")
public class TransactionCapsule implements ProtoCapsule<Transaction> {

  private static final ExecutorService executorService = Executors
      .newFixedThreadPool(CommonParameter.getInstance()
          .getValidContractProtoThreadNum());
  private static final String OWNER_ADDRESS = "ownerAddress_";

  private Transaction transaction;
  @Setter
  private boolean isVerified = false;
  @Setter
  @Getter
  private long blockNum = -1;
  @Getter
  @Setter
  private TransactionTrace trxTrace;

  private StringBuilder toStringBuff = new StringBuilder();
  @Getter
  @Setter
  private long time;

  private byte[] ethRlpData;

  private Sha256Hash id;

  private byte[] ownerAddress;

  public byte[] getOwnerAddress() {
    if (this.ownerAddress == null) {
      this.ownerAddress = getOwner(this.transaction.getRawData().getContract(0));
    }
    return this.ownerAddress;
  }

  /**
   * constructor TransactionCapsule.
   */
  public TransactionCapsule(Transaction trx) {
    this.transaction = trx;
  }

  /**
   * get account from bytes data.
   */
  public TransactionCapsule(byte[] data) throws BadItemException {
    try {
      this.transaction = Transaction.parseFrom(Message.getCodedInputStream(data));
    } catch (Exception e) {
      throw new BadItemException("Transaction proto data parse exception");
    }
  }

  public TransactionCapsule(CodedInputStream codedInputStream) throws BadItemException {
    try {
      this.transaction = Transaction.parseFrom(codedInputStream);
    } catch (IOException e) {
      throw new BadItemException("Transaction proto data parse exception");
    }
  }

  public TransactionCapsule(AccountCreateContract contract, AccountStore accountStore) {
    AccountCapsule account = accountStore.get(contract.getOwnerAddress().toByteArray());
    if (account != null && account.getType() == contract.getType()) {
      return; // Account isexit
    }

    createTransaction(contract, ContractType.AccountCreateContract);
  }

  public TransactionCapsule(TransferContract contract, AccountStore accountStore) {

    AccountCapsule owner = accountStore.get(contract.getOwnerAddress().toByteArray());
    if (owner == null || owner.getBalance() < contract.getAmount()) {
      return; //The balance is not enough
    }

    createTransaction(contract, ContractType.TransferContract);
  }

  public TransactionCapsule(VoteWitnessContract voteWitnessContract) {
    createTransaction(voteWitnessContract, ContractType.VoteWitnessContract);
  }

  public TransactionCapsule(WitnessCreateContract witnessCreateContract) {
    createTransaction(witnessCreateContract, ContractType.WitnessCreateContract);
  }

  public TransactionCapsule(WitnessUpdateContract witnessUpdateContract) {
    createTransaction(witnessUpdateContract, ContractType.WitnessUpdateContract);
  }

  public TransactionCapsule(TransferAssetContract transferAssetContract) {
    createTransaction(transferAssetContract, ContractType.TransferAssetContract);
  }

  public TransactionCapsule(ParticipateAssetIssueContract participateAssetIssueContract) {
    createTransaction(participateAssetIssueContract, ContractType.ParticipateAssetIssueContract);
  }

  public TransactionCapsule(raw rawData, List<ByteString> signatureList) {
    this.transaction = Transaction.newBuilder().setRawData(rawData).addAllSignature(signatureList)
        .build();
  }

  @Deprecated
  public TransactionCapsule(AssetIssueContract assetIssueContract) {
    createTransaction(assetIssueContract, ContractType.AssetIssueContract);
  }

  public TransactionCapsule(com.google.protobuf.Message message, ContractType contractType) {
    Transaction.raw.Builder transactionBuilder = Transaction.raw.newBuilder().addContract(
        Transaction.Contract.newBuilder().setType(contractType).setParameter(
            (message instanceof Any ? (Any) message : Any.pack(message))).build());
    transaction = Transaction.newBuilder().setRawData(transactionBuilder.build()).build();
  }

  public static long getWeight(Permission permission, byte[] address) {
    List<Key> list = permission.getKeysList();
    for (Key key : list) {
      if (key.getAddress().equals(ByteString.copyFrom(address))) {
        return key.getWeight();
      }
    }
    return 0;
  }

  public static long checkWeight(Permission permission, List<ByteString> sigs, byte[] hash,
      List<ByteString> approveList)
      throws SignatureException, PermissionException, SignatureFormatException {
    long currentWeight = 0;
    if (sigs.size() > permission.getKeysCount()) {
      throw new PermissionException(
          "Signature count is " + (sigs.size()) + " more than key counts of permission : "
              + permission.getKeysCount());
    }
    HashMap addMap = new HashMap();
    for (ByteString sig : sigs) {
      if (sig.size() < 65) {
        throw new SignatureFormatException(
            "Signature size is " + sig.size());
      }
      String base64 = TransactionCapsule.getBase64FromByteString(sig);
      byte[] address = SignUtils
          .signatureToAddress(hash, base64, CommonParameter.getInstance().isECKeyCryptoEngine());
      long weight = getWeight(permission, address);
      if (weight == 0) {
        throw new PermissionException(
            ByteArray.toHexString(sig.toByteArray()) + " is signed by " + encode58Check(address)
                + " but it is not contained of permission.");
      }
      if (addMap.containsKey(base64)) {
        throw new PermissionException(encode58Check(address) + " has signed twice!");
      }
      addMap.put(base64, weight);
      if (approveList != null) {
        approveList.add(ByteString.copyFrom(address)); //out put approve list.
      }
      currentWeight += weight;
    }
    return currentWeight;
  }

  //make sure that contractType is validated before
  //No exception will be thrown here
  public static byte[] getShieldTransactionHashIgnoreTypeException(Transaction tx) {
    try {
      return hashShieldTransaction(tx, CommonParameter.getInstance()
          .getZenTokenId());
    } catch (ContractValidateException | InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
    }
    return null;
  }

  public static byte[] hashShieldTransaction(Transaction tx, String tokenId)
      throws ContractValidateException, InvalidProtocolBufferException {
    Any contractParameter = tx.getRawData().getContract(0).getParameter();
    if (!contractParameter.is(ShieldedTransferContract.class)) {
      throw new ContractValidateException(
          "contract type error,expected type [ShieldedTransferContract],real type["
              + contractParameter
              .getClass() + "]");
    }

    ShieldedTransferContract shieldedTransferContract = contractParameter
        .unpack(ShieldedTransferContract.class);
    ShieldedTransferContract.Builder newContract = ShieldedTransferContract.newBuilder();
    newContract.setFromAmount(shieldedTransferContract.getFromAmount());
    newContract.addAllReceiveDescription(shieldedTransferContract.getReceiveDescriptionList());
    newContract.setToAmount(shieldedTransferContract.getToAmount());
    newContract.setTransparentFromAddress(shieldedTransferContract.getTransparentFromAddress());
    newContract.setTransparentToAddress(shieldedTransferContract.getTransparentToAddress());
    for (SpendDescription spendDescription : shieldedTransferContract.getSpendDescriptionList()) {
      newContract
          .addSpendDescription(spendDescription.toBuilder().clearSpendAuthoritySignature().build());
    }

    Transaction.raw.Builder rawBuilder = tx.toBuilder()
        .getRawDataBuilder()
        .clearContract()
        .addContract(
            Transaction.Contract.newBuilder().setType(ContractType.ShieldedTransferContract)
                .setParameter(
                    Any.pack(newContract.build())).build());

    Transaction transaction = tx.toBuilder().clearRawData()
        .setRawData(rawBuilder).build();

    byte[] mergedByte = Bytes.concat(Sha256Hash
            .of(CommonParameter.getInstance().isECKeyCryptoEngine(), tokenId.getBytes()).getBytes(),
        transaction.getRawData().toByteArray());
    return Sha256Hash.of(CommonParameter
        .getInstance().isECKeyCryptoEngine(), mergedByte).getBytes();
  }

  // todo mv this static function to capsule util
  public static byte[] getOwner(Transaction.Contract contract) {
    ByteString owner;
    try {
      Any contractParameter = contract.getParameter();
      switch (contract.getType()) {
        case ShieldedTransferContract: {
          ShieldedTransferContract shieldedTransferContract = contractParameter
              .unpack(ShieldedTransferContract.class);
          if (!shieldedTransferContract.getTransparentFromAddress().isEmpty()) {
            owner = shieldedTransferContract.getTransparentFromAddress();
          } else {
            return new byte[0];
          }
          break;
        }
        case TriggerSmartContract: {
          TriggerSmartContract triggerSmartContract = contractParameter.unpack(TriggerSmartContract.class);
          if (!triggerSmartContract.getOwnerAddress().isEmpty()){
            owner = triggerSmartContract.getOwnerAddress();
          } else{
            return new byte[0];
          }
          break;
        }
        default: {
          Class<? extends GeneratedMessageV3> clazz = TransactionFactory
              .getContract(contract.getType());
          if (clazz == null) {
            logger.error("not exist {}", contract.getType());
            return new byte[0];
          }
          GeneratedMessageV3 generatedMessageV3 = contractParameter.unpack(clazz);
          owner = ReflectUtils.getFieldValue(generatedMessageV3, OWNER_ADDRESS);
          if (owner == null) {
            logger.error("not exist [{}] field,{}", OWNER_ADDRESS, clazz);
            return new byte[0];
          }
          break;
        }
      }
      return owner.toByteArray();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
      return new byte[0];
    }
  }

  public byte[] getEthRlpData(DynamicPropertiesStore dynamicPropertiesStore){
    if (this.ethRlpData != null){
      return this.ethRlpData;
    }

    try {
      Transaction.Contract contract = this.getInstance().getRawData().getContract(0);

      switch (contract.getType()){
        case CreateSmartContract: getCreateSmartContractRlpData(); break;
        case TriggerSmartContract: getTriggerSmartContractRlpData(); break;
        case TransferContract: getTransferContractRlpData(); break;
        default:
          break;
      }

      if (dynamicPropertiesStore.supportEthereumCompatibleTransactionNativeStep1()){
        switch (contract.getType()){
          case VoteWitnessContract: getVoteWitnessContractRlpData(); break;
          case WithdrawBalanceContract: getWithdrawBalanceContractRlpData(); break;
          case FreezeBalanceContract: getFreezeBalanceContractRlpData(); break;
          case UnfreezeBalanceContract: getUnfreezeBalanceContractRlpData(); break;
          case WitnessCreateContract: getWitnessCreateContractRlpData(); break;
          case WitnessUpdateContract: getWitnessUpdateContractRlpData(); break;
          case UpdateBrokerageContract: getUpdateBrokerageContractRlpData(); break;
          case ProposalApproveContract: getProposalApproveContractRlpData(); break;
          case ProposalCreateContract: getProposalCreateContractRlpData(); break;
          case ProposalDeleteContract: getProposalDeleteContractRlpData(); break;
          case AccountUpdateContract: getAccountUpdateContractRlpData(); break;
          default:
            break;
        }
      }
    } catch (Exception ex) {
      logger.error("getEthRlpData failed, {}",ex.getMessage());
    }
    return this.ethRlpData;
  }

  private void getCreateSmartContractRlpData(){
    CreateSmartContract c = ContractCapsule.getCreateSmartContractFromTransaction(this.getInstance());
    if (c != null && c.getType() == 1) {
      this.ethRlpData = c.getRlpData().toByteArray();
    }
  }

  private void getTriggerSmartContractRlpData(){
    TriggerSmartContract c = ContractCapsule.getTriggerContractFromTransaction(this.getInstance());
    if (c != null && c.getType() == 1) {
      this.ethRlpData = c.getRlpData().toByteArray();
    }
  }

  private void getTransferContractRlpData(){
    TransferContract c = ContractCapsule.getTransferContractFromTransaction(this.getInstance());
    if (c != null && c.getType() == 1) {
      this.ethRlpData = c.getRlpData().toByteArray();
    }
  }

  private void getVoteWitnessContractRlpData(){
    VoteWitnessContract c = ContractCapsule.getVoteWitnessContractFromTransaction(this.getInstance());
    if (c != null && c.getType() == 1) {
      this.ethRlpData = c.getRlpData().toByteArray();
    }
  }

  private void getWithdrawBalanceContractRlpData(){
    WithdrawBalanceContract c = ContractCapsule.getWithdrawBalanceContractFromTransaction(this.getInstance());
    if (c != null && c.getRlpType() == 1) {
      this.ethRlpData = c.getRlpData().toByteArray();
    }
  }

  private void getFreezeBalanceContractRlpData(){
    FreezeBalanceContract c = ContractCapsule.getFreezeBalanceContractFromTransaction(this.getInstance());
    if (c != null && c.getType() == 1) {
      this.ethRlpData = c.getRlpData().toByteArray();
    }
  }

  private void getUnfreezeBalanceContractRlpData(){
    UnfreezeBalanceContract c = ContractCapsule.getUnfreezeBalanceContractFromTransaction(this.getInstance());
    if (c != null && c.getType() == 1) {
      this.ethRlpData = c.getRlpData().toByteArray();
    }
  }

  private void getWitnessCreateContractRlpData(){
    WitnessCreateContract c = ContractCapsule.getWitnessCreateContractFromTransaction(this.getInstance());
    if (c != null && c.getType() == 1) {
      this.ethRlpData = c.getRlpData().toByteArray();
    }
  }

  private void getWitnessUpdateContractRlpData(){
    WitnessUpdateContract c = ContractCapsule.getWitnessUpdateContractFromTransaction(this.getInstance());
    if (c != null && c.getType() == 1) {
      this.ethRlpData = c.getRlpData().toByteArray();
    }
  }

  private void getUpdateBrokerageContractRlpData(){
    StorageContract.UpdateBrokerageContract c = ContractCapsule.getUpdateBrokerageContractFromTransaction(this.getInstance());
    if (c != null && c.getType() == 1) {
      this.ethRlpData = c.getRlpData().toByteArray();
    }
  }

  private void getProposalApproveContractRlpData(){
    ProposalContract.ProposalApproveContract c = ContractCapsule.getProposalApproveContractFromTransaction(this.getInstance());
    if (c != null && c.getType() == 1) {
      this.ethRlpData = c.getRlpData().toByteArray();
    }
  }

  private void getProposalCreateContractRlpData(){
    ProposalContract.ProposalCreateContract c = ContractCapsule.getProposalCreateContractFromTransaction(this.getInstance());
    if (c != null && c.getType() == 1) {
      this.ethRlpData = c.getRlpData().toByteArray();
    }
  }

  private void getProposalDeleteContractRlpData(){
    ProposalContract.ProposalDeleteContract c = ContractCapsule.getProposalDeleteContractFromTransaction(this.getInstance());
    if (c != null && c.getType() == 1) {
      this.ethRlpData = c.getRlpData().toByteArray();
    }
  }

  private void getAccountUpdateContractRlpData(){
    AccountContract.AccountUpdateContract c = ContractCapsule.getAccountUpdateContractFromTransaction(this.getInstance());
    if (c != null && c.getType() == 1) {
      this.ethRlpData = c.getRlpData().toByteArray();
    }
  }

  public static <T extends com.google.protobuf.Message> T parse(Class<T> clazz,
      CodedInputStream codedInputStream) throws InvalidProtocolBufferException {
    T defaultInstance = Internal.getDefaultInstance(clazz);
    return (T) defaultInstance.getParserForType().parseFrom(codedInputStream);
  }

  public static void validContractProto(List<Transaction> transactionList) throws P2pException {
    List<Future<Boolean>> futureList = new ArrayList<>();
    transactionList.forEach(transaction -> {
      Future<Boolean> future = executorService.submit(() -> {
        try {
          validContractProto(transaction.getRawData().getContract(0));
          return true;
        } catch (Exception e) {
          logger.error("{}", e.getMessage());
        }
        return false;
      });
      futureList.add(future);
    });
    for (Future<Boolean> future : futureList) {
      try {
        if (!future.get()) {
          throw new P2pException(PROTOBUF_ERROR, PROTOBUF_ERROR.getDesc());
        }
      } catch (Exception e) {
        throw new P2pException(PROTOBUF_ERROR, PROTOBUF_ERROR.getDesc());
      }
    }
  }

  public static void validContractProto(Transaction.Contract contract)
      throws InvalidProtocolBufferException, P2pException {
    Any contractParameter = contract.getParameter();
    Class clazz = TransactionFactory.getContract(contract.getType());
    if (clazz == null) {
      throw new P2pException(PROTOBUF_ERROR, PROTOBUF_ERROR.getDesc());
    }
    com.google.protobuf.Message src = contractParameter.unpack(clazz);
    com.google.protobuf.Message contractMessage = parse(clazz,
        Message.getCodedInputStream(src.toByteArray()));

    Message.compareBytes(src.toByteArray(), contractMessage.toByteArray());
  }

  // todo mv this static function to capsule util
  public static byte[] getToAddress(Transaction.Contract contract) {
    ByteString to;
    try {
      Any contractParameter = contract.getParameter();
      switch (contract.getType()) {
        case TransferContract:
          to = contractParameter.unpack(TransferContract.class).getToAddress();
          break;
        case TransferAssetContract:
          to = contractParameter.unpack(TransferAssetContract.class).getToAddress();
          break;
        case ParticipateAssetIssueContract:
          to = contractParameter.unpack(ParticipateAssetIssueContract.class).getToAddress();
          break;

        default:
          return new byte[0];
      }
      return to.toByteArray();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
      return new byte[0];
    }
  }

  // todo mv this static function to capsule util
  public static long getCallValue(Transaction.Contract contract) {
    try {
      Any contractParameter = contract.getParameter();
      switch (contract.getType()) {
        case TriggerSmartContract:
          return contractParameter.unpack(TriggerSmartContract.class).getCallValue();

        case CreateSmartContract:
          return contractParameter.unpack(CreateSmartContract.class).getNewContract()
              .getCallValue();
        default:
          return 0L;
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage());
      return 0L;
    }
  }

  public static String getBase64FromByteString(ByteString sign) {
    byte[] r = sign.substring(0, 32).toByteArray();
    byte[] s = sign.substring(32, 64).toByteArray();
    byte v = sign.byteAt(64);
    if (v < 27) {
      v += 27; //revId -> v
    }
    ECDSASignature signature = ECDSASignature.fromComponents(r, s, v);
    return signature.toBase64();
  }

  public static boolean validateSignature(Transaction transaction,
      byte[] hash, AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
      throws PermissionException, SignatureException, SignatureFormatException {
    Transaction.Contract contract = transaction.getRawData().getContractList().get(0);
    int permissionId = contract.getPermissionId();
    byte[] owner = getOwner(contract);
    AccountCapsule account = accountStore.get(owner);
    Permission permission = null;
    if (account == null) {
      if (permissionId == 0) {
        permission = AccountCapsule.getDefaultPermission(ByteString.copyFrom(owner));
      }
      if (permissionId == 2) {
        permission = AccountCapsule
            .createDefaultActivePermission(ByteString.copyFrom(owner), dynamicPropertiesStore);
      }
    } else {
      permission = account.getPermissionById(permissionId);
    }
    if (permission == null) {
      throw new PermissionException("permission isn't exit");
    }
      //check oprations
    checkPermission(permissionId, permission, contract);
    long weight = checkWeight(permission, transaction.getSignatureList(), hash, null);
    if (weight >= permission.getThreshold()) {
      return true;
    }
    return false;
  }

  public void resetResult() {
    if (this.getInstance().getRetCount() > 0) {
      this.transaction = this.getInstance().toBuilder().clearRet().build();
    }
  }

  public void setResult(TransactionResultCapsule transactionResultCapsule) {
    this.transaction = this.getInstance().toBuilder().addRet(transactionResultCapsule.getInstance())
        .build();
  }

  public void setReference(long blockNum, byte[] blockHash) {
    byte[] refBlockNum = ByteArray.fromLong(blockNum);
    Transaction.raw rawData = this.transaction.getRawData().toBuilder()
        .setRefBlockHash(ByteString.copyFrom(ByteArray.subArray(blockHash, 8, 16)))
        .setRefBlockBytes(ByteString.copyFrom(ByteArray.subArray(refBlockNum, 6, 8)))
        .build();
//    this.transaction = this.transaction.toBuilder().setRawData(rawData).build();
    setRawData(rawData);
  }

  public long getExpiration() {
    return transaction.getRawData().getExpiration();
  }

  /**
   * @param expiration must be in milliseconds format
   */
  public void setExpiration(long expiration) {
    Transaction.raw rawData = this.transaction.getRawData().toBuilder().setExpiration(expiration)
        .build();
//    this.transaction = this.transaction.toBuilder().setRawData(rawData).build();
    setRawData(rawData);
  }

  public void setTimestamp() {
    Transaction.raw rawData = this.transaction.getRawData().toBuilder()
        .setTimestamp(System.currentTimeMillis())
        .build();
//    this.transaction = this.transaction.toBuilder().setRawData(rawData).build();
    setRawData(rawData);
  }
  public void setTimestamp(long timestamp) {
    Transaction.raw rawData = this.transaction.getRawData().toBuilder()
        .setTimestamp(timestamp)
        .build();
//    this.transaction = this.transaction.toBuilder().setRawData(rawData).build();
    setRawData(rawData);
  }

  public long getTimestamp() {
    return transaction.getRawData().getTimestamp();
  }

  public void setFeeLimit(long feeLimit) {
    Transaction.raw rawData = this.transaction.getRawData().toBuilder()
            .setFeeLimit(feeLimit)
            .build();
    setRawData(rawData);
  }

  public long getFeeLimit() {
    return transaction.getRawData().getFeeLimit();
  }

  @Deprecated
  public void createTransaction(com.google.protobuf.Message message, ContractType contractType) {
    Transaction.raw.Builder transactionBuilder = Transaction.raw.newBuilder().addContract(
        Transaction.Contract.newBuilder().setType(contractType).setParameter(
            Any.pack(message)).build());
    transaction = Transaction.newBuilder().setRawData(transactionBuilder.build()).build();
  }

  public Sha256Hash getMerkleHash() {
    byte[] transBytes = this.transaction.toByteArray();
    return Sha256Hash.of(CommonParameter.getInstance().isECKeyCryptoEngine(),
        transBytes);
  }

  private Sha256Hash getRawHash() {
    return Sha256Hash.of(CommonParameter.getInstance().isECKeyCryptoEngine(),
        this.transaction.getRawData().toByteArray());
  }

  public void sign(byte[] privateKey) {
    SignInterface cryptoEngine = SignUtils
        .fromPrivate(privateKey, CommonParameter.getInstance().isECKeyCryptoEngine());
    //    String signature = cryptoEngine.signHash(getRawHash().getBytes());
    //    ByteString sig = ByteString.copyFrom(signature.getBytes());
    ByteString sig = ByteString.copyFrom(cryptoEngine.Base64toBytes(cryptoEngine
        .signHash(getTransactionId().getBytes())));
    this.transaction = this.transaction.toBuilder().addSignature(sig).build();
  }

  public void addSign(byte[] privateKey, AccountStore accountStore)
      throws PermissionException, SignatureException, SignatureFormatException {
    Transaction.Contract contract = this.transaction.getRawData().getContract(0);
    int permissionId = contract.getPermissionId();
    byte[] owner = getOwner(contract);
    AccountCapsule account = accountStore.get(owner);
    if (account == null) {
      throw new PermissionException("Account is not exist!");
    }
    Permission permission = account.getPermissionById(permissionId);
    if (permission == null) {
      throw new PermissionException("permission isn't exit");
    }
      //check oprations
    checkPermission(permissionId, permission, contract);
    List<ByteString> approveList = new ArrayList<>();
    SignInterface cryptoEngine = SignUtils
        .fromPrivate(privateKey, CommonParameter.getInstance().isECKeyCryptoEngine());
    byte[] address = cryptoEngine.getAddress();
    if (this.transaction.getSignatureCount() > 0) {
      checkWeight(permission, this.transaction.getSignatureList(), this.getTransactionId().getBytes(),
          approveList);
      if (approveList.contains(ByteString.copyFrom(address))) {
        throw new PermissionException(encode58Check(address) + " had signed!");
      }
    }

    long weight = getWeight(permission, address);
    if (weight == 0) {
      throw new PermissionException(
          ByteArray.toHexString(privateKey) + "'s address is " + encode58Check(address)
              + " but it is not contained of permission.");
    }
    //    String signature = cryptoEngine.signHash(getRawHash().getBytes());
    ByteString sig = ByteString.copyFrom(cryptoEngine.Base64toBytes(cryptoEngine
        .signHash(getTransactionId().getBytes())));
    this.transaction = this.transaction.toBuilder().addSignature(sig).build();
  }
  private static void checkPermission(int permissionId, Permission permission, Transaction.Contract contract) throws PermissionException {
    if (permissionId != 0) {
      if (permission.getType() != PermissionType.Active) {
        throw new PermissionException("Permission type is error");
      }
      //check operations
      if (!checkPermissionOperations(permission, contract)) {
        throw new PermissionException("Permission denied");
      }
    }
  }

  /**
   * validate eth signature validateEthSignature TriggerSmartContract
   */
  public boolean validateEthSignature(AccountStore accountStore,
                                      DynamicPropertiesStore dynamicPropertiesStore,
                                      TriggerSmartContract contract)
          throws ValidateSignatureException {
    if (!isVerified) {
      if (dynamicPropertiesStore.getAllowEthereumCompatibleTransaction() == 0){
        throw new ValidateSignatureException("EthereumCompatibleTransaction is off, need to be opened by proposal");
      }
      if (this.transaction.getSignatureCount() <= 0
              || this.transaction.getRawData().getContractCount() <= 0) {
        throw new ValidateSignatureException("miss sig or contract");
      }
      if (this.transaction.getSignatureCount() != 1) {
        throw new ValidateSignatureException("eth contract only one signature");
      }
      if (contract.getType() != 1) {
        throw new ValidateSignatureException("not eth contract");
      }

      EthTrx t = new EthTrx(contract.getRlpData().toByteArray());
      t.rlpParse();
      try {
        TriggerSmartContract contractFromParse = t.rlpParseToTriggerSmartContract(dynamicPropertiesStore);
        if(!contractFromParse.equals(contract)){
          isVerified = false;
          throw new ValidateSignatureException("eth sig error, vision transaction have been changed,not equal rlp parsed transaction");
        }
        if (!validateSignature(this.transaction, t.getRawHash(), accountStore, dynamicPropertiesStore)) {
          isVerified = false;
          throw new ValidateSignatureException("eth sig error");
        }
      } catch (SignatureException | PermissionException | SignatureFormatException e) {
        isVerified = false;
        throw new ValidateSignatureException(e.getMessage());
      }
      isVerified = true;
    }
    return true;
  }

  /**
   * validate eth signature validateEthSignature CreateSmartContract
   */
  public boolean validateEthSignature(AccountStore accountStore,
                                      DynamicPropertiesStore dynamicPropertiesStore,
                                      CreateSmartContract contract)
          throws ValidateSignatureException {
    if (!isVerified) {
      if (dynamicPropertiesStore.getAllowEthereumCompatibleTransaction() == 0){
        throw new ValidateSignatureException("EthereumCompatibleTransaction is off, need to be opened by proposal");
      }
      if (this.transaction.getSignatureCount() <= 0
              || this.transaction.getRawData().getContractCount() <= 0) {
        throw new ValidateSignatureException("miss sig or contract");
      }
      if (this.transaction.getSignatureCount() != 1) {
        throw new ValidateSignatureException("eth contract only one signature");
      }
      if (contract.getType() != 1) {
        throw new ValidateSignatureException("not eth contract");
      }

      EthTrx t = new EthTrx(contract.getRlpData().toByteArray());
      t.rlpParse();
      try {
        CreateSmartContract contractFromParse = t.rlpParseToDeployContract(dynamicPropertiesStore);
        if(!contractFromParse.equals(contract)){
          isVerified = false;
          throw new ValidateSignatureException("eth sig error, vision transaction have been changed,not equal rlp parsed transaction");
        }
        if (!validateSignature(this.transaction, t.getRawHash(), accountStore, dynamicPropertiesStore)) {
          isVerified = false;
          throw new ValidateSignatureException("eth sig error");
        }
      } catch (SignatureException | PermissionException | SignatureFormatException e) {
        isVerified = false;
        throw new ValidateSignatureException(e.getMessage());
      }
      isVerified = true;
    }
    return true;
  }

  /**
   * validate eth signature validateEthSignature TransferContract
   */
  public boolean validateEthSignature(AccountStore accountStore,
                                      DynamicPropertiesStore dynamicPropertiesStore,
                                      TransferContract contract)
          throws ValidateSignatureException {
    if (!isVerified) {
      if (dynamicPropertiesStore.getAllowEthereumCompatibleTransaction() == 0){
        throw new ValidateSignatureException("EthereumCompatibleTransaction is off, need to be opened by proposal");
      }
      if (this.transaction.getSignatureCount() <= 0
              || this.transaction.getRawData().getContractCount() <= 0) {
        throw new ValidateSignatureException("miss sig or contract");
      }
      if (this.transaction.getSignatureCount() != 1) {
        throw new ValidateSignatureException("eth contract only one signature");
      }
      if (contract.getType() != 1) {
        throw new ValidateSignatureException("not eth contract");
      }

      EthTrx t = new EthTrx(contract.getRlpData().toByteArray());
      t.rlpParse();
      try {
        TransferContract contractFromParse = t.rlpParseToTransferContract();
        if(!contractFromParse.equals(contract)){
          isVerified = false;
          throw new ValidateSignatureException("eth sig error, vision transaction have been changed,not equal rlp parsed transaction");
        }
        if (!validateSignature(this.transaction, t.getRawHash(), accountStore, dynamicPropertiesStore)) {
          isVerified = false;
          throw new ValidateSignatureException("eth sig error");
        }
      } catch (SignatureException | PermissionException | SignatureFormatException e) {
        isVerified = false;
        throw new ValidateSignatureException(e.getMessage());
      }
      isVerified = true;
    }
    return true;
  }

  /**
   * validate eth signature validateEthSignature WithdrawBalanceContract
   */
  public boolean validateEthSignature(AccountStore accountStore,
                                      DynamicPropertiesStore dynamicPropertiesStore,
                                      BalanceContract.WithdrawBalanceContract contract)
          throws ValidateSignatureException {
    if (!isVerified) {
      validateAllowEthereumCompatibleTransaction(dynamicPropertiesStore);
      validateEthSignatureCount();
      if (contract.getRlpType() != 1) {
        throw new ValidateSignatureException("not eth contract");
      }

      EthTrx t = new EthTrx(contract.getRlpData().toByteArray());
      t.rlpParse();
      try {
        BalanceContract.WithdrawBalanceContract contractFromParse = t.rlpParseToWithdrawBalanceContract();
        if(!contractFromParse.equals(contract)){
          isVerified = false;
          throw new ValidateSignatureException("eth sig error, vision transaction have been changed,not equal rlp parsed transaction");
        }
        if (!validateSignature(this.transaction, t.getRawHash(), accountStore, dynamicPropertiesStore)) {
          isVerified = false;
          throw new ValidateSignatureException("eth sig error");
        }
      } catch (SignatureException | PermissionException | SignatureFormatException e) {
        isVerified = false;
        throw new ValidateSignatureException(e.getMessage());
      }
      isVerified = true;
    }
    return true;
  }

  public boolean validateEthSignature(AccountStore accountStore,
                                      DynamicPropertiesStore dynamicPropertiesStore,
                                      BalanceContract.FreezeBalanceContract contract)
          throws ValidateSignatureException {
    if (!isVerified) {
      validateAllowEthereumCompatibleTransaction(dynamicPropertiesStore);
      validateEthSignatureCount();
      if (contract.getType() != 1) {
        throw new ValidateSignatureException("not eth contract");
      }

      EthTrx t = new EthTrx(contract.getRlpData().toByteArray());
      t.rlpParse();
      try {
        BalanceContract.FreezeBalanceContract contractFromParse = t.rlpParseToFreezeBalanceContract();
        if(!contractFromParse.equals(contract)){
          isVerified = false;
          throw new ValidateSignatureException("eth sig error, vision transaction have been changed,not equal rlp parsed transaction");
        }
        if (!validateSignature(this.transaction, t.getRawHash(), accountStore, dynamicPropertiesStore)) {
          isVerified = false;
          throw new ValidateSignatureException("eth sig error");
        }
      } catch (SignatureException | PermissionException | SignatureFormatException e) {
        isVerified = false;
        throw new ValidateSignatureException(e.getMessage());
      }
      isVerified = true;
    }
    return true;
  }

  public boolean validateEthSignature(AccountStore accountStore,
                                      DynamicPropertiesStore dynamicPropertiesStore,
                                      BalanceContract.UnfreezeBalanceContract contract)
          throws ValidateSignatureException {
    if (!isVerified) {
      validateAllowEthereumCompatibleTransaction(dynamicPropertiesStore);
      validateEthSignatureCount();
      if (contract.getType() != 1) {
        throw new ValidateSignatureException("not eth contract");
      }

      EthTrx t = new EthTrx(contract.getRlpData().toByteArray());
      t.rlpParse();
      try {
        BalanceContract.UnfreezeBalanceContract contractFromParse = t.rlpParseToUnfreezeBalanceContract();
        if(!contractFromParse.equals(contract)){
          isVerified = false;
          throw new ValidateSignatureException("eth sig error, vision transaction have been changed,not equal rlp parsed transaction");
        }
        if (!validateSignature(this.transaction, t.getRawHash(), accountStore, dynamicPropertiesStore)) {
          isVerified = false;
          throw new ValidateSignatureException("eth sig error");
        }
      } catch (SignatureException | PermissionException | SignatureFormatException e) {
        isVerified = false;
        throw new ValidateSignatureException(e.getMessage());
      }
      isVerified = true;
    }
    return true;
  }

  public boolean validateEthSignature(AccountStore accountStore,
                                      DynamicPropertiesStore dynamicPropertiesStore,
                                      VoteWitnessContract contract)
          throws ValidateSignatureException {
    if (!isVerified) {
      validateAllowEthereumCompatibleTransaction(dynamicPropertiesStore);
      validateEthSignatureCount();

      if (contract.getType() != 1) {
        throw new ValidateSignatureException("not eth contract");
      }

      EthTrx t = new EthTrx(contract.getRlpData().toByteArray());
      t.rlpParse();
      try {
        VoteWitnessContract contractFromParse = t.rlpParseToVoteWitnessContract();
        if(!contractFromParse.equals(contract)){
          isVerified = false;
          throw new ValidateSignatureException("eth sig error, vision transaction have been changed,not equal rlp parsed transaction");
        }
        if (!validateSignature(this.transaction, t.getRawHash(), accountStore, dynamicPropertiesStore)) {
          isVerified = false;
          throw new ValidateSignatureException("eth sig error");
        }
      } catch (SignatureException | PermissionException | SignatureFormatException e) {
        isVerified = false;
        throw new ValidateSignatureException(e.getMessage());
      }
      isVerified = true;
    }
    return true;
  }

  public boolean validateEthSignature(AccountStore accountStore,
                                      DynamicPropertiesStore dynamicPropertiesStore,
                                      WitnessCreateContract contract)
          throws ValidateSignatureException {
    if (!isVerified) {
      validateAllowEthereumCompatibleTransaction(dynamicPropertiesStore);
      validateEthSignatureCount();

      if (contract.getType() != 1) {
        throw new ValidateSignatureException("not eth contract");
      }

      EthTrx t = new EthTrx(contract.getRlpData().toByteArray());
      t.rlpParse();
      try {
        WitnessCreateContract contractFromParse = t.rlpParseToWitnessCreateContract();
        if(!contractFromParse.equals(contract)){
          isVerified = false;
          throw new ValidateSignatureException("eth sig error, vision transaction have been changed,not equal rlp parsed transaction");
        }
        if (!validateSignature(this.transaction, t.getRawHash(), accountStore, dynamicPropertiesStore)) {
          isVerified = false;
          throw new ValidateSignatureException("eth sig error");
        }
      } catch (SignatureException | PermissionException | SignatureFormatException e) {
        isVerified = false;
        throw new ValidateSignatureException(e.getMessage());
      }
      isVerified = true;
    }
    return true;
  }

  public boolean validateEthSignature(AccountStore accountStore,
                                      DynamicPropertiesStore dynamicPropertiesStore,
                                      WitnessUpdateContract contract)
          throws ValidateSignatureException {
    if (!isVerified) {
      validateAllowEthereumCompatibleTransaction(dynamicPropertiesStore);
      validateEthSignatureCount();

      if (contract.getType() != 1) {
        throw new ValidateSignatureException("not eth contract");
      }

      EthTrx t = new EthTrx(contract.getRlpData().toByteArray());
      t.rlpParse();
      try {
        WitnessUpdateContract contractFromParse = t.rlpParseToWitnessUpdateContract();
        if(!contractFromParse.equals(contract)){
          isVerified = false;
          throw new ValidateSignatureException("eth sig error, vision transaction have been changed,not equal rlp parsed transaction");
        }
        if (!validateSignature(this.transaction, t.getRawHash(), accountStore, dynamicPropertiesStore)) {
          isVerified = false;
          throw new ValidateSignatureException("eth sig error");
        }
      } catch (SignatureException | PermissionException | SignatureFormatException e) {
        isVerified = false;
        throw new ValidateSignatureException(e.getMessage());
      }
      isVerified = true;
    }
    return true;
  }

  public boolean validateEthSignature(AccountStore accountStore,
                                      DynamicPropertiesStore dynamicPropertiesStore,
                                      StorageContract.UpdateBrokerageContract contract)
          throws ValidateSignatureException {
    if (!isVerified) {
      validateAllowEthereumCompatibleTransaction(dynamicPropertiesStore);
      validateEthSignatureCount();

      if (contract.getType() != 1) {
        throw new ValidateSignatureException("not eth contract");
      }

      EthTrx t = new EthTrx(contract.getRlpData().toByteArray());
      t.rlpParse();
      try {
        StorageContract.UpdateBrokerageContract contractFromParse = t.rlpParseToUpdateBrokerageContract();
        if(!contractFromParse.equals(contract)){
          isVerified = false;
          throw new ValidateSignatureException("eth sig error, vision transaction have been changed,not equal rlp parsed transaction");
        }
        if (!validateSignature(this.transaction, t.getRawHash(), accountStore, dynamicPropertiesStore)) {
          isVerified = false;
          throw new ValidateSignatureException("eth sig error");
        }
      } catch (SignatureException | PermissionException | SignatureFormatException e) {
        isVerified = false;
        throw new ValidateSignatureException(e.getMessage());
      }
      isVerified = true;
    }
    return true;
  }

  public boolean validateEthSignature(AccountStore accountStore,
                                      DynamicPropertiesStore dynamicPropertiesStore,
                                      ProposalContract.ProposalApproveContract contract)
          throws ValidateSignatureException {
    if (!isVerified) {
      validateAllowEthereumCompatibleTransaction(dynamicPropertiesStore);
      validateEthSignatureCount();

      if (contract.getType() != 1) {
        throw new ValidateSignatureException("not eth contract");
      }

      EthTrx t = new EthTrx(contract.getRlpData().toByteArray());
      t.rlpParse();
      try {
        ProposalContract.ProposalApproveContract contractFromParse = t.rlpParseToProposalApproveContract();
        if(!contractFromParse.equals(contract)){
          isVerified = false;
          throw new ValidateSignatureException("eth sig error, vision transaction have been changed,not equal rlp parsed transaction");
        }
        if (!validateSignature(this.transaction, t.getRawHash(), accountStore, dynamicPropertiesStore)) {
          isVerified = false;
          throw new ValidateSignatureException("eth sig error");
        }
      } catch (SignatureException | PermissionException | SignatureFormatException e) {
        isVerified = false;
        throw new ValidateSignatureException(e.getMessage());
      }
      isVerified = true;
    }
    return true;
  }

  public boolean validateEthSignature(AccountStore accountStore,
                                      DynamicPropertiesStore dynamicPropertiesStore,
                                      ProposalContract.ProposalCreateContract contract)
          throws ValidateSignatureException {
    if (!isVerified) {
      validateAllowEthereumCompatibleTransaction(dynamicPropertiesStore);
      validateEthSignatureCount();

      if (contract.getType() != 1) {
        throw new ValidateSignatureException("not eth contract");
      }

      EthTrx t = new EthTrx(contract.getRlpData().toByteArray());
      t.rlpParse();
      try {
        ProposalContract.ProposalCreateContract contractFromParse = t.rlpParseToProposalCreateContract();
        if(!contractFromParse.equals(contract)){
          isVerified = false;
          throw new ValidateSignatureException("eth sig error, vision transaction have been changed,not equal rlp parsed transaction");
        }
        if (!validateSignature(this.transaction, t.getRawHash(), accountStore, dynamicPropertiesStore)) {
          isVerified = false;
          throw new ValidateSignatureException("eth sig error");
        }
      } catch (SignatureException | PermissionException | SignatureFormatException e) {
        isVerified = false;
        throw new ValidateSignatureException(e.getMessage());
      }
      isVerified = true;
    }
    return true;
  }

  public boolean validateEthSignature(AccountStore accountStore,
                                      DynamicPropertiesStore dynamicPropertiesStore,
                                      ProposalContract.ProposalDeleteContract contract)
          throws ValidateSignatureException {
    if (!isVerified) {
      validateAllowEthereumCompatibleTransaction(dynamicPropertiesStore);
      validateEthSignatureCount();

      if (contract.getType() != 1) {
        throw new ValidateSignatureException("not eth contract");
      }

      EthTrx t = new EthTrx(contract.getRlpData().toByteArray());
      t.rlpParse();
      try {
        ProposalContract.ProposalDeleteContract contractFromParse = t.rlpParseToProposalDeleteContract();
        if(!contractFromParse.equals(contract)){
          isVerified = false;
          throw new ValidateSignatureException("eth sig error, vision transaction have been changed,not equal rlp parsed transaction");
        }
        if (!validateSignature(this.transaction, t.getRawHash(), accountStore, dynamicPropertiesStore)) {
          isVerified = false;
          throw new ValidateSignatureException("eth sig error");
        }
      } catch (SignatureException | PermissionException | SignatureFormatException e) {
        isVerified = false;
        throw new ValidateSignatureException(e.getMessage());
      }
      isVerified = true;
    }
    return true;
  }

  public boolean validateEthSignature(AccountStore accountStore,
                                      DynamicPropertiesStore dynamicPropertiesStore,
                                      AccountContract.AccountUpdateContract contract)
          throws ValidateSignatureException {
    if (!isVerified) {
      validateAllowEthereumCompatibleTransaction(dynamicPropertiesStore);
      validateEthSignatureCount();

      if (contract.getType() != 1) {
        throw new ValidateSignatureException("not eth contract");
      }

      EthTrx t = new EthTrx(contract.getRlpData().toByteArray());
      t.rlpParse();
      try {
        AccountContract.AccountUpdateContract contractFromParse = t.rlpParseToAccountUpdateContract();
        if(!contractFromParse.equals(contract)){
          isVerified = false;
          throw new ValidateSignatureException("eth sig error, vision transaction have been changed,not equal rlp parsed transaction");
        }
        if (!validateSignature(this.transaction, t.getRawHash(), accountStore, dynamicPropertiesStore)) {
          isVerified = false;
          throw new ValidateSignatureException("eth sig error");
        }
      } catch (SignatureException | PermissionException | SignatureFormatException e) {
        isVerified = false;
        throw new ValidateSignatureException(e.getMessage());
      }
      isVerified = true;
    }
    return true;
  }

  private void validateAllowEthereumCompatibleTransaction(DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    if (dynamicPropertiesStore.getAllowEthereumCompatibleTransaction() == 0){
      throw new ValidateSignatureException("EthereumCompatibleTransaction is off, need to be opened by proposal");
    }
  }

  private void validateEthSignatureCount() throws ValidateSignatureException {
    if (this.transaction.getSignatureCount() <= 0
            || this.transaction.getRawData().getContractCount() <= 0) {
      throw new ValidateSignatureException("miss sig or contract");
    }

    if (this.transaction.getSignatureCount() != 1) {
      throw new ValidateSignatureException("eth contract only one signature");
    }
  }

  public static class EthTrx {
    private static final BigInteger DEFAULT_GAS_PRICE = new BigInteger("10000000000000");
    private static final BigInteger DEFAULT_BALANCE_GAS = new BigInteger("21000");
    public static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};

    public static final int HASH_LENGTH = 32;
    public static final int ADDRESS_LENGTH = 20;

    public static final int VALUE_SIZE = 64;

    /**
     * 64-bit address with prefix 000000000000000000000000
     */
    public static final String ADDRESS_PREFIX_0 = "000000000000000000000000";

    /* SHA3 hash of the RLP encoded transaction */
    private byte[] hash;

    /* a counter used to make sure each transaction can only be processed once */
    private byte[] nonce;

    /* the amount of ether to transfer (calculated as wei) */
    private byte[] value;

    /* the address of the destination account
     * In creation transaction the receive address is - 0 */
    private byte[] receiveAddress;

    /* the amount of ether to pay as a transaction fee
     * to the miner for each unit of gas */
    private byte[] gasPrice;

    /* the amount of "gas" to allow for the computation.
     * Gas is the fuel of the computational engine;
     * every computational step taken and every byte added
     * to the state or transaction list consumes some gas. */
    private byte[] gasLimit;

    /* An unlimited size byte array specifying
     * input [data] of the message call or
     * Initialization code for a new contract */
    private byte[] data;

    /**
     * Since EIP-155, we could encode chainId in V
     */
    private static final int CHAIN_ID_INC = 35;
    private static final int LOWER_REAL_V = 27;
    private Integer chainId = null;

    /* the elliptic curve signature
     * (including public key recovery bits) */
    private ECDSASignature signature;

    protected byte[] sendAddress;

    /* Tx in encoded form */
    protected byte[] rlpEncoded;
    private byte[] rawHash;
    /* Indicates if this transaction has been parsed
     * from the RLP-encoded data */
    protected boolean parsed = false;

    public EthTrx(byte[] rawData) {
      this.rlpEncoded = rawData;
      parsed = false;
    }

    public EthTrx(byte[] nonce, byte[] gasPrice, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data,
                       Integer chainId) {
      this.nonce = nonce;
      this.gasPrice = gasPrice;
      this.gasLimit = gasLimit;
      this.receiveAddress = receiveAddress;
      if (ByteUtil.isSingleZero(value)) {
        this.value = EMPTY_BYTE_ARRAY;
      } else {
        this.value = value;
      }
      this.data = data;
      this.chainId = chainId;

      if (receiveAddress == null) {
        this.receiveAddress = ByteUtil.EMPTY_BYTE_ARRAY;
      }

      parsed = true;
    }

    /**
     * Warning: this transaction would not be protected by replay-attack protection mechanism
     * Use {@link EthTrx#EthTrx(byte[], byte[], byte[], byte[], byte[], byte[], Integer)} constructor instead
     * and specify the desired chainID
     */
    public EthTrx(byte[] nonce, byte[] gasPrice, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data) {
      this(nonce, gasPrice, gasLimit, receiveAddress, value, data, null);
    }

    public EthTrx(byte[] nonce, byte[] gasPrice, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data,
                       byte[] r, byte[] s, byte v, Integer chainId) {
      this(nonce, gasPrice, gasLimit, receiveAddress, value, data, chainId);
      this.signature = ECDSASignature.fromComponents(r, s, v);
    }

    /**
     * Warning: this transaction would not be protected by replay-attack protection mechanism
     * Use {@link EthTrx#EthTrx(byte[], byte[], byte[], byte[], byte[], byte[], byte[], byte[], byte, Integer)}
     * constructor instead and specify the desired chainID
     */
    public EthTrx(byte[] nonce, byte[] gasPrice, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data,
                       byte[] r, byte[] s, byte v) {
      this(nonce, gasPrice, gasLimit, receiveAddress, value, data, r, s, v, null);
    }


    private Integer extractChainIdFromRawSignature(BigInteger bv, byte[] r, byte[] s) {
      if (r == null && s == null) return bv.intValue();  // EIP 86
      if (bv.bitLength() > 31) return Integer.MAX_VALUE; // chainId is limited to 31 bits, longer are not valid for now
      long v = bv.longValue();
      if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) return null;
      return (int) ((v - CHAIN_ID_INC) / 2);
    }

    private byte getRealV(BigInteger bv) {
      if (bv.bitLength() > 31) return 0; // chainId is limited to 31 bits, longer are not valid for now
      long v = bv.longValue();
      if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) return (byte) v;
      byte realV = LOWER_REAL_V;
      int inc = 0;
      if ((int) v % 2 == 0) inc = 1;
      return (byte) (realV + inc);
    }

//    public long transactionCost(BlockchainNetConfig config, Block block){
//
//      rlpParse();
//
//      return config.getConfigForBlock(block.getNumber()).
//              getTransactionCost(this);
//    }

    public synchronized void verify() {
      rlpParse();
      validate();
    }

    public synchronized void rlpParse() {
      if (parsed) return;
      try {
        RLPList decodedTxList = RLP.decode2(rlpEncoded);
        RLPList transaction = (RLPList) decodedTxList.get(0);

        // Basic verification
        if (transaction.size() > 9 ) throw new RuntimeException("Too many RLP elements");
        for (RLPElement rlpElement : transaction) {
          if (!(rlpElement instanceof RLPItem))
            throw new RuntimeException("Transaction RLP elements shouldn't be lists");
        }

        this.nonce = transaction.get(0).getRLPData();
        this.gasPrice = transaction.get(1).getRLPData();
        this.gasLimit = transaction.get(2).getRLPData();
        this.receiveAddress = transaction.get(3).getRLPData();
        this.value = transaction.get(4).getRLPData();
        this.data = transaction.get(5).getRLPData();
        // only parse signature in case tx is signed
        if (transaction.get(6).getRLPData() != null) {
          byte[] vData =  transaction.get(6).getRLPData();
          BigInteger v = ByteUtil.bytesToBigInteger(vData);
          byte[] r = transaction.get(7).getRLPData();
          byte[] s = transaction.get(8).getRLPData();
          this.chainId = extractChainIdFromRawSignature(v, r, s);
          if (r != null && s != null) {
            this.signature = ECDSASignature.fromComponents(r, s, getRealV(v));
          }
        } else {
          logger.debug("RLP encoded tx is not signed!");
        }
        this.hash = Hash.sha3(rlpEncoded);
        this.parsed = true;
      } catch (Exception e) {
        throw new RuntimeException("Error on parsing RLP", e);
      }
    }

    private void validate() {
      if (getNonce().length > HASH_LENGTH) throw new RuntimeException("Nonce is not valid");
      if (receiveAddress != null && receiveAddress.length != 0 && receiveAddress.length != ADDRESS_LENGTH)
        throw new RuntimeException("Receive address is not valid");
      if (gasLimit.length > HASH_LENGTH)
        throw new RuntimeException("Gas Limit is not valid");
      if (gasPrice != null && gasPrice.length > HASH_LENGTH)
        throw new RuntimeException("Gas Price is not valid");
      if (value != null  && value.length > HASH_LENGTH)
        throw new RuntimeException("Value is not valid");
      if (getSignature() != null) {
        if (BigIntegers.asUnsignedByteArray(signature.r).length > HASH_LENGTH)
          throw new RuntimeException("Signature R is not valid");
        if (BigIntegers.asUnsignedByteArray(signature.s).length > HASH_LENGTH)
          throw new RuntimeException("Signature S is not valid");
        if (getSender() != null && getSender().length != ADDRESS_LENGTH)
          throw new RuntimeException("Sender is not valid");
      }
    }

    public boolean isParsed() {
      return parsed;
    }

    public byte[] getHash() {
      if (!isEmpty(hash)) return hash;
      rlpParse();
      getEncoded();
      return hash;
    }

    public byte[] getRawHash() {
      rlpParse();
      if (rawHash != null) return rawHash;
      byte[] plainMsg = this.getEncodedRaw();
      return rawHash = Hash.sha3(plainMsg);
    }


    public byte[] getNonce() {
      rlpParse();

      return nonce == null ? ZERO_BYTE_ARRAY : nonce;
    }

    protected void setNonce(byte[] nonce) {
      this.nonce = nonce;
      parsed = true;
    }

    public boolean isValueTx() {
      rlpParse();
      return value != null;
    }

    public byte[] getValue() {
      rlpParse();
      return value == null ? ZERO_BYTE_ARRAY : value;
    }

    protected void setValue(byte[] value) {
      this.value = value;
      parsed = true;
    }

    public byte[] getReceiveAddress() {
      rlpParse();
      return receiveAddress;
    }

    protected void setReceiveAddress(byte[] receiveAddress) {
      this.receiveAddress = receiveAddress;
      parsed = true;
    }

    public byte[] getGasPrice() {
      rlpParse();
      return gasPrice == null ? ZERO_BYTE_ARRAY : gasPrice;
    }

    protected void setGasPrice(byte[] gasPrice) {
      this.gasPrice = gasPrice;
      parsed = true;
    }

    public byte[] getGasLimit() {
      rlpParse();
      return gasLimit == null ? ZERO_BYTE_ARRAY : gasLimit;
    }

    protected void setGasLimit(byte[] gasLimit) {
      this.gasLimit = gasLimit;
      parsed = true;
    }

    public long nonZeroDataBytes() {
      if (data == null) return 0;
      int counter = 0;
      for (final byte aData : data) {
        if (aData != 0) ++counter;
      }
      return counter;
    }

    public long zeroDataBytes() {
      if (data == null) return 0;
      int counter = 0;
      for (final byte aData : data) {
        if (aData == 0) ++counter;
      }
      return counter;
    }


    public byte[] getData() {
      rlpParse();
      return data;
    }

    protected void setData(byte[] data) {
      this.data = data;
      parsed = true;
    }

    public ECDSASignature getSignature() {
      rlpParse();
      return signature;
    }

//    public byte[] getContractAddress() {
//      if (!isContractCreation()) return null;
//      return Hash.sha3omit12(this.getSender());
//    }

    public boolean isContractCreation() {
      rlpParse();
      return this.receiveAddress == null || Arrays.equals(this.receiveAddress,ByteUtil.EMPTY_BYTE_ARRAY);
    }

    /*
     * Crypto
     */

    public ECKey getKey() {
      byte[] hash = getRawHash();
      return ECKey.recoverFromSignature(signature.v, signature, hash);
    }

    public synchronized byte[] getSender() {
      try {
        if (sendAddress == null && getSignature() != null) {
          sendAddress = ECKey.signatureToAddress(getRawHash(), getSignature());
        }
        return sendAddress;
      } catch (SignatureException e) {
        logger.error(e.getMessage(), e);
      }
      return null;
    }

    public Integer getChainId() {
      rlpParse();
      return chainId == null ? null : (int) chainId;
    }

    /**
     * @deprecated should prefer #sign(ECKey) over this method
     */
    public void sign(byte[] privKeyBytes) throws ECKey.MissingPrivateKeyException {
      sign(ECKey.fromPrivate(privKeyBytes));
    }

    public void sign(ECKey key) throws ECKey.MissingPrivateKeyException {
      this.signature = key.sign(this.getRawHash());
      this.rlpEncoded = null;
    }

    @Override
    public String toString() {
      return toString(Integer.MAX_VALUE);
    }

    public String toString(int maxDataSize) {
      rlpParse();
      String dataS;
      if (data == null) {
        dataS = "";
      } else if (data.length < maxDataSize) {
        dataS = ByteArray.toHexString(data);
      } else {
        dataS = ByteArray.toHexString(Arrays.copyOfRange(data, 0, maxDataSize)) +
                "... (" + data.length + " bytes)";
      }
      return "TransactionData [" + "hash=" + ByteArray.toHexString(hash) +
              "  nonce=" + ByteArray.toHexString(nonce) +
              ", gasPrice=" + ByteArray.toHexString(gasPrice) +
              ", gas=" + ByteArray.toHexString(gasLimit) +
              ", receiveAddress=" + ByteArray.toHexString(receiveAddress) +
              ", sendAddress=" + ByteArray.toHexString(getSender())  +
              ", value=" + ByteArray.toHexString(value) +
              ", data=" + dataS +
              ", signatureV=" + (signature == null ? "" : signature.v) +
              ", signatureR=" + (signature == null ? "" : ByteArray.toHexString(BigIntegers.asUnsignedByteArray(signature.r))) +
              ", signatureS=" + (signature == null ? "" : ByteArray.toHexString(BigIntegers.asUnsignedByteArray(signature.s))) +
              "]";
    }

    /**
     * For signatures you have to keep also
     * RLP of the transaction without any signature data
     */
    public byte[] getEncodedRaw() {

      rlpParse();
      byte[] rlpRaw;

      // parse null as 0 for nonce
      byte[] nonce = null;
      if (this.nonce == null || this.nonce.length == 1 && this.nonce[0] == 0) {
        nonce = RLP.encodeElement(null);
      } else {
        nonce = RLP.encodeElement(this.nonce);
      }
      byte[] gasPrice = RLP.encodeElement(this.gasPrice);
      byte[] gasLimit = RLP.encodeElement(this.gasLimit);
      byte[] receiveAddress = RLP.encodeElement(this.receiveAddress);
      byte[] value = RLP.encodeElement(this.value);
      byte[] data = RLP.encodeElement(this.data);

      // Since EIP-155 use chainId for v
      if (chainId == null) {
        rlpRaw = RLP.encodeList(nonce, gasPrice, gasLimit, receiveAddress,
                value, data);
      } else {
        byte[] v, r, s;
        v = RLP.encodeInt(chainId);
        r = RLP.encodeElement(EMPTY_BYTE_ARRAY);
        s = RLP.encodeElement(EMPTY_BYTE_ARRAY);
        rlpRaw = RLP.encodeList(nonce, gasPrice, gasLimit, receiveAddress,
                value, data, v, r, s);
      }
      return rlpRaw;
    }

    public synchronized byte[] getEncoded() {

      if (rlpEncoded != null) return rlpEncoded;

      // parse null as 0 for nonce
      byte[] nonce = null;
      if (this.nonce == null || this.nonce.length == 1 && this.nonce[0] == 0) {
        nonce = RLP.encodeElement(null);
      } else {
        nonce = RLP.encodeElement(this.nonce);
      }
      byte[] gasPrice = RLP.encodeElement(this.gasPrice);
      byte[] gasLimit = RLP.encodeElement(this.gasLimit);
      byte[] receiveAddress = RLP.encodeElement(this.receiveAddress);
      byte[] value = RLP.encodeElement(this.value);
      byte[] data = RLP.encodeElement(this.data);

      byte[] v, r, s;

      if (signature != null) {
        int encodeV;
        if (chainId == null) {
          encodeV = signature.v;
        } else {
          encodeV = signature.v - LOWER_REAL_V;
          encodeV += chainId * 2 + CHAIN_ID_INC;
        }
        v = RLP.encodeInt(encodeV);
        r = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.r));
        s = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.s));
      } else {
        // Since EIP-155 use chainId for v
        v = chainId == null ? RLP.encodeElement(EMPTY_BYTE_ARRAY) : RLP.encodeInt(chainId);
        r = RLP.encodeElement(EMPTY_BYTE_ARRAY);
        s = RLP.encodeElement(EMPTY_BYTE_ARRAY);
      }

      this.rlpEncoded = RLP.encodeList(nonce, gasPrice, gasLimit,
              receiveAddress, value, data, v, r, s);

      this.hash = Hash.sha3(rlpEncoded);

      return rlpEncoded;
    }

    @Override
    public int hashCode() {

      byte[] hash = this.getHash();
      int hashCode = 0;

      for (int i = 0; i < hash.length; ++i) {
        hashCode += hash[i] * i;
      }

      return hashCode;
    }

    @Override
    public boolean equals(Object obj) {

      if (!(obj instanceof EthTrx)) return false;
      EthTrx tx = (EthTrx) obj;

      return tx.hashCode() == this.hashCode();
    }

    /**
     * @deprecated Use {@link EthTrx#createDefault(String, BigInteger, BigInteger, Integer)} instead
     */
    public static EthTrx createDefault(String to, BigInteger amount, BigInteger nonce){
      return create(to, amount, nonce, DEFAULT_GAS_PRICE, DEFAULT_BALANCE_GAS);
    }

    public static EthTrx createDefault(String to, BigInteger amount, BigInteger nonce, Integer chainId){
      return create(to, amount, nonce, DEFAULT_GAS_PRICE, DEFAULT_BALANCE_GAS, chainId);
    }

    /**
     * @deprecated use {@link EthTrx#create(String, BigInteger, BigInteger, BigInteger, BigInteger, Integer)} instead
     */
    public static EthTrx create(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit){
      return new EthTrx(BigIntegers.asUnsignedByteArray(nonce),
              BigIntegers.asUnsignedByteArray(gasPrice),
              BigIntegers.asUnsignedByteArray(gasLimit),
              Hex.decode(to),
              BigIntegers.asUnsignedByteArray(amount),
              null);
    }

    public static EthTrx create(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice,
                                     BigInteger gasLimit, Integer chainId){
      return new EthTrx(BigIntegers.asUnsignedByteArray(nonce),
              BigIntegers.asUnsignedByteArray(gasPrice),
              BigIntegers.asUnsignedByteArray(gasLimit),
              Hex.decode(to),
              BigIntegers.asUnsignedByteArray(amount),
              null,
              chainId);
    }

    public String parseData(){
      String data = ByteArray.toHexString(this.data);
      if (StringUtils.isEmpty(data) || data.length() < 8){
        return null;
      }

      if (data.contains("0x")){
        data = data.substring(2);
      }
      return data;
    }

    public static String parseToVisionAddress(String address) {
      if (address == null || address.length() != VALUE_SIZE){
        return address;
      }
      return address.replaceFirst(ADDRESS_PREFIX_0, Constant.ADD_PRE_FIX_STRING_MAINNET);
    }

    public synchronized TriggerSmartContract rlpParseToTriggerSmartContract(DynamicPropertiesStore dynamicPropertiesStore) {
      if (!parsed)
        rlpParse();
      TriggerSmartContract.Builder build = TriggerSmartContract.newBuilder();
      build.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ByteArray.toHexString(this.getSender()).replace(Constant.ETH_PRE_FIX_STRING_MAINNET, Constant.ADD_PRE_FIX_STRING_MAINNET))));
      build.setContractAddress(ByteString.copyFrom(ByteArray.fromHexString(Constant.ADD_PRE_FIX_STRING_MAINNET + ByteArray.toHexString(this.getReceiveAddress()))));

      long callValue = dynamicPropertiesStore.getLatestBlockHeaderNumber() >= CommonParameter.PARAMETER.ethCompatibleRlpDeDupEffectBlockNum ?
              ByteUtil.byteArrayToLongDividePrecision(this.value, "1000000000000") : ByteUtil.byteArrayToLong(this.value);
      build.setCallValue(callValue);
      build.setData(ByteString.copyFrom(this.data));
      build.setCallTokenValue(0);
      build.setTokenId(0);
      build.setType(1);
      build.setRlpData(ByteString.copyFrom(rlpEncoded));
      return build.build();
    }

    public synchronized TransferContract rlpParseToTransferContract() {
      if (!parsed)
        rlpParse();
      TransferContract.Builder build = TransferContract.newBuilder();
      build.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ByteArray.toHexString(this.getSender()).replace(Constant.ETH_PRE_FIX_STRING_MAINNET, Constant.ADD_PRE_FIX_STRING_MAINNET))));
      build.setAmount(ByteUtil.byteArrayToLongDividePrecision(this.value, "1000000000000"));
      build.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(Constant.ADD_PRE_FIX_STRING_MAINNET + ByteArray.toHexString(this.getReceiveAddress()))));
      build.setType(1);
      build.setRlpData(ByteString.copyFrom(rlpEncoded));
      return build.build();
    }

    public synchronized CreateSmartContract rlpParseToDeployContract(DynamicPropertiesStore dynamicPropertiesStore) {
      if (!parsed)
        rlpParse();
      CreateSmartContract.Builder build = CreateSmartContract.newBuilder();

      SmartContract.Builder smartBuilder = SmartContract.newBuilder();
      ABI.Builder abiBuilder = ABI.newBuilder();

      boolean isEthEffectBlock = dynamicPropertiesStore.getLatestBlockHeaderNumber() >= CommonParameter.PARAMETER.ethCompatibleRlpDeDupEffectBlockNum;
      long callValue = isEthEffectBlock ? ByteUtil.byteArrayToLongDividePrecision(this.value, "1000000000000") : 0L;
      long entropyLimit = isEthEffectBlock ? ByteUtil.byteArrayToLong(gasLimit) : 50000;
      smartBuilder
              .setAbi(abiBuilder)
              .setBytecode(ByteString.copyFrom(this.data))
              .setCallValue(callValue) // transfer to contract
              .setConsumeUserResourcePercent(100)
              .setOriginEntropyLimit(entropyLimit);
      smartBuilder.setOriginAddress(ByteString.copyFrom(ByteArray.fromHexString(ByteArray.toHexString(this.getSender()).replace(Constant.ETH_PRE_FIX_STRING_MAINNET, Constant.ADD_PRE_FIX_STRING_MAINNET))));

      build.setNewContract(smartBuilder);
      build.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ByteArray.toHexString(this.getSender()).replace(Constant.ETH_PRE_FIX_STRING_MAINNET, Constant.ADD_PRE_FIX_STRING_MAINNET))));
      build.setCallTokenValue(0L); // default is 0l,this can drop
      build.setTokenId(0L); // default is 0l,this can drop
      build.setType(1);
      build.setRlpData(ByteString.copyFrom(rlpEncoded));

      return build.build();
    }

    public synchronized WithdrawBalanceContract rlpParseToWithdrawBalanceContract() {
      if (!parsed)
        rlpParse();

      WithdrawBalanceContract.Builder build = WithdrawBalanceContract.newBuilder();
      build.setOwnerAddress(ByteString.copyFrom(this.getSender()));
      String data = parseData();
      if (data == null){
        return build.build();
      }

      String dataValue = data.substring(8);
      long withdraw_type = dataValue.length() >= VALUE_SIZE ? ByteUtil.byteArrayToLong(ByteArray.fromHexString(dataValue.substring(0, VALUE_SIZE))) : 0L;
      build.setType(withdraw_type == 1L ? WithdrawBalanceContract.WithdrawBalanceType.SPREAD_MINT : WithdrawBalanceContract.WithdrawBalanceType.ALL);

      build.setRlpType(1);
      build.setRlpData(ByteString.copyFrom(rlpEncoded));
      return build.build();
    }

    public synchronized VoteWitnessContract rlpParseToVoteWitnessContract() {
      if (!parsed)
        rlpParse();

      VoteWitnessContract.Builder build = VoteWitnessContract.newBuilder();
      build.setOwnerAddress(ByteString.copyFrom(this.getSender()));
      String data = parseData();
      if (data == null){
        return build.build();
      }

      String dataValue = data.substring(8);
      // first array index start should add two parameter size
      int voteAddressArrayIndex = VALUE_SIZE * 2;
      int voteAddressArraySize = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(voteAddressArrayIndex, voteAddressArrayIndex + VALUE_SIZE)));

      // second array index start should add two parameter size, first array length, first array parameters size
      int voteCountArrayIndex = VALUE_SIZE * 2 + VALUE_SIZE + VALUE_SIZE * voteAddressArraySize;
      int voteCountArraySize = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(voteCountArrayIndex, voteCountArrayIndex + VALUE_SIZE)));
      if (voteAddressArraySize != voteCountArraySize){
       return build.build();
      }

      int index = 1;
      int startIndex, endIndex;
      while (index <= voteCountArraySize){
        startIndex = index * VALUE_SIZE;
        endIndex = (index +1) * VALUE_SIZE;
        VoteWitnessContract.Vote.Builder vote = VoteWitnessContract.Vote.newBuilder();
        String address = dataValue.substring(voteAddressArrayIndex + startIndex, voteAddressArrayIndex + endIndex);
        address = parseToVisionAddress(address);

        vote.setVoteAddress(ByteString.copyFrom(ByteArray.fromHexString(address)));
        vote.setVoteCount(ByteUtil.byteArrayToLong(ByteArray.fromHexString(dataValue.substring(voteCountArrayIndex + startIndex, voteCountArrayIndex + endIndex))));
        build.addVotes(vote);
        index++;
      }

      build.setType(1);
      build.setRlpData(ByteString.copyFrom(rlpEncoded));
      return build.build();
    }

    public synchronized FreezeBalanceContract rlpParseToFreezeBalanceContract() {
      if (!parsed)
        rlpParse();

      FreezeBalanceContract.Builder build = FreezeBalanceContract.newBuilder();
      build.setOwnerAddress(ByteString.copyFrom(this.getSender()));
      String data = parseData();
      if (data == null){
        return build.build();
      }

      String dataValue = data.substring(8);
      if (dataValue.length() >= VALUE_SIZE * 4){ // four parameter // freezeBalance(uint256,uint256,uint256,address)
        build.setFrozenBalance(ByteUtil.byteArrayToLong(ByteArray.fromHexString(dataValue.substring(0, VALUE_SIZE))));
        build.setFrozenDuration(ByteUtil.byteArrayToLong(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE, VALUE_SIZE * 2))));
        build.setResourceValue(ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE * 2, VALUE_SIZE * 3))));

        String receiverAddress = parseToVisionAddress(dataValue.substring(VALUE_SIZE * 3, VALUE_SIZE * 4));
        if (build.getResourceValue() == Common.ResourceCode.SPREAD_VALUE){
          build.setParentAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress)));
        }else {
          if (!receiverAddress.equals(ByteArray.toHexString(build.getOwnerAddress().toByteArray()))){
            build.setReceiverAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress)));
          }
        }

        if (dataValue.length() > VALUE_SIZE * 4) { // for freeze stage parameter // freezeBalance(uint256,uint256,uint256,address,uint256[],uint256[])
          int stageStartIndex = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE * 4, VALUE_SIZE * 5)));
          int frozenStartIndex = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE * 5, VALUE_SIZE * 6)));
          int refreezeStartIndex = 0;

          int stageArraySize = (int)Math.ceil(stageStartIndex / 32.0);
          int frozenArraySize = (int)Math.ceil(frozenStartIndex / 32.0);
          int refreezeArraySize = 0;

          int refreezeIndex = 0;
          int refreezeSize = 0;

          if (stageArraySize * VALUE_SIZE > VALUE_SIZE * 6){
            refreezeStartIndex = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE * 6, VALUE_SIZE * 7)));
            refreezeArraySize = (int)Math.ceil(refreezeStartIndex / 32.0);
            refreezeIndex = refreezeArraySize * VALUE_SIZE;
            refreezeSize = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(refreezeIndex, refreezeIndex + VALUE_SIZE)));
          }

          int stageIndex = stageArraySize * VALUE_SIZE;
          int stageSize = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(stageIndex, stageIndex + VALUE_SIZE)));

          int frozenIndex = frozenArraySize * VALUE_SIZE;
          int frozenSize = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(frozenIndex, frozenIndex + VALUE_SIZE)));

          if (stageSize != frozenSize ||
                  (refreezeIndex > 0 && stageSize != refreezeSize)){
            return build.build();
          }

          int index = 1;
          int startIndex, endIndex;
          while (index <= stageSize){
            startIndex = index * VALUE_SIZE;
            endIndex = (index + 1) * VALUE_SIZE;
            BalanceContract.FreezeBalanceStage.Builder freezeBalanceStage = BalanceContract.FreezeBalanceStage.newBuilder();
            freezeBalanceStage.setStage(ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(stageIndex + startIndex, stageIndex + endIndex))));
            freezeBalanceStage.setFrozenBalance(ByteUtil.byteArrayToLong(ByteArray.fromHexString(dataValue.substring(frozenIndex + startIndex, frozenIndex + endIndex))));
            if (refreezeStartIndex > 0){
              freezeBalanceStage.setRefreeze(ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(refreezeIndex + startIndex, refreezeIndex + endIndex))) == 1);
            }
            build.addFreezeBalanceStage(freezeBalanceStage);
            index++;
          }
        }
      }

      build.setType(1);
      build.setRlpData(ByteString.copyFrom(rlpEncoded));
      return build.build();
    }

    public synchronized UnfreezeBalanceContract rlpParseToUnfreezeBalanceContract() {
      if (!parsed)
        rlpParse();
      UnfreezeBalanceContract.Builder build = UnfreezeBalanceContract.newBuilder();
      build.setOwnerAddress(ByteString.copyFrom(this.getSender()));
      String data = parseData();
      if (data == null){
        return null;
      }
      String dataValue = data.substring(8);

      int resource = dataValue.length() > 0 ? ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(0, VALUE_SIZE))) : 0;
      build.setResourceValue(resource);

      if (dataValue.length() >= VALUE_SIZE * 2){ // unfreezeBalance(uint256,address)
        String receiverAddress = parseToVisionAddress(dataValue.substring(VALUE_SIZE, VALUE_SIZE * 2));
        if (!Arrays.equals(this.getSender(), ByteArray.fromHexString(receiverAddress))){
          build.setReceiverAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress)));
        }

        if (dataValue.length() >= VALUE_SIZE * 3){ // unfreezeBalance(uint256,address,uint256[]) || unfreezeBalance(uint256,address,uint256[],uint256)
          int stageIndex = VALUE_SIZE * 3;

          int stageArrIndex =  ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE * 2, VALUE_SIZE * 3))) * 2;
          if (stageArrIndex == VALUE_SIZE * 4) {
            build.setUnfreezeBalance(ByteUtil.byteArrayToLong(ByteArray.fromHexString(dataValue.substring(stageIndex, stageIndex + VALUE_SIZE))));
            stageIndex += VALUE_SIZE;
          }

          int stageSize = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(stageIndex, stageIndex + VALUE_SIZE)));
          int index = 1;
          int startIndex, endIndex;
          while (index <= stageSize){
            startIndex = index * VALUE_SIZE;
            endIndex = (index + 1) * VALUE_SIZE;
            build.addStages(ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(stageIndex + startIndex, stageIndex + endIndex))));
            index++;
          }
        }
      }

      build.setType(1);
      build.setRlpData(ByteString.copyFrom(rlpEncoded));
      return build.build();
    }

    public synchronized WitnessCreateContract rlpParseToWitnessCreateContract() {
      if (!parsed)
        rlpParse();
      WitnessCreateContract.Builder build = WitnessCreateContract.newBuilder();
      build.setOwnerAddress(ByteString.copyFrom(this.getSender()));
      String data = parseData();
      if (data == null){
        return null;
      }
      String dataValue = data.substring(8);
      int urlSize = 0;
      int parameterNum = 1;
      if (dataValue.length() > VALUE_SIZE * 2){
        int parameterSize = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(0, VALUE_SIZE)));
        parameterNum = (int) Math.ceil((double) parameterSize / 32);

        urlSize = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(parameterNum * VALUE_SIZE , parameterNum * VALUE_SIZE + VALUE_SIZE)));
      }

      String url = new String(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE * (parameterNum + 1))));

      if (url.length() < urlSize){
        return null;
      }
      url = url.substring(0, urlSize);

      build.setUrl(ByteString.copyFrom(url.getBytes(StandardCharsets.UTF_8)));
      build.setType(1);
      build.setRlpData(ByteString.copyFrom(rlpEncoded));
      return build.build();
    }

    public synchronized WitnessUpdateContract rlpParseToWitnessUpdateContract() {
      if (!parsed)
        rlpParse();
      WitnessUpdateContract.Builder build = WitnessUpdateContract.newBuilder();
      build.setOwnerAddress(ByteString.copyFrom(this.getSender()));
      String data = parseData();
      if (data == null){
        return null;
      }
      String dataValue = data.substring(8);

      int urlSize = 0;
      if (dataValue.length() > VALUE_SIZE * 2){
        urlSize = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE, VALUE_SIZE + VALUE_SIZE)));
      }
      String url = new String(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE * 2)));
      if (url.length() < urlSize){
        return null;
      }
      url = url.substring(0, urlSize);

      build.setUpdateUrl(ByteString.copyFrom(url.getBytes(StandardCharsets.UTF_8)));
      build.setType(1);
      build.setRlpData(ByteString.copyFrom(rlpEncoded));
      return build.build();
    }

    public synchronized StorageContract.UpdateBrokerageContract rlpParseToUpdateBrokerageContract() {
      if (!parsed)
        rlpParse();
      StorageContract.UpdateBrokerageContract.Builder build = StorageContract.UpdateBrokerageContract.newBuilder();
      build.setOwnerAddress(ByteString.copyFrom(this.getSender()));
      String data = parseData();
      if (data == null){
        return null;
      }
      String dataValue = data.substring(8);

      if (dataValue.length() >= VALUE_SIZE){
        build.setBrokerage(ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue)));
      }

      build.setType(1);
      build.setRlpData(ByteString.copyFrom(rlpEncoded));
      return build.build();
    }

    public synchronized ProposalContract.ProposalApproveContract rlpParseToProposalApproveContract() {
      if (!parsed)
        rlpParse();
      ProposalContract.ProposalApproveContract.Builder build = ProposalContract.ProposalApproveContract.newBuilder();
      build.setOwnerAddress(ByteString.copyFrom(this.getSender()));
      String data = parseData();
      if (data == null){
        return null;
      }
      String dataValue = data.substring(8);
      if (dataValue.length() >= VALUE_SIZE * 2){
        build.setProposalId(ByteUtil.byteArrayToLong(ByteArray.fromHexString(dataValue.substring(0, VALUE_SIZE))));
        int isApprove =  ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE, VALUE_SIZE * 2)));
        build.setIsAddApproval(isApprove == 1);
      }

      build.setType(1);
      build.setRlpData(ByteString.copyFrom(rlpEncoded));
      return build.build();
    }

    public synchronized ProposalContract.ProposalCreateContract rlpParseToProposalCreateContract() {
      if (!parsed)
        rlpParse();
      ProposalContract.ProposalCreateContract.Builder build = ProposalContract.ProposalCreateContract.newBuilder();
      build.setOwnerAddress(ByteString.copyFrom(this.getSender()));
      String data = parseData();
      if (data == null){
        return null;
      }
      String dataValue = data.substring(8);

      // parse parameter
      if (dataValue.length() == VALUE_SIZE * 2){
        long proposal_id = ByteUtil.byteArrayToLong(ByteArray.fromHexString(dataValue.substring(0, VALUE_SIZE)));
        long proposal_value = ByteUtil.byteArrayToLong(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE, VALUE_SIZE * 2)));
        build.putParameters(proposal_id, proposal_value);
      } else if (dataValue.length() >= VALUE_SIZE * 4){
        // parse string parameter
        long proposal_id = ByteUtil.byteArrayToLong(ByteArray.fromHexString(dataValue.substring(0, VALUE_SIZE)));
        int proposal_value_length = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE * 2 , VALUE_SIZE * 3)));
        String proposal_value = new String(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE * 3)));

        if (proposal_value_length < proposal_value.length()){
          proposal_value = proposal_value.substring(0, proposal_value_length);
        }
        build.putStringParameters(proposal_id, proposal_value);
      }

      build.setType(1);
      build.setRlpData(ByteString.copyFrom(rlpEncoded));
      return build.build();
    }

    public synchronized ProposalContract.ProposalDeleteContract rlpParseToProposalDeleteContract() {
      if (!parsed)
        rlpParse();
      ProposalContract.ProposalDeleteContract.Builder build = ProposalContract.ProposalDeleteContract.newBuilder();
      build.setOwnerAddress(ByteString.copyFrom(this.getSender()));
      String data = parseData();
      if (data == null){
        return null;
      }

      String dataValue = data.substring(8);
      if (dataValue.length() >= VALUE_SIZE){
        build.setProposalId(ByteUtil.byteArrayToLong(ByteArray.fromHexString(dataValue.substring(0, VALUE_SIZE))));
      }

      build.setType(1);
      build.setRlpData(ByteString.copyFrom(rlpEncoded));
      return build.build();
    }

    public synchronized AccountContract.AccountUpdateContract rlpParseToAccountUpdateContract() {
      if (!parsed)
        rlpParse();
      AccountContract.AccountUpdateContract.Builder build = AccountContract.AccountUpdateContract.newBuilder();
      build.setOwnerAddress(ByteString.copyFrom(this.getSender()));
      String data = parseData();
      if (data == null){
        return null;
      }

      String dataValue = data.substring(8);
      if (dataValue.length() >= VALUE_SIZE * 3){
        int name_length = ByteUtil.byteArrayToInt(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE, VALUE_SIZE * 2)));
        String account_name = new String(ByteArray.fromHexString(dataValue.substring(VALUE_SIZE * 2)));

        if (name_length < account_name.length()){
          account_name = account_name.substring(0, name_length);
        }

        build.setAccountName(ByteString.copyFrom(account_name.getBytes(StandardCharsets.UTF_8)));
      }

      build.setType(1);
      build.setRlpData(ByteString.copyFrom(rlpEncoded));
      return build.build();
    }

  }


  /**
   * validate signature
   */
  public boolean validatePubSignature(AccountStore accountStore,
      DynamicPropertiesStore dynamicPropertiesStore)
      throws ValidateSignatureException {
    if (!isVerified) {
    if (this.transaction.getSignatureCount() <= 0
        || this.transaction.getRawData().getContractCount() <= 0) {
      throw new ValidateSignatureException("miss sig or contract");
    }
    if (this.transaction.getSignatureCount() > dynamicPropertiesStore
        .getTotalSignNum()) {
      throw new ValidateSignatureException("too many signatures");
    }

    byte[] hash = this.getTransactionId().getBytes();

    try {
      if (!validateSignature(this.transaction, hash, accountStore, dynamicPropertiesStore)) {
        isVerified = false;
        throw new ValidateSignatureException("sig error");
      }
    } catch (SignatureException | PermissionException | SignatureFormatException e) {
      isVerified = false;
      throw new ValidateSignatureException(e.getMessage());
    }
    isVerified = true;
    }
    return true;
  }

  /**
   * validate signature
   */
  public boolean validateSignature(AccountStore accountStore,
      DynamicPropertiesStore dynamicPropertiesStore) throws ValidateSignatureException {
    if (!isVerified) {
    //Do not support multi contracts in one transaction
    Transaction.Contract contract = this.getInstance().getRawData().getContract(0);
    if (contract.getType() != ContractType.ShieldedTransferContract) {
      int v = 0;
      switch (contract.getType()) {
        case CreateSmartContract: v = validateCreateSmartContractSignature(accountStore, dynamicPropertiesStore); break;
        case TriggerSmartContract: v = validateTriggerSmartContractSignature(accountStore, dynamicPropertiesStore); break;
        case TransferContract: v = validateTransferContractSignature(accountStore, dynamicPropertiesStore); break;
        default:
          break;
      }

      if (dynamicPropertiesStore.supportEthereumCompatibleTransactionNativeStep1()){
        switch (contract.getType()) {
          case WithdrawBalanceContract: v = validateWithdrawBalanceContractSignature(accountStore, dynamicPropertiesStore); break;
          case FreezeBalanceContract: v = validateFreezeBalanceContractSignature(accountStore, dynamicPropertiesStore); break;
          case UnfreezeBalanceContract: v = validateUnfreezeBalanceContractSignature(accountStore, dynamicPropertiesStore); break;
          case VoteWitnessContract: v = validateVoteWitnessContractSignature(accountStore, dynamicPropertiesStore); break;
          case WitnessCreateContract: v = validateWitnessCreateContractSignature(accountStore, dynamicPropertiesStore); break;
          case WitnessUpdateContract: v = validateWitnessUpdateContractSignature(accountStore, dynamicPropertiesStore); break;
          case UpdateBrokerageContract: v = validateUpdateBrokerageContractSignature(accountStore, dynamicPropertiesStore); break;
          case ProposalApproveContract: v = validateProposalApproveContractSignature(accountStore, dynamicPropertiesStore); break;
          case ProposalCreateContract: v = validateProposalCreateContractSignature(accountStore, dynamicPropertiesStore); break;
          case ProposalDeleteContract: v = validateProposalDeleteContractSignature(accountStore, dynamicPropertiesStore); break;
          case AccountUpdateContract: v = validateAccountUpdateContractSignature(accountStore, dynamicPropertiesStore); break;
          default:
            break;
        }
      }

      if (v==0){
        validatePubSignature(accountStore, dynamicPropertiesStore);
      }
    } else {  //ShieldedTransfer
      byte[] owner = getOwner(contract);
      if (!ArrayUtils.isEmpty(owner)) { //transfer from transparent address
        validatePubSignature(accountStore, dynamicPropertiesStore);
      } else { //transfer from shielded address
        if (this.transaction.getSignatureCount() > 0) {
          throw new ValidateSignatureException("there should be no signatures signed by "
              + "transparent address when transfer from shielded address");
        }
      }
    }

    isVerified = true;
    }
    return true;
  }

  private int validateCreateSmartContractSignature(AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    int v = 0;
    CreateSmartContract c = ContractCapsule.getCreateSmartContractFromTransaction(this.getInstance());
    if (c == null) {
      throw new ValidateSignatureException("get create smart contract error");
    }
    if(c.getType() == 1){
      v = 1;
      validateEthSignature(accountStore, dynamicPropertiesStore, c);
    }
    return v;
  }

  private int validateTransferContractSignature(AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    int v = 0;
    TransferContract c = ContractCapsule.getTransferContractFromTransaction(this.getInstance());
    if (c == null) {
      throw new ValidateSignatureException("get transfer contract error");
    }
    if(c.getType()==1){
      v = 1;
      validateEthSignature(accountStore, dynamicPropertiesStore, c);
    }
    return v;
  }

  private int validateTriggerSmartContractSignature(AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    int v = 0;
    TriggerSmartContract c = ContractCapsule.getTriggerContractFromTransaction(this.getInstance());
    if (c == null) {
      throw new ValidateSignatureException("get trigger smart contract error");
    }
    if(c.getType()==1){
      v = 1;
      validateEthSignature(accountStore, dynamicPropertiesStore, c);
    }
    return v;
  }

  private int validateWithdrawBalanceContractSignature(AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    int v = 0;
    BalanceContract.WithdrawBalanceContract c = ContractCapsule.getWithdrawBalanceContractFromTransaction(this.getInstance());
    if (c == null) {
      throw new ValidateSignatureException("get withdraw balance contract error");
    }
    if(c.getRlpType() == 1){
      v = 1;
      validateEthSignature(accountStore, dynamicPropertiesStore, c);
    }
    return v;
  }

  private int validateFreezeBalanceContractSignature(AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    int v = 0;
    BalanceContract.FreezeBalanceContract c = ContractCapsule.getFreezeBalanceContractFromTransaction(this.getInstance());
    if (c == null) {
      throw new ValidateSignatureException("get freeze balance contract error");
    }
    if(c.getType() == 1){
      v = 1;
      validateEthSignature(accountStore, dynamicPropertiesStore, c);
    }
    return v;
  }

  private int validateUnfreezeBalanceContractSignature(AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    int v = 0;
    BalanceContract.UnfreezeBalanceContract c = ContractCapsule.getUnfreezeBalanceContractFromTransaction(this.getInstance());
    if (c == null) {
      throw new ValidateSignatureException("get unfreeze balance contract error");
    }
    if(c.getType() == 1){
      v = 1;
      validateEthSignature(accountStore, dynamicPropertiesStore, c);
    }
    return v;
  }

  private int validateVoteWitnessContractSignature(AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    int v = 0;
    VoteWitnessContract c = ContractCapsule.getVoteWitnessContractFromTransaction(this.getInstance());
    if (c == null) {
      throw new ValidateSignatureException("get vote witness contract error");
    }
    if(c.getType() == 1){
      v = 1;
      validateEthSignature(accountStore, dynamicPropertiesStore, c);
    }
    return v;
  }

  private int validateWitnessCreateContractSignature(AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    int v = 0;
    WitnessCreateContract c = ContractCapsule.getWitnessCreateContractFromTransaction(this.getInstance());
    if (c == null) {
      throw new ValidateSignatureException("get witness create contract error");
    }
    if(c.getType() == 1){
      v = 1;
      validateEthSignature(accountStore, dynamicPropertiesStore, c);
    }
    return v;
  }

  private int validateWitnessUpdateContractSignature(AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    int v = 0;
    WitnessUpdateContract c = ContractCapsule.getWitnessUpdateContractFromTransaction(this.getInstance());
    if (c == null) {
      throw new ValidateSignatureException("get witness update contract error");
    }
    if(c.getType() == 1){
      v = 1;
      validateEthSignature(accountStore, dynamicPropertiesStore, c);
    }
    return v;
  }

  private int validateUpdateBrokerageContractSignature(AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    int v = 0;
    StorageContract.UpdateBrokerageContract c = ContractCapsule.getUpdateBrokerageContractFromTransaction(this.getInstance());
    if (c == null) {
      throw new ValidateSignatureException("get update brokerage contract error");
    }
    if(c.getType() == 1){
      v = 1;
      validateEthSignature(accountStore, dynamicPropertiesStore, c);
    }
    return v;
  }

  private int validateProposalApproveContractSignature(AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    int v = 0;
    ProposalContract.ProposalApproveContract c = ContractCapsule.getProposalApproveContractFromTransaction(this.getInstance());
    if (c == null) {
      throw new ValidateSignatureException("get proposal approve contract error");
    }
    if(c.getType() == 1){
      v = 1;
      validateEthSignature(accountStore, dynamicPropertiesStore, c);
    }
    return v;
  }

  private int validateProposalCreateContractSignature(AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    int v = 0;
    ProposalContract.ProposalCreateContract c = ContractCapsule.getProposalCreateContractFromTransaction(this.getInstance());
    if (c == null) {
      throw new ValidateSignatureException("get proposal create contract error");
    }
    if(c.getType() == 1){
      v = 1;
      validateEthSignature(accountStore, dynamicPropertiesStore, c);
    }
    return v;
  }

  private int validateProposalDeleteContractSignature(AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    int v = 0;
    ProposalContract.ProposalDeleteContract c = ContractCapsule.getProposalDeleteContractFromTransaction(this.getInstance());
    if (c == null) {
      throw new ValidateSignatureException("get proposal delete contract error");
    }
    if(c.getType() == 1){
      v = 1;
      validateEthSignature(accountStore, dynamicPropertiesStore, c);
    }
    return v;
  }

  private int validateAccountUpdateContractSignature(AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore)
          throws ValidateSignatureException {
    int v = 0;
    AccountContract.AccountUpdateContract c = ContractCapsule.getAccountUpdateContractFromTransaction(this.getInstance());
    if (c == null) {
      throw new ValidateSignatureException("get account update contract error");
    }
    if(c.getType() == 1){
      v = 1;
      validateEthSignature(accountStore, dynamicPropertiesStore, c);
    }
    return v;
  }

  public Sha256Hash getTransactionId() {
    if (this.id == null) {
      this.id = getRawHash();
    }
    return this.id;
  }

  private void setRawData(Transaction.raw rawData) {
    this.transaction = this.transaction.toBuilder().setRawData(rawData).build();
    // invalidate trxId
    this.id = null;
  }

  public Sha256Hash getEthRlpDataHash(DynamicPropertiesStore dynamicPropertiesStore){
    byte[] rlpData = getEthRlpData(dynamicPropertiesStore);
    if (rlpData == null || rlpData.length <= 0){
      return null;
    }

    return Sha256Hash.of(CommonParameter.getInstance().isECKeyCryptoEngine(), rlpData);
  }

  @Override
  public byte[] getData() {
    return this.transaction.toByteArray();
  }

  public long getSerializedSize() {
    return this.transaction.getSerializedSize();
  }

  public long getResultSerializedSize() {
    long size = 0;
    for (Result result : this.transaction.getRetList()) {
      size += result.getSerializedSize();
    }
    return size;
  }

  @Override
  public Transaction getInstance() {
    return this.transaction;
  }

  @Override
  public String toString() {

    toStringBuff.setLength(0);
    toStringBuff.append("TransactionCapsule \n[ ");

    toStringBuff.append("hash=").append(getTransactionId()).append("\n");
    AtomicInteger i = new AtomicInteger();
    if (!getInstance().getRawData().getContractList().isEmpty()) {
      toStringBuff.append("contract list:{ ");
      getInstance().getRawData().getContractList().forEach(contract -> {
        toStringBuff.append("[" + i + "] ").append("type: ").append(contract.getType())
            .append("\n");
        toStringBuff.append("from address=").append(getOwner(contract)).append("\n");
        toStringBuff.append("to address=").append(getToAddress(contract)).append("\n");
        if (contract.getType().equals(ContractType.TransferContract)) {
          TransferContract transferContract;
          try {
            transferContract = contract.getParameter()
                .unpack(TransferContract.class);
            toStringBuff.append("transfer amount=").append(transferContract.getAmount())
                .append("\n");
          } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
          }
        } else if (contract.getType().equals(ContractType.TransferAssetContract)) {
          TransferAssetContract transferAssetContract;
          try {
            transferAssetContract = contract.getParameter()
                .unpack(TransferAssetContract.class);
            toStringBuff.append("transfer asset=").append(transferAssetContract.getAssetName())
                .append("\n");
            toStringBuff.append("transfer amount=").append(transferAssetContract.getAmount())
                .append("\n");
          } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
          }
        }
        if (this.transaction.getSignatureList().size() >= i.get() + 1) {
          toStringBuff.append("sign=").append(getBase64FromByteString(
              this.transaction.getSignature(i.getAndIncrement()))).append("\n");
        }
      });
      toStringBuff.append("}\n");
    } else {
      toStringBuff.append("contract list is empty\n");
    }

    toStringBuff.append("]");
    return toStringBuff.toString();
  }

  public void setResult(TransactionContext context) {
    this.setResultCode(context.getProgramResult().getResultCode());
  }

  public void setResultCode(contractResult code) {
    Result ret;
    if (this.transaction.getRetCount() > 0) {
      ret = this.transaction.getRet(0).toBuilder().setContractRet(code).build();

      this.transaction = transaction.toBuilder().setRet(0, ret).build();
      return;
    }
    ret = Result.newBuilder().setContractRet(code).build();
    this.transaction = transaction.toBuilder().addRet(ret).build();
  }

  public Transaction.Result.contractResult getContractResult() {
    if (this.transaction.getRetCount() > 0) {
      return this.transaction.getRet(0).getContractRet();
    }
    return null;
  }



  public contractResult getContractRet() {
    if (this.transaction.getRetCount() <= 0) {
      return null;
    }
    return this.transaction.getRet(0).getContractRet();
  }

  /**
   * Check if a transaction capsule contains a smart contract transaction or not.
   * @return
   */
  public boolean isContractType() {
    try {
      ContractType type = this.getInstance().getRawData().getContract(0).getType();
      return  (type == ContractType.TriggerSmartContract || type == ContractType.CreateSmartContract);
    } catch (Exception ex) {
      logger.warn("check contract type failed, reason {}", ex.getMessage());
      return false;
    }
  }
  public BalanceContract.TransferContract getTransferContract() {
    try {
      return transaction.getRawData()
          .getContract(0)
          .getParameter()
          .unpack(BalanceContract.TransferContract.class);
    } catch (InvalidProtocolBufferException e) {
      return null;
    }
  }
}
