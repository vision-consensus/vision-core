package org.vision.common.application;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.vision.core.db.Manager;
import org.vision.common.overlay.discover.DiscoverServer;
import org.vision.common.overlay.discover.node.NodeManager;
import org.vision.common.overlay.server.ChannelManager;

public class VisionApplicationContext extends AnnotationConfigApplicationContext {

  public VisionApplicationContext() {
  }

  public VisionApplicationContext(DefaultListableBeanFactory beanFactory) {
    super(beanFactory);
  }

  public VisionApplicationContext(Class<?>... annotatedClasses) {
    super(annotatedClasses);
  }

  public VisionApplicationContext(String... basePackages) {
    super(basePackages);
  }

  @Override
  public void destroy() {

    Application appT = ApplicationFactory.create(this);
    appT.shutdownServices();
    appT.shutdown();

    DiscoverServer discoverServer = getBean(DiscoverServer.class);
    discoverServer.close();
    ChannelManager channelManager = getBean(ChannelManager.class);
    channelManager.close();
    NodeManager nodeManager = getBean(NodeManager.class);
    nodeManager.close();

    Manager dbManager = getBean(Manager.class);
    dbManager.stopRePushThread();
    dbManager.stopRePushTriggerThread();
    super.destroy();
  }
}
