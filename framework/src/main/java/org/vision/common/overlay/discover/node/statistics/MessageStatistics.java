package org.vision.common.overlay.discover.node.statistics;

import lombok.extern.slf4j.Slf4j;
import org.vision.common.net.udp.message.UdpMessageTypeEnum;
import org.vision.common.overlay.message.Message;
import org.vision.core.net.message.FetchInvDataMessage;
import org.vision.core.net.message.InventoryMessage;
import org.vision.core.net.message.MessageTypes;
import org.vision.core.net.message.TransactionsMessage;

@Slf4j
public class MessageStatistics {

  //udp discovery
  public final MessageCount discoverInPing = new MessageCount();
  public final MessageCount discoverOutPing = new MessageCount();
  public final MessageCount discoverInPong = new MessageCount();
  public final MessageCount discoverOutPong = new MessageCount();
  public final MessageCount discoverInFindNode = new MessageCount();
  public final MessageCount discoverOutFindNode = new MessageCount();
  public final MessageCount discoverInNeighbours = new MessageCount();
  public final MessageCount discoverOutNeighbours = new MessageCount();

  //tcp p2p
  public final MessageCount p2pInHello = new MessageCount();
  public final MessageCount p2pOutHello = new MessageCount();
  public final MessageCount p2pInPing = new MessageCount();
  public final MessageCount p2pOutPing = new MessageCount();
  public final MessageCount p2pInPong = new MessageCount();
  public final MessageCount p2pOutPong = new MessageCount();
  public final MessageCount p2pInDisconnect = new MessageCount();
  public final MessageCount p2pOutDisconnect = new MessageCount();

  //tcp vision
  public final MessageCount visionInMessage = new MessageCount();
  public final MessageCount visionOutMessage = new MessageCount();

  public final MessageCount visionInSyncBlockChain = new MessageCount();
  public final MessageCount visionOutSyncBlockChain = new MessageCount();
  public final MessageCount visionInBlockChainInventory = new MessageCount();
  public final MessageCount visionOutBlockChainInventory = new MessageCount();

  public final MessageCount visionInTrxInventory = new MessageCount();
  public final MessageCount visionOutTrxInventory = new MessageCount();
  public final MessageCount visionInTrxInventoryElement = new MessageCount();
  public final MessageCount visionOutTrxInventoryElement = new MessageCount();

  public final MessageCount visionInBlockInventory = new MessageCount();
  public final MessageCount visionOutBlockInventory = new MessageCount();
  public final MessageCount visionInBlockInventoryElement = new MessageCount();
  public final MessageCount visionOutBlockInventoryElement = new MessageCount();

  public final MessageCount visionInTrxFetchInvData = new MessageCount();
  public final MessageCount visionOutTrxFetchInvData = new MessageCount();
  public final MessageCount visionInTrxFetchInvDataElement = new MessageCount();
  public final MessageCount visionOutTrxFetchInvDataElement = new MessageCount();

  public final MessageCount visionInBlockFetchInvData = new MessageCount();
  public final MessageCount visionOutBlockFetchInvData = new MessageCount();
  public final MessageCount visionInBlockFetchInvDataElement = new MessageCount();
  public final MessageCount visionOutBlockFetchInvDataElement = new MessageCount();


  public final MessageCount visionInTrx = new MessageCount();
  public final MessageCount visionOutTrx = new MessageCount();
  public final MessageCount visionInTrxs = new MessageCount();
  public final MessageCount visionOutTrxs = new MessageCount();
  public final MessageCount visionInBlock = new MessageCount();
  public final MessageCount visionOutBlock = new MessageCount();
  public final MessageCount visionOutAdvBlock = new MessageCount();

  public void addUdpInMessage(UdpMessageTypeEnum type) {
    addUdpMessage(type, true);
  }

  public void addUdpOutMessage(UdpMessageTypeEnum type) {
    addUdpMessage(type, false);
  }

  public void addTcpInMessage(Message msg) {
    addTcpMessage(msg, true);
  }

  public void addTcpOutMessage(Message msg) {
    addTcpMessage(msg, false);
  }

