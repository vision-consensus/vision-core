package org.vision.core.db2;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.vision.common.utils.FileUtil;
import org.vision.common.utils.SessionOptional;
import org.vision.core.Constant;
import org.vision.core.capsule.ProtoCapsule;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.core.db2.core.Snapshot;
import org.vision.core.db2.core.SnapshotManager;
import org.vision.core.db2.core.SnapshotRoot;
import org.vision.common.application.Application;
import org.vision.common.application.ApplicationFactory;
import org.vision.common.application.VisionApplicationContext;

public class SnapshotRootTest {

  private RevokingDbWithCacheNewValueTest.TestRevokingVisionStore visionDatabase;
  private VisionApplicationContext context;
  private Application appT;
  private SnapshotManager revokingDatabase;

  @Before
  public void init() {
    Args.setParam(new String[]{"-d", "output_revokingStore_test"}, Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
    appT = ApplicationFactory.create(context);
  }

  @After
  public void removeDb() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File("output_revokingStore_test"));
  }

  @Test
  public synchronized void testRemove() {
    ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest("test".getBytes());
    visionDatabase = new RevokingDbWithCacheNewValueTest.TestRevokingVisionStore("testSnapshotRoot-testRemove");
    visionDatabase.put("test".getBytes(), testProtoCapsule);
    Assert.assertEquals(testProtoCapsule, visionDatabase.get("test".getBytes()));

    visionDatabase.delete("test".getBytes());
    Assert.assertEquals(null, visionDatabase.get("test".getBytes()));
    visionDatabase.close();
  }

  @Test
  public synchronized void testMerge() {
    visionDatabase = new RevokingDbWithCacheNewValueTest.TestRevokingVisionStore("testSnapshotRoot-testMerge");
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    revokingDatabase.add(visionDatabase.getRevokingDB());

    SessionOptional dialog = SessionOptional.instance().setValue(revokingDatabase.buildSession());
    ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest("merge".getBytes());
    visionDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
    revokingDatabase.getDbs().forEach(db -> db.getHead().getRoot().merge(db.getHead()));
    dialog.reset();
    Assert.assertEquals(visionDatabase.get(testProtoCapsule.getData()), testProtoCapsule);

    visionDatabase.close();
  }

  @Test
  public synchronized void testMergeList() {
    visionDatabase = new RevokingDbWithCacheNewValueTest.TestRevokingVisionStore("testSnapshotRoot-testMergeList");
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    revokingDatabase.add(visionDatabase.getRevokingDB());

    SessionOptional.instance().setValue(revokingDatabase.buildSession());
    ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest("test".getBytes());
    visionDatabase.put("merge".getBytes(), testProtoCapsule);
    for (int i = 1; i < 11; i++) {
      ProtoCapsuleTest tmpProtoCapsule = new ProtoCapsuleTest(("mergeList" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        visionDatabase.put(tmpProtoCapsule.getData(), tmpProtoCapsule);
        tmpSession.commit();
      }
    }
    revokingDatabase.getDbs().forEach(db -> {
      List<Snapshot> snapshots = new ArrayList<>();
      SnapshotRoot root = (SnapshotRoot) db.getHead().getRoot();
      Snapshot next = root;
      for (int i = 0; i < 11; ++i) {
        next = next.getNext();
        snapshots.add(next);
      }
      root.merge(snapshots);
      root.resetSolidity();

      for (int i = 1; i < 11; i++) {
        ProtoCapsuleTest tmpProtoCapsule = new ProtoCapsuleTest(("mergeList" + i).getBytes());
        Assert.assertEquals(tmpProtoCapsule, visionDatabase.get(tmpProtoCapsule.getData()));
      }

    });
    revokingDatabase.updateSolidity(10);
    visionDatabase.close();
  }

  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class ProtoCapsuleTest implements ProtoCapsule<Object> {

    private byte[] value;

    @Override
    public byte[] getData() {
      return value;
    }

    @Override
    public Object getInstance() {
      return value;
    }

    @Override
    public String toString() {
      return "ProtoCapsuleTest{"
          + "value=" + Arrays.toString(value)
          + ", string=" + (value == null ? "" : new String(value))
          + '}';
    }
  }
}
