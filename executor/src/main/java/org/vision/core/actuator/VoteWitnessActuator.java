package org.vision.core.actuator;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.math.LongMath;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.spongycastle.util.encoders.Hex;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.*;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.TransactionResultCapsule;
import org.vision.core.capsule.VotesCapsule;
import org.vision.core.config.Parameter;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.service.MortgageService;
import org.vision.core.store.AccountStore;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.core.store.VotesStore;
import org.vision.core.store.WitnessStore;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.protos.contract.WitnessContract.VoteWitnessContract;
import org.vision.protos.contract.WitnessContract.VoteWitnessContract.Vote;

import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
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
      votesCapsule.addNewVotes(vote.getVoteAddress(), vote.getVoteCount(), voteCount);
      accountCapsule.addVotes(vote.getVoteAddress(), vote.getVoteCount(), voteCount);

    });

    accountStore.put(accountCapsule.createDbKey(), accountCapsule);
    votesStore.put(ownerAddress, votesCapsule);
    if(CommonParameter.PARAMETER.isKafkaEnable()){
      try {
        JSONObject itemJsonObject = new JSONObject();
        List<org.vision.protos.Protocol.Vote> voteList = accountCapsule.getVotesList();
        JSONArray voteArray = new JSONArray();
        if (null != voteList && voteList.size() > 0) {
          for (org.vision.protos.Protocol.Vote vote : voteList) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("voteAddress", Hex.toHexString(vote.getVoteAddress().toByteArray()));
            jsonObject.put("voteCount", vote.getVoteCount());
            voteArray.add(jsonObject);
          }
        }

        String address = Hex.toHexString(accountCapsule.getAddress().toByteArray());
        itemJsonObject.put("address", address);
        itemJsonObject.put("votesList", voteArray);
        itemJsonObject.put("createTime", Calendar.getInstance().getTimeInMillis());
        itemJsonObject.putAll(chainBaseManager.getBalanceTraceStore().assembleJsonInfo());

        String jsonStr = itemJsonObject.toJSONString();
        logger.info("send VOTEWITNESS TOPIC start, accontId:{}", address);
        Producer.getInstance().send("VOTEWITNESS", jsonStr);
      } catch (Exception e) {
        logger.error("send VOTEWITNESS fail", e);
      }

    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(VoteWitnessContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}
