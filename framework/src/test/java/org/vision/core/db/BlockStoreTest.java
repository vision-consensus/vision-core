package org.vision.core.db;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.vision.common.utils.FileUtil;
import org.vision.core.Constant;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.common.application.VisionApplicationContext;

@Slf4j
public class BlockStoreTest {

  private static final String dbPath = "output-blockStore-test";
  private static VisionApplicationContext context;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath},
        Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
  }

  BlockStore blockStore;

  @Before
  public void init() {
    blockStore = context.getBean(BlockStore.class);
  }

  @After
  public void destroy() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }

  @Test
  public void testCreateBlockStore() {
  }
}
