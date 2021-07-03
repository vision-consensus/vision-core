package org.vision.core.service;

import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;
import org.vision.common.utils.StringUtil;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.WitnessCapsule;
import org.vision.core.config.Parameter.ChainConstant;
import org.vision.core.exception.BalanceInsufficientException;
import org.vision.core.store.AccountStore;
import org.vision.core.store.DelegationStore;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.core.store.WitnessStore;
import org.vision.protos.Protocol.Vote;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j(topic = "mortgage")
@Component
public class MortgageService {

  @Setter
  private WitnessStore witnessStore;

  @Setter
  @Getter
  private DelegationStore delegationStore;

  @Setter
  private DynamicPropertiesStore dynamicPropertiesStore;

  @Setter
  private AccountStore accountStore;

  public void initStore(WitnessStore witnessStore, DelegationStore delegationStore,
      DynamicPropertiesStore dynamicPropertiesStore, AccountStore accountStore) {
    this.witnessStore = witnessStore;
    this.delegationStore = delegationStore;
    this.dynamicPropertiesStore = dynamicPropertiesStore;
    this.accountStore = accountStore;
  }

  public void payStandbyWitness() {
    List<ByteString> witnessAddressList = new ArrayList<>();
    for (WitnessCapsule witnessCapsule : witnessStore.getAllWitnesses()) {
      witnessAddressList.add(witnessCapsule.getAddress());
    }
    sortWitness(witnessAddressList);
    if (witnessAddressList.size() > ChainConstant.WITNESS_STANDBY_LENGTH) {
      witnessAddressList = witnessAddressList.subList(0, ChainConstant.WITNESS_STANDBY_LENGTH);
    }

    long voteSum = 0;
    long totalPay = dynamicPropertiesStore.getWitness100PayPerBlock() * (dynamicPropertiesStore.getInflationRate() / 120000 + 1);
    for (ByteString b : witnessAddressList) {
      voteSum += getWitnessByAddress(b).getVoteCount();
    }
    if (voteSum > 0) {
      for (ByteString b : witnessAddressList) {
        double eachVotePay = (double) totalPay / voteSum;
        long pay = (long) (getWitnessByAddress(b).getVoteCount() * eachVotePay);
        logger.debug("pay {} stand reward {}", Hex.toHexString(b.toByteArray()), pay);
        payReward(b.toByteArray(), pay);
      }
    }

  }

  public void payBlockReward(byte[] witnessAddress, long value) {
    logger.debug("pay {} block reward {}", Hex.toHexString(witnessAddress), value);
    payReward(witnessAddress, value);
  }

  public void payTransactionFeeReward(byte[] witnessAddress, long value) {
    logger.debug("pay {} transaction fee reward {}", Hex.toHexString(witnessAddress), value);
    payReward(witnessAddress, value);
  }

  private void payReward(byte[] witnessAddress, long value) {
    long cycle = dynamicPropertiesStore.getCurrentCycleNumber();
    int brokerage = delegationStore.getBrokerage(cycle, witnessAddress);
    double brokerageRate = (double) brokerage / 100;
    long brokerageAmount = (long) (brokerageRate * value);
    value -= brokerageAmount;
    delegationStore.addReward(cycle, witnessAddress, value);
    adjustAllowance(witnessAddress, brokerageAmount);
  }

  public void paySpreadMintReward(long value) {
    long cycle = dynamicPropertiesStore.getCurrentCycleNumber();
    delegationStore.addSpreadMintReward(cycle, value);
  }

  public void withdrawReward(byte[] address) {
    if (!dynamicPropertiesStore.allowChangeDelegation()) {
      return;
    }
    AccountCapsule accountCapsule = accountStore.get(address);
    long beginCycle = delegationStore.getBeginCycle(address);
    long endCycle = delegationStore.getEndCycle(address);
    long currentCycle = dynamicPropertiesStore.getCurrentCycleNumber();
    long reward = 0;
    if (beginCycle > currentCycle || accountCapsule == null) {
      return;
    }
    if (beginCycle == currentCycle) {
      AccountCapsule account = delegationStore.getAccountVote(beginCycle, address);
      if (account != null) {
        return;
      }
    }
    //withdraw the latest cycle reward
    if (beginCycle + 1 == endCycle && beginCycle < currentCycle) {
      AccountCapsule account = delegationStore.getAccountVote(beginCycle, address);
      if (account != null) {
        reward = computeReward(beginCycle, account);
        adjustAllowance(address, reward);
        reward = 0;
        logger.info("latest cycle reward {},{}", beginCycle, account.getVotesList());
      }
      beginCycle += 1;
    }
    //
    endCycle = currentCycle;
    if (CollectionUtils.isEmpty(accountCapsule.getVotesList())) {
      delegationStore.setBeginCycle(address, endCycle + 1);
      return;
    }
    if (beginCycle < endCycle) {
      for (long cycle = beginCycle; cycle < endCycle; cycle++) {
        reward += computeReward(cycle, accountCapsule);
      }
      adjustAllowance(address, reward);
    }
    delegationStore.setBeginCycle(address, endCycle);
    delegationStore.setEndCycle(address, endCycle + 1);
    delegationStore.setAccountVote(endCycle, address, accountCapsule);
    logger.info("adjust {} allowance {}, now currentCycle {}, beginCycle {}, endCycle {}, "
            + "account vote {},", Hex.toHexString(address), reward, currentCycle,
        beginCycle, endCycle, accountCapsule.getVotesList());
  }

