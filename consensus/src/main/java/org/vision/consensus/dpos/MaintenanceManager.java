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
import org.vision.common.args.Witness;
import org.vision.common.parameter.CommonParameter;
import org.vision.consensus.ConsensusDelegate;
import org.vision.consensus.pbft.PbftManager;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.capsule.VotesCapsule;
import org.vision.core.capsule.WitnessCapsule;
import org.vision.core.config.Parameter;
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
import static org.vision.core.config.Parameter.ChainConstant.VS_PRECISION;

@Slf4j(topic = "consensus")
@Component
public class MaintenanceManager {

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

    if(consensusDelegate.getDynamicPropertiesStore().getLatestBlockHeaderNumber() >= CommonParameter.PARAMETER.witnessSortEffectBlockNum){
      consensusDelegate.getAllWitnesses().forEach(witnessCapsule -> {
        AccountCapsule account = consensusDelegate.getAccount(witnessCapsule.getAddress().toByteArray());
        DynamicPropertiesStore dynamicPropertiesStore = consensusDelegate.getDynamicPropertiesStore();
        long fvGuaranteeFrozenBalance = account.getFVGuaranteeFrozenBalance();
        if (fvGuaranteeFrozenBalance > dynamicPropertiesStore.getSrFreezeLowest()) {
          long fvGuaranteeGain =  (fvGuaranteeFrozenBalance - dynamicPropertiesStore.getSrFreezeLowest()) / VS_PRECISION ;
          long maxVoteCounts = (long) (fvGuaranteeGain / ((float) dynamicPropertiesStore.getSrFreezeLowestPercent() / Parameter.ChainConstant.FV_FREEZE_LOWEST_PRECISION));
          witnessCapsule.setVoteCountThreshold(maxVoteCounts);
          consensusDelegate.saveWitness(witnessCapsule);
        }
      });
    }

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
        long maxVoteCounts = 0;
        long fvGuaranteeFrozenBalance = account.getFVGuaranteeFrozenBalance();
        if (fvGuaranteeFrozenBalance > dynamicPropertiesStore.getSrFreezeLowest()) {
          long fvGuaranteeGain =  (fvGuaranteeFrozenBalance - dynamicPropertiesStore.getSrFreezeLowest()) / VS_PRECISION ;
          maxVoteCounts = (long) (fvGuaranteeGain / ((float) dynamicPropertiesStore.getSrFreezeLowestPercent() / Parameter.ChainConstant.FV_FREEZE_LOWEST_PRECISION));
        }
        witnessCapsule.setVoteCountWeight(witnessCapsule.getVoteCountWeight() + voteBuilder.getVoteCountWeight());
        witnessCapsule.setVoteCount(witnessCapsule.getVoteCount() + voteBuilder.getVoteCount());
        witnessCapsule.setVoteCountThreshold(maxVoteCounts);
        consensusDelegate.saveWitness(witnessCapsule);
        logger.info("address is {} , countVote is {} , countVoteWeight is {} , countVoteThreshold is {}", witnessCapsule.createReadableString(),
                witnessCapsule.getVoteCount() ,witnessCapsule.getVoteCountWeight() ,witnessCapsule.getVoteCountThreshold());
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
        delegationStore.setWitnessVote(nextCycle, witness.createDbKey(), witness.getVoteCount());
        delegationStore.setWitnessVoteWeight(nextCycle, witness.createDbKey(), witness.getVoteCountWeight());
      });
    }
    calculationCyclePledgeRate();
    long cycle = dynamicPropertiesStore.getCurrentCycleNumber();
    long economicCycle = dynamicPropertiesStore.getEconomyCycle();
    long latestEconomyEndCycle = dynamicPropertiesStore.getLatestEconomyEndCycle();
    long effectEconomicCycle = dynamicPropertiesStore.getEffectEconomyCycle();
    if (latestEconomyEndCycle == cycle) {
      long pledgeRate = savePledgeRate(1, cycle, latestEconomyEndCycle);
      saveInflationRate(pledgeRate);
      dynamicPropertiesStore.saveEffectEconomyCycle(economicCycle);
    } else if ((cycle - latestEconomyEndCycle) % effectEconomicCycle == 0) {
      long pledgeRate = savePledgeRate(latestEconomyEndCycle + 1, cycle, effectEconomicCycle);
      saveInflationRate(pledgeRate);
      dynamicPropertiesStore.saveLatestEconomyEndCycle(cycle);
      dynamicPropertiesStore.saveEffectEconomyCycle(economicCycle);
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
      witnessCapsule.setVoteCountWeight(witnessCapsule.getVoteCountWeight() - witness.getVoteCount());
      consensusDelegate.saveWitness(witnessCapsule);
    });
    consensusDelegate.saveRemoveThePowerOfTheGr(-1);
  }

  private void calculationCyclePledgeRate() {
    DynamicPropertiesStore dynamicPropertiesStore = consensusDelegate.getDynamicPropertiesStore();
    long cycle = dynamicPropertiesStore.getCurrentCycleNumber();
    BigDecimal bigTotalPhoton = new BigDecimal(dynamicPropertiesStore.getTotalPhotonWeight()).multiply(new BigDecimal(VS_PRECISION));
    BigDecimal bigTotalEntropy = new BigDecimal(dynamicPropertiesStore.getTotalEntropyWeight()).multiply(new BigDecimal(VS_PRECISION));
    BigDecimal bigTotalFVGuarantee = new BigDecimal(dynamicPropertiesStore.getTotalFVGuaranteeWeight()).multiply(new BigDecimal(VS_PRECISION));
    BigDecimal bigTotalAssets = new BigDecimal(dynamicPropertiesStore.getTotalAssets());
    BigDecimal totalPledgeAmount = bigTotalPhoton.add(bigTotalEntropy).add(bigTotalFVGuarantee);

    BigDecimal bigGalaxyLiquidityAmount = getBigGalaxyLiquidityAmount();
    BigDecimal bigAvalonLiquidityAmount = getBigAvalonLiquidityAmount();
    BigDecimal bigPrivateSaleLiquidityAmount = getBigPrivateSaleLiquidityAmount();
    BigDecimal bigTeamLiquidityAmount = getBigTeamLiquidityAmount();
    BigDecimal bigDAOLiquidityAmount = getBigDAOLiquidityAmount();
    BigDecimal bigDevLiquidityAmount = getBigDevLiquidityAmount();
    BigDecimal bigPromotionLiquidityAmount = getBigPromotionLiquidityAmount();

    BigDecimal assets = bigTotalAssets.add(bigGalaxyLiquidityAmount).add(bigAvalonLiquidityAmount)
            .add(bigPrivateSaleLiquidityAmount).add(bigTeamLiquidityAmount)
            .add(bigDAOLiquidityAmount).add(bigDevLiquidityAmount).add(bigPromotionLiquidityAmount);

    long cyclePledgeRate = 0;
    if (assets.longValue() > 0){
      cyclePledgeRate = totalPledgeAmount.divide(assets,2,BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)).longValue();
      if (0 > cyclePledgeRate) {
        cyclePledgeRate = 0;
      } else if (100 < cyclePledgeRate) {
        cyclePledgeRate = 100;
      }
    }

    logger.info("calculationCyclePledgeRate result, cycle: {} , pledge: {}, totalPledgeAmount: {}, totalAssets: {}", cycle, cyclePledgeRate, totalPledgeAmount, assets);
    dynamicPropertiesStore.saveCyclePledgeRateNumerator(totalPledgeAmount.toString());
    dynamicPropertiesStore.saveCyclePledgeRateDenominator(assets.toString());
    consensusDelegate.getDelegationStore().addCyclePledgeRate(cycle, cyclePledgeRate);
  }

  private BigDecimal getBigAvalonLiquidityAmount(){
    long avalonBalance = accountStore.getAvalon().getBalance();
    BigDecimal bigAvalonBalance = new BigDecimal(avalonBalance);
    long avalonInitialAmount = consensusDelegate.getDynamicPropertiesStore().getAvalonInitialAmount();
    if (0 == avalonInitialAmount) {
      consensusDelegate.getDynamicPropertiesStore().saveAvalonInitialAmount(avalonBalance);
      avalonInitialAmount = avalonBalance;
    }
    BigDecimal bigAvalonInitialAmount = new BigDecimal(avalonInitialAmount);
    return bigAvalonInitialAmount.subtract(bigAvalonBalance);
  }

  private BigDecimal getBigGalaxyLiquidityAmount(){
    long galaxyBalance = accountStore.getGalaxy().getBalance();
    BigDecimal bigGalaxyBalance = new BigDecimal(galaxyBalance);
    long galaxyInitialAmount = consensusDelegate.getDynamicPropertiesStore().getGalaxyInitialAmount();
    if (0 == galaxyInitialAmount) {
      consensusDelegate.getDynamicPropertiesStore().saveGalaxyInitialAmount(galaxyBalance);
      galaxyInitialAmount = galaxyBalance;
    }
    BigDecimal bigGalaxyInitialAmount = new BigDecimal(galaxyInitialAmount);
    return bigGalaxyInitialAmount.subtract(bigGalaxyBalance);
  }

  private BigDecimal getBigPrivateSaleLiquidityAmount(){
    long privateSaleBalance = accountStore.getPrivateSale().getBalance();
    BigDecimal bigPrivateSaleBalance = new BigDecimal(privateSaleBalance);
    long privateSaleInitialAmount = consensusDelegate.getDynamicPropertiesStore().getPrivateSaleInitialAmount();
    if (0 == privateSaleInitialAmount) {
      consensusDelegate.getDynamicPropertiesStore().savePrivateSaleInitialAmount(privateSaleBalance);
      privateSaleInitialAmount = privateSaleBalance;
    }
    BigDecimal bigPrivateSaleInitialAmount = new BigDecimal(privateSaleInitialAmount);
    return bigPrivateSaleInitialAmount.subtract(bigPrivateSaleBalance);
  }

  private BigDecimal getBigTeamLiquidityAmount(){
    long teamBalance = accountStore.getTeam().getBalance();
    BigDecimal bigTeamBalance = new BigDecimal(teamBalance);
    long teamInitialAmount = consensusDelegate.getDynamicPropertiesStore().getTeamInitialAmount();
    if (0 == teamInitialAmount) {
      consensusDelegate.getDynamicPropertiesStore().saveTeamInitialAmount(teamBalance);
      teamInitialAmount = teamBalance;
    }
    BigDecimal bigTeamInitialAmount = new BigDecimal(teamInitialAmount);
    return bigTeamInitialAmount.subtract(bigTeamBalance);
  }

  private BigDecimal getBigDAOLiquidityAmount(){
    long daoBalance = accountStore.getDAO().getBalance();
    BigDecimal bigDAOBalance = new BigDecimal(daoBalance);
    long daoInitialAmount = consensusDelegate.getDynamicPropertiesStore().getDAOInitialAmount();
    if (0 == daoInitialAmount) {
      consensusDelegate.getDynamicPropertiesStore().saveDAOInitialAmount(daoBalance);
      daoInitialAmount = daoBalance;
    }
    BigDecimal bigDAOInitialAmount = new BigDecimal(daoInitialAmount);
    return bigDAOInitialAmount.subtract(bigDAOBalance);
  }

  private BigDecimal getBigDevLiquidityAmount(){
    long devBalance = accountStore.getDev().getBalance();
    BigDecimal bigDevBalance = new BigDecimal(devBalance);
    long devInitialAmount = consensusDelegate.getDynamicPropertiesStore().getDevInitialAmount();
    if (0 == devInitialAmount) {
      consensusDelegate.getDynamicPropertiesStore().saveDevInitialAmount(devInitialAmount);
      devInitialAmount = devBalance;
    }
    BigDecimal bigDevInitialAmount = new BigDecimal(devInitialAmount);
    return bigDevInitialAmount.subtract(bigDevBalance);
  }

  private BigDecimal getBigPromotionLiquidityAmount(){
    long promotionBalance = accountStore.getPromotion().getBalance();
    BigDecimal bigPromotionBalance = new BigDecimal(promotionBalance);
    long promotionInitialAmount = consensusDelegate.getDynamicPropertiesStore().getPromotionInitialAmount();
    if (0 == promotionInitialAmount) {
      consensusDelegate.getDynamicPropertiesStore().savePromotionInitialAmount(promotionInitialAmount);
      promotionInitialAmount = promotionBalance;
    }
    BigDecimal bigDevInitialAmount = new BigDecimal(promotionInitialAmount);
    return bigDevInitialAmount.subtract(bigPromotionBalance);
  }

  private void saveInflationRate(long pledgeRate) {
    DynamicPropertiesStore dynamicPropertiesStore = consensusDelegate.getDynamicPropertiesStore();
    if (dynamicPropertiesStore.getPledgeRateThreshold() <= pledgeRate) {
      consensusDelegate.getDynamicPropertiesStore().saveInflationRate(dynamicPropertiesStore.getLowInflationRate());
    } else {
      consensusDelegate.getDynamicPropertiesStore().saveInflationRate(dynamicPropertiesStore.getHighInflationRate());
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
