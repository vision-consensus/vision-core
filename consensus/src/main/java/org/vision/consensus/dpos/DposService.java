package org.vision.consensus.dpos;

import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.common.args.GenesisBlock;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.ByteArray;
import org.vision.consensus.ConsensusDelegate;
import org.vision.consensus.base.BlockHandle;
import org.vision.consensus.base.ConsensusInterface;
import org.vision.consensus.base.Param;
import org.vision.consensus.base.Param.Miner;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.capsule.WitnessCapsule;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static org.vision.core.config.Parameter.ChainConstant.MAX_ACTIVE_WITNESS_NUM;
import static org.vision.core.config.Parameter.ChainConstant.SOLIDIFIED_THRESHOLD;

@Slf4j(topic = "consensus")
@Component
public class DposService implements ConsensusInterface {

  @Autowired
  private ConsensusDelegate consensusDelegate;

  @Autowired
  private DposTask dposTask;

  @Autowired
  private DposSlot dposSlot;

  @Autowired
  private StateManager stateManager;

  @Autowired
  private StatisticManager statisticManager;

  @Autowired
  private MaintenanceManager maintenanceManager;

  @Getter
  @Setter
  private volatile boolean needSyncCheck;
  @Getter
  private volatile boolean enable;
  @Getter
  private int minParticipationRate;
  @Getter
  private int blockProduceTimeoutPercent;
  @Getter
  private long genesisBlockTime;
  @Getter
  private BlockHandle blockHandle;
  @Getter
  private GenesisBlock genesisBlock;
  @Getter
  private Map<ByteString, Miner> miners = new HashMap<>();

  @Override
  public void start(Param param) {
    this.enable = param.isEnable();
    this.needSyncCheck = param.isNeedSyncCheck();
    this.minParticipationRate = param.getMinParticipationRate();
    this.blockProduceTimeoutPercent = param.getBlockProduceTimeoutPercent();
    this.blockHandle = param.getBlockHandle();
    this.genesisBlock = param.getGenesisBlock();
    this.genesisBlockTime = Long.parseLong(param.getGenesisBlock().getTimestamp());
    param.getMiners().forEach(miner -> miners.put(miner.getWitnessAddress(), miner));

    dposTask.setDposService(this);
    dposSlot.setDposService(this);
    stateManager.setDposService(this);
    maintenanceManager.setDposService(this);

    if (consensusDelegate.getLatestBlockHeaderNumber() == 0) {
      List<ByteString> witnesses = new ArrayList<>();
      consensusDelegate.getAllWitnesses().forEach(witnessCapsule ->
          witnesses.add(witnessCapsule.getAddress()));
      updateWitness(witnesses);
      List<ByteString> addresses = consensusDelegate.getActiveWitnesses();
      addresses.forEach(address -> {
        WitnessCapsule witnessCapsule = consensusDelegate.getWitness(address.toByteArray());
        witnessCapsule.setIsJobs(true);
        consensusDelegate.saveWitness(witnessCapsule);
      });
    }
    maintenanceManager.init();
    dposTask.init();
  }

  @Override
  public void stop() {
    dposTask.stop();
  }

  @Override
  public void receiveBlock(BlockCapsule blockCapsule) {
    stateManager.receiveBlock(blockCapsule);
  }

  @Override
  public boolean validBlock(BlockCapsule blockCapsule) {
    if (consensusDelegate.getLatestBlockHeaderNumber() == 0) {
      return true;
    }
    ByteString witnessAddress = blockCapsule.getWitnessAddress();
    long timeStamp = blockCapsule.getTimeStamp();
    long bSlot = dposSlot.getAbSlot(timeStamp);
    long hSlot = dposSlot.getAbSlot(consensusDelegate.getLatestBlockHeaderTimestamp());
    if (bSlot <= hSlot) {
      logger.warn("ValidBlock failed: bSlot: {} <= hSlot: {}", bSlot, hSlot);
      return false;
    }

    long slot = dposSlot.getSlot(timeStamp);
    final ByteString scheduledWitness = dposSlot.getScheduledWitness(slot);
    if (!scheduledWitness.equals(witnessAddress)) {
      logger.warn("ValidBlock failed: sWitness: {}, bWitness: {}, bTimeStamp: {}, slot: {}",
          ByteArray.toHexString(scheduledWitness.toByteArray()),
          ByteArray.toHexString(witnessAddress.toByteArray()), new DateTime(timeStamp), slot);
      return false;
    }

    return true;
  }

  @Override
  public boolean applyBlock(BlockCapsule blockCapsule) {
    statisticManager.applyBlock(blockCapsule);
    maintenanceManager.applyBlock(blockCapsule);
    updateSolidBlock();
    return true;
  }

  private void updateSolidBlock() {
//    List<Long> numbers = consensusDelegate.getActiveWitnesses().stream()
//        .map(address -> consensusDelegate.getWitness(address.toByteArray()).getLatestBlockNum())
//        .sorted()
//        .collect(Collectors.toList());
    List<Long> numbers = new ArrayList<>();
    for (ByteString item : consensusDelegate.getActiveWitnesses()) {
      WitnessCapsule witness = consensusDelegate.getWitness(item.toByteArray());
      if (witness.getLatestBlockNum() > 0) {
        numbers.add(witness.getLatestBlockNum());
      }
    }
    Collections.sort(numbers);
    StringBuilder numsb = new StringBuilder();
    numbers.forEach(n-> numsb.append(String.valueOf(n)).append(", "));
    StringBuilder addsb = new StringBuilder();
    consensusDelegate.getActiveWitnesses().forEach(addr -> addsb.append(ByteArray.toHexString(addr.toByteArray()))
            .append(", "));
    logger.info("number details: "+ numsb.toString());
    logger.info("address details: "+ addsb.toString());
    long size = consensusDelegate.getActiveWitnesses().size();
    int position = (int) (size * (1 - SOLIDIFIED_THRESHOLD * 1.0 / 100));
    long newSolidNum = numbers.get(position);
    long oldSolidNum = consensusDelegate.getLatestSolidifiedBlockNum();
    logger.info(" number size:"+ numbers.size()+"size:"+ size+" position:"+ position);
    if (newSolidNum < oldSolidNum) {
      logger.warn("Update solid block number failed, new: {} < old: {}", newSolidNum, oldSolidNum);
      return;
    }
    CommonParameter.getInstance()
        .setOldSolidityBlockNum(consensusDelegate.getLatestSolidifiedBlockNum());
    consensusDelegate.saveLatestSolidifiedBlockNum(newSolidNum);
    logger.info("Update solid block number to {}", newSolidNum);
  }

  public void updateWitness(List<ByteString> list) {
    list.sort(Comparator.comparingLong((ByteString b) ->
    {
      WitnessCapsule witnessCapsule = consensusDelegate.getWitness(b.toByteArray());
      return Math.min(witnessCapsule.getVoteCountWeight(), witnessCapsule.getVoteCountThreshold());
    })
        .reversed()
        .thenComparing(Comparator.comparingInt(ByteString::hashCode).reversed()));

    if (list.size() > MAX_ACTIVE_WITNESS_NUM) {
      consensusDelegate
          .saveActiveWitnesses(list.subList(0, MAX_ACTIVE_WITNESS_NUM));
    } else {
      consensusDelegate.saveActiveWitnesses(list);
    }
  }

}
