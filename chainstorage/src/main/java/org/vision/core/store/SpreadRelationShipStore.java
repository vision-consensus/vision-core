package org.vision.core.store;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.ArrayUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.JsonFormat;
import org.vision.common.utils.Producer;
import org.vision.core.capsule.SpreadRelationShipCapsule;
import org.vision.core.db.VisionStoreWithRevoking;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class SpreadRelationShipStore extends VisionStoreWithRevoking<SpreadRelationShipCapsule> {

  @Autowired
  public SpreadRelationShipStore(@Value("SpreadRelationShip") String dbName) {
    super(dbName);
  }

  @Override
  public SpreadRelationShipCapsule get(byte[] key) {

    byte[] value = revokingDB.getUnchecked(key);
    return ArrayUtils.isEmpty(value) ? null : new SpreadRelationShipCapsule(value);
  }

  @Deprecated
  public List<SpreadRelationShipCapsule> getByOwner(byte[] key) {
    return revokingDB.getValuesNext(key, Long.MAX_VALUE).stream()
        .map(SpreadRelationShipCapsule::new)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public void put(byte[] key, SpreadRelationShipCapsule item, boolean isUpdate,long frozenDuration) {
    super.put(key, item);

    if(CommonParameter.PARAMETER.isKafkaEnable()) {
      JSONObject jsonObject= JSONObject.parseObject(JsonFormat.printToString(item.getInstance(), true));
      String type = isUpdate ? "update" : "freeze";
      jsonObject.put("type", type);
      jsonObject.put("frozenDuration", frozenDuration);
      Producer.getInstance().send("SPREADRELATIONSHIP", Hex.toHexString(item.getOwner().toByteArray()), jsonObject.toJSONString());
    }
  }

  @Override
  public void delete(byte[] key) {
    super.delete(key);
  }
}
