package org.vision.core.store;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.ByteUtil;
import org.vision.common.utils.JsonFormat;
import org.vision.common.utils.Producer;
import org.vision.core.capsule.ContractCapsule;
import org.vision.core.db.VisionStoreWithRevoking;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;

@Slf4j(topic = "DB")
@Component
public class ContractStore extends VisionStoreWithRevoking<ContractCapsule> {

  @Autowired
  private ContractStore(@Value("contract") String dbName) {
    super(dbName);
  }

  @Autowired
  private BalanceTraceStore balanceTraceStore;

  @Override
  public ContractCapsule get(byte[] key) {
    return getUnchecked(key);
  }

  @Override
  public void put(byte[] key, ContractCapsule item){
    super.put(key, item);
    if (CommonParameter.PARAMETER.isKafkaEnable()) {
      try {
        item.setRuntimecode(ByteUtil.ZERO_BYTE_ARRAY);
        JSONObject jsonObject = JSONObject
                .parseObject(JsonFormat.printToString(item.generateWrapper(), true));
        jsonObject.putAll(balanceTraceStore.assembleJsonInfo());
        Producer.getInstance().send("CONTRACT", Hex.toHexString(item.getInstance().getContractAddress().toByteArray()), jsonObject.toJSONString());
      } catch (Exception e) {
        e.printStackTrace();
        logger.error("contract-error:" + e.getMessage());
      }
    }
  }

  /**
   * get total transaction.
   */
  public long getTotalContracts() {
    return Streams.stream(revokingDB.iterator()).count();
  }

  /**
   * find a transaction  by it's id.
   */
  public byte[] findContractByHash(byte[] trxHash) {
    return revokingDB.getUnchecked(trxHash);
  }

  /**
   *
   * @param contractAddress
   * @return
   */
  public SmartContract.ABI getABI(byte[] contractAddress) {
    byte[] value = revokingDB.getUnchecked(contractAddress);
    if (ArrayUtils.isEmpty(value)) {
      return null;
    }

    ContractCapsule contractCapsule = new ContractCapsule(value);
    SmartContract smartContract = contractCapsule.getInstance();
    if (smartContract == null) {
      return null;
    }

    return smartContract.getAbi();
  }

}
