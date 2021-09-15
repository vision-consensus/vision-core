package org.vision.consensus.dpos;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.consensus.ConsensusDelegate;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.WitnessCapsule;

import java.util.List;

import static org.vision.core.config.Parameter.ChainConstant.WITNESS_STANDBY_LENGTH;

@Slf4j(topic = "consensus")
@Component
public class IncentiveManager {

  @Autowired
  private ConsensusDelegate consensusDelegate;


  public void reward(List<ByteString> witnesses) {
    if (consensusDelegate.allowChangeDelegation()) {
      return;
    }
    if (witnesses.size() > WITNESS_STANDBY_LENGTH) {
      witnesses = witnesses.subList(0, WITNESS_STANDBY_LENGTH);
    }
    long voteSum = 0;
    for (ByteString witness : witnesses) {
      WitnessCapsule witnessCapsule = consensusDelegate.getWitness(witness.toByteArray());
      voteSum += Math.min(witnessCapsule.getVoteCountWeight(), witnessCapsule.getVoteCountThreshold());
    }
    if (voteSum <= 0) {
      return;
    }
    long totalPay = consensusDelegate.getWitnessStandbyAllowanceInflation();
    for (ByteString witness : witnesses) {
      byte[] address = witness.toByteArray();
      WitnessCapsule witnessCapsule = consensusDelegate.getWitness(address);
      long voteCountWeight = Math.min(witnessCapsule.getVoteCountWeight(), witnessCapsule.getVoteCountThreshold());
      long pay = (long) (voteCountWeight * ((double) totalPay
              / voteSum));
      AccountCapsule accountCapsule = consensusDelegate.getAccount(address);
      accountCapsule.setAllowance(accountCapsule.getAllowance() + pay);
      consensusDelegate.saveAccount(accountCapsule);
    }
  }
}
