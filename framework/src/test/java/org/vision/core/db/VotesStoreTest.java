package org.vision.core.db;

import com.google.protobuf.ByteString;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.vision.common.utils.FileUtil;
import org.vision.core.Constant;
import org.vision.core.capsule.VotesCapsule;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.core.store.VotesStore;
import org.vision.common.application.VisionApplicationContext;
import org.vision.protos.Protocol.Vote;

@Slf4j
public class VotesStoreTest {

  private static final String dbPath = "output-votesStore-test";
  private static VisionApplicationContext context;

  static {
    Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
  }

  VotesStore votesStore;

  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }

  @Before
  public void initDb() {
    this.votesStore = context.getBean(VotesStore.class);
  }

  @Test
  public void putAndGetVotes() {
    List<Vote> oldVotes = new ArrayList<Vote>();

    VotesCapsule votesCapsule = new VotesCapsule(ByteString.copyFromUtf8("100000000x"), oldVotes);
    this.votesStore.put(votesCapsule.createDbKey(), votesCapsule);

    Assert.assertTrue("votesStore is empyt", votesStore.iterator().hasNext());
    Assert.assertTrue(votesStore.has(votesCapsule.createDbKey()));
    VotesCapsule votesSource = this.votesStore
        .get(ByteString.copyFromUtf8("100000000x").toByteArray());
    Assert.assertEquals(votesCapsule.getAddress(), votesSource.getAddress());
    Assert.assertEquals(ByteString.copyFromUtf8("100000000x"), votesSource.getAddress());
  }
}