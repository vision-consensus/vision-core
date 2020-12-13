package org.vision.common.logsfilter.capsule;

import static org.vision.common.logsfilter.EventPluginLoader.matchFilter;

import lombok.Getter;
import lombok.Setter;
import org.vision.common.logsfilter.trigger.ContractLogTrigger;
import org.vision.common.logsfilter.EventPluginLoader;

public class ContractLogTriggerCapsule extends TriggerCapsule {

  @Getter
  @Setter
  private ContractLogTrigger contractLogTrigger;

  public ContractLogTriggerCapsule(ContractLogTrigger contractLogTrigger) {
    this.contractLogTrigger = contractLogTrigger;
  }

  public void setLatestSolidifiedBlockNumber(long latestSolidifiedBlockNumber) {
    contractLogTrigger.setLatestSolidifiedBlockNumber(latestSolidifiedBlockNumber);
  }

  @Override
  public void processTrigger() {
    if (matchFilter(contractLogTrigger)) {
      EventPluginLoader.getInstance().postContractLogTrigger(contractLogTrigger);
    }
  }
}
