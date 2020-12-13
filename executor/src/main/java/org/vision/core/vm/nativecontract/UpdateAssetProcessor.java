package org.vision.core.vm.nativecontract;

import com.google.protobuf.ByteString;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.AssetIssueCapsule;
import org.vision.core.utils.TransactionUtil;
import org.vision.core.vm.nativecontract.param.UpdateAssetParam;
import org.vision.common.utils.DecodeUtil;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.vm.repository.Repository;

@Slf4j(topic = "Processor")
public class UpdateAssetProcessor {

  public void execute(Object contract, Repository repository) {
    UpdateAssetParam updateAssetParam = (UpdateAssetParam) contract;
    AccountCapsule accountCapsule = repository.getAccount(updateAssetParam.getOwnerAddress());

    AssetIssueCapsule assetIssueCapsuleV2;
    assetIssueCapsuleV2 = repository.getAssetIssue(accountCapsule.getAssetIssuedID().toByteArray());
    assetIssueCapsuleV2.setUrl(ByteString.copyFrom(updateAssetParam.getNewUrl()));
    assetIssueCapsuleV2.setDescription(ByteString.copyFrom(updateAssetParam.getNewDesc()));

    repository.putAssetIssueValue(assetIssueCapsuleV2.createDbV2Key(), assetIssueCapsuleV2);
  }

  public void validate(Object contract, Repository repository) throws ContractValidateException {
    if (Objects.isNull(contract)) {
      throw new ContractValidateException(ContractProcessorConstant.CONTRACT_NULL);
    }
    if (repository == null) {
      throw new ContractValidateException(ContractProcessorConstant.STORE_NOT_EXIST);
    }
    if (!(contract instanceof UpdateAssetParam)) {
      throw new ContractValidateException(
          "contract type error,expected type [TokenIssuedContract],real type[" + contract
              .getClass() + "]");
    }
    UpdateAssetParam updateAssetParam = (UpdateAssetParam) contract;
    if (!DecodeUtil.addressValid(updateAssetParam.getOwnerAddress())) {
      throw new ContractValidateException("Invalid ownerAddress");
    }
    AccountCapsule account = repository.getAccount(updateAssetParam.getOwnerAddress());
    if (account == null) {
      throw new ContractValidateException("Account does not exist");
    }
    if (account.getAssetIssuedID().isEmpty()) {
      throw new ContractValidateException("Account has not issued any asset");
    }
    if (repository.getAssetIssue(account.getAssetIssuedID().toByteArray())
        == null) {
      throw new ContractValidateException("Asset is not existed in AssetIssueV2Store");
    }
    if (!TransactionUtil.validUrl(updateAssetParam.getNewUrl())) {
      throw new ContractValidateException("Invalid url");
    }
    if (!TransactionUtil.validAssetDescription(updateAssetParam.getNewDesc())) {
      throw new ContractValidateException("Invalid description");
    }
  }
}
