package org.vision.core.store;

import com.alibaba.fastjson.JSONObject;
import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Sha256Hash;
import org.vision.core.capsule.BlockBalanceTraceCapsule;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.db.VisionStoreWithRevoking;
import org.vision.core.exception.BadItemException;
import org.vision.protos.contract.BalanceContract;
import org.vision.protos.contract.BalanceContract.TransactionBalanceTrace;

import java.util.Objects;


@Component
@Slf4j(topic = "DB")
public class BalanceTraceStore extends VisionStoreWithRevoking<BlockBalanceTraceCapsule>  {

  @Getter
  private BlockCapsule.BlockId currentBlockId;

  @Getter
  private Sha256Hash currentTransactionId;

  @Getter
  @Setter
  private BlockBalanceTraceCapsule currentBlockBalanceTraceCapsule;

  @Getter
  @Setter
  private TransactionBalanceTrace currentTransactionBalanceTrace;

  @Autowired
  protected BalanceTraceStore(@Value("balance-trace") String dbName) {
    super(dbName);
  }

  public void setCurrentTransactionId(TransactionCapsule transactionCapsule) {
    if (currentBlockId == null) {
      return;
    }
    currentTransactionId = transactionCapsule.getTransactionId();
  }

  public void setCurrentBlockId(BlockCapsule blockCapsule) {
    currentBlockId = blockCapsule.getBlockId();
  }

  public void resetCurrentTransactionTrace() {
    if (!CommonParameter.getInstance().isHistoryBalanceLookup()) {
      return;
    }

    if (currentBlockId == null) {
      return;
    }

    if (!CollectionUtils.isEmpty(currentTransactionBalanceTrace.getOperationList())) {
      currentBlockBalanceTraceCapsule.addTransactionBalanceTrace(currentTransactionBalanceTrace);
    }

    currentTransactionId = null;
    currentTransactionBalanceTrace = null;
  }

  public void resetCurrentBlockTrace() {
    if (CommonParameter.getInstance().isHistoryBalanceLookup()) {
      putBlockBalanceTrace(currentBlockBalanceTraceCapsule);
      currentBlockId = null;
      currentBlockBalanceTraceCapsule = null;
    }
  }

  public void initCurrentBlockBalanceTrace(BlockCapsule blockCapsule) {
    if (CommonParameter.getInstance().isHistoryBalanceLookup()) {
      setCurrentBlockId(blockCapsule);
      currentBlockBalanceTraceCapsule = new BlockBalanceTraceCapsule(blockCapsule);
    }
  }

  public void initCurrentTransactionBalanceTrace(TransactionCapsule transactionCapsule) {
    if (!CommonParameter.getInstance().isHistoryBalanceLookup()) {
      return;
    }

    if (currentBlockId == null) {
      return;
    }

    setCurrentTransactionId(transactionCapsule);
    currentTransactionBalanceTrace = TransactionBalanceTrace.newBuilder()
        .setTransactionIdentifier(transactionCapsule.getTransactionId().getByteString())
        .setType(transactionCapsule.getInstance().getRawData().getContract(0).getType().name())
        .build();
  }

  public void updateCurrentTransactionStatus(String status) {
    if (!CommonParameter.getInstance().isHistoryBalanceLookup()) {
      return;
    }
      if (currentBlockId == null) {
      return;
    }

    currentTransactionBalanceTrace = currentTransactionBalanceTrace.toBuilder()
        .setStatus(StringUtils.isEmpty(status) ? "SUCCESS" : status)
        .build();
  }

  private void putBlockBalanceTrace(BlockBalanceTraceCapsule blockBalanceTrace) {
    byte[] key = ByteArray.fromLong(getCurrentBlockId().getNum());
    put(key, blockBalanceTrace);
  }

  public BlockBalanceTraceCapsule getBlockBalanceTrace(BlockCapsule.BlockId blockId) throws BadItemException {
    long blockNumber = blockId.getNum();
    if (blockNumber == -1) {
      return null;
    }

    byte[] key = ByteArray.fromLong(blockNumber);
    byte[] value = revokingDB.getUnchecked(key);
    if (Objects.isNull(value)) {
      return null;
    }

    BlockBalanceTraceCapsule blockBalanceTraceCapsule = new BlockBalanceTraceCapsule(value);
    if (Objects.isNull(blockBalanceTraceCapsule.getInstance())) {
      return null;
    }

    return blockBalanceTraceCapsule;
  }

  public TransactionBalanceTrace getTransactionBalanceTrace(BlockCapsule.BlockId blockId, Sha256Hash transactionId) throws BadItemException {
    long blockNumber = blockId.getNum();
    if (blockNumber == -1) {
      return null;
    }

    byte[] key = ByteArray.fromLong(blockNumber);
    byte[] value = revokingDB.getUnchecked(key);
    if (Objects.isNull(value)) {
      return null;
    }

    BlockBalanceTraceCapsule blockBalanceTraceCapsule = new BlockBalanceTraceCapsule(value);
    if (Objects.isNull(blockBalanceTraceCapsule.getInstance())) {
      return null;
    }

    for (TransactionBalanceTrace transactionBalanceTrace : blockBalanceTraceCapsule.getInstance().getTransactionBalanceTraceList()) {
      if (transactionBalanceTrace.getTransactionIdentifier().equals(transactionId.getByteString())) {
        return transactionBalanceTrace;
      }
    }

    return null;
  }

  // This is for the function of mongo branch
  public JSONObject assembleJsonInfo(){
    BlockCapsule.BlockId blockId = getCurrentBlockId();
    JSONObject jsonObject = new JSONObject();
    if (blockId != null){
      jsonObject.put("blockNum", blockId.getNum());
      jsonObject.put("blockId", blockId);
      jsonObject.put("trxId", getCurrentTransactionId());
      jsonObject.put("status", "normal");
    }
    return jsonObject;
  }
}
