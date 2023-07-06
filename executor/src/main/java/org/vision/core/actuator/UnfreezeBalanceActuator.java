package org.vision.core.actuator;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.vision.common.parameter.CommonParameter;
import org.spongycastle.util.encoders.Hex;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.DecodeUtil;
import org.vision.common.utils.JsonFormat;
import org.vision.common.utils.Producer;
import org.vision.common.utils.StringUtil;
import org.vision.core.capsule.*;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.service.MortgageService;
import org.vision.core.store.*;
import org.vision.protos.Protocol.Vote;
import org.vision.protos.Protocol.Account.AccountResource;
import org.vision.protos.Protocol.Account.Frozen;
import org.vision.protos.Protocol.AccountType;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.protos.contract.BalanceContract.UnfreezeBalanceContract;
import org.vision.protos.contract.Common.ResourceCode;

import java.util.*;

import static org.vision.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;
import static org.vision.core.config.Parameter.ChainConstant.*;
import static org.vision.core.config.Parameter.ChainConstant.FROZEN_PERIOD;
import static org.vision.core.config.Parameter.ChainConstant.VS_PRECISION;

@Slf4j(topic = "actuator")
public class UnfreezeBalanceActuator extends AbstractActuator {

  public UnfreezeBalanceActuator() {
    super(ContractType.UnfreezeBalanceContract, UnfreezeBalanceContract.class);
  }

