package org.vision.core.vm.repository;

import static java.lang.Long.max;
import static org.vision.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;

import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.spongycastle.util.Strings;
import org.spongycastle.util.encoders.Hex;
import org.vision.common.utils.*;
import org.vision.core.ChainBaseManager;
import org.vision.core.capsule.*;
import org.vision.core.db.*;
import org.vision.core.service.MortgageService;
import org.vision.core.store.*;
import org.vision.common.crypto.Hash;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.runtime.vm.DataWord;
import org.vision.core.config.Parameter;
import org.vision.core.exception.BadItemException;
import org.vision.core.exception.ItemNotFoundException;
import org.vision.core.exception.StoreException;
import org.vision.core.vm.config.VMConfig;
import org.vision.core.vm.program.Program.IllegalOperationException;
import org.vision.core.vm.program.Storage;
import org.vision.protos.Protocol;
import org.vision.protos.Protocol.AccountType;

@Slf4j(topic = "Repository")
public class RepositoryImpl implements Repository {

  //for entropy cal
  private long precision = Parameter.ChainConstant.PRECISION;
  private long windowSize = Parameter.ChainConstant.WINDOW_SIZE_MS /
          BLOCK_PRODUCED_INTERVAL;
  private static final byte[] TOTAL_PHOTON_WEIGHT = "TOTAL_PHOTON_WEIGHT".getBytes();

  private StoreFactory storeFactory;
  @Getter
  private DynamicPropertiesStore dynamicPropertiesStore;
  @Getter
  private AccountStore accountStore;
  @Getter
  private AssetIssueStore assetIssueStore;
  @Getter
  private AssetIssueV2Store assetIssueV2Store;
  @Getter
  private CodeStore codeStore;
  @Getter
  private ContractStore contractStore;
  @Getter
  private StorageRowStore storageRowStore;
  @Getter
  private BlockStore blockStore;
  @Getter
  private KhaosDatabase khaosDb;
  @Getter
  private BlockIndexStore blockIndexStore;
  @Getter
  private WitnessStore witnessStore;
  @Getter
  private VotesStore votesStore;
  @Getter
  private MortgageService mortgageService;
  @Getter
  private DelegationStore delegationStore;
  @Getter
  private BalanceTraceStore balanceTraceStore;

  private Repository parent = null;

  private HashMap<Key, Value> accountCache = new HashMap<>();
  private HashMap<Key, Value> codeCache = new HashMap<>();
  private HashMap<Key, Value> contractCache = new HashMap<>();
  private HashMap<Key, Value> dynamicPropertiesCache = new HashMap<>();
  private HashMap<Key, Storage> storageCache = new HashMap<>();

  private HashMap<Key, Value> assetIssueCache = new HashMap<>();
  private HashMap<Key, Value> votesCache = new HashMap<>();
  private HashMap<Key, Value> delegationCache = new HashMap<>();

  public RepositoryImpl(StoreFactory storeFactory, RepositoryImpl repository) {
    init(storeFactory, repository);
  }

  public static RepositoryImpl createRoot(StoreFactory storeFactory) {
    return new RepositoryImpl(storeFactory, null);
  }

  protected void init(StoreFactory storeFactory, RepositoryImpl parent) {
    if (storeFactory != null) {
      this.storeFactory = storeFactory;
      ChainBaseManager manager = storeFactory.getChainBaseManager();
      dynamicPropertiesStore = manager.getDynamicPropertiesStore();
      accountStore = manager.getAccountStore();
      codeStore = manager.getCodeStore();
      contractStore = manager.getContractStore();
      assetIssueStore = manager.getAssetIssueStore();
      assetIssueV2Store = manager.getAssetIssueV2Store();
      storageRowStore = manager.getStorageRowStore();
      blockStore = manager.getBlockStore();
      khaosDb = manager.getKhaosDb();
      blockIndexStore = manager.getBlockIndexStore();
      witnessStore = manager.getWitnessStore();
      votesStore = manager.getVotesStore();
      mortgageService = manager.getMortgageService();
      delegationStore = manager.getDelegationStore();
      balanceTraceStore = manager.getBalanceTraceStore();
    }
    this.parent = parent;
  }

  @Override
  public Repository newRepositoryChild() {
    return new RepositoryImpl(storeFactory, this);
  }

