package org.vision.core.db;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.core.capsule.EthereumCompatibleRlpDedupCapsule;
import org.vision.core.exception.BadItemException;

import java.util.Objects;

@Slf4j(topic = "DB")
@Component
public class EthereumCompatibleRlpDedupStore extends VisionStoreWithRevoking<EthereumCompatibleRlpDedupCapsule> {

  @Autowired
  private EthereumCompatibleRlpDedupStore(@Value("eth-rlp-trans") String dbName) {
    super(dbName);
  }

  @Override
  public void put(byte[] key, EthereumCompatibleRlpDedupCapsule item) {
    if (Objects.isNull(item) || item.getRlpDataValue().length <= 0) {
      super.put(key, item);
    } else {
      revokingDB.put(key, item.getRlpDataValue());
    }
  }

  @Override
  public EthereumCompatibleRlpDedupCapsule get(byte[] key) throws BadItemException {
    byte[] value = revokingDB.getUnchecked(key);
    if (ArrayUtils.isEmpty(value)) {
      return null;
    }
    EthereumCompatibleRlpDedupCapsule ethereumCompatibleRlpDedupCapsule = null;
    if (value.length > 0) {
      ethereumCompatibleRlpDedupCapsule = new EthereumCompatibleRlpDedupCapsule(key, value);
    }

    return ethereumCompatibleRlpDedupCapsule == null ? new EthereumCompatibleRlpDedupCapsule(value) : ethereumCompatibleRlpDedupCapsule;
  }

  @Override
  public EthereumCompatibleRlpDedupCapsule getUnchecked(byte[] key) {
    try {
      return get(key);
    } catch (Exception e) {
      return null;
    }
  }
}
