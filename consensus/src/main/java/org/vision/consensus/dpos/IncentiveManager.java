package org.vision.consensus.dpos;

import static org.vision.core.config.Parameter.ChainConstant.WITNESS_STANDBY_LENGTH;

import com.google.protobuf.ByteString;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.common.utils.ActuatorRateUtil;
import org.vision.consensus.ConsensusDelegate;
import org.vision.core.ChainBaseManager;
import org.vision.core.capsule.AccountCapsule;
import org.vision.protos.Protocol;

@Slf4j(topic = "consensus")
@Component
public class IncentiveManager {

  @Autowired
  private ConsensusDelegate consensusDelegate;

  @Autowired
  private ChainBaseManager chainBaseManager;

  public void reward(List<ByteString> witnesses) {
    if (consensusDelegate.allowChangeDelegation()) {
      return;
    }
    if (witnesses.size() > WITNESS_STANDBY_LENGTH) {
      witnesses = witnesses.subList(0, WITNESS_STANDBY_LENGTH);
    }
    long voteSum = 0;
    for (ByteString witness : witnesses) {
      voteSum += consensusDelegate.getWitness(witness.toByteArray()).getVoteCount();
    }
    if (voteSum <= 0) {
      return;
    }
    long totalPay = consensusDelegate.getWitnessStandbyAllowance();
    for (ByteString witness : witnesses) {
      byte[] address = witness.toByteArray();
      long pay = (long) (consensusDelegate.getWitness(address).getVoteCount() * ((double) totalPay
          / voteSum));
      Protocol.Block lstBlock = chainBaseManager.getBlockStore().getBlockByLatestNum(1).get(0).getInstance();
      AccountCapsule accountCapsule = consensusDelegate.getAccount(address);
      accountCapsule.setAllowance(accountCapsule.getAllowance() + pay * ActuatorRateUtil.getActuatorRate(ActuatorRateUtil.getActuatorRate(lstBlock.getBlockHeader().getRawData().getNumber())));
      consensusDelegate.saveAccount(accountCapsule);
    }
  }
}