  @Override
  public long getAccountLeftEntropyFromFreeze(AccountCapsule accountCapsule) {
    long now = getHeadSlot();

    long entropyUsage = accountCapsule.getEntropyUsage();
    long latestConsumeTime = accountCapsule.getAccountResource().getLatestConsumeTimeForEntropy();
    long entropyLimit = calculateGlobalEntropyLimit(accountCapsule);

    long newEntropyUsage = increase(entropyUsage, 0, latestConsumeTime, now);

    return max(entropyLimit - newEntropyUsage, 0); // us
  }

  @Override
  public AssetIssueCapsule getAssetIssue(byte[] tokenId) {
    byte[] tokenIdWithoutLeadingZero = ByteUtil.stripLeadingZeroes(tokenId);
    Key key = Key.create(tokenIdWithoutLeadingZero);
    if (assetIssueCache.containsKey(key)) {
      return assetIssueCache.get(key).getAssetIssue();
    }

    AssetIssueCapsule assetIssueCapsule;
    if (this.parent != null) {
      assetIssueCapsule = parent.getAssetIssue(tokenIdWithoutLeadingZero);
    } else {
      assetIssueCapsule = Commons
          .getAssetIssueStoreFinal(dynamicPropertiesStore, assetIssueStore, assetIssueV2Store)
          .get(tokenIdWithoutLeadingZero);
    }
    if (assetIssueCapsule != null) {
      assetIssueCache.put(key, Value.create(assetIssueCapsule.getData()));
    }
    return assetIssueCapsule;
  }

  @Override
  public AccountCapsule createAccount(byte[] address, Protocol.AccountType type) {
    Key key = new Key(address);
    AccountCapsule account = new AccountCapsule(ByteString.copyFrom(address), type);
    accountCache.put(key, new Value(account.getData(), Type.VALUE_TYPE_CREATE));
    return account;
  }

  @Override
  public AccountCapsule createAccount(byte[] address, String accountName,
      Protocol.AccountType type) {
    Key key = new Key(address);
    AccountCapsule account = new AccountCapsule(ByteString.copyFrom(address),
        ByteString.copyFromUtf8(accountName),
        type);

    accountCache.put(key, new Value(account.getData(), Type.VALUE_TYPE_CREATE));
    return account;
  }

  @Override
  public AccountCapsule getAccount(byte[] address) {
    Key key = new Key(address);
    if (accountCache.containsKey(key)) {
      return accountCache.get(key).getAccount();
    }

    AccountCapsule accountCapsule;
    if (parent != null) {
      accountCapsule = parent.getAccount(address);
    } else {
      accountCapsule = getAccountStore().get(address);
    }

    if (accountCapsule != null) {
      accountCache.put(key, Value.create(accountCapsule.getData()));
    }
    return accountCapsule;
  }

  @Override
  public BytesCapsule getDynamic(byte[] word) {
    Key key = Key.create(word);
    if (dynamicPropertiesCache.containsKey(key)) {
      return dynamicPropertiesCache.get(key).getDynamicProperties();
    }

    BytesCapsule bytesCapsule;
    if (parent != null) {
      bytesCapsule = parent.getDynamic(word);
    } else {
      try {
        bytesCapsule = getDynamicPropertiesStore().get(word);
      } catch (BadItemException | ItemNotFoundException e) {
        logger.warn("Not found dynamic property:" + Strings.fromUTF8ByteArray(word));
        bytesCapsule = null;
      }
    }

    if (bytesCapsule != null) {
      dynamicPropertiesCache.put(key, Value.create(bytesCapsule.getData()));
    }
    return bytesCapsule;
  }

  @Override
  public VotesCapsule getVotesCapsule(byte[] address) {
    Key cacheKey = new Key(address);
    if(votesCache.containsKey(cacheKey)) {
      return votesCache.get(cacheKey).getVotes();
    }

    VotesCapsule votesCapsule;
    if(parent != null) {
      votesCapsule = parent.getVotesCapsule(address);
    } else {
      votesCapsule = getVotesStore().get(address);
    }

    if(votesCapsule != null) {
      votesCache.put(cacheKey, Value.create(votesCapsule.getData()));
    }
    return votesCapsule;
  }