  public long queryReward(byte[] address) {
    if (!dynamicPropertiesStore.allowChangeDelegation()) {
      return 0;
    }

    AccountCapsule accountCapsule = accountStore.get(address);
    long beginCycle = delegationStore.getBeginCycle(address);
    long endCycle = delegationStore.getEndCycle(address);
    long currentCycle = dynamicPropertiesStore.getCurrentCycleNumber();
    long reward = 0;
    if (accountCapsule == null) {
      return 0;
    }
    if (beginCycle > currentCycle) {
      return accountCapsule.getAllowance();
    }
    //withdraw the latest cycle reward
    if (beginCycle + 1 == endCycle && beginCycle < currentCycle) {
      AccountCapsule account = delegationStore.getAccountVote(beginCycle, address);
      if (account != null) {
        reward = computeReward(beginCycle, account);
      }
      beginCycle += 1;
    }
    //
    endCycle = currentCycle;
    if (CollectionUtils.isEmpty(accountCapsule.getVotesList())) {
      return reward + accountCapsule.getAllowance();
    }
    if (beginCycle < endCycle) {
      for (long cycle = beginCycle; cycle < endCycle; cycle++) {
        reward += computeReward(cycle, accountCapsule);
      }
    }
    return reward + accountCapsule.getAllowance();
  }

  private long computeReward(long cycle, AccountCapsule accountCapsule) {
    long reward = 0;
    for (Vote vote : accountCapsule.getVotesList()) {
      byte[] srAddress = vote.getVoteAddress().toByteArray();
      long totalReward = delegationStore.getReward(cycle, srAddress);
      long totalVote = delegationStore.getWitnessVote(cycle, srAddress);
      if (totalVote == DelegationStore.REMARK || totalVote == 0) {
        continue;
      }
      long userVote = vote.getVoteCountWeight();
      double voteRate = (double) userVote / totalVote;
      reward += voteRate * totalReward;
      logger.debug("computeReward {} {} {} {},{},{},{}", cycle,
          Hex.toHexString(accountCapsule.getAddress().toByteArray()), Hex.toHexString(srAddress),
          userVote, totalVote, totalReward, reward);
    }
    //with spread mint reward
    reward += computeSpreadMintReward(cycle, accountCapsule);
    return reward;
  }

  private long computeSpreadMintReward(long cycle, AccountCapsule accountCapsule) {
    long totalFreeze = delegationStore.getTotalFreezeBalanceForSpreadMint(cycle);
    if(totalFreeze==0L){
      return 0;
    }
    long accountFreeze = accountCapsule.getAccountResource().getFrozenBalanceForSpread().getFrozenBalance();
    long totalReward = delegationStore.getSpreadMintReward(cycle);
    return totalReward * accountFreeze / totalFreeze;
  }

  public WitnessCapsule getWitnessByAddress(ByteString address) {
    return witnessStore.get(address.toByteArray());
  }

  public void adjustAllowance(byte[] address, long amount) {
    try {
      if (amount <= 0) {
        return;
      }
      adjustAllowance(accountStore, address, amount);
    } catch (BalanceInsufficientException e) {
      logger.error("withdrawReward error: {},{}", Hex.toHexString(address), address, e);
    }
  }

  public void adjustAllowance(AccountStore accountStore, byte[] accountAddress, long amount)
      throws BalanceInsufficientException {
    AccountCapsule account = accountStore.getUnchecked(accountAddress);
    long allowance = account.getAllowance();
    if (amount == 0) {
      return;
    }

    if (amount < 0 && allowance < -amount) {
      throw new BalanceInsufficientException(
          StringUtil.createReadableString(accountAddress) + " insufficient balance");
    }
    account.setAllowance(allowance + amount);
    accountStore.put(account.createDbKey(), account);
  }

  private void sortWitness(List<ByteString> list) {
    list.sort(Comparator.comparingLong((ByteString b) -> {
      WitnessCapsule witnessCapsule = getWitnessByAddress(b);
      return Math.min(witnessCapsule.getVoteCountWeight(), witnessCapsule.getVoteCountThreshold());
    })
        .reversed().thenComparing(Comparator.comparingInt(ByteString::hashCode).reversed()));
  }

  public long getVoteSum() {
    List<ByteString> witnessAddressList = new ArrayList<>();
    for (WitnessCapsule witnessCapsule : witnessStore.getAllWitnesses()) {
      witnessAddressList.add(witnessCapsule.getAddress());
    }
    sortWitness(witnessAddressList);
    if (witnessAddressList.size() > ChainConstant.WITNESS_STANDBY_LENGTH) {
      witnessAddressList = witnessAddressList.subList(0, ChainConstant.WITNESS_STANDBY_LENGTH);
    }
    long voteSum = 0;
    for (ByteString b : witnessAddressList) {
      voteSum += getWitnessByAddress(b).getVoteCount();
    }
    return voteSum;
  }
}
