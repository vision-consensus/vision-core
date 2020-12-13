package org.vision.core.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.vision.core.capsule.BytesCapsule;
import org.vision.core.db2.common.TxCacheDB;

@Slf4j
public class TransactionCache extends VisionStoreWithRevoking<BytesCapsule> {

  @Autowired
  public TransactionCache(@Value("trans-cache") String dbName) {
    super(new TxCacheDB(dbName));
  }
}