  @Override
  public WitnessCapsule getWitnessCapsule(byte[] address) {
    return witnessStore.get(address);
  }

  @Override
  public long getBeginCycle(byte[] address){
    Key cacheKey = new Key(address);
    BytesCapsule bytesCapsule = getDelegationCache(cacheKey);
    return bytesCapsule == null ? 0 : ByteArray.toLong(bytesCapsule.getData());
  }

  @Override
  public long getEndCycle(byte[] address){
    byte[] key = ("end-" + Hex.toHexString(address)).getBytes();
    Key cacheKey = new Key(key);
    BytesCapsule bytesCapsule = getDelegationCache(cacheKey);
    return bytesCapsule == null ? DelegationStore.REMARK : ByteArray.toLong(bytesCapsule.getData());
  }

  @Override
  public AccountCapsule getAccountVote(long cycle, byte[] address) {
    byte[] key = (cycle + "-" + Hex.toHexString(address) + "-account-vote").getBytes();
    Key cacheKey = new Key(key);
    BytesCapsule bytesCapsule = getDelegationCache(cacheKey);
    if (bytesCapsule == null) {
      return null;
    } else {
      return new AccountCapsule(bytesCapsule.getData());
    }
  }

  @Override
  public BytesCapsule getDelegationCache(Key key) {
    if (delegationCache.containsKey(key)) {
      return delegationCache.get(key).getBytes();
    }
    BytesCapsule bytesCapsule;
    if (parent != null) {
      bytesCapsule = parent.getDelegationCache(key);
    } else {
      bytesCapsule = getDelegationStore().get(key.getData());
    }
    if (bytesCapsule != null) {
      delegationCache.put(key, Value.create(bytesCapsule.getData()));
    }
    return bytesCapsule;
  }


  @Override
  public void deleteContract(byte[] address) {
    getCodeStore().delete(address);
    getAccountStore().delete(address);
    getContractStore().delete(address);
  }

  @Override
  public void createContract(byte[] address, ContractCapsule contractCapsule) {
    Key key = Key.create(address);
    Value value = Value.create(contractCapsule.getData(), Type.VALUE_TYPE_CREATE);
    contractCache.put(key, value);
  }

  @Override
  public ContractCapsule getContract(byte[] address) {
    Key key = Key.create(address);
    if (contractCache.containsKey(key)) {
      return contractCache.get(key).getContract();
    }

    ContractCapsule contractCapsule;
    if (parent != null) {
      contractCapsule = parent.getContract(address);
    } else {
      contractCapsule = getContractStore().get(address);
    }

    if (contractCapsule != null) {
      contractCache.put(key, Value.create(contractCapsule.getData()));
    }
    return contractCapsule;
  }

  @Override
  public void updateContract(byte[] address, ContractCapsule contractCapsule) {
    Key key = Key.create(address);
    Value value = Value.create(contractCapsule.getData(), Type.VALUE_TYPE_DIRTY);
    contractCache.put(key, value);
  }

  @Override
  public void updateAccount(byte[] address, AccountCapsule accountCapsule) {
    Key key = Key.create(address);
    Value value = Value.create(accountCapsule.getData(), Type.VALUE_TYPE_DIRTY);
    accountCache.put(key, value);
  }

  @Override
  public void updateDynamic(byte[] word, BytesCapsule bytesCapsule) {
    Key key = Key.create(word);
    Value value = Value.create(bytesCapsule.getData(), Type.VALUE_TYPE_DIRTY);
    dynamicPropertiesCache.put(key, value);
  }

  @Override
  public void updateVotesCapsule(byte[] word, VotesCapsule votesCapsule) {
    Key key = Key.create(word);
    Value value = Value.create(votesCapsule.getData(), Type.VALUE_TYPE_DIRTY);
    votesCache.put(key, value);
  }

  @Override
  public void updateBeginCycle(byte[] word, long cycle) {
    BytesCapsule bytesCapsule = new BytesCapsule(ByteArray.fromLong(cycle));
    updateDelegation(word, bytesCapsule);
  }

  @Override
  public void updateEndCycle(byte[] word, long cycle) {
    BytesCapsule bytesCapsule = new BytesCapsule(ByteArray.fromLong(cycle));
    byte[] key = ("end-" + Hex.toHexString(word)).getBytes();
    updateDelegation(key, bytesCapsule);
  }

