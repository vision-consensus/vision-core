package org.vision.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.vision.common.utils.ByteArray;
import org.vision.core.store.AccountFrozenStageResourceStore;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.protos.Protocol.AccountFrozenStageResource;

import java.util.List;
import java.util.Map;

import static org.vision.core.config.Parameter.ChainConstant.FROZEN_PERIOD;

@Slf4j(topic = "capsule")
public class AccountFrozenStageResourceCapsule implements ProtoCapsule<AccountFrozenStageResource> {

  private AccountFrozenStageResource accountFrozenStageResource;

  public AccountFrozenStageResourceCapsule(final AccountFrozenStageResource accountFrozenStageResource){
    this.accountFrozenStageResource = accountFrozenStageResource;
  }

  public AccountFrozenStageResourceCapsule(final byte[] data) {
    try {
      this.accountFrozenStageResource = AccountFrozenStageResource.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
    }
  }

  public AccountFrozenStageResourceCapsule(byte[] ownerAddress, long stage) {
    this.accountFrozenStageResource = AccountFrozenStageResource.newBuilder()
        .setAddress(ByteString.copyFrom(ownerAddress))
        .setStage(stage)
        .build();
  }

  public static byte[] createDbKey(byte[] ownerAddress, long stage) {
    byte[] stageKey = ByteArray.fromLong(stage);
    byte[] key = new byte[ownerAddress.length + stageKey.length];
    System.arraycopy(ownerAddress, 0, key, 0, ownerAddress.length);
    System.arraycopy(stageKey, 0, key, ownerAddress.length, stageKey.length);
    return key;
  }

  public static boolean dealReFreezeConsideration(AccountCapsule accountCapsule, AccountFrozenStageResourceStore accountFrozenStageResourceStore, DynamicPropertiesStore dynamicStore) {
    boolean refreeze = false;
    byte[] ownerAddress = accountCapsule.getAddress().toByteArray();
    Map<Long, List<Long>> stageWeights = dynamicStore.getVPFreezeStageWeights();
    long now = dynamicStore.getLatestBlockHeaderTimestamp();
    long consider = dynamicStore.getRefreezeConsiderationPeriodResult();
    for (Map.Entry<Long, List<Long>> entry : stageWeights.entrySet()) {
      if (entry.getKey() == 1L) {
        continue;
      }
      byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, entry.getKey());
      AccountFrozenStageResourceCapsule capsule = accountFrozenStageResourceStore.get(key);
      if (capsule == null) {
        continue;
      }

      if (capsule.getInstance().getFrozenBalanceForPhoton() > 0
          && capsule.getInstance().getExpireTimeForPhoton() < now - consider) {
        refreeze = true;
        long cycle = (now - capsule.getInstance().getExpireTimeForPhoton())
            / entry.getValue().get(0) / FROZEN_PERIOD;
        long tmp = capsule.getInstance().getExpireTimeForPhoton() +
            (cycle + 1) * entry.getValue().get(0) * FROZEN_PERIOD;
        capsule.setFrozenBalanceForPhoton(capsule.getInstance().getFrozenBalanceForPhoton(), tmp);
        accountFrozenStageResourceStore.put(key, capsule);
        accountCapsule.setFrozenForPhoton(accountCapsule.getFrozenBalance(),
            Math.max(accountCapsule.getFrozenExpireTime(), tmp));
      }
      if (capsule.getInstance().getFrozenBalanceForEntropy() > 0
          && capsule.getInstance().getExpireTimeForEntropy() < now - consider) {
        refreeze = true;
        long cycle = (now - capsule.getInstance().getExpireTimeForEntropy())
            / entry.getValue().get(0) / FROZEN_PERIOD;
        long tmp = capsule.getInstance().getExpireTimeForEntropy() +
            (cycle + 1) * entry.getValue().get(0) * FROZEN_PERIOD;
        capsule.setFrozenBalanceForEntropy(capsule.getInstance().getFrozenBalanceForEntropy(), tmp);
        accountFrozenStageResourceStore.put(key, capsule);
        accountCapsule.setFrozenForEntropy(accountCapsule.getEntropyFrozenBalance(),
            Math.max(accountCapsule.getEntropyFrozenExpireTime(), tmp));
      }
    }
    return refreeze;
  }

  public static void freezeBalance(byte[] ownerAddress, long stage, long balance, long expireTime, boolean isPhoton, AccountFrozenStageResourceStore accountFrozenStageResourceStore) {
    byte[] key = createDbKey(ownerAddress, stage);
    AccountFrozenStageResourceCapsule capsule = accountFrozenStageResourceStore.get(key);
    if (capsule == null) {
      capsule = new AccountFrozenStageResourceCapsule(ownerAddress, stage);
      if(isPhoton){
        capsule.setFrozenBalanceForPhoton(balance, expireTime);
      } else {
        capsule.setFrozenBalanceForEntropy(balance, expireTime);
      }
    } else {
      if(isPhoton) {
        capsule.addFrozenBalanceForPhoton(balance, expireTime);
      } else {
        capsule.addFrozenBalanceForEntropy(balance, expireTime);
      }
    }
    accountFrozenStageResourceStore.put(key, capsule);
  }

  @Override
  public byte[] getData() {
    return this.accountFrozenStageResource.toByteArray();
  }

  @Override
  public AccountFrozenStageResource getInstance() {
    return this.accountFrozenStageResource;
  }

  public void setFrozenBalanceForPhoton(long balance, long expireTime) {
    this.accountFrozenStageResource = this.accountFrozenStageResource.toBuilder()
        .setFrozenBalanceForPhoton(balance)
        .setExpireTimeForPhoton(expireTime)
        .build();
  }

  public void setFrozenBalanceForEntropy(long balance, long expireTime) {
    this.accountFrozenStageResource = this.accountFrozenStageResource.toBuilder()
        .setFrozenBalanceForEntropy(balance)
        .setExpireTimeForEntropy(expireTime)
        .build();
  }

  public void addFrozenBalanceForPhoton(long balance, long expireTime) {
    this.accountFrozenStageResource = this.accountFrozenStageResource.toBuilder()
        .setFrozenBalanceForPhoton(this.accountFrozenStageResource.getFrozenBalanceForPhoton()
            + balance)
        .setExpireTimeForPhoton(expireTime)
        .build();
  }

  public void addFrozenBalanceForEntropy(long balance, long expireTime) {
    this.accountFrozenStageResource = this.accountFrozenStageResource.toBuilder()
        .setFrozenBalanceForEntropy(this.accountFrozenStageResource.getFrozenBalanceForEntropy()
            + balance)
        .setExpireTimeForEntropy(expireTime)
        .build();
  }
}
