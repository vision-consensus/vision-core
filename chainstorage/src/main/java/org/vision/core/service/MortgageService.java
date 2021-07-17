package org.vision.core.service;

import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.Producer;
import org.vision.common.utils.StringUtil;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.SpreadRelationShipCapsule;
import org.vision.core.capsule.WitnessCapsule;
import org.vision.core.config.Parameter.ChainConstant;
import org.vision.core.exception.BalanceInsufficientException;
import org.vision.core.store.*;
import org.vision.protos.Protocol.Vote;

import javax.xml.crypto.dsig.spec.ExcC14NParameterSpec;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;

import static org.vision.core.config.Parameter.ChainConstant.VS_PRECISION;

@Slf4j(topic = "mortgage")
@Component
public class MortgageService {

  @Setter
  private WitnessStore witnessStore;

  @Setter
  @Getter
  private DelegationStore delegationStore;

  @Setter
  @Getter
  private SpreadRelationShipStore spreadRelationShipStore;

  @Setter
  private DynamicPropertiesStore dynamicPropertiesStore;

  @Setter
  private AccountStore accountStore;

  public void initStore(WitnessStore witnessStore, DelegationStore delegationStore,
      DynamicPropertiesStore dynamicPropertiesStore, AccountStore accountStore, SpreadRelationShipStore spreadRelationShipStore) {
    this.witnessStore = witnessStore;
    this.delegationStore = delegationStore;
    this.dynamicPropertiesStore = dynamicPropertiesStore;
    this.accountStore = accountStore;
    this.spreadRelationShipStore = spreadRelationShipStore;
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
    long totalPay = dynamicPropertiesStore.getWitness123PayPerBlockInflation();
    for (ByteString b : witnessAddressList) {
      WitnessCapsule witnessCapsule = getWitnessByAddress(b);
      voteSum += Math.min(witnessCapsule.getVoteCountWeight(), witnessCapsule.getVoteCountThreshold());
    }
    if (voteSum > 0) {
      for (ByteString b : witnessAddressList) {
        double eachVotePay = (double) totalPay / voteSum;
        WitnessCapsule witnessCapsule = getWitnessByAddress(b);
        long pay = (long) (Math.min(witnessCapsule.getVoteCountWeight(), witnessCapsule.getVoteCountThreshold()) * eachVotePay);
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

  public void withdrawSpreadMintReward(byte[] address) {
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

    //withdraw the latest cycle reward
    if (beginCycle + 1 == endCycle && beginCycle < currentCycle) {
      beginCycle += 1;
    }
    endCycle = currentCycle;

    if (beginCycle < endCycle) {
      long spreadReward =0;
      for (long cycle = beginCycle; cycle < endCycle; cycle++) {
        spreadReward += computeSpreadMintReward(cycle, accountCapsule, true);
      }
      adjustAllowance(address, spreadReward);
    }

    logger.info("adjust {} allowance {}, now currentCycle {}, beginCycle {}, endCycle {}, "
            + "account vote {},", Hex.toHexString(address), reward, currentCycle,
        beginCycle, endCycle, accountCapsule.getVotesList());
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

    if (beginCycle < endCycle) {
      long spreadReward =0;
      for (long cycle = beginCycle; cycle < endCycle; cycle++) {
        spreadReward += computeSpreadMintReward(cycle, accountCapsule, true);
      }
      adjustAllowance(address, spreadReward);
    }

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

    if (beginCycle < endCycle) {
      for (long cycle = beginCycle; cycle < endCycle; cycle++) {
        reward += computeSpreadMintReward(cycle, accountCapsule, false);
      }
    }

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
      long totalVote = delegationStore.getWitnessVoteWeight(cycle, srAddress);
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
    return reward;
  }

  private long computeSpreadMintReward(long cycle, AccountCapsule accountCapsule, boolean isWithdrawReward) {
    if (!dynamicPropertiesStore.supportSpreadMint()){
      return 0;
    }

    long totalFreeze = delegationStore.getTotalFreezeBalanceForSpreadMint(cycle);
    if(totalFreeze==0L){
      return 0;
    }
    long accountFreeze = accountCapsule.getAccountResource().getFrozenBalanceForSpread().getFrozenBalance();
    long totalReward = delegationStore.getSpreadMintReward(cycle);
    long spreadReward = (long)(totalReward * accountFreeze * 1.0 / VS_PRECISION / totalFreeze);

    String spreadLevelProp = dynamicPropertiesStore.getSpreadMintLevelProp();
    String[] levelProps = spreadLevelProp.split(",");
    int[] props = new int[levelProps.length];
    int sumProps = 0;
    for(int i = 0; i < levelProps.length; i++)
    {
      props[i] = Integer.parseInt(levelProps[i]);
      if (props[i] < 0 || props[i] > 100){
        break;
      }
      sumProps += props[i];
    }
    
    if (sumProps != 100){
      logger.error("computeSpreadMintReward, sum of spreadLevelProp is not equal to 100: {}, {}", Hex.toHexString(accountCapsule.getAddress().toByteArray()), spreadLevelProp);
      return spreadReward;
    }

    if(!isWithdrawReward){
      return (long)(spreadReward * (props[0] / 100.0));
    }
    computeSpreadMintParentReward(accountCapsule, props, spreadReward, accountFreeze);
    return (long)(spreadReward * (props[0] / 100.0));
  }

  private void computeSpreadMintParentReward(AccountCapsule accountCapsule, int[] props, long spreadReward, long accountFreeze){
    try {
      AccountCapsule parentCapsule = accountCapsule;
      ArrayList<String> addressList = new ArrayList<>();
      for (int i = 1; i < props.length; i++) {
        SpreadRelationShipCapsule spreadRelationShipCapsule = spreadRelationShipStore.get(parentCapsule.getAddress().toByteArray());
        if (spreadRelationShipCapsule == null){
          break;
        }

        addressList.add(spreadRelationShipCapsule.getOwner().toString());
        if (addressList.contains(spreadRelationShipCapsule.getParent().toString())){ // deal loop parent address
          break;
        }

        parentCapsule = accountStore.get(spreadRelationShipCapsule.getParent().toByteArray());
        long spreadAmount = (long)(spreadReward * props[i] / 100.0 * minSpreadMintProp(parentCapsule, accountFreeze));
        adjustAllowance(spreadRelationShipCapsule.getParent().toByteArray(), spreadAmount);
      }
    }catch (Exception e){
      logger.error("calculateSpreadMintProp error: {},{}", Hex.toHexString(accountCapsule.getAddress().toByteArray()), accountCapsule.getAddress(), e);
    }
  }

  public double minSpreadMintProp(AccountCapsule parentCapsule, long accountFreeze){
    long parentAccountFreeze = parentCapsule.getSpreadFrozenBalance();
    double minSpreadMintProp = parentAccountFreeze * 1.0 / accountFreeze;
    return minSpreadMintProp < 1 ? minSpreadMintProp : 1.0;
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
    if(CommonParameter.PARAMETER.isKafkaEnable()){
      try {
        JSONObject itemJsonObject = new JSONObject();
        itemJsonObject.put("accountId", Hex.toHexString(account.getAddress().toByteArray()));
        itemJsonObject.put("allowance", account.getAllowance());
        itemJsonObject.put("createTime", Calendar.getInstance().getTimeInMillis());
        String jsonStr = itemJsonObject.toJSONString();
        Producer.getInstance().send("PAYREWARD", jsonStr);
      } catch (Exception e) {
        logger.error("send PAYREWARD fail", e);
      }
    }
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
