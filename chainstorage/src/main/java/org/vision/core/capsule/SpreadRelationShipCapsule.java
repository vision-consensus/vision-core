package org.vision.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.vision.common.parameter.CommonParameter;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.core.store.SpreadRelationShipStore;
import org.vision.protos.Protocol.SpreadRelationShip;

import static org.vision.core.config.Parameter.ChainConstant.FROZEN_PERIOD;

@Slf4j(topic = "capsule")
public class SpreadRelationShipCapsule implements ProtoCapsule<SpreadRelationShip> {

  private SpreadRelationShip spreadRelationShip;

  public SpreadRelationShipCapsule(final SpreadRelationShip spreadRelationShip) {
    this.spreadRelationShip = spreadRelationShip;
  }

  public SpreadRelationShipCapsule(final byte[] data) {
    try {
      this.spreadRelationShip = SpreadRelationShip.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
    }
  }

  public SpreadRelationShipCapsule(ByteString owner, ByteString parent) {
    this.spreadRelationShip = spreadRelationShip.newBuilder()
        .setOwner(owner)
        .setParent(parent)
        .build();
  }

  public static byte[] createDbKey(byte[] from, byte[] to) {
    byte[] key = new byte[from.length + to.length];
    System.arraycopy(from, 0, key, 0, from.length);
    System.arraycopy(to, 0, key, from.length, to.length);
    return key;
  }

  public static boolean dealSpreadReFreezeConsideration(AccountCapsule accountCapsule, SpreadRelationShipStore spreadRelationShipStore, DynamicPropertiesStore dynamicStore) {
    byte[] ownerAddress = accountCapsule.getAddress().toByteArray();
    long spreadConsider = dynamicStore.getSpreadRefreezeConsiderationPeriod() * FROZEN_PERIOD;
    long spreadBalance = accountCapsule.getAccountResource().getFrozenBalanceForSpread().getFrozenBalance();
    long spreadExpireTime = accountCapsule.getAccountResource().getFrozenBalanceForSpread().getExpireTime();
    long now = dynamicStore.getLatestBlockHeaderTimestamp();
    boolean refreeze = false;
    if (spreadBalance > 0 && spreadExpireTime < now - spreadConsider) {
      refreeze = true;
      long cycle = (now - spreadExpireTime) / FROZEN_PERIOD / dynamicStore.getSpreadFreezePeriodLimit();
      spreadExpireTime += (cycle + 1) * dynamicStore.getSpreadFreezePeriodLimit() * FROZEN_PERIOD;
      accountCapsule.setFrozenForSpread(spreadBalance, spreadExpireTime);

      SpreadRelationShipCapsule spreadRelationShipCapsule = spreadRelationShipStore.get(ownerAddress);
      if (spreadRelationShipCapsule != null) {
        spreadRelationShipCapsule.setFrozenBalanceForSpread(spreadBalance, spreadExpireTime, dynamicStore.getCurrentCycleNumber());
        if (dynamicStore.getLatestBlockHeaderNumber() >= CommonParameter.PARAMETER.spreadMintUnfreezeClearRelationShipEffectBlockNum){
          spreadRelationShipStore.put(ownerAddress, spreadRelationShipCapsule);
        }
      }
    }
    return refreeze;
  }

  public ByteString getOwner() {
    return this.spreadRelationShip.getOwner();
  }

  public ByteString getParent() {
    return this.spreadRelationShip.getParent();
  }

  public long getFrozenBalanceForSpread() {
    return this.spreadRelationShip.getFrozenBalanceForSpread();
  }

  public void setFrozenBalanceForSpread(long spread, long expireTime, long cycle) {
    this.spreadRelationShip = this.spreadRelationShip.toBuilder()
        .setFrozenBalanceForSpread(spread)
        .setExpireTimeForSpread(expireTime)
        .setFrozenCycle(cycle)
        .build();
  }

  public void addFrozenBalanceForSpread(long spread, long expireTime, long cycle) {
    this.spreadRelationShip = this.spreadRelationShip.toBuilder()
        .setFrozenBalanceForSpread(this.spreadRelationShip.getFrozenBalanceForSpread() + spread)
        .setExpireTimeForSpread(expireTime)
        .setFrozenCycle(cycle)
        .build();
  }

  public long getExpireTimeForSpread() {
    return this.spreadRelationShip.getExpireTimeForSpread();
  }

  public void setExpireTimeForSpread(long ExpireTime) {
    this.spreadRelationShip = this.spreadRelationShip.toBuilder()
        .setExpireTimeForSpread(ExpireTime)
        .build();
  }

  public byte[] createDbKey() {
    return createDbKey(this.spreadRelationShip.getOwner().toByteArray(),
        this.spreadRelationShip.getParent().toByteArray());
  }

  @Override
  public byte[] getData() {
    return this.spreadRelationShip.toByteArray();
  }

  @Override
  public SpreadRelationShip getInstance() {
    return this.spreadRelationShip;
  }

  public long getFrozenCycle(){
    SpreadRelationShip spreadRelationShip = getInstance();
    if (spreadRelationShip == null){
      return 0;
    }
    return spreadRelationShip.getFrozenCycle();
  }

  public void setFrozenCycle(long cycle){
    this.spreadRelationShip = this.spreadRelationShip.toBuilder()
            .setFrozenCycle(cycle)
            .build();
  }
}
