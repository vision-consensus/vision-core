package org.vision.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.vision.protos.Protocol.FreezeAccount;

import java.util.List;


@Slf4j(topic = "capsule")
public class FreezeAccountCapsule implements ProtoCapsule<FreezeAccount> {

  private FreezeAccount freezeAccount;

  public FreezeAccountCapsule(final FreezeAccount freezeAccount) {
    this.freezeAccount = freezeAccount;
  }

  public FreezeAccountCapsule(final byte[] data) {
    try {
      this.freezeAccount = FreezeAccount.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
    }
  }

  public FreezeAccountCapsule(List<ByteString> freezeAccounts) {
    this.freezeAccount = FreezeAccount.newBuilder()
            .addAllFreezeAccounts(freezeAccounts)
            .build();
  }


  public void setAllAddresses(List<ByteString> freezeAccounts) {
    this.freezeAccount = this.freezeAccount.toBuilder()
            .clearFreezeAccounts()
            .addAllFreezeAccounts(freezeAccounts)
            .build();
  }

  public void addAddress(ByteString freezeAddress) {
    this.freezeAccount = this.freezeAccount.toBuilder()
            .addFreezeAccounts(freezeAddress)
            .build();
  }

  public void addAllAddress(List<ByteString> freezeAccounts) {
    this.freezeAccount = this.freezeAccount.toBuilder()
            .addAllFreezeAccounts(freezeAccounts)
            .build();
  }

  public List<ByteString> getAddressesList() {
    return this.freezeAccount.getFreezeAccountsList();
  }

  public boolean checkFreeze(ByteString address) {
    return this.getAddressesList().contains(address);
  }

  public byte[] createFreezeAccountDbKey() {
    return "FREEZE_ACCOUNT_KEY".getBytes();
  }

  @Override
  public byte[] getData() {
    return this.freezeAccount.toByteArray();
  }

  @Override
  public FreezeAccount getInstance() {
    return this.freezeAccount;
  }

}
