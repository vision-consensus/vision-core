package org.vision.core.vm.repository;

import org.vision.core.capsule.*;
import org.vision.core.store.*;
import org.vision.common.runtime.vm.DataWord;
import org.vision.core.vm.program.Storage;
import org.vision.core.capsule.*;
import org.vision.core.store.*;
import org.vision.protos.Protocol;

public interface Repository {

  AssetIssueCapsule getAssetIssue(byte[] tokenId);

  AssetIssueV2Store getAssetIssueV2Store();

  AssetIssueStore getAssetIssueStore();

  DynamicPropertiesStore getDynamicPropertiesStore();

  DelegationStore getDelegationStore();

  WitnessStore getWitnessStore();

  AccountCapsule createAccount(byte[] address, Protocol.AccountType type);

  AccountCapsule createAccount(byte[] address, String accountName, Protocol.AccountType type);

  AccountCapsule getAccount(byte[] address);

    BytesCapsule getDynamic(byte[] bytesKey);

  VotesCapsule getVotesCapsule(byte[] address);

  long getBeginCycle(byte[] address);

  long getEndCycle(byte[] address);

  AccountCapsule getAccountVote(long cycle, byte[] address);

  BytesCapsule getDelegationCache(Key key);

  void deleteContract(byte[] address);

  void createContract(byte[] address, ContractCapsule contractCapsule);

  ContractCapsule getContract(byte[] address);

  void updateContract(byte[] address, ContractCapsule contractCapsule);

  void updateAccount(byte[] address, AccountCapsule accountCapsule);

  void updateDynamic(byte[] word, BytesCapsule bytesCapsule);

  void updateVotesCapsule(byte[] word, VotesCapsule votesCapsule);

  void updateBeginCycle(byte[] word, long cycle);

  void updateEndCycle(byte[] word, long cycle);

  void updateAccountVote(byte[] word, long cycle, AccountCapsule accountCapsule);

  void updateRemark(byte[] word, long cycle);

  void updateDelegation(byte[] word, BytesCapsule bytesCapsule);

  void updateLastWithdrawCycle(byte[] address, long cycle);

  void saveCode(byte[] address, byte[] code);

  byte[] getCode(byte[] address);

  void putStorageValue(byte[] address, DataWord key, DataWord value);

  DataWord getStorageValue(byte[] address, DataWord key);

  Storage getStorage(byte[] address);

  long getBalance(byte[] address);

  long addBalance(byte[] address, long value);

  Repository newRepositoryChild();

  void setParent(Repository deposit);

  void commit();

  void putAccount(Key key, Value value);

  void putCode(Key key, Value value);

  void putContract(Key key, Value value);

  void putStorage(Key key, Storage cache);

  void putAccountValue(byte[] address, AccountCapsule accountCapsule);

  void putDynamic(Key key, Value value);

  void putAssetIssue(Key key, Value value);

  void putVotesCapsule(Key key, Value value);

  void putAssetIssueValue(byte[] tokenId, AssetIssueCapsule assetIssueCapsule);

  void putDelegation(Key key, Value value);

  long addTokenBalance(byte[] address, byte[] tokenId, long value);

  long getTokenBalance(byte[] address, byte[] tokenId);

  long getAccountLeftEntropyFromFreeze(AccountCapsule accountCapsule);

  long calculateGlobalEntropyLimit(AccountCapsule accountCapsule);

  byte[] getBlackHoleAddress();

  BlockCapsule getBlockByNum(final long num);

  AccountCapsule createNormalAccount(byte[] address);

  WitnessCapsule getWitnessCapsule(byte[] address);

  void saveTokenIdNum(long num);

  long getTokenIdNum();

  void addTotalPhotonWeight(long amount);

  void saveTotalPhotonWeight(long totalPhotonWeight);

  long getTotalPhotonWeight();
}
