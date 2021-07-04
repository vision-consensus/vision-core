package org.vision.core.capsule;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.vision.common.utils.ByteArray;
import org.vision.protos.Protocol.Proposal;
import org.vision.protos.Protocol.Proposal.State;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.vision.common.utils.WalletUtil.getAddressStringList;
import static org.vision.core.config.Parameter.ChainConstant.MAX_ACTIVE_WITNESS_NUM;

@Slf4j(topic = "capsule")
public class ProposalCapsule implements ProtoCapsule<Proposal> {

  private Proposal proposal;

  public ProposalCapsule(final Proposal proposal) {
    this.proposal = proposal;
  }

  public ProposalCapsule(final byte[] data) {
    try {
      this.proposal = Proposal.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
    }
  }

  public ProposalCapsule(ByteString address, final long id) {
    this.proposal = Proposal.newBuilder()
        .setProposerAddress(address)
        .setProposalId(id)
        .build();
  }

  public static byte[] calculateDbKey(long number) {
    return ByteArray.fromLong(number);
  }

  public long getID() {
    return this.proposal.getProposalId();
  }

  public void setID(long id) {
    this.proposal = this.proposal.toBuilder()
        .setProposalId(id)
        .build();
  }

  public ByteString getProposalAddress() {
    return this.proposal.getProposerAddress();
  }

  public void setProposalAddress(ByteString address) {
    this.proposal = this.proposal.toBuilder()
        .setProposerAddress(address)
        .build();
  }

  public Map<Long, Long> getParameters() {
    return this.proposal.getParametersMap();
  }

  public void setParameters(Map<Long, Long> parameters) {
    this.proposal = this.proposal.toBuilder()
        .putAllParameters(parameters)
        .build();
  }

  public Map<Long, String> getStringParameters() {
    return this.proposal.getStringParametersMap();
  }

  public void setStringParameters(Map<Long, String> stringParameters) {
    this.proposal = this.proposal.toBuilder()
            .putAllStringParameters(stringParameters)
            .build();
  }

  public long getExpirationTime() {
    return this.proposal.getExpirationTime();
  }

  public void setExpirationTime(long time) {
    this.proposal = this.proposal.toBuilder()
        .setExpirationTime(time)
        .build();
  }

  public long getCreateTime() {
    return this.proposal.getCreateTime();
  }

  public void setCreateTime(long time) {
    this.proposal = this.proposal.toBuilder()
        .setCreateTime(time)
        .build();
  }

  public List<ByteString> getApprovals() {
    return this.proposal.getApprovalsList();
  }

  public void removeApproval(ByteString address) {
    List<ByteString> approvals = Lists.newArrayList();
    approvals.addAll(getApprovals());
    approvals.remove(address);
    this.proposal = this.proposal.toBuilder()
        .clearApprovals()
        .addAllApprovals(approvals)
        .build();
  }

  public void clearApproval() {
    this.proposal = this.proposal.toBuilder().clearApprovals().build();
  }

  public void addApproval(ByteString committeeAddress) {
    this.proposal = this.proposal.toBuilder()
        .addApprovals(committeeAddress)
        .build();
  }

  public State getState() {
    return this.proposal.getState();
  }

  public void setState(State state) {
    this.proposal = this.proposal.toBuilder()
        .setState(state)
        .build();
  }

  public boolean hasProcessed() {
    return this.proposal.getState().equals(State.DISAPPROVED) || this.proposal.getState()
        .equals(State.APPROVED);
  }

  public boolean hasCanceled() {
    return this.proposal.getState().equals(State.CANCELED);
  }

  public boolean hasExpired(long time) {
    return this.proposal.getExpirationTime() <= time;
  }

  public byte[] createDbKey() {
    return calculateDbKey(getID());
  }

  @Override
  public byte[] getData() {
    return this.proposal.toByteArray();
  }

  @Override
  public Proposal getInstance() {
    return this.proposal;
  }

  public boolean hasMostApprovals(List<ByteString> activeWitnesses) {
    long count = this.proposal.getApprovalsList().stream()
        .filter(witness -> activeWitnesses.contains(witness)).count();
    if (count != this.proposal.getApprovalsCount()) {
      List<ByteString> InvalidApprovalList = this.proposal.getApprovalsList().stream()
          .filter(witness -> !activeWitnesses.contains(witness)).collect(Collectors.toList());
      logger.info("InvalidApprovalList:" + getAddressStringList(InvalidApprovalList));
    }
    if (activeWitnesses.size() != MAX_ACTIVE_WITNESS_NUM) {
      logger.info("activeWitnesses size = {}", activeWitnesses.size());
    }
    return count >= activeWitnesses.size() * 7 / 10;
  }
}
