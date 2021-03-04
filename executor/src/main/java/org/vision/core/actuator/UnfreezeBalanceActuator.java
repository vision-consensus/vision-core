package org.vision.core.actuator;

import static org.vision.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;
import static org.vision.core.config.Parameter.ChainConstant.VS_PRECISION;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.vision.common.utils.DecodeUtil;
import org.vision.common.utils.StringUtil;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.DelegatedResourceAccountIndexCapsule;
import org.vision.core.capsule.DelegatedResourceCapsule;
import org.vision.core.capsule.TransactionResultCapsule;
import org.vision.core.capsule.VotesCapsule;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.service.MortgageService;
import org.vision.core.store.AccountStore;
import org.vision.core.store.DelegatedResourceAccountIndexStore;
import org.vision.core.store.DelegatedResourceStore;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.core.store.VotesStore;
import org.vision.protos.Protocol.Account.AccountResource;
import org.vision.protos.Protocol.Account.Frozen;
import org.vision.protos.Protocol.AccountType;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.protos.contract.BalanceContract.UnfreezeBalanceContract;

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
    mortgageService.withdrawReward(ownerAddress);

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    long oldBalance = accountCapsule.getBalance();

    long unfreezeBalance = 0L;

    byte[] receiverAddress = unfreezeBalanceContract.getReceiverAddress().toByteArray();
    //If the receiver is not included in the contract, unfreeze frozen balance for this account.
    //otherwise,unfreeze delegated frozen balance provided this account.
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
      switch (unfreezeBalanceContract.getResource()) {
        case PHOTON:

          List<Frozen> frozenList = Lists.newArrayList();
          frozenList.addAll(accountCapsule.getFrozenList());
          Iterator<Frozen> iterator = frozenList.iterator();
          long now = dynamicStore.getLatestBlockHeaderTimestamp();
          while (iterator.hasNext()) {
            Frozen next = iterator.next();
            if (next.getExpireTime() <= now) {
              unfreezeBalance += next.getFrozenBalance();
              iterator.remove();
            }
          }

          accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
              .setBalance(oldBalance + unfreezeBalance)
              .clearFrozen().addAllFrozen(frozenList).build());

          break;
        case ENTROPY:
          unfreezeBalance = accountCapsule.getAccountResource().getFrozenBalanceForEntropy()
              .getFrozenBalance();

          AccountResource newAccountResource = accountCapsule.getAccountResource().toBuilder()
              .clearFrozenBalanceForEntropy().build();
          accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
              .setBalance(oldBalance + unfreezeBalance)
              .setAccountResource(newAccountResource).build());

          break;
        default:
          //this should never happen
          break;
      }

    }

    switch (unfreezeBalanceContract.getResource()) {
      case PHOTON:
        dynamicStore
            .addTotalPhotonWeight(-unfreezeBalance / VS_PRECISION);
        break;
      case ENTROPY:
        dynamicStore
            .addTotalEntropyWeight(-unfreezeBalance / VS_PRECISION);
        break;
      default:
        //this should never happen
        break;
    }

    VotesCapsule votesCapsule;
    if (!votesStore.has(ownerAddress)) {
      votesCapsule = new VotesCapsule(unfreezeBalanceContract.getOwnerAddress(),
          accountCapsule.getVotesList());
    } else {
      votesCapsule = votesStore.get(ownerAddress);
    }
    accountCapsule.clearVotes();
    votesCapsule.clearNewVotes();

    accountStore.put(ownerAddress, accountCapsule);

    votesStore.put(ownerAddress, votesCapsule);

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
    long now = dynamicStore.getLatestBlockHeaderTimestamp();
    byte[] receiverAddress = unfreezeBalanceContract.getReceiverAddress().toByteArray();
    //If the receiver is not included in the contract, unfreeze frozen balance for this account.
    //otherwise,unfreeze delegated frozen balance provided this account.
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

          long allowedUnfreezeCount = accountCapsule.getFrozenList().stream()
              .filter(frozen -> frozen.getExpireTime() <= now).count();
          if (allowedUnfreezeCount <= 0) {
            throw new ContractValidateException("It's not time to unfreeze(PHOTON).");
          }
          break;
        case ENTROPY:
          Frozen frozenBalanceForEntropy = accountCapsule.getAccountResource()
              .getFrozenBalanceForEntropy();
          if (frozenBalanceForEntropy.getFrozenBalance() <= 0) {
            throw new ContractValidateException("no frozenBalance(Entropy)");
          }
          if (frozenBalanceForEntropy.getExpireTime() > now) {
            throw new ContractValidateException("It's not time to unfreeze(Entropy).");
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

}
