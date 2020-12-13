package org.vision.core.pbft;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.vision.common.utils.ByteArray;
import org.vision.consensus.base.Param;
import org.vision.consensus.pbft.message.PbftMessage;
import org.vision.core.capsule.BlockCapsule;
import org.vision.protos.Protocol.Block;

public class PbftTest {

  @Test
  public void testPbftSrMessage() {
    byte[] pk = ByteArray.fromHexString("41f08012b4881c320eb40b80f1228731898824e09d");
    BlockCapsule blockCapsule = new BlockCapsule(Block.getDefaultInstance());
    List<ByteString> srList = new ArrayList<>();
    Param param = Param.getInstance();
    List<Param.Miner> minerList = new ArrayList<>();
    Param.Miner miner = param.new Miner(pk, null, null);
    minerList.add(miner);
    param.setMiners(minerList);
    srList.add(
        ByteString.copyFrom(ByteArray.fromHexString("41f08012b4881c320eb40b80f1228731898824e09d")));
    srList.add(
        ByteString.copyFrom(ByteArray.fromHexString("41df309fef25b311e7895562bd9e11aab2a58816d2")));
    PbftMessage pbftSrMessage = PbftMessage
        .prePrepareSRLMsg(blockCapsule, srList, 1, miner);
    System.out.println(pbftSrMessage);
  }

}