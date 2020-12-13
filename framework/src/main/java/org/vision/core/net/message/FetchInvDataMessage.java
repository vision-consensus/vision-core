package org.vision.core.net.message;

import java.util.List;
import org.vision.common.utils.Sha256Hash;
import org.vision.protos.Protocol.Inventory;
import org.vision.protos.Protocol.Inventory.InventoryType;

public class FetchInvDataMessage extends InventoryMessage {


  public FetchInvDataMessage(byte[] packed) throws Exception {
    super(packed);
    this.type = MessageTypes.FETCH_INV_DATA.asByte();
  }

  public FetchInvDataMessage(Inventory inv) {
    super(inv);
    this.type = MessageTypes.FETCH_INV_DATA.asByte();
  }

  public FetchInvDataMessage(List<Sha256Hash> hashList, InventoryType type) {
    super(hashList, type);
    this.type = MessageTypes.FETCH_INV_DATA.asByte();
  }

}