  @Override
  public void updateAccountVote(byte[] word, long cycle, AccountCapsule accountCapsule) {
    BytesCapsule bytesCapsule = new BytesCapsule(accountCapsule.getData());
    byte[] key = (cycle + "-" + Hex.toHexString(word) + "-account-vote").getBytes();
    updateDelegation(key, bytesCapsule);
  }

  @Override
  public void updateRemark(byte[] word, long cycle) {
    BytesCapsule bytesCapsule = new BytesCapsule(ByteArray.fromLong(DelegationStore.REMARK));
    byte[] key = (cycle + "-" + Hex.toHexString(word) + "-remark").getBytes();
    updateDelegation(key, bytesCapsule);
  }

  @Override
  public void updateDelegation(byte[] word, BytesCapsule bytesCapsule) {
    Key key = Key.create(word);
    Value value = Value.create(bytesCapsule.getData(), Type.VALUE_TYPE_DIRTY);
    delegationCache.put(key, value);
  }

  @Override
  public void updateLastWithdrawCycle(byte[] address, long cycle) {
    BytesCapsule bytesCapsule = new BytesCapsule(ByteArray.fromLong(cycle));
    updateDelegation(("lastWithdraw-" + Hex.toHexString(address)).getBytes(), bytesCapsule);
  }

  @Override
  public void saveCode(byte[] address, byte[] code) {
    Key key = Key.create(address);
    Value value = Value.create(code, Type.VALUE_TYPE_CREATE);
    codeCache.put(key, value);

    if (VMConfig.allowVvmConstantinople()) {
      ContractCapsule contract = getContract(address);
      byte[] codeHash = Hash.sha3(code);
      contract.setCodeHash(codeHash);
      updateContract(address, contract);
    }
  }

  @Override
  public byte[] getCode(byte[] address) {
    Key key = Key.create(address);
    if (codeCache.containsKey(key)) {
      return codeCache.get(key).getCode().getData();
    }

    byte[] code;
    if (parent != null) {
      code = parent.getCode(address);
    } else {
      if (null == getCodeStore().get(address)) {
        code = null;
      } else {
        code = getCodeStore().get(address).getData();
      }
    }
    if (code != null) {
      codeCache.put(key, Value.create(code));
    }
    return code;
  }

  @Override
  public void putStorageValue(byte[] address, DataWord key, DataWord value) {
    address = TransactionTrace.convertToVisionAddress(address);
    if (getAccount(address) == null) {
      return;
    }
    Key addressKey = Key.create(address);
    Storage storage;
    if (storageCache.containsKey(addressKey)) {
      storage = storageCache.get(addressKey);
    } else {
      storage = getStorage(address);
      storageCache.put(addressKey, storage);
    }
    storage.put(key, value);
    if (CommonParameter.PARAMETER.isKafkaEnable()) {
      try{
        JSONObject json = new JSONObject();
        json.put(key.toHexString(), value.bigIntValue());
        json.put("address", StringUtil.encode58Check(address));
        json.put("hexAddress", ByteArray.toHexString(address));
        json.putAll(balanceTraceStore.assembleJsonInfo());
        Producer.getInstance().send("STORAGE", Hex.toHexString(address), json.toJSONString());
        logger.info("send STORAGE success, address:{}",StringUtil.encode58Check(address));
      }catch (Exception e){
        logger.error("send STORAGE fail", e);
      }
    }
  }

  @Override
  public DataWord getStorageValue(byte[] address, DataWord key) {
    address = TransactionTrace.convertToVisionAddress(address);
    if (getAccount(address) == null) {
      return null;
    }
    Key addressKey = Key.create(address);
    Storage storage;
    if (storageCache.containsKey(addressKey)) {
      storage = storageCache.get(addressKey);
    } else {
      storage = getStorage(address);
      storageCache.put(addressKey, storage);
    }
    return storage.getValue(key);
  }

