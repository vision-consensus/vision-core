package org.vision.common.runtime.vm;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.spongycastle.util.encoders.Hex;
import org.vision.common.runtime.Runtime;
import org.vision.common.utils.FileUtil;
import org.vision.consensus.dpos.DposSlot;
import org.vision.consensus.dpos.MaintenanceManager;
import org.vision.core.ChainBaseManager;
import org.vision.core.Constant;
import org.vision.core.Wallet;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.core.consensus.ConsensusService;
import org.vision.core.db.DelegationService;
import org.vision.core.db.Manager;
import org.vision.core.store.StoreFactory;
import org.vision.core.store.WitnessStore;
import org.vision.core.vm.repository.Repository;
import org.vision.core.vm.repository.RepositoryImpl;
import org.vision.common.application.VisionApplicationContext;
import org.vision.protos.Protocol;

@Slf4j
public class VMContractTestBase {
  protected String dbPath;
  protected Runtime runtime;
  protected Manager manager;
  protected Repository rootRepository;
  protected VisionApplicationContext context;
  protected ConsensusService consensusService;
  protected ChainBaseManager chainBaseManager;
  protected MaintenanceManager maintenanceManager;
  protected DposSlot dposSlot;

  protected static String OWNER_ADDRESS;
  protected static String WITNESS_SR1_ADDRESS;

  WitnessStore witnessStore;
  DelegationService delegationService;

  static {
    // 27Ssb1WE8FArwJVRRb8Dwy3ssVGuLY8L3S1 (test.config)
    WITNESS_SR1_ADDRESS =
            Constant.ADD_PRE_FIX_STRING_TESTNET + "299F3DB80A24B20A254B89CE639D59132F157F13";
  }

  @Before
  public void init() {
    dbPath = "output_" + this.getClass().getName();
    Args.setParam(new String[]{"--output-directory", dbPath, "--debug"}, Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);

    // TRdmP9bYvML7dGUX9Rbw2kZrE2TayPZmZX - 41abd4b9367799eaa3197fecb144eb71de1e049abc
    OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";

    rootRepository = RepositoryImpl.createRoot(StoreFactory.getInstance());
    rootRepository.createAccount(Hex.decode(OWNER_ADDRESS), Protocol.AccountType.Normal);
    rootRepository.addBalance(Hex.decode(OWNER_ADDRESS), 30000000000000L);
    rootRepository.commit();

    manager = context.getBean(Manager.class);
    dposSlot = context.getBean(DposSlot.class);
    chainBaseManager = manager.getChainBaseManager();
    witnessStore = context.getBean(WitnessStore.class);
    consensusService = context.getBean(ConsensusService.class);
    maintenanceManager = context.getBean(MaintenanceManager.class);
    delegationService = context.getBean(DelegationService.class);
    consensusService.start();
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
