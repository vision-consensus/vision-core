package org.vision.core.store;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.JsonFormat;
import org.vision.common.utils.Producer;
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

  @Override
  public void put(byte[] key, AccountFrozenStageResourceCapsule item) {
    super.put(key, item);
    if(CommonParameter.PARAMETER.isKafkaEnable()){
      Producer.getInstance().send("ACCOUNTFROZENSTAGE", JsonFormat.printToString(item.getInstance()));
    }
  }
}
