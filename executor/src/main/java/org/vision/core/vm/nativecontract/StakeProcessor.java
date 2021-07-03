package org.vision.core.vm.nativecontract;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.math.LongMath;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.spongycastle.util.encoders.Hex;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.DecodeUtil;
import org.vision.common.utils.Producer;
import org.vision.common.utils.StringUtil;
import org.vision.core.actuator.ActuatorConstant;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.VotesCapsule;
import org.vision.core.config.Parameter;
import org.vision.core.config.Parameter.ChainConstant;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.core.store.WitnessStore;
import org.vision.core.vm.nativecontract.param.StakeParam;
import org.vision.core.vm.repository.Repository;
import org.vision.protos.Protocol;

import java.util.Calendar;
import java.util.List;

import static org.vision.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;
import static org.vision.core.actuator.ActuatorConstant.NOT_EXIST_STR;
import static org.vision.core.config.Parameter.ChainConstant.VS_PRECISION;

@Slf4j(topic = "Processor")
public class StakeProcessor {

  public void process(StakeParam stakeParam, Repository repository)
      throws ContractValidateException, ContractExeException {
    selfValidate(stakeParam, repository);
    AccountCapsule accountCapsule = repository.getAccount(stakeParam.getOwnerAddress());
    long visionPower = accountCapsule.getVisionPower();
    long freezeBalance = stakeParam.getStakeAmount() - visionPower;
    // if need freeze balance
    if (freezeBalance > 0) {
      long frozenDuration = repository.getDynamicPropertiesStore().getMinFrozenTime();
      validateFreeze(stakeParam.getOwnerAddress(), frozenDuration, freezeBalance, repository);
      executeFreeze(stakeParam.getOwnerAddress(), frozenDuration, freezeBalance, stakeParam.getNow(), repository);
    } else {
      logger.info("no need to freeze for stake");
    }
    long voteCount = stakeParam.getStakeAmount() / ChainConstant.VS_PRECISION;
    Protocol.Vote vote = Protocol.Vote.newBuilder()
        .setVoteAddress(ByteString.copyFrom(stakeParam.getSrAddress()))
        .setVoteCount(voteCount).build();
    validateVote(stakeParam.getOwnerAddress(), vote, repository);
    executeVote(stakeParam.getOwnerAddress(), vote, repository);
  }

  private void selfValidate(StakeParam stakeParam, Repository repository)
      throws ContractValidateException {
    if (stakeParam == null) {
      throw new ContractValidateException(ActuatorConstant.CONTRACT_NOT_EXIST);
    }
    if (repository == null) {
      throw new ContractValidateException(ActuatorConstant.STORE_NOT_EXIST);
    }

    byte[] ownerAddress = stakeParam.getOwnerAddress();
    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    AccountCapsule accountCapsule = repository.getAccount(ownerAddress);
    if (accountCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
              ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
    }
  }

  private void validateFreeze(byte[] ownerAddress, long frozenDuration,
                              long frozenBalance, Repository repository)
      throws ContractValidateException {
    AccountCapsule accountCapsule = repository.getAccount(ownerAddress);

    if (frozenBalance <= 0) {
      throw new ContractValidateException("frozenBalance must be positive");
    }
    if (frozenBalance < ChainConstant.VS_PRECISION) {
        throw new ContractValidateException("frozenBalance must be more than 1VS");
    }

    int frozenCount = accountCapsule.getFrozenCount();
    if (frozenCount > 1) {
      throw new ContractValidateException("frozenCount must be 0 or 1");
    }
    if (frozenBalance > accountCapsule.getBalance()) {
      throw new ContractValidateException("frozenBalance must be less than accountBalance");
    }
  }

  private void validateVote(byte[] ownerAddress, Protocol.Vote vote, Repository repository)
      throws ContractValidateException {
    AccountCapsule accountCapsule = repository.getAccount(ownerAddress);
    WitnessStore witnessStore = repository.getWitnessStore();
    try {
      long sum;
      {
        byte[] witnessCandidate = vote.getVoteAddress().toByteArray();
        if (!DecodeUtil.addressValid(witnessCandidate)) {
          throw new ContractValidateException("Invalid vote address!");
        }
        if (vote.getVoteCount() <= 0) {
          throw new ContractValidateException("vote count must be greater than 0");
        }
        if (repository.getAccount(witnessCandidate) == null) {
          String readableWitnessAddress = StringUtil.createReadableString(vote.getVoteAddress());
          throw new ContractValidateException(
              ContractProcessorConstant.ACCOUNT_EXCEPTION_STR
                  + readableWitnessAddress + ContractProcessorConstant.NOT_EXIST_STR);
        }
        if (!witnessStore.has(witnessCandidate)) {
          String readableWitnessAddress = StringUtil.createReadableString(vote.getVoteAddress());
          throw new ContractValidateException(
              ContractProcessorConstant.WITNESS_EXCEPTION_STR
                  + readableWitnessAddress + ContractProcessorConstant.NOT_EXIST_STR);
        }
        sum = vote.getVoteCount();
      }
      long visionPower = accountCapsule.getVisionPower();

      // vs -> drop. The vote count is based on VS
      sum = LongMath.checkedMultiply(sum, ChainConstant.VS_PRECISION);
      if (sum > visionPower) {
        throw new ContractValidateException(
            "The total number of votes[" + sum + "] is greater than the visionPower[" + visionPower
                + "]");
      }
    } catch (ArithmeticException e) {
      logger.error(e.getMessage(), e);
      throw new ContractValidateException("error when sum all votes");
    }
  }

