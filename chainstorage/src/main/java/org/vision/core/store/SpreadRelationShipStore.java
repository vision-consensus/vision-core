package org.vision.core.store;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.ArrayUtils;
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

  @Override
  public void put(byte[] key, SpreadRelationShipCapsule item) {
    super.put(key, item);

    if(CommonParameter.PARAMETER.isKafkaEnable()) {
      JSONObject jsonObject= JSONObject.parseObject(JsonFormat.printToString(item.getInstance()));
      jsonObject.put("type", "update");
      Producer.getInstance().send("SPREADRELATIONSHIP", jsonObject.toJSONString());
    }
  }

  @Override
  public void delete(byte[] key) {
    super.delete(key);
    if (CommonParameter.PARAMETER.isKafkaEnable()) {
      SpreadRelationShipCapsule capsule = get(key);
      JSONObject jsonObject= JSONObject.parseObject(JsonFormat.printToString(capsule.getInstance()));
      jsonObject.put("type", "delete");
      Producer.getInstance().send("SPREADRELATIONSHIP", jsonObject.toJSONString());
    }
  }
}