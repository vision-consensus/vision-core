package org.vision.core.db2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.vision.common.utils.FileUtil;
import org.vision.core.Constant;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.core.db.AbstractRevokingStore;
import org.vision.core.db.RevokingDatabase;
import org.vision.core.db.VisionStoreWithRevoking;
import org.vision.core.exception.RevokingStoreIllegalStateException;
import org.vision.common.application.Application;
import org.vision.common.application.ApplicationFactory;
import org.vision.common.application.VisionApplicationContext;

@Slf4j
public class RevokingDbWithCacheOldValueTest {

  private AbstractRevokingStore revokingDatabase;
  private VisionApplicationContext context;
  private Application appT;

  @Before
  public void init() {
    Args.setParam(new String[]{"-d", "output_revokingStore_test"}, Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
    appT = ApplicationFactory.create(context);
    revokingDatabase = new TestRevokingVisionDatabase();
    revokingDatabase.enable();
  }

  @After
  public void removeDb() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File("output_revokingStore_test"));
  }

  @Test
  public synchronized void testReset() {
    revokingDatabase.getStack().clear();
    TestRevokingVisionStore visionDatabase = new TestRevokingVisionStore(
        "testrevokingvisionstore-testReset", revokingDatabase);
    SnapshotRootTest.ProtoCapsuleTest testProtoCapsule = new SnapshotRootTest.ProtoCapsuleTest(("reset").getBytes());
    try (ISession tmpSession = revokingDatabase.buildSession()) {
      visionDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
      tmpSession.commit();
    }
    Assert.assertTrue(visionDatabase.has(testProtoCapsule.getData()));
    visionDatabase.reset();
    Assert.assertFalse(visionDatabase.has(testProtoCapsule.getData()));
    visionDatabase.reset();
  }

  @Test
  public synchronized void testPop() throws RevokingStoreIllegalStateException {
    revokingDatabase.getStack().clear();
    TestRevokingVisionStore visionDatabase = new TestRevokingVisionStore(
        "testrevokingvisionstore-testPop", revokingDatabase);

    for (int i = 1; i < 11; i++) {
      SnapshotRootTest.ProtoCapsuleTest testProtoCapsule = new SnapshotRootTest.ProtoCapsuleTest(("pop" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        visionDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        Assert.assertEquals(1, revokingDatabase.getActiveDialog());
        tmpSession.commit();
        Assert.assertEquals(i, revokingDatabase.getStack().size());
        Assert.assertEquals(0, revokingDatabase.getActiveDialog());
      }
    }

    for (int i = 1; i < 11; i++) {
      revokingDatabase.pop();
      Assert.assertEquals(10 - i, revokingDatabase.getStack().size());
    }

    visionDatabase.close();

    Assert.assertEquals(0, revokingDatabase.getStack().size());
  }

  @Test
  public synchronized void testUndo() throws RevokingStoreIllegalStateException {
    revokingDatabase.getStack().clear();
    TestRevokingVisionStore visionDatabase = new TestRevokingVisionStore(
        "testrevokingvisionstore-testUndo", revokingDatabase);

    ISession dialog = revokingDatabase.buildSession();
    for (int i = 0; i < 10; i++) {
      SnapshotRootTest.ProtoCapsuleTest testProtoCapsule = new SnapshotRootTest.ProtoCapsuleTest(("undo" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        visionDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        Assert.assertEquals(2, revokingDatabase.getStack().size());
        tmpSession.merge();
        Assert.assertEquals(1, revokingDatabase.getStack().size());
      }
    }

    Assert.assertEquals(1, revokingDatabase.getStack().size());

    dialog.destroy();
    Assert.assertTrue(revokingDatabase.getStack().isEmpty());
    Assert.assertEquals(0, revokingDatabase.getActiveDialog());

    dialog = revokingDatabase.buildSession();
    revokingDatabase.disable();
    SnapshotRootTest.ProtoCapsuleTest testProtoCapsule = new SnapshotRootTest.ProtoCapsuleTest("del".getBytes());
    visionDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
    revokingDatabase.enable();

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      visionDatabase.put(testProtoCapsule.getData(), new SnapshotRootTest.ProtoCapsuleTest("del2".getBytes()));
      tmpSession.merge();
    }

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      visionDatabase.put(testProtoCapsule.getData(), new SnapshotRootTest.ProtoCapsuleTest("del22".getBytes()));
      tmpSession.merge();
    }

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      visionDatabase.put(testProtoCapsule.getData(), new SnapshotRootTest.ProtoCapsuleTest("del222".getBytes()));
      tmpSession.merge();
    }

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      visionDatabase.delete(testProtoCapsule.getData());
      tmpSession.merge();
    }

    dialog.destroy();

    logger.info(
        "**********testProtoCapsule:" + visionDatabase.getUnchecked(testProtoCapsule.getData())
            .toString());
    Assert.assertArrayEquals("del".getBytes(),
        visionDatabase.getUnchecked(testProtoCapsule.getData()).getData());
    Assert.assertEquals(testProtoCapsule, visionDatabase.getUnchecked(testProtoCapsule.getData()));

    visionDatabase.close();
  }

  @Test
  public synchronized void testGetlatestValues() {
    revokingDatabase.getStack().clear();
    TestRevokingVisionStore visionDatabase = new TestRevokingVisionStore(
        "testrevokingvisionstore-testGetlatestValues", revokingDatabase);

    for (int i = 0; i < 10; i++) {
      SnapshotRootTest.ProtoCapsuleTest testProtoCapsule = new SnapshotRootTest.ProtoCapsuleTest(("getLastestValues" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        visionDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        tmpSession.commit();
      }
    }
    Set<SnapshotRootTest.ProtoCapsuleTest> result = visionDatabase.getRevokingDB().getlatestValues(5).stream()
        .map(SnapshotRootTest.ProtoCapsuleTest::new)
        .collect(Collectors.toSet());

    for (int i = 9; i >= 5; i--) {
      Assert.assertTrue(result.contains(new SnapshotRootTest.ProtoCapsuleTest(("getLastestValues" + i).getBytes())));
    }
    visionDatabase.close();
  }

  @Test
  public synchronized void testGetValuesNext() {
    revokingDatabase.getStack().clear();
    TestRevokingVisionStore visionDatabase = new TestRevokingVisionStore(
        "testrevokingvisionstore-testGetValuesNext", revokingDatabase);

    for (int i = 0; i < 10; i++) {
      SnapshotRootTest.ProtoCapsuleTest testProtoCapsule = new SnapshotRootTest.ProtoCapsuleTest(("getValuesNext" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        visionDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        tmpSession.commit();
      }
    }
    Set<SnapshotRootTest.ProtoCapsuleTest> result =
        visionDatabase.getRevokingDB().getValuesNext(
            new SnapshotRootTest.ProtoCapsuleTest("getValuesNext2".getBytes()).getData(), 3)
            .stream()
            .map(SnapshotRootTest.ProtoCapsuleTest::new)
            .collect(Collectors.toSet());

    for (int i = 2; i < 5; i++) {
      Assert.assertTrue(result.contains(new SnapshotRootTest.ProtoCapsuleTest(("getValuesNext" + i).getBytes())));
    }
    visionDatabase.close();
  }

  @Test
  public synchronized void testGetKeysNext() {
    revokingDatabase.getStack().clear();
    TestRevokingVisionStore visionDatabase = new TestRevokingVisionStore(
        "testrevokingvisionstore-testGetKeysNext", revokingDatabase);

    String protoCapsuleStr = "getKeysNext";
    for (int i = 0; i < 10; i++) {
      SnapshotRootTest.ProtoCapsuleTest testProtoCapsule = new SnapshotRootTest.ProtoCapsuleTest((protoCapsuleStr + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        visionDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        tmpSession.commit();
      }
    }

    int start = 2;
    List<byte[]> result =
        visionDatabase.getRevokingDB().getKeysNext(
            new SnapshotRootTest.ProtoCapsuleTest((protoCapsuleStr + start).getBytes()).getData(), 3);

    for (int i = start; i < 5; i++) {
      Assert.assertArrayEquals(new SnapshotRootTest.ProtoCapsuleTest((protoCapsuleStr + i).getBytes()).getData(),
          result.get(i - 2));
    }
    visionDatabase.close();
  }

  @Test
  public void shutdown() throws RevokingStoreIllegalStateException {
    revokingDatabase.getStack().clear();
    TestRevokingVisionStore visionDatabase = new TestRevokingVisionStore(
        "testrevokingvisionstore-shutdown", revokingDatabase);

    List<SnapshotRootTest.ProtoCapsuleTest> capsules = new ArrayList<>();
    for (int i = 1; i < 11; i++) {
      revokingDatabase.buildSession();
      SnapshotRootTest.ProtoCapsuleTest testProtoCapsule = new SnapshotRootTest.ProtoCapsuleTest(("test" + i).getBytes());
      capsules.add(testProtoCapsule);
      visionDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
      Assert.assertEquals(revokingDatabase.getActiveDialog(), i);
      Assert.assertEquals(revokingDatabase.getStack().size(), i);
    }

    for (SnapshotRootTest.ProtoCapsuleTest capsule : capsules) {
      logger.info(new String(capsule.getData()));
      Assert.assertEquals(capsule, visionDatabase.getUnchecked(capsule.getData()));
    }

    revokingDatabase.shutdown();

    for (SnapshotRootTest.ProtoCapsuleTest capsule : capsules) {
      logger.info(visionDatabase.getUnchecked(capsule.getData()).toString());
      Assert.assertEquals(null, visionDatabase.getUnchecked(capsule.getData()).getData());
    }

    Assert.assertEquals(0, revokingDatabase.getStack().size());
    visionDatabase.close();

  }

  private static class TestRevokingVisionStore extends VisionStoreWithRevoking<SnapshotRootTest.ProtoCapsuleTest> {

    protected TestRevokingVisionStore(String dbName, RevokingDatabase revokingDatabase) {
      super(dbName, revokingDatabase);
    }

    @Override
    public SnapshotRootTest.ProtoCapsuleTest get(byte[] key) {
      byte[] value = this.revokingDB.getUnchecked(key);
      return ArrayUtils.isEmpty(value) ? null : new SnapshotRootTest.ProtoCapsuleTest(value);
    }
  }

  private static class TestRevokingVisionDatabase extends AbstractRevokingStore {

  }
}
