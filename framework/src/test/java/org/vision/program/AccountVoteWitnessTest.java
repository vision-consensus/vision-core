package org.vision.program;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vision.consensus.dpos.MaintenanceManager;
import org.vision.core.Constant;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.WitnessCapsule;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.core.db.Manager;
import org.vision.common.application.VisionApplicationContext;
import org.vision.protos.Protocol.AccountType;

import java.io.File;
import java.util.List;

@Slf4j
public class AccountVoteWitnessTest {

  private static VisionApplicationContext context;

  private static Manager dbManager;
  private static MaintenanceManager maintenanceManager;
  private static String dbPath = "output_witness_test";

  static {
    Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
  }

  /**
   * init db.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    maintenanceManager = context.getBean(MaintenanceManager.class);
    // Args.setParam(new String[]{}, Constant.TEST_CONF);
    //  dbManager = new Manager();
    //  dbManager.init();
  }

  /**
   * remo db when after test.
   */
  @AfterClass
  public static void removeDb() {
    Args.clearParam();
    context.destroy();
    File dbFolder = new File(dbPath);
    if (deleteFolder(dbFolder)) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  private static Boolean deleteFolder(File index) {
    if (!index.isDirectory() || index.listFiles().length <= 0) {
      return index.delete();
    }
    for (File file : index.listFiles()) {
      if (null != file && !deleteFolder(file)) {
        return false;
      }
    }
    return index.delete();
  }

  @Test
  public void testAccountVoteWitness() {
    final List<AccountCapsule> accountCapsuleList = this.getAccountList();
    final List<WitnessCapsule> witnessCapsuleList = this.getWitnessList();
    accountCapsuleList.forEach(
        accountCapsule -> {
          dbManager
              .getAccountStore()
              .put(accountCapsule.getAddress().toByteArray(), accountCapsule);
          this.printAccount(accountCapsule.getAddress());
        });
    witnessCapsuleList.forEach(
        witnessCapsule ->
            dbManager
                .getWitnessStore()
                .put(witnessCapsule.getAddress().toByteArray(), witnessCapsule));
    maintenanceManager.doMaintenance();
    this.printWitness(ByteString.copyFrom("00000000001".getBytes()));
    this.printWitness(ByteString.copyFrom("00000000002".getBytes()));
    this.printWitness(ByteString.copyFrom("00000000003".getBytes()));
    this.printWitness(ByteString.copyFrom("00000000004".getBytes()));
    this.printWitness(ByteString.copyFrom("00000000005".getBytes()));
    this.printWitness(ByteString.copyFrom("00000000006".getBytes()));
    this.printWitness(ByteString.copyFrom("00000000007".getBytes()));
  }

  private void printAccount(final ByteString address) {
    final AccountCapsule accountCapsule = dbManager.getAccountStore().get(address.toByteArray());
    if (null == accountCapsule) {
      logger.info("address is {}  , account is null", address.toStringUtf8());
      return;
    }
    logger.info(
        "address is {}  ,countVoteSize is {}",
        accountCapsule.getAddress().toStringUtf8(),
        accountCapsule.getVotesList().size());
  }

  private void printWitness(final ByteString address) {
    final WitnessCapsule witnessCapsule = dbManager.getWitnessStore().get(address.toByteArray());
    if (null == witnessCapsule) {
      logger.info("address is {}  , witness is null", address.toStringUtf8());
      return;
    }
    logger.info(
        "address is {}  ,countVote is {}",
        witnessCapsule.getAddress().toStringUtf8(),
        witnessCapsule.getVoteCount());
  }

  private List<AccountCapsule> getAccountList() {
    final List<AccountCapsule> accountCapsuleList = Lists.newArrayList();
    final AccountCapsule accountVision =
        new AccountCapsule(
            ByteString.copyFrom("00000000001".getBytes()),
            ByteString.copyFromUtf8("Vision"),
            AccountType.Normal);
    final AccountCapsule accountMarcus =
        new AccountCapsule(
            ByteString.copyFrom("00000000002".getBytes()),
            ByteString.copyFromUtf8("Marcus"),
            AccountType.Normal);
    final AccountCapsule accountOlivier =
        new AccountCapsule(
            ByteString.copyFrom("00000000003".getBytes()),
            ByteString.copyFromUtf8("Olivier"),
            AccountType.Normal);
    final AccountCapsule accountSasaXie =
        new AccountCapsule(
            ByteString.copyFrom("00000000004".getBytes()),
            ByteString.copyFromUtf8("SasaXie"),
            AccountType.Normal);
    final AccountCapsule accountVivider =
        new AccountCapsule(
            ByteString.copyFrom("00000000005".getBytes()),
            ByteString.copyFromUtf8("Vivider"),
            AccountType.Normal);
    // accountVision addVotes
    accountVision.addVotes(accountMarcus.getAddress(), 100, 0);
    accountVision.addVotes(accountOlivier.getAddress(), 100, 0);
    accountVision.addVotes(accountSasaXie.getAddress(), 100, 0);
    accountVision.addVotes(accountVivider.getAddress(), 100, 0);

    // accountMarcus addVotes
    accountMarcus.addVotes(accountVision.getAddress(), 100, 0);
    accountMarcus.addVotes(accountOlivier.getAddress(), 100, 0);
    accountMarcus.addVotes(accountSasaXie.getAddress(), 100, 0);
    accountMarcus.addVotes(ByteString.copyFrom("00000000006".getBytes()), 100, 0);
    accountMarcus.addVotes(ByteString.copyFrom("00000000007".getBytes()), 100, 0);
    // accountOlivier addVotes
    accountOlivier.addVotes(accountVision.getAddress(), 100, 0);
    accountOlivier.addVotes(accountMarcus.getAddress(), 100, 0);
    accountOlivier.addVotes(accountSasaXie.getAddress(), 100, 0);
    accountOlivier.addVotes(accountVivider.getAddress(), 100, 0);
    // accountSasaXie addVotes
    // accountVivider addVotes
    accountCapsuleList.add(accountVision);
    accountCapsuleList.add(accountMarcus);
    accountCapsuleList.add(accountOlivier);
    accountCapsuleList.add(accountSasaXie);
    accountCapsuleList.add(accountVivider);
    return accountCapsuleList;
  }

  private List<WitnessCapsule> getWitnessList() {
    final List<WitnessCapsule> witnessCapsuleList = Lists.newArrayList();
    final WitnessCapsule witnessVision =
        new WitnessCapsule(ByteString.copyFrom("00000000001".getBytes()), 0, "");
    final WitnessCapsule witnessOlivier =
        new WitnessCapsule(ByteString.copyFrom("00000000003".getBytes()), 100, "");
    final WitnessCapsule witnessVivider =
        new WitnessCapsule(ByteString.copyFrom("00000000005".getBytes()), 200, "");
    final WitnessCapsule witnessSenaLiu =
        new WitnessCapsule(ByteString.copyFrom("00000000006".getBytes()), 300, "");
    witnessCapsuleList.add(witnessVision);
    witnessCapsuleList.add(witnessOlivier);
    witnessCapsuleList.add(witnessVivider);
    witnessCapsuleList.add(witnessSenaLiu);
    return witnessCapsuleList;
  }

}
