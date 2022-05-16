package org.vision.core.actuator;

import com.google.common.math.LongMath;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.DecodeUtil;
import org.vision.common.utils.StringUtil;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.AccountFrozenStageResourceCapsule;
import org.vision.core.capsule.TransactionResultCapsule;
import org.vision.core.capsule.VotesCapsule;
import org.vision.core.config.Parameter;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.service.MortgageService;
import org.vision.core.store.*;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.protos.contract.WitnessContract.VoteWitnessContract;
import org.vision.protos.contract.WitnessContract.VoteWitnessContract.Vote;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.vision.core.actuator.ActuatorConstant.*;
import static org.vision.core.config.Parameter.ChainConstant.MAX_VOTE_NUMBER;
import static org.vision.core.config.Parameter.ChainConstant.VS_PRECISION;

@Slf4j(topic = "actuator")
public class VoteWitnessActuator extends AbstractActuator {

  public VoteWitnessActuator() {
    super(ContractType.VoteWitnessContract, VoteWitnessContract.class);
  }

  @Override
  public boolean execute(Object object) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) object;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();
    try {
      VoteWitnessContract voteContract = any.unpack(VoteWitnessContract.class);
      countVoteAccount(voteContract);
      ret.setStatus(fee, code.SUCESS);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.any == null) {
      throw new ContractValidateException(ActuatorConstant.CONTRACT_NOT_EXIST);
    }
    if (chainBaseManager == null) {
      throw new ContractValidateException(ActuatorConstant.STORE_NOT_EXIST);
    }
    AccountStore accountStore = chainBaseManager.getAccountStore();
    WitnessStore witnessStore = chainBaseManager.getWitnessStore();
    if (!this.any.is(VoteWitnessContract.class)) {
      throw new ContractValidateException(
          "contract type error, expected type [VoteWitnessContract], real type[" + any
              .getClass() + "]");
    }
    final VoteWitnessContract contract;
    try {
      contract = this.any.unpack(VoteWitnessContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    if (!DecodeUtil.addressValid(contract.getOwnerAddress().toByteArray())) {
      throw new ContractValidateException("Invalid address");
    }
    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

    if (contract.getVotesCount() == 0) {
      throw new ContractValidateException(
          "VoteNumber must more than 0");
    }
    int maxVoteNumber = MAX_VOTE_NUMBER;
    if (contract.getVotesCount() > maxVoteNumber) {
      throw new ContractValidateException(
          "VoteNumber more than maxVoteNumber " + maxVoteNumber);
    }
    try {
      Iterator<Vote> iterator = contract.getVotesList().iterator();
      Long sum = 0L;
      while (iterator.hasNext()) {
        Vote vote = iterator.next();
        byte[] witnessCandidate = vote.getVoteAddress().toByteArray();
        if (!DecodeUtil.addressValid(witnessCandidate)) {
          throw new ContractValidateException("Invalid vote address!");
        }
        long voteCount = vote.getVoteCount();
        if (voteCount <= 0) {
          throw new ContractValidateException("vote count must be greater than 0");
        }
        String readableWitnessAddress = StringUtil.createReadableString(vote.getVoteAddress());
        if (!accountStore.has(witnessCandidate)) {
          throw new ContractValidateException(
              ACCOUNT_EXCEPTION_STR + readableWitnessAddress + NOT_EXIST_STR);
        }
        if (!witnessStore.has(witnessCandidate)) {
          throw new ContractValidateException(
              WITNESS_EXCEPTION_STR + readableWitnessAddress + NOT_EXIST_STR);
        }
        sum = LongMath.checkedAdd(sum, vote.getVoteCount());
      }

      AccountCapsule accountCapsule = accountStore.get(ownerAddress);
      if (accountCapsule == null) {
        throw new ContractValidateException(
            ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      }

      long visionPower = accountCapsule.getVisionPower();

      sum = LongMath
          .checkedMultiply(sum, VS_PRECISION); //vs -> drop. The vote count is based on VS
      if (sum > visionPower) {
        throw new ContractValidateException(
            "The total number of votes[" + sum + "] is greater than the visionPower[" + visionPower
                + "]");
      }
    } catch (ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    return true;
  }

  private void countVoteAccount(VoteWitnessContract voteContract) {
    AccountStore accountStore = chainBaseManager.getAccountStore();
    VotesStore votesStore = chainBaseManager.getVotesStore();
    MortgageService mortgageService = chainBaseManager.getMortgageService();
    byte[] ownerAddress = voteContract.getOwnerAddress().toByteArray();

    VotesCapsule votesCapsule;

    //
    mortgageService.withdrawReward(ownerAddress, false);

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);

    if (!votesStore.has(ownerAddress)) {
      votesCapsule = new VotesCapsule(voteContract.getOwnerAddress(),
          accountCapsule.getVotesList());
    } else {
      votesCapsule = votesStore.get(ownerAddress);
    }

    accountCapsule.clearVotes();
    votesCapsule.clearNewVotes();

    voteContract.getVotesList().forEach(vote -> {
      logger.debug("countVoteAccount, address[{}]",
          ByteArray.toHexString(vote.getVoteAddress().toByteArray()));
      // get freeze and compute a new votes
      DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
      long interval1 = LongMath.checkedMultiply(dynamicStore.getVoteFreezeStageLevel1(), VS_PRECISION);
      long interval2 = LongMath.checkedMultiply(dynamicStore.getVoteFreezeStageLevel2(), VS_PRECISION);
      long interval3 = LongMath.checkedMultiply(dynamicStore.getVoteFreezeStageLevel3(), VS_PRECISION);
      long visionPower = accountCapsule.getVisionPower();
      long voteCount = vote.getVoteCount();
      if (visionPower >= interval3) {
        voteCount = (long) (voteCount * ((float) dynamicStore.getVoteFreezePercentLevel3() / Parameter.ChainConstant.VOTE_PERCENT_PRECISION));
      } else if (visionPower >= interval2) {
        voteCount = (long) (voteCount * ((float) dynamicStore.getVoteFreezePercentLevel2() /Parameter.ChainConstant.VOTE_PERCENT_PRECISION));
      } else if (visionPower >= interval1) {
        voteCount = (long) (voteCount * ((float) dynamicStore.getVoteFreezePercentLevel1() /Parameter.ChainConstant.VOTE_PERCENT_PRECISION));
      }
      if (dynamicStore.getAllowVPFreezeStageWeight() == 1L) {
        long merge = calcAccountFrozenStageWeightMerge(ownerAddress, accountCapsule);
        accountCapsule.setFrozenStageWeightMerge(merge);
        voteCount = voteCount * accountCapsule.getFrozenStageWeightMerge() / 100L;
      }
      votesCapsule.addNewVotes(vote.getVoteAddress(), vote.getVoteCount(), voteCount);
      accountCapsule.addVotes(vote.getVoteAddress(), vote.getVoteCount(), voteCount);

    });

    accountStore.put(accountCapsule.createDbKey(), accountCapsule);
    votesStore.put(ownerAddress, votesCapsule);
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(VoteWitnessContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

  private long calcAccountFrozenStageWeightMerge(byte[] ownerAddress, AccountCapsule account) {
    DynamicPropertiesStore dynamicPropertiesStore = chainBaseManager.getDynamicPropertiesStore();
    Map<Long, List<Long>> stageWeights = dynamicPropertiesStore.getVPFreezeStageWeights();
    AccountFrozenStageResourceStore accountFrozenStageResourceStore = chainBaseManager.getAccountFrozenStageResourceStore();
    long totalBalance = 0;
    long totalRate = 0;
    for (Map.Entry<Long, List<Long>> entry : stageWeights.entrySet()) {
      if (entry.getKey() == 1L) {
        continue;
      }
      byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, entry.getKey());
      AccountFrozenStageResourceCapsule capsule = accountFrozenStageResourceStore.get(key);
      if (capsule == null) {
        continue;
      }
      long balance = capsule.getInstance().getFrozenBalanceForPhoton();
      balance += capsule.getInstance().getFrozenBalanceForEntropy();
      totalRate += balance / VS_PRECISION * entry.getValue().get(1);
      totalBalance += balance;
    }

    long balance = account.getDelegatedFrozenBalanceForEntropy()
        + account.getDelegatedFrozenBalanceForPhoton();
    byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, 1L);
    AccountFrozenStageResourceCapsule capsule = accountFrozenStageResourceStore.get(key);
    if (capsule != null) {
      balance += capsule.getInstance().getFrozenBalanceForPhoton();
      balance += capsule.getInstance().getFrozenBalanceForEntropy();
    }
    long defaultFrozen = account.getEntropyFrozenBalance() + account.getFrozenBalance() - totalBalance;
    if (defaultFrozen > 0) {
      balance += defaultFrozen;
    }

    totalRate += balance / VS_PRECISION * stageWeights.get(1L).get(1);
    totalBalance += balance;

    if (totalBalance > 0) {
      return Math.max(totalRate / totalBalance, dynamicPropertiesStore.getVPFreezeWeightByStage(5L));
    } else {
      return 100L;
    }
  }
}
