package org.vision.core.store;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.JsonFormat;
import org.vision.common.utils.Producer;
import org.vision.core.capsule.DelegatedResourceCapsule;
import org.vision.core.db.VisionStoreWithRevoking;

@Component
public class DelegatedResourceStore extends VisionStoreWithRevoking<DelegatedResourceCapsule> {

  @Autowired
  public DelegatedResourceStore(@Value("DelegatedResource") String dbName) {
    super(dbName);
  }

  @Autowired
  private BalanceTraceStore balanceTraceStore;

  @Override
  public DelegatedResourceCapsule get(byte[] key) {

    byte[] value = revokingDB.getUnchecked(key);
    return ArrayUtils.isEmpty(value) ? null : new DelegatedResourceCapsule(value);
  }

  @Override
  public void put(byte[] key, DelegatedResourceCapsule item){
    super.put(key, item);
    if(CommonParameter.PARAMETER.isKafkaEnable()){
      JSONObject jsonObject = JSONObject.parseObject(JsonFormat.printToString(item.getInstance()));
      jsonObject.putAll(balanceTraceStore.assembleJsonInfo());
      Producer.getInstance().send("DELEGATE", jsonObject.toJSONString());
    }
  }

  @Deprecated
  public List<DelegatedResourceCapsule> getByFrom(byte[] key) {
    return revokingDB.getValuesNext(key, Long.MAX_VALUE).stream()
        .map(DelegatedResourceCapsule::new)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

}