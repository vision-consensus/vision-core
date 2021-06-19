package org.vision.consensus.dpos;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.consensus.ConsensusDelegate;
import org.vision.consensus.pbft.PbftManager;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.capsule.VotesCapsule;
import org.vision.core.capsule.WitnessCapsule;
import org.vision.core.service.MortgageService;
import org.vision.core.store.AccountStore;
import org.vision.core.store.DelegationStore;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.core.store.VotesStore;
import org.vision.protos.Protocol;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.vision.common.utils.WalletUtil.getAddressStringList;

@Slf4j(topic = "consensus")
@Component
public class MaintenanceManager {
  private static final long SR_FREEZE_LOWEST_PRECISION = 1000L;

  private static final long HIGH_EXPANSION_RATE = 2322L;

  private static final long LOW_EXPANSION_RATE = 689L;

  private static final long PLEDGE_RATE_THRESHOLD = 60L;

  private static final long FIRST_ECONOMIC_CYCLE = 42L;

  @Autowired
  private ConsensusDelegate consensusDelegate;

  @Autowired
  private IncentiveManager incentiveManager;

  @Setter
  private DposService dposService;

  @Setter
  private PbftManager pbftManager;

  @Autowired
  private MortgageService mortgageService;

  @Autowired
  private AccountStore accountStore;

  @Getter
  private final List<ByteString> beforeWitness = new ArrayList<>();
  @Getter
  private final List<ByteString> currentWitness = new ArrayList<>();
  @Getter
  private long beforeMaintenanceTime;

  public void init() {
    currentWitness.addAll(consensusDelegate.getActiveWitnesses());
  }

  public void applyBlock(BlockCapsule blockCapsule) {
    long blockNum = blockCapsule.getNum();
    long blockTime = blockCapsule.getTimeStamp();
    long nextMaintenanceTime = consensusDelegate.getNextMaintenanceTime();
    boolean flag = consensusDelegate.getNextMaintenanceTime() <= blockTime;
    if (flag) {
      if (blockNum != 1) {
        updateWitnessValue(beforeWitness);
        beforeMaintenanceTime = nextMaintenanceTime;
        doMaintenance();
        updateWitnessValue(currentWitness);
      }
      consensusDelegate.updateNextMaintenanceTime(blockTime);
      if (blockNum != 1) {
        //pbft sr msg
        pbftManager.srPrePrepare(blockCapsule, currentWitness,
            consensusDelegate.getNextMaintenanceTime());
      }
    }
    consensusDelegate.saveStateFlag(flag ? 1 : 0);
    //pbft block msg
    if (blockNum == 1) {
      nextMaintenanceTime = consensusDelegate.getNextMaintenanceTime();
    }
    pbftManager.blockPrePrepare(blockCapsule, nextMaintenanceTime);
  }

  private void updateWitnessValue(List<ByteString> srList) {
    srList.clear();
    srList.addAll(consensusDelegate.getActiveWitnesses());
  }

