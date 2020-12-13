package org.vision.common.logsfilter.capsule;

import lombok.Getter;
import lombok.Setter;
import org.vision.common.logsfilter.trigger.ContractEventTrigger;
import org.vision.common.logsfilter.EventPluginLoader;

public class SolidityEventCapsule extends TriggerCapsule {

  @Getter
  @Setter
  private ContractEventTrigger solidityEventTrigger;

  public SolidityEventCapsule(ContractEventTrigger solidityEventTrigger) {
    this.solidityEventTrigger = solidityEventTrigger;
  }

  @Override
  public void processTrigger() {
    EventPluginLoader.getInstance().postSolidityEventTrigger(solidityEventTrigger);
  }
}