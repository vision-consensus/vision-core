package org.vision.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.protos.Protocol.DelegatedResource;

@Slf4j(topic = "capsule")
public class DelegatedResourceCapsule implements ProtoCapsule<DelegatedResource> {

  private DelegatedResource delegatedResource;

  public DelegatedResourceCapsule(final DelegatedResource delegatedResource) {
    this.delegatedResource = delegatedResource;
  }

  public DelegatedResourceCapsule(final byte[] data) {
    try {
      this.delegatedResource = DelegatedResource.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
    }
  }

  public DelegatedResourceCapsule(ByteString from, ByteString to) {
    this.delegatedResource = DelegatedResource.newBuilder()
        .setFrom(from)
        .setTo(to)
        .build();
  }

  public static byte[] createDbKey(byte[] from, byte[] to) {
    byte[] key = new byte[from.length + to.length];
    System.arraycopy(from, 0, key, 0, from.length);
    System.arraycopy(to, 0, key, from.length, to.length);
    return key;
  }

  public ByteString getFrom() {
    return this.delegatedResource.getFrom();
  }

  public ByteString getTo() {
    return this.delegatedResource.getTo();
  }

  public long getFrozenBalanceForEntropy() {
    return this.delegatedResource.getFrozenBalanceForEntropy();
  }

  public void setFrozenBalanceForEntropy(long entropy, long expireTime) {
    this.delegatedResource = this.delegatedResource.toBuilder()
        .setFrozenBalanceForEntropy(entropy)
        .setExpireTimeForEntropy(expireTime)
        .build();
  }

  public void addFrozenBalanceForEntropy(long entropy, long expireTime) {
    this.delegatedResource = this.delegatedResource.toBuilder()
        .setFrozenBalanceForEntropy(this.delegatedResource.getFrozenBalanceForEntropy() + entropy)
        .setExpireTimeForEntropy(expireTime)
        .build();
  }

  public long getFrozenBalanceForBandwidth() {
    return this.delegatedResource.getFrozenBalanceForPhoton();
  }

  public void setFrozenBalanceForBandwidth(long Bandwidth, long expireTime) {
    this.delegatedResource = this.delegatedResource.toBuilder()
        .setFrozenBalanceForPhoton(Bandwidth)
        .setExpireTimeForPhoton(expireTime)
        .build();
  }

  public void addFrozenBalanceForBandwidth(long Bandwidth, long expireTime) {
    this.delegatedResource = this.delegatedResource.toBuilder()
        .setFrozenBalanceForPhoton(this.delegatedResource.getFrozenBalanceForPhoton()
            + Bandwidth)
        .setExpireTimeForPhoton(expireTime)
        .build();
  }

  public long getExpireTimeForBandwidth() {
    return this.delegatedResource.getExpireTimeForPhoton();
  }

  public void setExpireTimeForBandwidth(long ExpireTime) {
    this.delegatedResource = this.delegatedResource.toBuilder()
        .setExpireTimeForPhoton(ExpireTime)
        .build();
  }

  public long getExpireTimeForEntropy(DynamicPropertiesStore dynamicPropertiesStore) {
    if (dynamicPropertiesStore.getAllowMultiSign() == 0) {
      return this.delegatedResource.getExpireTimeForPhoton();
    } else {
      return this.delegatedResource.getExpireTimeForEntropy();
    }
  }

  public void setExpireTimeForEntropy(long ExpireTime) {
    this.delegatedResource = this.delegatedResource.toBuilder()
        .setExpireTimeForEntropy(ExpireTime)
        .build();
  }

  public byte[] createDbKey() {
    return createDbKey(this.delegatedResource.getFrom().toByteArray(),
        this.delegatedResource.getTo().toByteArray());
  }

  @Override
  public byte[] getData() {
    return this.delegatedResource.toByteArray();
  }

  @Override
  public DelegatedResource getInstance() {
    return this.delegatedResource;
  }

}