  public void doMaintenance() {
    VotesStore votesStore = consensusDelegate.getVotesStore();

    tryRemoveThePowerOfTheGr();

    Map<ByteString, Protocol.Vote.Builder> countWitness = countVote(votesStore);
    if (!countWitness.isEmpty()) {
      List<ByteString> currentWits = consensusDelegate.getActiveWitnesses();

      List<ByteString> newWitnessAddressList = new ArrayList<>();
      consensusDelegate.getAllWitnesses()
              .forEach(witnessCapsule -> newWitnessAddressList.add(witnessCapsule.getAddress()));

      countWitness.forEach((address, voteBuilder) -> {
        byte[] witnessAddress = address.toByteArray();
        WitnessCapsule witnessCapsule = consensusDelegate.getWitness(witnessAddress);
        if (witnessCapsule == null) {
          logger.warn("Witness capsule is null. address is {}", Hex.toHexString(witnessAddress));
          return;
        }
        AccountCapsule account = consensusDelegate.getAccount(witnessAddress);
        if (account == null) {
          logger.warn("Witness account is null. address is {}", Hex.toHexString(witnessAddress));
          return;
        }

        DynamicPropertiesStore dynamicPropertiesStore = consensusDelegate.getDynamicPropertiesStore();
        long maxVoteCounts = (long) ((account.getSRGuaranteeFrozenBalance() - dynamicPropertiesStore.getSrFreezeLowest())
                /(dynamicPropertiesStore.getSrFreezeLowestPercent() / SR_FREEZE_LOWEST_PRECISION));

        witnessCapsule.setVoteCountWeight(witnessCapsule.getVoteCountWeight() + voteBuilder.getVoteCountWeight());
        witnessCapsule.setVoteCount(witnessCapsule.getVoteCount() + voteBuilder.getVoteCount());
        witnessCapsule.setVoteCountThreshold(maxVoteCounts);
        // witnessCapsule.setVoteCount(witnessCapsule.getVoteCount() + voteCount);
        consensusDelegate.saveWitness(witnessCapsule);
        logger.info("address is {} , countVote is {}", witnessCapsule.createReadableString(),
                witnessCapsule.getVoteCount());
      });

      dposService.updateWitness(newWitnessAddressList);

      incentiveManager.reward(newWitnessAddressList);

      List<ByteString> newWits = consensusDelegate.getActiveWitnesses();
      if (!CollectionUtils.isEqualCollection(currentWits, newWits)) {
        currentWits.forEach(address -> {
          WitnessCapsule witnessCapsule = consensusDelegate.getWitness(address.toByteArray());
          witnessCapsule.setIsJobs(false);
          consensusDelegate.saveWitness(witnessCapsule);
        });
        newWits.forEach(address -> {
          WitnessCapsule witnessCapsule = consensusDelegate.getWitness(address.toByteArray());
          witnessCapsule.setIsJobs(true);
          consensusDelegate.saveWitness(witnessCapsule);
        });
      }

      logger.info("Update witness success. \nbefore: {} \nafter: {}",
              getAddressStringList(currentWits),
              getAddressStringList(newWits));
    }

    DynamicPropertiesStore dynamicPropertiesStore = consensusDelegate.getDynamicPropertiesStore();
    DelegationStore delegationStore = consensusDelegate.getDelegationStore();
    if (dynamicPropertiesStore.allowChangeDelegation()) {
      long nextCycle = dynamicPropertiesStore.getCurrentCycleNumber() + 1;
      dynamicPropertiesStore.saveCurrentCycleNumber(nextCycle);
      consensusDelegate.getAllWitnesses().forEach(witness -> {
        delegationStore.setBrokerage(nextCycle, witness.createDbKey(),
            delegationStore.getBrokerage(witness.createDbKey()));
        delegationStore.setWitnessVote(nextCycle, witness.createDbKey(), witness.getVoteCountWeight());
        //spread mint total freeze
        delegationStore.setTotalFreezeBalanceForSpreadMint(nextCycle, consensusDelegate.getDynamicPropertiesStore().getTotalSpreadMintWeight());
      });
    }
    calculationCyclePledgeRate();
    long cycle = dynamicPropertiesStore.getCurrentCycleNumber();
    long economicCycle = dynamicPropertiesStore.getEconomicCycle();
    if (FIRST_ECONOMIC_CYCLE == cycle) {
      long beginCycle = 1;
      long endCycle = cycle;
      long pledgeRate = savePledgeRate(beginCycle, endCycle, FIRST_ECONOMIC_CYCLE);
      saveExpansionRate(pledgeRate);
    } else if ((cycle - FIRST_ECONOMIC_CYCLE) % economicCycle == 0) {
      long economicCycleNumber = (cycle - FIRST_ECONOMIC_CYCLE) / economicCycle;
      long beginCycle = (economicCycleNumber - 1) * economicCycle + FIRST_ECONOMIC_CYCLE + 1;
      long endCycle = cycle;
      long pledgeRate = savePledgeRate(beginCycle, endCycle, economicCycle);
      saveExpansionRate(pledgeRate);
    }
  }

