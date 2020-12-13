package org.vision.common.runtime.vm;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.spongycastle.util.encoders.Hex;
import org.vision.common.runtime.Runtime;
import org.vision.common.utils.FileUtil;
import org.vision.core.Constant;
import org.vision.core.Wallet;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.core.db.Manager;
import org.vision.common.application.VisionApplicationContext;
import org.vision.common.storage.Deposit;
import org.vision.common.storage.DepositImpl;
import org.vision.protos.Protocol.AccountType;

@Slf4j
public class VMTestBase {

  protected Manager manager;
  protected VisionApplicationContext context;
  protected String dbPath;
  protected Deposit rootDeposit;
  protected String OWNER_ADDRESS;
  protected Runtime runtime;

  @Before
  public void init() {
    dbPath = "output_" + this.getClass().getName();
    Args.setParam(new String[]{"--output-directory", dbPath, "--debug"}, Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
    manager = context.getBean(Manager.class);
    rootDeposit = DepositImpl.createRoot(manager);
    rootDeposit.createAccount(Hex.decode(OWNER_ADDRESS), AccountType.Normal);
    rootDeposit.addBalance(Hex.decode(OWNER_ADDRESS), 30000000000000L);

    rootDeposit.commit();
  }

  @After
  public void destroy() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.error("Release resources failure.");
    }
  }

}
