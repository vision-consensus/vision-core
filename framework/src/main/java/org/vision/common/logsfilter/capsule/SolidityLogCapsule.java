package org.vision.common.logsfilter.capsule;

import lombok.Getter;
import lombok.Setter;
import org.vision.common.logsfilter.trigger.ContractLogTrigger;
import org.vision.common.logsfilter.EventPluginLoader;

public class SolidityLogCapsule extends TriggerCapsule {

  @Getter
  @Setter
  private ContractLogTrigger solidityLogTrigger;

  public SolidityLogCapsule(ContractLogTrigger solidityLogTrigger) {
    this.solidityLogTrigger = solidityLogTrigger;
  }

  @Override
  public void processTrigger() {
    EventPluginLoader.getInstance().postSolidityLogTrigger(solidityLogTrigger);
  }
}