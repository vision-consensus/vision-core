package org.vision.core.vm.nativecontract;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.DecodeUtil;
import org.vision.common.utils.StringUtil;
import org.vision.core.actuator.ActuatorConstant;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.VotesCapsule;
import org.vision.core.config.Parameter.ChainConstant;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.core.vm.nativecontract.param.UnstakeParam;
import org.vision.core.vm.repository.Repository;

@Slf4j(topic = "Processor")
public class UnstakeProcessor {

  public void execute(UnstakeParam unstakeParam, Repository repository)
      throws ContractExeException {
    byte[] ownerAddress = unstakeParam.getOwnerAddress();

    ContractService contractService = ContractService.getInstance();
    contractService.withdrawReward(ownerAddress, repository);

    AccountCapsule accountCapsule = repository.getAccount(ownerAddress);
    long oldBalance = accountCapsule.getBalance();

    long unfreezeBalance = accountCapsule.getFrozenList().get(0).getFrozenBalance();

    VotesCapsule votesCapsule = repository.getVotesCapsule(ownerAddress);
    if (votesCapsule == null) {
      votesCapsule = new VotesCapsule(ByteString.copyFrom(ownerAddress),
              accountCapsule.getVotesList());
    }

    accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
        .setBalance(oldBalance + unfreezeBalance)
        .removeFrozen(0).build());
    accountCapsule.clearVotes();
    repository.updateAccount(ownerAddress, accountCapsule);

    votesCapsule.clearNewVotes();
    repository.updateVotesCapsule(ownerAddress, votesCapsule);

    repository
            .addTotalPhotonWeight(-unfreezeBalance / ChainConstant.VS_PRECISION);
  }

  public void validate(UnstakeParam unstakeParam, Repository repository)
      throws ContractValidateException {
    if (unstakeParam == null) {
      throw new ContractValidateException(ActuatorConstant.CONTRACT_NOT_EXIST);
    }
    if (repository == null) {
      throw new ContractValidateException(ActuatorConstant.STORE_NOT_EXIST);
    }

    byte[] ownerAddress = unstakeParam.getOwnerAddress();

    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    AccountCapsule accountCapsule = repository.getAccount(ownerAddress);
    if (accountCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
          "Account[" + readableOwnerAddress + "] does not exist");
    }
    long now = unstakeParam.getNow();

    if (accountCapsule.getFrozenCount() != 1) {
      throw new ContractValidateException("no frozenBalance(PHOTON)");
    }

    boolean needCheckFrozenTime = CommonParameter.getInstance()
            .getCheckFrozenTime() == 1;//for test
    if (needCheckFrozenTime && accountCapsule.getFrozenList().get(0).getExpireTime() > now) {
      throw new ContractValidateException("It's not time to unfreeze(PHOTON).");
    }
  }

}
