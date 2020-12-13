package org.vision.core.actuator;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vision.common.utils.FileUtil;
import org.vision.core.Constant;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.common.application.Application;
import org.vision.common.application.ApplicationFactory;
import org.vision.common.application.VisionApplicationContext;


@Slf4j(topic = "actuator")
public class ActuatorConstantTest {

  private static final String dbPath = "output_actuatorConstant_test";
  public static Application AppT;
  private static VisionApplicationContext context;

  /**
   * Init .
   */
  @BeforeClass
  public static void init() {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
    AppT = ApplicationFactory.create(context);
  }

  /**
   * Release resources.
   */
  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  @Test
  public void variablecheck() {
    ActuatorConstant actuator = new ActuatorConstant();
    Assert.assertEquals("Account[", actuator.ACCOUNT_EXCEPTION_STR);
    Assert.assertEquals("Witness[", actuator.WITNESS_EXCEPTION_STR);
    Assert.assertEquals("Proposal[", actuator.PROPOSAL_EXCEPTION_STR);
    Assert.assertEquals("] not exists", actuator.NOT_EXIST_STR);
    Assert.assertTrue(actuator instanceof ActuatorConstant);
  }

}
