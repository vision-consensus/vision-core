package org.vision.core.store;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.core.capsule.MarketAccountOrderCapsule;
import org.vision.core.db.VisionStoreWithRevoking;
import org.vision.core.exception.ItemNotFoundException;

@Component
public class MarketAccountStore extends VisionStoreWithRevoking<MarketAccountOrderCapsule> {

  @Autowired
  protected MarketAccountStore(@Value("market_account") String dbName) {
    super(dbName);
  }

  @Override
  public MarketAccountOrderCapsule get(byte[] key) throws ItemNotFoundException {
    byte[] value = revokingDB.get(key);
    return new MarketAccountOrderCapsule(value);
  }

}