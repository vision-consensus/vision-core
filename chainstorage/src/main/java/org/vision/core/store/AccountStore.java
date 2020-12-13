package org.vision.core.store;

import com.typesafe.config.ConfigObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.common.utils.Commons;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.db.VisionStoreWithRevoking;
import org.vision.core.db.accountstate.AccountStateCallBackUtils;

@Slf4j(topic = "DB")
@Component
public class AccountStore extends VisionStoreWithRevoking<AccountCapsule> {

  private static Map<String, byte[]> assertsAddress = new HashMap<>(); // key = name , value = address

  @Autowired
  private AccountStateCallBackUtils accountStateCallBackUtils;

  @Autowired
  private AccountStore(@Value("account") String dbName) {
    super(dbName);
  }

  public static void setAccount(com.typesafe.config.Config config) {
    List list = config.getObjectList("genesis.block.assets");
    for (int i = 0; i < list.size(); i++) {
      ConfigObject obj = (ConfigObject) list.get(i);
      String accountName = obj.get("accountName").unwrapped().toString();
      byte[] address = Commons.decodeFromBase58Check(obj.get("address").unwrapped().toString());
      assertsAddress.put(accountName, address);
    }
  }

  @Override
  public AccountCapsule get(byte[] key) {
    byte[] value = revokingDB.getUnchecked(key);
    return ArrayUtils.isEmpty(value) ? null : new AccountCapsule(value);
  }

  @Override
  public void put(byte[] key, AccountCapsule item) {
    super.put(key, item);
    accountStateCallBackUtils.accountCallBack(key, item);
  }

  /**
   * Max VS account.
   */
  public AccountCapsule getGalaxy() {
    return getUnchecked(assertsAddress.get("Galaxy"));
  }

  /**
   * Min VS account.
   */
  public AccountCapsule getSingularity() {
    return getUnchecked(assertsAddress.get("Singularity"));
  }

  /**
   * Get foundation account info.
   */
  public AccountCapsule getAvalon() {
    return getUnchecked(assertsAddress.get("Avalon"));
  }

  @Override
  public void close() {
    super.close();
  }
}
