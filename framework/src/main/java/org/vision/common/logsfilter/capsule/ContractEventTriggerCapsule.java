package org.vision.common.logsfilter.capsule;

import static org.vision.common.logsfilter.EventPluginLoader.matchFilter;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.vision.common.logsfilter.trigger.ContractEventTrigger;
import org.vision.common.logsfilter.ContractEventParserAbi;
import org.vision.common.logsfilter.EventPluginLoader;
import org.vision.common.runtime.LogEventWrapper;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract.ABI.Entry;

public class ContractEventTriggerCapsule extends TriggerCapsule {

  @Getter
  @Setter
  private ContractEventTrigger contractEventTrigger;
  @Getter
  @Setter
  private List<byte[]> topicList;
  @Getter
  @Setter
  private byte[] data;
  @Getter
  @Setter
  private Entry abiEntry;

  public ContractEventTriggerCapsule(LogEventWrapper log) {
    this.contractEventTrigger = new ContractEventTrigger();

    this.contractEventTrigger.setUniqueId(log.getUniqueId());
    this.contractEventTrigger.setTransactionId(log.getTransactionId());
    this.contractEventTrigger.setContractAddress(log.getContractAddress());
    this.contractEventTrigger.setCallerAddress(log.getCallerAddress());
    this.contractEventTrigger.setOriginAddress(log.getOriginAddress());
    this.contractEventTrigger.setCreatorAddress(log.getCreatorAddress());
    this.contractEventTrigger.setBlockNumber(log.getBlockNumber());
    this.contractEventTrigger.setTimeStamp(log.getTimeStamp());

    this.topicList = log.getTopicList();
    this.data = log.getData();
    this.contractEventTrigger.setEventSignature(log.getEventSignature());
    this.contractEventTrigger.setEventSignatureFull(log.getEventSignatureFull());
    this.contractEventTrigger.setEventName(log.getAbiEntry().getName());
    this.abiEntry = log.getAbiEntry();
  }

  public void setLatestSolidifiedBlockNumber(long latestSolidifiedBlockNumber) {
    contractEventTrigger.setLatestSolidifiedBlockNumber(latestSolidifiedBlockNumber);
  }

  @Override
  public void processTrigger() {
    contractEventTrigger.setTopicMap(ContractEventParserAbi.parseTopics(topicList, abiEntry));
    contractEventTrigger
        .setDataMap(ContractEventParserAbi.parseEventData(data, topicList, abiEntry));

    if (matchFilter(contractEventTrigger)) {
      EventPluginLoader.getInstance().postContractEventTrigger(contractEventTrigger);
    }
  }
}
