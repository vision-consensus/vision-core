package org.vision.core.store;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.core.capsule.MarketOrderCapsule;
import org.vision.core.db.VisionStoreWithRevoking;
import org.vision.core.exception.ItemNotFoundException;

@Component
public class MarketOrderStore extends VisionStoreWithRevoking<MarketOrderCapsule> {

  @Autowired
  protected MarketOrderStore(@Value("market_order") String dbName) {
    super(dbName);
  }

  @Override
  public MarketOrderCapsule get(byte[] key) throws ItemNotFoundException {
    byte[] value = revokingDB.get(key);
    return new MarketOrderCapsule(value);
  }

}