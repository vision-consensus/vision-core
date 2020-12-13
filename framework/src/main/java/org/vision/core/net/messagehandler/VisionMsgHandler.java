package org.vision.core.net.messagehandler;

import org.vision.core.net.message.VisionMessage;
import org.vision.core.net.peer.PeerConnection;
import org.vision.core.exception.P2pException;

public interface VisionMsgHandler {

  void processMessage(PeerConnection peer, VisionMessage msg) throws P2pException;

}