  private Map<ByteString, Protocol.Vote.Builder> countVote(VotesStore votesStore) {
    final Map<ByteString, Protocol.Vote.Builder> countWitness = Maps.newHashMap();
    Iterator<Entry<byte[], VotesCapsule>> dbIterator = votesStore.iterator();
    long sizeCount = 0;
    while (dbIterator.hasNext()) {
      Entry<byte[], VotesCapsule> next = dbIterator.next();
      VotesCapsule votes = next.getValue();
      votes.getOldVotes().forEach(vote -> {
        ByteString voteAddress = vote.getVoteAddress();
        long voteCount = vote.getVoteCount();
        long voteCountWeight = vote.getVoteCountWeight();
        if (countWitness.containsKey(voteAddress)) {
          Protocol.Vote.Builder voteValue = countWitness.get(voteAddress);
          voteValue.setVoteCount(voteValue.getVoteCount() - voteCount);
          voteValue.setVoteCountWeight(voteValue.getVoteCountWeight() - voteCountWeight);
          countWitness.put(voteAddress, voteValue);
        } else {
          Protocol.Vote.Builder voteValue = Protocol.Vote.newBuilder();
          voteValue.setVoteCount(-voteCount);
          voteValue.setVoteCountWeight(-voteCountWeight);
          countWitness.put(voteAddress, voteValue);
        }
      });
      votes.getNewVotes().forEach(vote -> {
        ByteString voteAddress = vote.getVoteAddress();
        long voteCountWeight = vote.getVoteCountWeight();
        long voteCount = vote.getVoteCount();
        if (countWitness.containsKey(voteAddress)) {

          Protocol.Vote.Builder voteValue = countWitness.get(voteAddress);
          voteValue.setVoteCount(voteValue.getVoteCount() + voteCount);
          voteValue.setVoteCountWeight(voteValue.getVoteCountWeight() + voteCountWeight);
          countWitness.put(voteAddress, voteValue);
        } else {
          Protocol.Vote.Builder voteValue = Protocol.Vote.newBuilder();
          voteValue.setVoteCount(voteCount);
          voteValue.setVoteCountWeight(voteCountWeight);
          countWitness.put(voteAddress, voteValue);
        }
      });
      sizeCount++;
      votesStore.delete(next.getKey());
    }
    logger.info("There is {} new votes in this epoch", sizeCount);
    return countWitness;
  }

  private Map<ByteString, Long> countVoteOld(VotesStore votesStore) {
    final Map<ByteString, Long> countWitness = Maps.newHashMap();
    Iterator<Entry<byte[], VotesCapsule>> dbIterator = votesStore.iterator();
    long sizeCount = 0;
    while (dbIterator.hasNext()) {
      Entry<byte[], VotesCapsule> next = dbIterator.next();
      VotesCapsule votes = next.getValue();
      votes.getOldVotes().forEach(vote -> {
        ByteString voteAddress = vote.getVoteAddress();
        long voteCount = vote.getVoteCount();
        if (countWitness.containsKey(voteAddress)) {
          countWitness.put(voteAddress, countWitness.get(voteAddress) - voteCount);
        } else {
          countWitness.put(voteAddress, -voteCount);
        }
      });
      votes.getNewVotes().forEach(vote -> {
        ByteString voteAddress = vote.getVoteAddress();
        long voteCountWeight = vote.getVoteCountWeight();
        if (countWitness.containsKey(voteAddress)) {
          countWitness.put(voteAddress, countWitness.get(voteAddress) + voteCountWeight);
        } else {
          countWitness.put(voteAddress, voteCountWeight);
        }
      });
      sizeCount++;
      votesStore.delete(next.getKey());
    }
    logger.info("There is {} new votes in this epoch", sizeCount);
    return countWitness;
  }

