package org.vision.core.db2;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.vision.common.utils.FileUtil;
import org.vision.core.Constant;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.core.db2.core.SnapshotManager;
import org.vision.core.exception.BadItemException;
import org.vision.core.exception.ItemNotFoundException;
import org.vision.common.application.Application;
import org.vision.common.application.ApplicationFactory;
import org.vision.common.application.VisionApplicationContext;

@Slf4j
public class SnapshotManagerTest {

  private SnapshotManager revokingDatabase;
  private VisionApplicationContext context;
  private Application appT;
  private RevokingDbWithCacheNewValueTest.TestRevokingVisionStore visionDatabase;

  @Before
  public void init() {
    Args.setParam(new String[]{"-d", "output_SnapshotManager_test"},
        Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
    appT = ApplicationFactory.create(context);
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    visionDatabase = new RevokingDbWithCacheNewValueTest.TestRevokingVisionStore("testSnapshotManager-test");
    revokingDatabase.add(visionDatabase.getRevokingDB());
  }

  @After
  public void removeDb() {
    Args.clearParam();
    context.destroy();
    visionDatabase.close();
    FileUtil.deleteDir(new File("output_SnapshotManager_test"));
    revokingDatabase.getCheckTmpStore().close();
    visionDatabase.close();
  }

  @Test
  public synchronized void testRefresh()
      throws BadItemException, ItemNotFoundException {
    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }

    revokingDatabase.setMaxFlushCount(0);
    revokingDatabase.setUnChecked(false);
    revokingDatabase.setMaxSize(5);
    SnapshotRootTest.ProtoCapsuleTest protoCapsule = new SnapshotRootTest.ProtoCapsuleTest("refresh".getBytes());
    for (int i = 1; i < 11; i++) {
      SnapshotRootTest.ProtoCapsuleTest testProtoCapsule = new SnapshotRootTest.ProtoCapsuleTest(("refresh" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        visionDatabase.put(protoCapsule.getData(), testProtoCapsule);
        tmpSession.commit();
      }
    }

    revokingDatabase.flush();
    Assert.assertEquals(new SnapshotRootTest.ProtoCapsuleTest("refresh10".getBytes()),
        visionDatabase.get(protoCapsule.getData()));
  }

  @Test
  public synchronized void testClose() {
    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }

    revokingDatabase.setMaxFlushCount(0);
    revokingDatabase.setUnChecked(false);
    revokingDatabase.setMaxSize(5);
    SnapshotRootTest.ProtoCapsuleTest protoCapsule = new SnapshotRootTest.ProtoCapsuleTest("close".getBytes());
    for (int i = 1; i < 11; i++) {
      SnapshotRootTest.ProtoCapsuleTest testProtoCapsule = new SnapshotRootTest.ProtoCapsuleTest(("close" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        visionDatabase.put(protoCapsule.getData(), testProtoCapsule);
      }
    }
    Assert.assertEquals(null,
        visionDatabase.get(protoCapsule.getData()));

  }
}