  @Override
  public boolean execute(Object result) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) result;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();
    final UnfreezeBalanceContract unfreezeBalanceContract;
    AccountStore accountStore = chainBaseManager.getAccountStore();
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    DelegatedResourceStore delegatedResourceStore = chainBaseManager.getDelegatedResourceStore();
    DelegatedResourceAccountIndexStore delegatedResourceAccountIndexStore = chainBaseManager
        .getDelegatedResourceAccountIndexStore();
    VotesStore votesStore = chainBaseManager.getVotesStore();
    MortgageService mortgageService = chainBaseManager.getMortgageService();
    try {
      unfreezeBalanceContract = any.unpack(UnfreezeBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    byte[] ownerAddress = unfreezeBalanceContract.getOwnerAddress().toByteArray();

    //
    if(unfreezeBalanceContract.getResource() != ResourceCode.FVGUARANTEE){
      mortgageService.withdrawReward(ownerAddress, unfreezeBalanceContract.getResource() == ResourceCode.SPREAD);
    }

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    long oldBalance = accountCapsule.getBalance();

    long unfreezeBalance = 0L;
    long unfrozenFragBalance = 0L;

    byte[] receiverAddress = unfreezeBalanceContract.getReceiverAddress().toByteArray();
    //If the receiver is not included in the contract, unfreeze frozen balance for this account.
    //otherwise,unfreeze delegated frozen balance provided this account.
    boolean refreeze = false;
    if (!ArrayUtils.isEmpty(receiverAddress) && dynamicStore.supportDR()) {
      byte[] key = DelegatedResourceCapsule
          .createDbKey(unfreezeBalanceContract.getOwnerAddress().toByteArray(),
              unfreezeBalanceContract.getReceiverAddress().toByteArray());
      DelegatedResourceCapsule delegatedResourceCapsule = delegatedResourceStore
          .get(key);

      switch (unfreezeBalanceContract.getResource()) {
        case PHOTON:
          unfreezeBalance = delegatedResourceCapsule.getFrozenBalanceForPhoton();
          delegatedResourceCapsule.setFrozenBalanceForPhoton(0, 0);
          accountCapsule.addDelegatedFrozenBalanceForPhoton(-unfreezeBalance);
          break;
        case ENTROPY:
          unfreezeBalance = delegatedResourceCapsule.getFrozenBalanceForEntropy();
          delegatedResourceCapsule.setFrozenBalanceForEntropy(0, 0);
          accountCapsule.addDelegatedFrozenBalanceForEntropy(-unfreezeBalance);
          break;
        default:
          //this should never happen
          break;
      }

      AccountCapsule receiverCapsule = accountStore.get(receiverAddress);
      if (dynamicStore.getAllowVvmConstantinople() == 0 ||
          (receiverCapsule != null && receiverCapsule.getType() != AccountType.Contract)) {
        switch (unfreezeBalanceContract.getResource()) {
          case PHOTON:
            if (dynamicStore.getAllowVvmSolidity059() == 1
                && receiverCapsule.getAcquiredDelegatedFrozenBalanceForPhoton()
                < unfreezeBalance) {
              receiverCapsule.setAcquiredDelegatedFrozenBalanceForPhoton(0);
            } else {
              receiverCapsule.addAcquiredDelegatedFrozenBalanceForPhoton(-unfreezeBalance);
            }
            break;
          case ENTROPY:
            if (dynamicStore.getAllowVvmSolidity059() == 1
                && receiverCapsule.getAcquiredDelegatedFrozenBalanceForEntropy() < unfreezeBalance) {
              receiverCapsule.setAcquiredDelegatedFrozenBalanceForEntropy(0);
            } else {
              receiverCapsule.addAcquiredDelegatedFrozenBalanceForEntropy(-unfreezeBalance);
            }
            break;
          default:
            //this should never happen
            break;
        }
        accountStore.put(receiverCapsule.createDbKey(), receiverCapsule);
      }

      accountCapsule.setBalance(oldBalance + unfreezeBalance);

      if (delegatedResourceCapsule.getFrozenBalanceForPhoton() == 0
          && delegatedResourceCapsule.getFrozenBalanceForEntropy() == 0) {
        delegatedResourceStore.delete(key);

        //modify DelegatedResourceAccountIndexStore
        {
          DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = delegatedResourceAccountIndexStore
              .get(ownerAddress);
          if (delegatedResourceAccountIndexCapsule != null) {
            List<ByteString> toAccountsList = new ArrayList<>(delegatedResourceAccountIndexCapsule
                .getToAccountsList());
            toAccountsList.remove(ByteString.copyFrom(receiverAddress));
            delegatedResourceAccountIndexCapsule.setAllToAccounts(toAccountsList);
            delegatedResourceAccountIndexStore
                .put(ownerAddress, delegatedResourceAccountIndexCapsule);
          }
        }

        {
          DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = delegatedResourceAccountIndexStore
              .get(receiverAddress);
          if (delegatedResourceAccountIndexCapsule != null) {
            List<ByteString> fromAccountsList = new ArrayList<>(delegatedResourceAccountIndexCapsule
                .getFromAccountsList());
            fromAccountsList.remove(ByteString.copyFrom(ownerAddress));
            delegatedResourceAccountIndexCapsule.setAllFromAccounts(fromAccountsList);
            delegatedResourceAccountIndexStore
                .put(receiverAddress, delegatedResourceAccountIndexCapsule);
          }
        }

      } else {
        delegatedResourceStore.put(key, delegatedResourceCapsule);
      }
    } else {
      Map<Long, List<Long>> stageWeights = dynamicStore.getVPFreezeStageWeights();
      AccountFrozenStageResourceStore accountFrozenStageResourceStore = chainBaseManager.getAccountFrozenStageResourceStore();
      long now = dynamicStore.getLatestBlockHeaderTimestamp();

      if (dynamicStore.supportUnfreezeFragmentation()){
        switch (unfreezeBalanceContract.getResource()){
          case PHOTON:
          case ENTROPY:
            if (unfreezeBalanceContract.getUnfreezeBalance() > 0) {
              unfrozenFragBalance = unfreezeBalanceContract.getUnfreezeBalance();
            }
            break;
        }
      }

      switch (unfreezeBalanceContract.getResource()) {
        case PHOTON:
          List<Frozen> frozenList = Lists.newArrayList();
          frozenList.addAll(accountCapsule.getFrozenList());
          Iterator<Frozen> iterator = frozenList.iterator();
          while (iterator.hasNext()) {
            Frozen next = iterator.next();
            if (next.getExpireTime() <= now) {
              unfreezeBalance += next.getFrozenBalance();
              iterator.remove();
            }
          }
          long photonExpiredTime = accountCapsule.getFrozenExpireTime();

          if (dynamicStore.getAllowVPFreezeStageWeight() == 1) {
            unfreezeBalance = accountCapsule.getFrozenBalance();
            List<Long> stageList = parseStageList(unfreezeBalanceContract);
            for (Long stage : stageList) {
              byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, stage);
              AccountFrozenStageResourceCapsule capsule = accountFrozenStageResourceStore.get(key);
              if (stage!=1L &&
                  capsule.getInstance().getExpireTimeForPhoton() < now - dynamicStore.getRefreezeConsiderationPeriodResult()) {
                continue;
              }

              if (stage == 1L && capsule == null){
                unfrozenFragBalance = getUnfreezeFragBalance(unfreezeBalanceContract, dynamicStore, unfreezeBalance);
                frozenList.clear();
              } else {
                long stageFrozenBalance = capsule.getInstance().getFrozenBalanceForPhoton();
                unfrozenFragBalance = getUnfreezeFragBalance(unfreezeBalanceContract, dynamicStore, stageFrozenBalance);
                long remainFrozenBalance = stageFrozenBalance - unfrozenFragBalance;
                long expireTime = remainFrozenBalance > 0 ? capsule.getInstance().getExpireTimeForPhoton() : 0L;

                dynamicStore.addTotalStagePhotonWeight(Collections.singletonList(stage), -unfrozenFragBalance / VS_PRECISION);
                capsule.setFrozenBalanceForPhoton(remainFrozenBalance, expireTime);
                accountFrozenStageResourceStore.put(key, capsule);
              }
            }
            long totalStage = 0L;
            long expireTime = 0L;
            for (Map.Entry<Long, List<Long>> entry : stageWeights.entrySet()) {
              byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, entry.getKey());
              AccountFrozenStageResourceCapsule capsule = accountFrozenStageResourceStore.get(key);
              if (capsule == null || capsule.getInstance().getFrozenBalanceForPhoton() == 0L) {
                continue;
              }
              totalStage += capsule.getInstance().getFrozenBalanceForPhoton();
              expireTime = Math.max(expireTime, capsule.getInstance().getExpireTimeForPhoton());
            }

            if (totalStage == 0){
              totalStage = unfreezeBalance - unfrozenFragBalance;
              expireTime = photonExpiredTime;
            }

            if (totalStage > 0) {
              Frozen newFrozen = Frozen.newBuilder()
                  .setFrozenBalance(totalStage)
                  .setExpireTime(expireTime)
                  .build();
              unfreezeBalance = unfreezeBalance - totalStage;
              frozenList.clear();
              frozenList.addAll(Collections.singletonList(newFrozen));
            }
          }else {
            if (dynamicStore.supportUnfreezeFragmentation() && unfreezeBalance > unfrozenFragBalance) {
              long unfreezeRemainBalance = unfreezeBalance - unfrozenFragBalance;
              unfreezeBalance = unfrozenFragBalance;
              Frozen newFrozen = Frozen.newBuilder()
                      .setFrozenBalance(unfreezeRemainBalance)
                      .setExpireTime(photonExpiredTime)
                      .build();
              frozenList.clear();
              frozenList.addAll(Collections.singletonList(newFrozen));
            }
          }
          accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
              .setBalance(oldBalance + unfreezeBalance)
              .clearFrozen().addAllFrozen(frozenList).build());

          break;
        case ENTROPY:
          unfreezeBalance = accountCapsule.getAccountResource().getFrozenBalanceForEntropy()
              .getFrozenBalance();
          long entropyExpiredTime = accountCapsule.getEntropyFrozenExpireTime();
          unfrozenFragBalance = getUnfreezeFragBalance(unfreezeBalanceContract, dynamicStore, unfreezeBalance);

          AccountResource newAccountResource = accountCapsule.getAccountResource().toBuilder()
              .clearFrozenBalanceForEntropy().build();
          if (dynamicStore.getAllowVPFreezeStageWeight() == 1) {
            List<Long> stageList = parseStageList(unfreezeBalanceContract);
            for (Long stage : stageList) {
              byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, stage);
              AccountFrozenStageResourceCapsule capsule = accountFrozenStageResourceStore.get(key);
              if (stage!=1L &&
                  capsule.getInstance().getExpireTimeForEntropy() < now - dynamicStore.getRefreezeConsiderationPeriodResult()) {
                continue;
              }
              if (stage != 1L || capsule != null) {
                long stageFrozenBalance = capsule.getInstance().getFrozenBalanceForEntropy();
                unfrozenFragBalance = getUnfreezeFragBalance(unfreezeBalanceContract, dynamicStore, stageFrozenBalance);
                long remainFrozenBalance = stageFrozenBalance - unfrozenFragBalance;
                long expireTime = remainFrozenBalance > 0 ? capsule.getInstance().getExpireTimeForPhoton() : 0L;

                dynamicStore.addTotalStageEntropyWeight(Collections.singletonList(stage), -unfrozenFragBalance / VS_PRECISION);
                capsule.setFrozenBalanceForEntropy(remainFrozenBalance, expireTime);
                accountFrozenStageResourceStore.put(key, capsule);
              }
            }
            long totalStage = 0L;
            long expireTime = 0L;
            for (Map.Entry<Long, List<Long>> entry : stageWeights.entrySet()) {
              byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, entry.getKey());
              AccountFrozenStageResourceCapsule capsule = accountFrozenStageResourceStore.get(key);
              if (capsule == null || capsule.getInstance().getFrozenBalanceForEntropy() == 0L) {
                continue;
              }
              totalStage += capsule.getInstance().getFrozenBalanceForEntropy();
              expireTime = Math.max(expireTime, capsule.getInstance().getExpireTimeForEntropy());
            }

            if (totalStage == 0){
              totalStage = unfreezeBalance - unfrozenFragBalance;
              expireTime = entropyExpiredTime;
            }

            if (totalStage > 0) {
              Frozen newFrozenForEntropy = Frozen.newBuilder()
                  .setFrozenBalance(totalStage)
                  .setExpireTime(expireTime)
                  .build();
              newAccountResource = accountCapsule.getAccountResource().toBuilder()
                  .setFrozenBalanceForEntropy(newFrozenForEntropy).build();
              unfreezeBalance = unfreezeBalance - totalStage;
            }
          }else {
            if (dynamicStore.supportUnfreezeFragmentation() && unfreezeBalance > unfrozenFragBalance) {
              long unfreezeRemainBalance = unfreezeBalance - unfrozenFragBalance;
              unfreezeBalance = unfrozenFragBalance;
              Frozen newFrozenForEntropy = Frozen.newBuilder()
                      .setFrozenBalance(unfreezeRemainBalance)
                      .setExpireTime(entropyExpiredTime)
                      .build();
              newAccountResource = accountCapsule.getAccountResource().toBuilder()
                      .setFrozenBalanceForEntropy(newFrozenForEntropy).build();
            }
          }

          accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
              .setBalance(oldBalance + unfreezeBalance)
              .setAccountResource(newAccountResource).build());

          break;
        case FVGUARANTEE:
          unfreezeBalance = accountCapsule.getAccountResource().getFrozenBalanceForFvguarantee()
                  .getFrozenBalance();
          AccountResource newFVGuarantee = accountCapsule.getAccountResource().toBuilder()
                  .clearFrozenBalanceForFvguarantee().build();
          accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
                  .setBalance(oldBalance + unfreezeBalance)
                  .setAccountResource(newFVGuarantee).build());
          break;
        case SPREAD:
          unfreezeBalance = accountCapsule.getAccountResource().getFrozenBalanceForSpread()
              .getFrozenBalance();
            AccountResource newSpread = accountCapsule.getAccountResource().toBuilder()
                .clearFrozenBalanceForSpread().build();
            accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
                .setBalance(oldBalance + unfreezeBalance)
                .setAccountResource(newSpread).build());

            clearSpreadRelationShip(ownerAddress);
          break;
        default:
          //this should never happen
          break;
      }

      for (Map.Entry<Long, List<Long>> entry : stageWeights.entrySet()) {
        byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, entry.getKey());
        AccountFrozenStageResourceCapsule capsule = accountFrozenStageResourceStore.get(key);
        if (capsule == null) {
          continue;
        }
        if (capsule.getInstance().getFrozenBalanceForPhoton() == 0L
            && capsule.getInstance().getFrozenBalanceForEntropy() == 0L) {
          accountFrozenStageResourceStore.delete(key);
        }
      }
    }

    if (dynamicStore.getAllowVPFreezeStageWeight() == 1) {
      switch (unfreezeBalanceContract.getResource()) {
        case PHOTON:
        case ENTROPY:
          long weightMerge = AccountCapsule.calcAccountFrozenStageWeightMerge(
                  accountCapsule, chainBaseManager.getAccountFrozenStageResourceStore(), dynamicStore);
          accountCapsule.setFrozenStageWeightMerge(weightMerge);
          break;
      }
    }

    boolean clearVote = true;
    switch (unfreezeBalanceContract.getResource()) {
      case PHOTON:
        dynamicStore
                .addTotalPhotonWeight(-unfreezeBalance / VS_PRECISION);
        break;
      case ENTROPY:
        dynamicStore
                .addTotalEntropyWeight(-unfreezeBalance / VS_PRECISION);
        break;
      case SPREAD:
        dynamicStore
                .addTotalSpreadMintWeight(-unfreezeBalance / VS_PRECISION);
        clearVote = dynamicStore.getAllowUnfreezeSpreadOrFvGuaranteeClearVote() == 1;
        break;
      case FVGUARANTEE:
        dynamicStore
                .addTotalFVGuaranteeWeight(-unfreezeBalance / VS_PRECISION);
        clearVote = dynamicStore.getAllowUnfreezeSpreadOrFvGuaranteeClearVote() == 1;
        break;
      default:
        //this should never happen
        break;
    }

    if (clearVote) {
      if (dynamicStore.supportUnfreezeFragmentation()){
        updateVote(accountCapsule, unfreezeBalanceContract, ownerAddress);
      }else {
        VotesCapsule votesCapsule;
        if (!votesStore.has(ownerAddress)) {
          votesCapsule = new VotesCapsule(unfreezeBalanceContract.getOwnerAddress(),
                  accountCapsule.getVotesList());
        } else {
          votesCapsule = votesStore.get(ownerAddress);
        }
        accountCapsule.clearVotes();
        votesCapsule.clearNewVotes();

        votesStore.put(ownerAddress, votesCapsule);
      }
    }

    accountStore.put(ownerAddress, accountCapsule);

    ret.setUnfreezeAmount(unfreezeBalance);
    ret.setStatus(fee, code.SUCESS);

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
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    DelegatedResourceStore delegatedResourceStore = chainBaseManager.getDelegatedResourceStore();
    AccountFrozenStageResourceStore accountFrozenStageResourceStore = chainBaseManager.getAccountFrozenStageResourceStore();
    if (!this.any.is(UnfreezeBalanceContract.class)) {
      throw new ContractValidateException(
          "contract type error, expected type [UnfreezeBalanceContract], real type[" + any
              .getClass() + "]");
    }
    final UnfreezeBalanceContract unfreezeBalanceContract;
    try {
      unfreezeBalanceContract = this.any.unpack(UnfreezeBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    byte[] ownerAddress = unfreezeBalanceContract.getOwnerAddress().toByteArray();
    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    if (accountCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
          ACCOUNT_EXCEPTION_STR + readableOwnerAddress + "] does not exist");
    }

    if (dynamicStore.getAllowVPFreezeStageWeight() != 1
        && unfreezeBalanceContract.getStagesCount() > 0) {
      throw new ContractValidateException("unfreeze stages is not allowed yet");
    }
    long now = dynamicStore.getLatestBlockHeaderTimestamp();
    byte[] receiverAddress = unfreezeBalanceContract.getReceiverAddress().toByteArray();
    //If the receiver is not included in the contract, unfreeze frozen balance for this account.
    //otherwise,unfreeze delegated frozen balance provided this account.
    if (dynamicStore.getAllowVPFreezeStageWeight() == 1 &&
        (unfreezeBalanceContract.getResource() == ResourceCode.PHOTON
            || unfreezeBalanceContract.getResource() == ResourceCode.ENTROPY)) {
      Map<Long, List<Long>> stageWeight = dynamicStore.getVPFreezeStageWeights();
      Set<Long> stages = new HashSet<>();
      for (Long stage : unfreezeBalanceContract.getStagesList()) {
        if (!stageWeight.containsKey(stage)) {
          throw new ContractValidateException("stages must be on of " + stageWeight.keySet());
        }
        stages.add(stage);
      }
      if (stages.size() != unfreezeBalanceContract.getStagesCount()) {
        throw new ContractValidateException("stages must be not repeated");
      }
    }

    if (!checkUnfreezeFragBalanceCondition(unfreezeBalanceContract, dynamicStore)){
      throw new ContractValidateException("Invalid unfreeze operation, Illegal unfreeze_balance field");
    }

    if (!ArrayUtils.isEmpty(receiverAddress) && dynamicStore.supportDR()) {
      if (Arrays.equals(receiverAddress, ownerAddress)) {
        throw new ContractValidateException(
            "receiverAddress must not be the same as ownerAddress");
      }

      if (!DecodeUtil.addressValid(receiverAddress)) {
        throw new ContractValidateException("Invalid receiverAddress");
      }

      AccountCapsule receiverCapsule = accountStore.get(receiverAddress);
      if (dynamicStore.getAllowVvmConstantinople() == 0
          && receiverCapsule == null) {
        String readableReceiverAddress = StringUtil.createReadableString(receiverAddress);
        throw new ContractValidateException(
            "Receiver Account[" + readableReceiverAddress + "] does not exist");
      }

      byte[] key = DelegatedResourceCapsule
          .createDbKey(unfreezeBalanceContract.getOwnerAddress().toByteArray(),
              unfreezeBalanceContract.getReceiverAddress().toByteArray());
      DelegatedResourceCapsule delegatedResourceCapsule = delegatedResourceStore
          .get(key);
      if (delegatedResourceCapsule == null) {
        throw new ContractValidateException(
            "delegated Resource does not exist");
      }

      switch (unfreezeBalanceContract.getResource()) {
        case PHOTON:
          if (delegatedResourceCapsule.getFrozenBalanceForPhoton() <= 0) {
            throw new ContractValidateException("no delegatedFrozenBalance(PHOTON)");
          }

          if (dynamicStore.getAllowVvmConstantinople() == 0) {
            if (receiverCapsule.getAcquiredDelegatedFrozenBalanceForPhoton()
                < delegatedResourceCapsule.getFrozenBalanceForPhoton()) {
              throw new ContractValidateException(
                  "AcquiredDelegatedFrozenBalanceForPhoton[" + receiverCapsule
                      .getAcquiredDelegatedFrozenBalanceForPhoton() + "] < delegatedPhoton["
                      + delegatedResourceCapsule.getFrozenBalanceForPhoton()
                      + "]");
            }
          } else {
            if (dynamicStore.getAllowVvmSolidity059() != 1
                && receiverCapsule != null
                && receiverCapsule.getType() != AccountType.Contract
                && receiverCapsule.getAcquiredDelegatedFrozenBalanceForPhoton()
                < delegatedResourceCapsule.getFrozenBalanceForPhoton()) {
              throw new ContractValidateException(
                  "AcquiredDelegatedFrozenBalanceForPhoton[" + receiverCapsule
                      .getAcquiredDelegatedFrozenBalanceForPhoton() + "] < delegatedPhoton["
                      + delegatedResourceCapsule.getFrozenBalanceForPhoton()
                      + "]");
            }
          }

          if (delegatedResourceCapsule.getExpireTimeForPhoton() > now) {
            throw new ContractValidateException("It's not time to unfreeze.");
          }
          break;
        case ENTROPY:
          if (delegatedResourceCapsule.getFrozenBalanceForEntropy() <= 0) {
            throw new ContractValidateException("no delegateFrozenBalance(Entropy)");
          }
          if (dynamicStore.getAllowVvmConstantinople() == 0) {
            if (receiverCapsule.getAcquiredDelegatedFrozenBalanceForEntropy()
                < delegatedResourceCapsule.getFrozenBalanceForEntropy()) {
              throw new ContractValidateException(
                  "AcquiredDelegatedFrozenBalanceForEntropy[" + receiverCapsule
                      .getAcquiredDelegatedFrozenBalanceForEntropy() + "] < delegatedEntropy["
                      + delegatedResourceCapsule.getFrozenBalanceForEntropy() +
                      "]");
            }
          } else {
            if (dynamicStore.getAllowVvmSolidity059() != 1
                && receiverCapsule != null
                && receiverCapsule.getType() != AccountType.Contract
                && receiverCapsule.getAcquiredDelegatedFrozenBalanceForEntropy()
                < delegatedResourceCapsule.getFrozenBalanceForEntropy()) {
              throw new ContractValidateException(
                  "AcquiredDelegatedFrozenBalanceForEntropy[" + receiverCapsule
                      .getAcquiredDelegatedFrozenBalanceForEntropy() + "] < delegatedEntropy["
                      + delegatedResourceCapsule.getFrozenBalanceForEntropy() +
                      "]");
            }
          }

          if (delegatedResourceCapsule.getExpireTimeForEntropy(dynamicStore) > now) {
            throw new ContractValidateException("It's not time to unfreeze.");
          }
          break;
        default:
          throw new ContractValidateException(
              "ResourceCode error.valid ResourceCode[PHOTON、Entropy]");
      }

    } else {
      switch (unfreezeBalanceContract.getResource()) {
        case PHOTON:
          if (accountCapsule.getFrozenCount() <= 0) {
            throw new ContractValidateException("no frozenBalance(PHOTON)");
          }

          if (dynamicStore.getAllowVPFreezeStageWeight() != 1) {
            long allowedUnfreezeCount = accountCapsule.getFrozenList().stream()
                .filter(frozen -> frozen.getExpireTime() <= now).count();
            if (allowedUnfreezeCount <= 0) {
              throw new ContractValidateException("It's not time to unfreeze(PHOTON).");
            }
            if (checkUnFreezeBalanceIllegal(unfreezeBalanceContract, dynamicStore, accountCapsule.getFrozenBalance())){
              throw new ContractValidateException("Insufficient unfreezeBalance(PHOTON) ");
            }
          } else {
            List<Long> stageList = parseStageList(unfreezeBalanceContract);
            long frozenAmount = 0;
            if (stageList.contains(1L)){
              long totalStageBalance = AccountFrozenStageResourceCapsule.getTotalStageBalanceForPhoton(ownerAddress, 1L, accountFrozenStageResourceStore, dynamicStore);
              if (accountCapsule.getFrozenBalance() - totalStageBalance == 0) {
                throw new ContractValidateException("no frozenBalance(PHOTON)");
              }
              frozenAmount = accountCapsule.getFrozenBalance() - totalStageBalance;
            }

            if (!checkStageNumUnFreezeBalance(stageList, unfreezeBalanceContract, dynamicStore)) {
              throw new ContractValidateException("Invalid unfreeze operation, the number of unfreezing stage(PHOTON) exceeds limit");
            }

            for (Long stage : stageList) {
              byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, stage);
              AccountFrozenStageResourceCapsule stageCapsule = accountFrozenStageResourceStore.get(key);
              if (stage == 1L){
                if (stageCapsule == null){
                  long allowedUnfreezeCount = accountCapsule.getFrozenList().stream()
                          .filter(frozen -> frozen.getExpireTime() <= now).count();
                  if (allowedUnfreezeCount <= 0) {
                    throw new ContractValidateException("It's not time to unfreeze(PHOTON).");
                  }
                }else {
                  if (stageCapsule.getInstance().getFrozenBalanceForPhoton() == 0) {
                    throw new ContractValidateException("no frozenBalance(PHOTON) stage:" + stage);
                  }

                  if (stageCapsule.getInstance().getExpireTimeForPhoton() > now) {
                    throw new ContractValidateException("It's not time to unfreeze(PHOTON) stage: " + stage);
                  }
                  frozenAmount = stageCapsule.getInstance().getFrozenBalanceForPhoton();
                }

              }else {
                if (stageCapsule == null || stageCapsule.getInstance().getFrozenBalanceForPhoton() == 0) {
                  throw new ContractValidateException("no frozenBalance(PHOTON) stage:" + stage);
                }

                if (stageCapsule.getInstance().getExpireTimeForPhoton() > now ||
                        stageCapsule.getInstance().getExpireTimeForPhoton() < now - dynamicStore.getRefreezeConsiderationPeriodResult()) {
                  throw new ContractValidateException("It's not time to unfreeze(PHOTON) stage: " + stage);
                }
                frozenAmount = stageCapsule.getInstance().getFrozenBalanceForPhoton();
              }
              if (checkUnFreezeBalanceIllegal(unfreezeBalanceContract, dynamicStore, frozenAmount)){
                throw new ContractValidateException("Insufficient unfreezeBalance(PHOTON) stage: " + stage);
              }
            }
          }
          break;
        case ENTROPY:
          Frozen frozenBalanceForEntropy = accountCapsule.getAccountResource()
              .getFrozenBalanceForEntropy();
          if (frozenBalanceForEntropy.getFrozenBalance() <= 0) {
            throw new ContractValidateException("no frozenBalance(Entropy)");
          }

          if (dynamicStore.getAllowVPFreezeStageWeight() != 1)   {
            if (frozenBalanceForEntropy.getExpireTime() > now) {
              throw new ContractValidateException("It's not time to unfreeze(Entropy).");
            }
            if (checkUnFreezeBalanceIllegal(unfreezeBalanceContract, dynamicStore, frozenBalanceForEntropy.getFrozenBalance())){
              throw new ContractValidateException("Insufficient unfreezeBalance(Entropy)");
            }
          }else {
            List<Long> stageList = parseStageList(unfreezeBalanceContract);
            long frozenAmount = 0L;
            if (stageList.contains(1L)){
              long totalStageBalance = AccountFrozenStageResourceCapsule.getTotalStageBalanceForEntropy(ownerAddress, 1L, accountFrozenStageResourceStore, dynamicStore);
              if (accountCapsule.getEntropyFrozenBalance() - totalStageBalance == 0) {
                throw new ContractValidateException("no frozenBalance(Entropy)");
              }
              frozenAmount = accountCapsule.getEntropyFrozenBalance() - totalStageBalance;
            }

            if (!checkStageNumUnFreezeBalance(stageList, unfreezeBalanceContract, dynamicStore)) {
              throw new ContractValidateException("Invalid unfreeze operation, the number of unfreezing stage(ENTROPY) exceeds limit");
            }

            for (Long stage : stageList) {
              byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, stage);
              AccountFrozenStageResourceCapsule stageCapsule = accountFrozenStageResourceStore.get(key);
              if (stage == 1L) {
                if (stageCapsule == null){
                  if (frozenBalanceForEntropy.getExpireTime() > now) {
                    throw new ContractValidateException("It's not time to unfreeze(Entropy).");
                  }
                }else {
                  if (stageCapsule.getInstance().getExpireTimeForEntropy() > now) {
                    throw new ContractValidateException("It's not time to unfreeze(Entropy) stage: " + stage);
                  }
                  frozenAmount = stageCapsule.getInstance().getFrozenBalanceForEntropy();
                }
              } else {
                if (stageCapsule == null || stageCapsule.getInstance().getFrozenBalanceForEntropy() == 0) {
                  throw new ContractValidateException("no frozenBalance(Entropy) stage: "+stage);
                }

                if (stageCapsule.getInstance().getExpireTimeForEntropy() > now ||
                        stageCapsule.getInstance().getExpireTimeForEntropy() < now - dynamicStore.getRefreezeConsiderationPeriodResult()) {
                  throw new ContractValidateException("It's not time to unfreeze(Entropy) stage: " + stage);
                }
                frozenAmount = stageCapsule.getInstance().getFrozenBalanceForEntropy();
              }
              if (checkUnFreezeBalanceIllegal(unfreezeBalanceContract, dynamicStore, frozenAmount)){
                throw new ContractValidateException("Insufficient unfreezeBalance(Entropy) stage: " + stage);
              }
            }
          }
          break;
        case FVGUARANTEE:
          Frozen frozenBalanceForFVGuarantee = accountCapsule.getAccountResource()
                  .getFrozenBalanceForFvguarantee();
          if (frozenBalanceForFVGuarantee.getFrozenBalance() <= 0) {
            throw new ContractValidateException("no frozenBalance(FVGuarantee)");
          }
          if (frozenBalanceForFVGuarantee.getExpireTime() > now) {
            throw new ContractValidateException("It's not time to unfreeze(FVGuarantee).");
          }
          break;
        case SPREAD:
          Frozen frozenBalanceForSpread = accountCapsule.getAccountResource()
                  .getFrozenBalanceForSpread();
          if (frozenBalanceForSpread.getFrozenBalance() <= 0) {
            throw new ContractValidateException("no frozenBalance(SpreadMint)");
          }

          if (dynamicStore.getAllowVPFreezeStageWeight() != 1){
            if (frozenBalanceForSpread.getExpireTime() > now) {
              throw new ContractValidateException("It's not time to unfreeze(SpreadMint).");
            }
          }else {
            if (frozenBalanceForSpread.getExpireTime() > now
                    || frozenBalanceForSpread.getExpireTime() < now - dynamicStore.getSpreadRefreezeConsiderationPeriodResult()) {
              throw new ContractValidateException("It's not time to unfreeze(SpreadMint).");
            }
          }
          break;
        default:
          throw new ContractValidateException(
              "ResourceCode error.valid ResourceCode[PHOTON、ENTROPY]");
      }

    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(UnfreezeBalanceContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

  private void clearSpreadRelationShip(byte[] ownerAddress){
    SpreadRelationShipStore spreadRelationShipStore = chainBaseManager.getSpreadRelationShipStore();
    SpreadRelationShipCapsule spreadRelationShipCapsule = spreadRelationShipStore.get(ownerAddress);
    if (spreadRelationShipCapsule != null) {
      DynamicPropertiesStore dynamicPropertiesStore = chainBaseManager.getDynamicPropertiesStore();
      long cycle = dynamicPropertiesStore.getCurrentCycleNumber();
      long now = dynamicPropertiesStore.getLatestBlockHeaderTimestamp();
      spreadRelationShipCapsule.setFrozenBalanceForSpread(0, now, cycle); // clear SpreadRelationShip frozen_balance_for_spread, not delete key
      if (dynamicPropertiesStore.getLatestBlockHeaderNumber() >= CommonParameter.PARAMETER.spreadMintUnfreezeClearRelationShipEffectBlockNum){
        spreadRelationShipStore.put(ownerAddress, spreadRelationShipCapsule);
      }
      if (CommonParameter.PARAMETER.isKafkaEnable()) {
        JSONObject jsonObject= JSONObject.parseObject(JsonFormat.printToString(spreadRelationShipCapsule.getInstance(), true));
        if (CommonParameter.getInstance().isHistoryBalanceLookup()) {
          jsonObject.putAll(chainBaseManager.getBalanceTraceStore().assembleJsonInfo());
        }
        jsonObject.put("type", "unfreeze");
        Producer.getInstance().send("SPREADRELATIONSHIP", Hex.toHexString(ownerAddress), jsonObject.toJSONString());
      }
    }
  }

  private List<Long> parseStageList(UnfreezeBalanceContract unfreezeBalanceContract){
    List<Long> stageList = new ArrayList<>();
    if (unfreezeBalanceContract == null || unfreezeBalanceContract.getStagesCount() == 0) {
      stageList.add(1L);
    } else {
      stageList.addAll(unfreezeBalanceContract.getStagesList());
    }
    return stageList;
  }

  public boolean checkUnfreezeFragBalanceCondition(UnfreezeBalanceContract unfreezeBalanceContract, DynamicPropertiesStore dynamicStore){
    boolean checkOk = false;
    long unfreezeBalance = unfreezeBalanceContract.getUnfreezeBalance();
    byte[] receiverAddress = unfreezeBalanceContract.getReceiverAddress().toByteArray();
    if (!ArrayUtils.isEmpty(receiverAddress) && dynamicStore.supportDR()){
      checkOk = unfreezeBalance == 0;
    } else {
      if (dynamicStore.supportUnfreezeFragmentation()){
        switch (unfreezeBalanceContract.getResource()){
          case PHOTON:
          case ENTROPY:
            checkOk = unfreezeBalance > 0;
            break;
          case FVGUARANTEE:
          case SPREAD:
            checkOk = unfreezeBalance == 0;
            break;
        }
      }else {
        checkOk = unfreezeBalance == 0;
      }
    }

    return checkOk;
  }

  // only one stage can be unfrozen fragmentation
  public boolean checkStageNumUnFreezeBalance(List<Long> stageList, UnfreezeBalanceContract unfreezeBalanceContract, DynamicPropertiesStore dynamicStore){
    if (dynamicStore.supportUnfreezeFragmentation()){
      switch (unfreezeBalanceContract.getResource()) {
        case ENTROPY:
        case PHOTON:
          if (unfreezeBalanceContract.getUnfreezeBalance() > 0 && stageList.size() > 1){
            return false;
          }
      }
    }

    return true;
  }

  public long getUnfreezeFragBalance(UnfreezeBalanceContract unfreezeBalanceContract, DynamicPropertiesStore dynamicStore, long unfreezeBalance){
    long unfreezeFragBalance = 0;
    if (dynamicStore.supportUnfreezeFragmentation()){
      unfreezeFragBalance = unfreezeBalanceContract.getUnfreezeBalance();
    } else {
      unfreezeFragBalance = unfreezeBalance;
    }
    return unfreezeFragBalance;
  }

  // false - pass， true -- reject
  public boolean checkUnFreezeBalanceIllegal(UnfreezeBalanceContract unfreezeBalanceContract, DynamicPropertiesStore dynamicStore, long frozenAmount){
    if (dynamicStore.supportUnfreezeFragmentation()){
      return unfreezeBalanceContract.getUnfreezeBalance() <= 0 || unfreezeBalanceContract.getUnfreezeBalance() > frozenAmount;
    }

    return false;
  }

  private void updateVote(AccountCapsule accountCapsule, final UnfreezeBalanceContract unfreezeBalanceContract, byte[] ownerAddress) {
    if (accountCapsule.getVotesList().isEmpty()) {
      return;
    }

    long totalVote = 0;
    for (Vote vote : accountCapsule.getVotesList()) {
      totalVote += vote.getVoteCount();
    }
    if (totalVote == 0) {
      return;
    }

    long ownedVisionPower = accountCapsule.getVisionPower();
    // vision power is enough to total votes
    if (ownedVisionPower >= totalVote * VS_PRECISION) {
      return;
    }

    VotesStore votesStore = chainBaseManager.getVotesStore();
    VotesCapsule votesCapsule;
    if (!votesStore.has(ownerAddress)) {
      votesCapsule = new VotesCapsule(
              unfreezeBalanceContract.getOwnerAddress(),
              accountCapsule.getVotesList()
      );
    } else {
      votesCapsule = votesStore.get(ownerAddress);
    }

    // Update Owner Voting
    votesCapsule.clearNewVotes();
    for (Vote vote : accountCapsule.getVotesList()) {
      long newVoteCount = (long)
              ((double) vote.getVoteCount() / totalVote * ownedVisionPower / VS_PRECISION);
      if (newVoteCount > 0) {
        votesCapsule.addNewVotes(vote.getVoteAddress(), newVoteCount);
      }
    }
    votesStore.put(ownerAddress, votesCapsule);

    accountCapsule.clearVotes();
    for (Vote vote : votesCapsule.getNewVotes()) {
      accountCapsule.addVotes(vote.getVoteAddress(), vote.getVoteCount());
    }
  }

}
