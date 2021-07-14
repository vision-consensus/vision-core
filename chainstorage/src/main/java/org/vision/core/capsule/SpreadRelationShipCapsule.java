package org.vision.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.vision.protos.Protocol.SpreadRelationShip;

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

  public ByteString getOwner() {
    return this.spreadRelationShip.getOwner();
  }

  public ByteString getParent() {
    return this.spreadRelationShip.getParent();
  }

  public long getFrozenBalanceForSpread() {
    return this.spreadRelationShip.getFrozenBalanceForSpread();
  }

  public void setFrozenBalanceForSpread(long entropy, long expireTime) {
    this.spreadRelationShip = this.spreadRelationShip.toBuilder()
        .setFrozenBalanceForSpread(entropy)
        .setExpireTimeForSpread(expireTime)
        .build();
  }

  public void addFrozenBalanceForSpread(long spread, long expireTime) {
    this.spreadRelationShip = this.spreadRelationShip.toBuilder()
        .setFrozenBalanceForSpread(this.spreadRelationShip.getFrozenBalanceForSpread() + spread)
        .setExpireTimeForSpread(expireTime)
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

}
