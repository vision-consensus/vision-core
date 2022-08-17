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
import org.vision.protos.Protocol.Account.AccountResource;
import org.vision.protos.Protocol.Account.Frozen;
import org.vision.protos.Protocol.AccountType;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.protos.contract.BalanceContract;
import org.vision.protos.contract.BalanceContract.UnfreezeBalanceContract;
import org.vision.protos.contract.Common;

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
    if(unfreezeBalanceContract.getResource() != Common.ResourceCode.FVGUARANTEE){
      mortgageService.withdrawReward(ownerAddress, unfreezeBalanceContract.getResource() == Common.ResourceCode.SPREAD);
    }

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    long oldBalance = accountCapsule.getBalance();

    long unfreezeBalance = 0L;

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
                frozenList.clear();
              } else {
                dynamicStore
                        .addTotalStagePhotonWeight(Collections.singletonList(stage),
                                -capsule.getInstance().getFrozenBalanceForPhoton() / VS_PRECISION);
                capsule.setFrozenBalanceForPhoton(0, 0);
                accountFrozenStageResourceStore.put(key, capsule);
              }
            }
            AccountFrozenStageResourceCapsule.dealReFreezeConsideration(
                accountCapsule, accountFrozenStageResourceStore, dynamicStore);
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
            if (totalStage > 0) {
              Frozen newFrozen = Frozen.newBuilder()
                  .setFrozenBalance(totalStage)
                  .setExpireTime(expireTime)
                  .build();
              unfreezeBalance = unfreezeBalance - totalStage;
              frozenList.clear();
              frozenList.addAll(Collections.singletonList(newFrozen));
            }
          }
          accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
              .setBalance(oldBalance + unfreezeBalance)
              .clearFrozen().addAllFrozen(frozenList).build());

          if (dynamicStore.getAllowVPFreezeStageWeight() == 1) {
            long merge = AccountCapsule.calcAccountFrozenStageWeightMerge(
                accountCapsule, accountFrozenStageResourceStore, dynamicStore);
            accountCapsule.setFrozenStageWeightMerge(merge);
          }
          break;
        case ENTROPY:
          unfreezeBalance = accountCapsule.getAccountResource().getFrozenBalanceForEntropy()
              .getFrozenBalance();

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
                dynamicStore
                        .addTotalStageEntropyWeight(Collections.singletonList(stage),
                                -capsule.getInstance().getFrozenBalanceForEntropy() / VS_PRECISION);
                capsule.setFrozenBalanceForEntropy(0, 0);
                accountFrozenStageResourceStore.put(key, capsule);
              }
            }
            AccountFrozenStageResourceCapsule.dealReFreezeConsideration(
                accountCapsule, accountFrozenStageResourceStore, dynamicStore);
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
            if (totalStage > 0) {
              Frozen newFrozenForEntropy = Frozen.newBuilder()
                  .setFrozenBalance(totalStage)
                  .setExpireTime(expireTime)
                  .build();
              newAccountResource = accountCapsule.getAccountResource().toBuilder()
                  .setFrozenBalanceForEntropy(newFrozenForEntropy).build();
              unfreezeBalance = unfreezeBalance - totalStage;
            }
          }

          accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
              .setBalance(oldBalance + unfreezeBalance)
              .setAccountResource(newAccountResource).build());

          if (dynamicStore.getAllowVPFreezeStageWeight() == 1) {
            long merge = AccountCapsule.calcAccountFrozenStageWeightMerge(
                accountCapsule, accountFrozenStageResourceStore, dynamicStore);
            accountCapsule.setFrozenStageWeightMerge(merge);
          }
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
          if (dynamicStore.getAllowVPFreezeStageWeight() != 1) {
            AccountResource newSpread = accountCapsule.getAccountResource().toBuilder()
                .clearFrozenBalanceForSpread().build();
            accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
                .setBalance(oldBalance + unfreezeBalance)
                .setAccountResource(newSpread).build());

            clearSpreadRelationShip(ownerAddress);
          } else {
            refreeze = SpreadRelationShipCapsule.dealSpreadReFreezeConsideration(
                accountCapsule, chainBaseManager.getSpreadRelationShipStore(), dynamicStore);
            if (!refreeze) {
              AccountResource newSpread = accountCapsule.getAccountResource().toBuilder()
                  .clearFrozenBalanceForSpread().build();
              accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
                  .setBalance(oldBalance + unfreezeBalance)
                  .setAccountResource(newSpread).build());

              clearSpreadRelationShip(ownerAddress);
            }else {
              unfreezeBalance = 0L;
            }
          }
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
        (unfreezeBalanceContract.getResource() == Common.ResourceCode.PHOTON
            || unfreezeBalanceContract.getResource() == Common.ResourceCode.ENTROPY)) {
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
          } else {
            List<Long> stageList = parseStageList(unfreezeBalanceContract);

            if (stageList.contains(1L)){
              long totalStageBalance = AccountFrozenStageResourceCapsule.getTotalStageBalanceForPhoton(ownerAddress, 1L, accountFrozenStageResourceStore, dynamicStore);
              if (accountCapsule.getFrozenBalance() - totalStageBalance == 0) {
                throw new ContractValidateException("no frozenBalance(PHOTON)");
              }
            }

            for (Long stage : stageList) {
              byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, stage);
              AccountFrozenStageResourceCapsule stageCapsule = accountFrozenStageResourceStore.get(key);
              if (stage == 1L && stageCapsule == null){
                long allowedUnfreezeCount = accountCapsule.getFrozenList().stream()
                        .filter(frozen -> frozen.getExpireTime() <= now).count();
                if (allowedUnfreezeCount <= 0) {
                  throw new ContractValidateException("It's not time to unfreeze(PHOTON).");
                }
              }else {
                if (stageCapsule == null || stageCapsule.getInstance().getFrozenBalanceForPhoton() == 0) {
                  throw new ContractValidateException("no frozenBalance(PHOTON) stage:" + stage);
                }

                if (stageCapsule.getInstance().getExpireTimeForPhoton() > now) {
                  throw new ContractValidateException("It's not time to unfreeze(PHOTON) stage: " + stage);
                }
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
          if (frozenBalanceForEntropy.getExpireTime() > now
              && dynamicStore.getAllowVPFreezeStageWeight() != 1) {
            throw new ContractValidateException("It's not time to unfreeze(Entropy).");
          }

          if (dynamicStore.getAllowVPFreezeStageWeight() == 1) {
            List<Long> stageList = parseStageList(unfreezeBalanceContract);
            if (stageList.contains(1L)){
              long totalStageBalance = AccountFrozenStageResourceCapsule.getTotalStageBalanceForEntropy(ownerAddress, 1L, accountFrozenStageResourceStore, dynamicStore);
              if (accountCapsule.getEntropyFrozenBalance() - totalStageBalance == 0) {
                throw new ContractValidateException("no frozenBalance(Entropy)");
              }
            }

            for (Long stage : stageList) {
              byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, stage);
              AccountFrozenStageResourceCapsule stageCapsule = accountFrozenStageResourceStore.get(key);
              if (stage == 1L && stageCapsule == null) {
                if (frozenBalanceForEntropy.getExpireTime() > now) {
                  throw new ContractValidateException("It's not time to unfreeze(Entropy).");
                }
              } else {
                if (stageCapsule == null || stageCapsule.getInstance().getFrozenBalanceForEntropy() == 0) {
                  throw new ContractValidateException("no frozenBalance(Entropy) stage: "+stage);
                }

                if (stageCapsule.getInstance().getExpireTimeForEntropy() > now) {
                  throw new ContractValidateException("It's not time to unfreeze(Entropy) stage: " + stage);
                }
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

          if (frozenBalanceForSpread.getExpireTime() > now) {
            throw new ContractValidateException("It's not time to unfreeze(SpreadMint).");
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
}