  private void addUdpMessage(UdpMessageTypeEnum type, boolean flag) {
    switch (type) {
      case DISCOVER_PING:
        if (flag) {
          discoverInPing.add();
        } else {
          discoverOutPing.add();
        }
        break;
      case DISCOVER_PONG:
        if (flag) {
          discoverInPong.add();
        } else {
          discoverOutPong.add();
        }
        break;
      case DISCOVER_FIND_NODE:
        if (flag) {
          discoverInFindNode.add();
        } else {
          discoverOutFindNode.add();
        }
        break;
      case DISCOVER_NEIGHBORS:
        if (flag) {
          discoverInNeighbours.add();
        } else {
          discoverOutNeighbours.add();
        }
        break;
      default:
        break;
    }
  }

  private void addTcpMessage(Message msg, boolean flag) {

    if (flag) {
      visionInMessage.add();
    } else {
      visionOutMessage.add();
    }

    switch (msg.getType()) {
      case P2P_HELLO:
        if (flag) {
          p2pInHello.add();
        } else {
          p2pOutHello.add();
        }
        break;
      case P2P_PING:
        if (flag) {
          p2pInPing.add();
        } else {
          p2pOutPing.add();
        }
        break;
      case P2P_PONG:
        if (flag) {
          p2pInPong.add();
        } else {
          p2pOutPong.add();
        }
        break;
      case P2P_DISCONNECT:
        if (flag) {
          p2pInDisconnect.add();
        } else {
          p2pOutDisconnect.add();
        }
        break;
      case SYNC_BLOCK_CHAIN:
        if (flag) {
          visionInSyncBlockChain.add();
        } else {
          visionOutSyncBlockChain.add();
        }
        break;
      case BLOCK_CHAIN_INVENTORY:
        if (flag) {
          visionInBlockChainInventory.add();
        } else {
          visionOutBlockChainInventory.add();
        }
        break;
      case INVENTORY:
        InventoryMessage inventoryMessage = (InventoryMessage) msg;
        int inventorySize = inventoryMessage.getInventory().getIdsCount();
        if (flag) {
          if (inventoryMessage.getInvMessageType() == MessageTypes.TRX) {
            visionInTrxInventory.add();
            visionInTrxInventoryElement.add(inventorySize);
          } else {
            visionInBlockInventory.add();
            visionInBlockInventoryElement.add(inventorySize);
          }
        } else {
          if (inventoryMessage.getInvMessageType() == MessageTypes.TRX) {
            visionOutTrxInventory.add();
            visionOutTrxInventoryElement.add(inventorySize);
          } else {
            visionOutBlockInventory.add();
            visionOutBlockInventoryElement.add(inventorySize);
          }
        }
        break;
      case FETCH_INV_DATA:
        FetchInvDataMessage fetchInvDataMessage = (FetchInvDataMessage) msg;
        int fetchSize = fetchInvDataMessage.getInventory().getIdsCount();
        if (flag) {
          if (fetchInvDataMessage.getInvMessageType() == MessageTypes.TRX) {
            visionInTrxFetchInvData.add();
            visionInTrxFetchInvDataElement.add(fetchSize);
          } else {
            visionInBlockFetchInvData.add();
            visionInBlockFetchInvDataElement.add(fetchSize);
          }
        } else {
          if (fetchInvDataMessage.getInvMessageType() == MessageTypes.TRX) {
            visionOutTrxFetchInvData.add();
            visionOutTrxFetchInvDataElement.add(fetchSize);
          } else {
            visionOutBlockFetchInvData.add();
            visionOutBlockFetchInvDataElement.add(fetchSize);
          }
        }
        break;
      case TRXS:
        TransactionsMessage transactionsMessage = (TransactionsMessage) msg;
        if (flag) {
          visionInTrxs.add();
          visionInTrx.add(transactionsMessage.getTransactions().getTransactionsCount());
        } else {
          visionOutTrxs.add();
          visionOutTrx.add(transactionsMessage.getTransactions().getTransactionsCount());
        }
        break;
      case TRX:
        if (flag) {
          visionInMessage.add();
        } else {
          visionOutMessage.add();
        }
        break;
      case BLOCK:
        if (flag) {
          visionInBlock.add();
        }
        visionOutBlock.add();
        break;
      default:
        break;
    }
  }

}