  @Override
  public Storage getStorage(byte[] address) {
    Key key = Key.create(address);
    if (storageCache.containsKey(key)) {
      return storageCache.get(key);
    }
    Storage storage;
    if (this.parent != null) {
      Storage parentStorage = parent.getStorage(address);
      if (StorageUtils.getEntropyLimitHardFork()) {
        // deep copy
        storage = new Storage(parentStorage);
      } else {
        storage = parentStorage;
      }
    } else {
      storage = new Storage(address, getStorageRowStore());
    }
    ContractCapsule contract = getContract(address);
    if (contract != null && !ByteUtil.isNullOrZeroArray(contract.getTrxHash())) {
      storage.generateAddrHash(contract.getTrxHash());
    }
    return storage;
  }

  @Override
  public long getBalance(byte[] address) {
    AccountCapsule accountCapsule = getAccount(address);
    return accountCapsule == null ? 0L : accountCapsule.getBalance();
  }

  @Override
  public long addBalance(byte[] address, long value) {
    AccountCapsule accountCapsule = getAccount(address);
    if (accountCapsule == null) {
      accountCapsule = createAccount(address, Protocol.AccountType.Normal);
    }

    long balance = accountCapsule.getBalance();
    if (value == 0) {
      return balance;
    }

    if (value < 0 && balance < -value) {
      throw new RuntimeException(
          StringUtil.createReadableString(accountCapsule.createDbKey())
              + " insufficient balance");
    }
    accountCapsule.setBalance(Math.addExact(balance, value));
    Key key = Key.create(address);
    Value val = Value.create(accountCapsule.getData(),
        Type.VALUE_TYPE_DIRTY | accountCache.get(key).getType().getType());
    accountCache.put(key, val);
    return accountCapsule.getBalance();
  }

  @Override
  public void setParent(Repository repository) {
    parent = repository;
  }

  @Override
  public void commit() {
    Repository repository = null;
    if (parent != null) {
      repository = parent;
    }
    commitAccountCache(repository);
    commitCodeCache(repository);
    commitContractCache(repository);
    commitStorageCache(repository);
    commitDynamicCache(repository);
    commitVotesCache(repository);
    commitAssetIssue(repository);
    commitDelegationCache(repository);
  }

  @Override
  public void putAccount(Key key, Value value) {
    accountCache.put(key, value);
  }

  @Override
  public void putCode(Key key, Value value) {
    codeCache.put(key, value);
  }

  @Override
  public void putContract(Key key, Value value) {
    contractCache.put(key, value);
  }

  @Override
  public void putStorage(Key key, Storage cache) {
    storageCache.put(key, cache);
  }

  @Override
  public void putAccountValue(byte[] address, AccountCapsule accountCapsule) {
    Key key = new Key(address);
    accountCache.put(key, new Value(accountCapsule.getData(), Type.VALUE_TYPE_CREATE));
  }

  @Override
  public void putDynamic(Key key, Value value){
    dynamicPropertiesCache.put(key, value);
  }

  @Override
  public void putAssetIssue(Key key, Value value) {
    assetIssueCache.put(key, value);
  }

  @Override
  public void putVotesCapsule(Key key, Value value) {
    votesCache.put(key, value);
  }

  @Override
  public void putAssetIssueValue(byte[] tokenId, AssetIssueCapsule assetIssueCapsule) {
    Key key = new Key(tokenId);
    Value value = new Value(assetIssueCapsule.getData(), Type.VALUE_TYPE_CREATE);
    assetIssueCache.put(key, value);
  }

  @Override
  public void putDelegation(Key key, Value value){
    delegationCache.put(key,value);
  }


  @Override
  public long addTokenBalance(byte[] address, byte[] tokenId, long value) {
    byte[] tokenIdWithoutLeadingZero = ByteUtil.stripLeadingZeroes(tokenId);
    AccountCapsule accountCapsule = getAccount(address);
    if (accountCapsule == null) {
      accountCapsule = createAccount(address, Protocol.AccountType.Normal);
    }
    long balance = accountCapsule.getAssetMapV2()
        .getOrDefault(new String(tokenIdWithoutLeadingZero), new Long(0));
    if (value == 0) {
      return balance;
    }

    if (value < 0 && balance < -value) {
      throw new RuntimeException(
          StringUtil.createReadableString(accountCapsule.createDbKey())
              + " insufficient balance");
    }
    if (value >= 0) {
      accountCapsule.addAssetAmountV2(tokenIdWithoutLeadingZero, value, getDynamicPropertiesStore(),
          getAssetIssueStore());
    } else {
      accountCapsule
          .reduceAssetAmountV2(tokenIdWithoutLeadingZero, -value, getDynamicPropertiesStore(),
              getAssetIssueStore());
    }
    Key key = Key.create(address);
    Value V = Value.create(accountCapsule.getData(),
        Type.VALUE_TYPE_DIRTY | accountCache.get(key).getType().getType());
    accountCache.put(key, V);
    return accountCapsule.getAssetMapV2().get(new String(tokenIdWithoutLeadingZero));
  }

