package org.vision.core.vm.config;


import lombok.extern.slf4j.Slf4j;
import org.vision.core.capsule.ReceiptCapsule;
import org.vision.core.store.DynamicPropertiesStore;
import org.vision.core.store.StoreFactory;
import org.vision.common.parameter.CommonParameter;

@Slf4j(topic = "VMConfigLoader")
public class ConfigLoader {

  //only for unit test
  public static boolean disable = true;

  public static void load(StoreFactory storeFactory) {
    if (!disable) {
      DynamicPropertiesStore ds = storeFactory.getChainBaseManager().getDynamicPropertiesStore();
      VMConfig.setVmTrace(CommonParameter.getInstance().isVmTrace());
      if (ds != null) {
        VMConfig.initVmHardFork(ReceiptCapsule.checkForEnergyLimit(ds));
        VMConfig.initAllowMultiSign(ds.getAllowMultiSign());
        VMConfig.initAllowVvmTransferVrc10(ds.getAllowVvmTransferVrc10());
        VMConfig.initAllowVvmConstantinople(ds.getAllowVvmConstantinople());
        VMConfig.initAllowVvmSolidity059(ds.getAllowVvmSolidity059());
        VMConfig.initAllowShieldedVRC20Transaction(ds.getAllowShieldedVRC20Transaction());
        VMConfig.initAllowVvmIstanbul(ds.getAllowVvmIstanbul());
        VMConfig.initAllowVvmStake(ds.getAllowVvmStake());
        VMConfig.initAllowVvmAssetIssue(ds.getAllowVvmAssetIssue());
      }
    }
  }
}
