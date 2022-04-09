package org.vision.core.store;

import com.google.protobuf.ByteString;
import com.typesafe.config.ConfigObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.Commons;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.db.VisionStoreWithRevoking;
import org.vision.core.db.accountstate.AccountStateCallBackUtils;
import org.vision.protos.contract.BalanceContract.TransactionBalanceTrace;
import org.vision.protos.contract.BalanceContract.TransactionBalanceTrace.Operation;

@Slf4j(topic = "DB")
@Component
public class AccountStore extends VisionStoreWithRevoking<AccountCapsule> {

  private static Map<String, byte[]> assertsAddress = new HashMap<>(); // key = name , value = address

  @Autowired
  private AccountStateCallBackUtils accountStateCallBackUtils;
  @Autowired
  private BalanceTraceStore balanceTraceStore;
  @Autowired
  private AccountTraceStore accountTraceStore;

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
    if (CommonParameter.getInstance().isHistoryBalanceLookup()) {
      AccountCapsule old = super.getUnchecked(key);
      if (old == null) {
        if (item.getBalance() != 0) {
          recordBalance(item, item.getBalance());
          BlockCapsule.BlockId blockId = balanceTraceStore.getCurrentBlockId();
          if (blockId != null) {
            accountTraceStore.recordBalanceWithBlock(key, blockId.getNum(), item.getBalance());
          }
        }
      } else if (old.getBalance() != item.getBalance()) {
        recordBalance(item, item.getBalance() - old.getBalance());
        BlockCapsule.BlockId blockId = balanceTraceStore.getCurrentBlockId();
        if (blockId != null) {
          accountTraceStore.recordBalanceWithBlock(key, blockId.getNum(), item.getBalance());
        }
      }
    }
    super.put(key, item);
    accountStateCallBackUtils.accountCallBack(key, item);
  }

  @Override
  public void delete(byte[] key) {
    if (CommonParameter.getInstance().isHistoryBalanceLookup()) {
      AccountCapsule old = super.getUnchecked(key);
      if (old != null) {
        recordBalance(old, -old.getBalance());
      }
      BlockCapsule.BlockId blockId = balanceTraceStore.getCurrentBlockId();
      if (blockId != null) {
        accountTraceStore.recordBalanceWithBlock(key, blockId.getNum(), 0);
      }
    }
    super.delete(key);
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

  public byte[] getSingularityAddress() {
    return assertsAddress.get("Singularity");
  }
  /**
   * Get foundation account info.
   */
  public AccountCapsule getAvalon() {
    return getUnchecked(assertsAddress.get("Avalon"));
  }

  /**
   * Get finance account info
   */
  public AccountCapsule getPrivateSale() {
    return getUnchecked(assertsAddress.get("PrivateSale"));
  }

  /**
   * Get Team account info
   */
  public AccountCapsule getTeam() {
    return getUnchecked(assertsAddress.get("Team"));
  }

  /**
   * Get DAO community account info
   */
  public AccountCapsule getDAO() {
    return getUnchecked(assertsAddress.get("DAO"));
  }

  /**
   * Get Developer account info
   */
  public AccountCapsule getDev() {
    return getUnchecked(assertsAddress.get("Dev"));
  }

  /**
   * Get Promotion account info
   */
  public AccountCapsule getPromotion() {
    return getUnchecked(assertsAddress.get("Promotion"));
  }

  private void recordBalance(AccountCapsule accountCapsule, long diff) {
    TransactionBalanceTrace transactionBalanceTrace = balanceTraceStore.getCurrentTransactionBalanceTrace();
    if (transactionBalanceTrace == null) {
      return;
    }
    long operationIdentifier;
    OptionalLong max = transactionBalanceTrace.getOperationList().stream()
        .mapToLong(Operation::getOperationIdentifier)
        .max();
    if (max.isPresent()) {
      operationIdentifier = max.getAsLong() + 1;
    } else {
      operationIdentifier = 0;
    }
    ByteString address = accountCapsule.getAddress();
    Operation operation = Operation.newBuilder()
        .setAddress(address)
        .setAmount(diff)
        .setOperationIdentifier(operationIdentifier)
        .build();
    transactionBalanceTrace = transactionBalanceTrace.toBuilder()
        .addOperation(operation)
        .build();
    balanceTraceStore.setCurrentTransactionBalanceTrace(transactionBalanceTrace);
  }
  @Override
  public void close() {
    super.close();
  }
}