  private void tryRemoveThePowerOfTheGr() {
    if (consensusDelegate.getRemoveThePowerOfTheGr() != 1) {
      return;
    }
    dposService.getGenesisBlock().getWitnesses().forEach(witness -> {
      WitnessCapsule witnessCapsule = consensusDelegate.getWitness(witness.getAddress());
      witnessCapsule.setVoteCount(witnessCapsule.getVoteCount() - witness.getVoteCount());
      consensusDelegate.saveWitness(witnessCapsule);
    });
    consensusDelegate.saveRemoveThePowerOfTheGr(-1);
  }

  private void calculationCyclePledgeRate() {
    DynamicPropertiesStore dynamicPropertiesStore = consensusDelegate.getDynamicPropertiesStore();
    long cycle = dynamicPropertiesStore.getCurrentCycleNumber();
    long totalPhotonWeight = dynamicPropertiesStore.getTotalPhotonWeight();
    BigDecimal bigTotalPhotonWeight = new BigDecimal(totalPhotonWeight);
    long totalEntropyWeight = dynamicPropertiesStore.getTotalEntropyWeight();
    BigDecimal bigTotalEntropyWeight = new BigDecimal(totalEntropyWeight);
    long totalSRGuaranteeWeight = dynamicPropertiesStore.getTotalSRGuaranteeWeight();
    BigDecimal bigTotalSRGuaranteeWeight = new BigDecimal(totalSRGuaranteeWeight);
    long voteSum = mortgageService.getVoteSum();
    BigDecimal bigVoteSum = new BigDecimal(voteSum);
    long totalAssets = dynamicPropertiesStore.getTotalAssets();
    BigDecimal bigTotalAssets = new BigDecimal(totalAssets);
    long totalSpreadMintWeight = dynamicPropertiesStore.getTotalSpreadMintWeight();
    BigDecimal bigTotalSpreadMintWeight = new BigDecimal(totalSpreadMintWeight);
    BigDecimal pledgeAmount= bigTotalPhotonWeight.add(bigTotalEntropyWeight).add(bigTotalSRGuaranteeWeight);
    long initialReservedAmount = dynamicPropertiesStore.getInitialReservedAmount();
    BigDecimal bigInitialReservedAmount = new BigDecimal(initialReservedAmount);
    long galaxyBalance = accountStore.getGalaxy().getBalance();
    BigDecimal bigGalaxyBalance = new BigDecimal(galaxyBalance);
    long singularityBalance = accountStore.getSingularity().getBalance();
    BigDecimal bigSingularityBalance = new BigDecimal(singularityBalance);
    BigDecimal assets= bigTotalAssets.add(bigTotalSpreadMintWeight).subtract(bigTotalPhotonWeight).subtract(bigTotalEntropyWeight).add(bigVoteSum)
            .add(bigInitialReservedAmount).subtract(bigGalaxyBalance).subtract(bigSingularityBalance);
    long cyclePledgeRate = pledgeAmount.divide(assets,2,BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)).longValue();
    consensusDelegate.getDelegationStore().addCyclePledgeRate(cycle,cyclePledgeRate);
  }

  private void saveExpansionRate(long pledgeRate) {
    if (PLEDGE_RATE_THRESHOLD <= pledgeRate) {
      consensusDelegate.getDynamicPropertiesStore().saveExpansionRate(LOW_EXPANSION_RATE);
    } else {
      consensusDelegate.getDynamicPropertiesStore().saveExpansionRate(HIGH_EXPANSION_RATE);
    }
  }

  private long savePledgeRate(long beginCycle, long endCycle, long economicCycle) {
    long totalPledgeRate = 0L;
    for (long i = beginCycle; i <= endCycle; i++) {
      long cyclePledgeRate = consensusDelegate.getDelegationStore().getCyclePledgeRate(i);
      totalPledgeRate += cyclePledgeRate;
    }
    BigDecimal bigTotalPledgeRate = new BigDecimal(totalPledgeRate);
    BigDecimal bigEconomicCycle = new BigDecimal(economicCycle);
    long pledgeRate = bigTotalPledgeRate.divide(bigEconomicCycle,0,BigDecimal.ROUND_HALF_UP).longValue();
    consensusDelegate.getDynamicPropertiesStore().savePledgeRate(pledgeRate);
    return pledgeRate;
  }

}
