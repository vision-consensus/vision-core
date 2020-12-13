package org.vision.core.vm.nativecontract;

import com.google.common.math.LongMath;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.vision.core.actuator.ActuatorConstant;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.VotesCapsule;
import org.vision.core.store.WitnessStore;
import org.vision.core.vm.nativecontract.param.StakeParam;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.DecodeUtil;
import org.vision.common.utils.StringUtil;
import org.vision.core.config.Parameter.ChainConstant;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.vm.repository.Repository;
import org.vision.protos.Protocol;


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
          "Account[" + readableOwnerAddress + "] not exists");
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
    long newFrozenBalanceForBandwidth =
        frozenBalance + accountCapsule.getFrozenBalance();
    accountCapsule.setFrozenForBandwidth(newFrozenBalanceForBandwidth, expireTime);

    accountCapsule.setBalance(newBalance);
    repository.updateAccount(accountCapsule.createDbKey(), accountCapsule);
    repository
            .addTotalNetWeight(frozenBalance / ChainConstant.VS_PRECISION);
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

    votesCapsule.addNewVotes(vote.getVoteAddress(), vote.getVoteCount());
    accountCapsule.addVotes(vote.getVoteAddress(), vote.getVoteCount());

    repository.putAccountValue(accountCapsule.createDbKey(), accountCapsule);
    repository.updateVotesCapsule(ownerAddress, votesCapsule);
  }
}
