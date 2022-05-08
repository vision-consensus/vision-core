package org.vision.core.store;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.core.capsule.AccountFrozenStageResourceCapsule;
import org.vision.core.db.VisionStoreWithRevoking;

@Slf4j(topic = "DB")
@Component
public class AccountFrozenStageResourceStore extends VisionStoreWithRevoking<AccountFrozenStageResourceCapsule> {

  @Autowired
  private AccountFrozenStageResourceStore(@Value("account-frozen-stage-resource") String dbName) {
      super(dbName);
  }

  @Override
  public AccountFrozenStageResourceCapsule get(byte[] key) {
    byte[] value = revokingDB.getUnchecked(key);
    return ArrayUtils.isEmpty(value) ? null : new AccountFrozenStageResourceCapsule(value);
  }
}