  private void executeFreeze(byte[] ownerAddress, long frozenDuration,
                             long frozenBalance, long now, Repository repository)
      throws ContractExeException {
    AccountCapsule accountCapsule = repository.getAccount(ownerAddress);

    long duration = frozenDuration * ChainConstant.FROZEN_PERIOD;

    long newBalance = accountCapsule.getBalance() - frozenBalance;

    long expireTime = now + duration;
    long newFrozenBalanceForPhoton =
        frozenBalance + accountCapsule.getFrozenBalance();
    accountCapsule.setFrozenForPhoton(newFrozenBalanceForPhoton, expireTime);

    accountCapsule.setBalance(newBalance);
    repository.updateAccount(accountCapsule.createDbKey(), accountCapsule);
    repository
            .addTotalPhotonWeight(frozenBalance / ChainConstant.VS_PRECISION);
  }

  private void executeVote(byte[] ownerAddress, Protocol.Vote vote, Repository repository)
      throws ContractExeException {
    VotesCapsule votesCapsule;

    ContractService contractService = ContractService.getInstance();
    contractService.withdrawReward(ownerAddress, repository);

    AccountCapsule accountCapsule = repository.getAccount(ownerAddress);
    if (repository.getVotesCapsule(ownerAddress) == null) {
      votesCapsule = new VotesCapsule(ByteString.copyFrom(ownerAddress),
          accountCapsule.getVotesList());
    } else {
      votesCapsule = repository.getVotesCapsule(ownerAddress);
    }

    accountCapsule.clearVotes();
    votesCapsule.clearNewVotes();

    logger.debug("countVoteAccount, address[{}]",
        ByteArray.toHexString(vote.getVoteAddress().toByteArray()));

    // get freeze and compute a new votes
    DynamicPropertiesStore dynamicStore = repository.getDynamicPropertiesStore();
    long interval1 = LongMath.checkedMultiply(dynamicStore.getVoteFreezeStageLevel1(), VS_PRECISION);
    long interval2 = LongMath.checkedMultiply(dynamicStore.getVoteFreezeStageLevel2(), VS_PRECISION);
    long interval3 = LongMath.checkedMultiply(dynamicStore.getVoteFreezeStageLevel3(), VS_PRECISION);
    long visionPower = accountCapsule.getVisionPower();
    long voteCount = vote.getVoteCount();
    if (visionPower >= interval3) {
      voteCount = (long) (voteCount * (dynamicStore.getVoteFreezePercentLevel3()/ Parameter.ChainConstant.VOTE_PERCENT_PRECISION));
    } else if (visionPower >= interval2) {
      voteCount = (long) (voteCount * (dynamicStore.getVoteFreezePercentLevel2()/Parameter.ChainConstant.VOTE_PERCENT_PRECISION));
    } else if (visionPower >= interval1) {
      voteCount = (long) (voteCount * (dynamicStore.getVoteFreezePercentLevel1()/Parameter.ChainConstant.VOTE_PERCENT_PRECISION));
    }
    votesCapsule.addNewVotes(vote.getVoteAddress(), vote.getVoteCount(), voteCount);
    accountCapsule.addVotes(vote.getVoteAddress(), vote.getVoteCount(), voteCount);

    repository.putAccountValue(accountCapsule.createDbKey(), accountCapsule);
    repository.updateVotesCapsule(ownerAddress, votesCapsule);
    if(CommonParameter.PARAMETER.isKafkaEnable()){
      try {
        JSONObject itemJsonObject = new JSONObject();
        List<Protocol.Vote> voteList = accountCapsule.getVotesList();
        JSONArray voteArray = new JSONArray();
        if (null != voteList && voteList.size() > 0) {
          for (org.vision.protos.Protocol.Vote voteTmp : voteList) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("voteAddress", Hex.toHexString(voteTmp.getVoteAddress().toByteArray()));
            jsonObject.put("voteCount", voteTmp.getVoteCount());
            voteArray.add(jsonObject);
          }
        }

        String address = Hex.toHexString(accountCapsule.getAddress().toByteArray());
        logger.info("send votewitness to kafka accountId={}", address);
        itemJsonObject.put("accountId", address);
        itemJsonObject.put("votesList", voteArray.toString());
        itemJsonObject.put("createTime", Calendar.getInstance().getTimeInMillis());
        String jsonStr = itemJsonObject.toJSONString();
        logger.info("send VOTEWITNESS start");
        Producer.getInstance().send("VOTEWITNESS", jsonStr);
      } catch (Exception e) {
        logger.error("send VOTEWITNESS fail", e);
      }
    }
  }
}