  @Override
  public long getTokenBalance(byte[] address, byte[] tokenId) {
    AccountCapsule accountCapsule = getAccount(address);
    if (accountCapsule == null) {
      return 0;
    }
    String tokenStr = new String(ByteUtil.stripLeadingZeroes(tokenId));
    return accountCapsule.getAssetMapV2().getOrDefault(tokenStr, 0L);
  }

  @Override
  public byte[] getBlackHoleAddress() {
    return getAccountStore().getSingularityAddress();
  }

  @Override
  public BlockCapsule getBlockByNum(long num) {
    try {
      Sha256Hash hash = getBlockIdByNum(num);
      BlockCapsule block = this.khaosDb.getBlock(hash);
      if (block == null) {
        block = blockStore.get(hash.getBytes());
      }
      return block;
    } catch (StoreException e) {
      throw new IllegalOperationException("cannot find block num");
    }
  }

  private long increase(long lastUsage, long usage, long lastTime, long now) {
    return increase(lastUsage, usage, lastTime, now, windowSize);
  }

  private long increase(long lastUsage, long usage, long lastTime, long now, long windowSize) {
    long averageLastUsage = divideCeil(lastUsage * precision, windowSize);
    long averageUsage = divideCeil(usage * precision, windowSize);

    if (lastTime != now) {
      assert now > lastTime;
      if (lastTime + windowSize > now) {
        long delta = now - lastTime;
        double decay = (windowSize - delta) / (double) windowSize;
        averageLastUsage = Math.round(averageLastUsage * decay);
      } else {
        averageLastUsage = 0;
      }
    }
    averageLastUsage += averageUsage;
    return getUsage(averageLastUsage, windowSize);
  }

  private long divideCeil(long numerator, long denominator) {
    return (numerator / denominator) + ((numerator % denominator) > 0 ? 1 : 0);
  }

  private long getUsage(long usage, long windowSize) {
    return usage * windowSize / precision;
  }

  public long calculateGlobalEntropyLimit(AccountCapsule accountCapsule) {
    long frozeBalance = accountCapsule.getAllFrozenBalanceForEntropy();
    if (frozeBalance < 1_000_000L) {
      return 0;
    }
    long entropyWeight = frozeBalance / 1_000_000L;
    long totalEntropyLimit = getDynamicPropertiesStore().getTotalEntropyCurrentLimit();
    long totalEntropyWeight = getDynamicPropertiesStore().getTotalEntropyWeight();

    assert totalEntropyWeight > 0;

    return (long) (entropyWeight * ((double) totalEntropyLimit / totalEntropyWeight));
  }

  public long getHeadSlot() {
    return (getDynamicPropertiesStore().getLatestBlockHeaderTimestamp()
        - Long.parseLong(CommonParameter.getInstance()
            .getGenesisBlock().getTimestamp()))
        / BLOCK_PRODUCED_INTERVAL;
  }

  private void commitAccountCache(Repository deposit) {
    accountCache.forEach((key, value) -> {
      if (value.getType().isCreate() || value.getType().isDirty()) {
        if (deposit != null) {
          deposit.putAccount(key, value);
        } else {
          getAccountStore().put(key.getData(), value.getAccount());
        }
      }
    });
  }

  private void commitCodeCache(Repository deposit) {
    codeCache.forEach(((key, value) -> {
      if (value.getType().isDirty() || value.getType().isCreate()) {
        if (deposit != null) {
          deposit.putCode(key, value);
        } else {
          getCodeStore().put(key.getData(), value.getCode());
        }
      }
    }));
  }

