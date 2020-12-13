package org.vision.common.logsfilter.capsule;

import lombok.Getter;
import lombok.Setter;
import org.vision.common.logsfilter.trigger.SolidityTrigger;
import org.vision.common.logsfilter.EventPluginLoader;

public class SolidityTriggerCapsule extends TriggerCapsule {

  @Getter
  @Setter
  private SolidityTrigger solidityTrigger;

  public SolidityTriggerCapsule(long latestSolidifiedBlockNum) {
    solidityTrigger = new SolidityTrigger();
    solidityTrigger.setLatestSolidifiedBlockNumber(latestSolidifiedBlockNum);
  }

  @Override
  public void processTrigger() {
    EventPluginLoader.getInstance().postSolidityTrigger(solidityTrigger);
  }
}

