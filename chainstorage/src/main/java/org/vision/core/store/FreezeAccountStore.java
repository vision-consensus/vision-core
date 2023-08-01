package org.vision.core.store;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.core.capsule.FreezeAccountCapsule;
import org.vision.core.db.VisionStoreWithRevoking;



@Component
public class FreezeAccountStore extends VisionStoreWithRevoking<FreezeAccountCapsule> {

  @Autowired
  public FreezeAccountStore(@Value("freeze-account") String dbName) {
    super(dbName);
  }

  @Override
  public FreezeAccountCapsule get(byte[] key) {
    byte[] value = revokingDB.getUnchecked(key);
    return ArrayUtils.isEmpty(value) ? null : new FreezeAccountCapsule(value);
  }

  public void put(byte[] key, FreezeAccountCapsule item) {
    super.put(key, item);
  }

  @Override
  public void delete(byte[] key) {
    super.delete(key);
  }

  public byte[] createFreezeAccountDbKey() {
    return "FREEZE_ACCOUNT_KEY".getBytes();
  }

}