  private void commitContractCache(Repository deposit) {
    contractCache.forEach(((key, value) -> {
      if (value.getType().isDirty() || value.getType().isCreate()) {
        if (deposit != null) {
          deposit.putContract(key, value);
        } else {
          getContractStore().put(key.getData(), value.getContract());
        }
      }
    }));
  }

  private void commitStorageCache(Repository deposit) {
    storageCache.forEach((Key address, Storage storage) -> {
      if (deposit != null) {
        // write to parent cache
        deposit.putStorage(address, storage);
      } else {
        // persistence
        storage.commit();
      }
    });

  }

  private void commitDynamicCache(Repository deposit) {
    dynamicPropertiesCache.forEach(((key, value) -> {
      if (value.getType().isDirty() || value.getType().isCreate()) {
        if (deposit != null) {
          deposit.putDynamic(key, value);
        } else {
          getDynamicPropertiesStore().put(key.getData(), value.getDynamicProperties());
        }
      }
    }));
  }

  private void commitVotesCache(Repository deposit) {
    votesCache.forEach(((key, value) -> {
      if(value.getType().isDirty() || value.getType().isCreate()) {
        if(deposit != null) {
          deposit.putVotesCapsule(key, value);
        } else {
          getVotesStore().put(key.getData(), value.getVotes());
        }
      }
    }));
  }

  private void commitAssetIssue(Repository deposit) {
    AssetIssueStore assetIssueStoreFinal = Commons
            .getAssetIssueStoreFinal(dynamicPropertiesStore, assetIssueStore, assetIssueV2Store);

    assetIssueCache.forEach((key, value) -> {
      if (value.getType().isCreate() || value.getType().isDirty()) {
        if (deposit != null) {
          deposit.putAssetIssue(key, value);
        } else {
          assetIssueStoreFinal
                  .put(key.getData(), value.getAssetIssue());
        }
      }
    });
  }

  private void commitDelegationCache(Repository deposit) {
    delegationCache.forEach((key, value) -> {
      if(value.getType().isDirty() || value.getType().isCreate()) {
        if(deposit != null) {
          deposit.putDelegation(key, value);
        } else {
          getDelegationStore().put(key.getData(), value.getBytes());
        }
      }
    });
  }

  /**
   * Get the block id from the number.
   */
  private BlockCapsule.BlockId getBlockIdByNum(final long num) throws ItemNotFoundException {
    return this.blockIndexStore.get(num);
  }

  @Override
  public AccountCapsule createNormalAccount(byte[] address) {
    boolean withDefaultPermission =
        getDynamicPropertiesStore().getAllowMultiSign() == 1;
    Key key = new Key(address);
    AccountCapsule account = new AccountCapsule(ByteString.copyFrom(address), AccountType.Normal,
        getDynamicPropertiesStore().getLatestBlockHeaderTimestamp(), withDefaultPermission,
        getDynamicPropertiesStore());

    accountCache.put(key, new Value(account.getData(), Type.VALUE_TYPE_CREATE));
    return account;
  }

  @Override
  public void saveTokenIdNum(long num) {
    this.updateDynamic(DynamicPropertiesStore.getTOKEN_ID_NUM(),
            new BytesCapsule(ByteArray.fromLong(num)));
  }

  @Override
  public long getTokenIdNum() {
    return Optional.ofNullable(this.getDynamic(DynamicPropertiesStore.getTOKEN_ID_NUM()))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElseThrow(
                    () -> new IllegalArgumentException("error in contract not found TOKEN_ID_NUM"));
  }

  //The unit is vs
  @Override
  public void addTotalPhotonWeight(long amount) {
    long totalPhotonWeight = getTotalPhotonWeight();
    totalPhotonWeight += amount;
    saveTotalPhotonWeight(totalPhotonWeight);
  }

  @Override
  public void saveTotalPhotonWeight(long totalPhotonWeight) {
    updateDynamic(TOTAL_PHOTON_WEIGHT, new BytesCapsule(ByteArray.fromLong(totalPhotonWeight)));
  }

  @Override
  public long getTotalPhotonWeight() {
    return Optional.ofNullable(getDynamic(TOTAL_PHOTON_WEIGHT))
            .map(BytesCapsule::getData)
            .map(ByteArray::toLong)
            .orElseThrow(
                    () -> new IllegalArgumentException("not found TOTAL_PHOTON_WEIGHT"));
  }
}
