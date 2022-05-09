package org.vision.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.vision.common.utils.ByteArray;
import org.vision.protos.Protocol.AccountFrozenStageResource;

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

  public void setDelegatedFrozenBalanceForPhoton(long balance) {
    this.accountFrozenStageResource = this.accountFrozenStageResource.toBuilder()
        .setDelegatedFrozenBalanceForPhoton(balance)
        .build();
  }

  public void setDelegatedFrozenBalanceForEntropy(long balance) {
    this.accountFrozenStageResource = this.accountFrozenStageResource.toBuilder()
        .setDelegatedFrozenBalanceForEntropy(balance)
        .build();
  }

  public void addDelegatedFrozenBalanceForPhoton(long balance) {
    this.accountFrozenStageResource = this.accountFrozenStageResource.toBuilder()
        .setDelegatedFrozenBalanceForPhoton(
            this.accountFrozenStageResource.getDelegatedFrozenBalanceForPhoton() + balance)
        .build();
  }

  public void addDelegatedFrozenBalanceForEntropy(long balance) {
    this.accountFrozenStageResource = this.accountFrozenStageResource.toBuilder()
        .setDelegatedFrozenBalanceForEntropy(
            this.accountFrozenStageResource.getDelegatedFrozenBalanceForEntropy() + balance)
        .build();
  }
}
