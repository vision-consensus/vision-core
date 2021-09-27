package org.vision.core;

import com.google.protobuf.ByteString;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.FileUtil;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.Parameter;
import org.vision.core.config.args.Args;
import org.vision.core.db.EntropyProcessor;
import org.vision.core.db.Manager;
import org.vision.common.application.VisionApplicationContext;
import org.vision.protos.Protocol.AccountType;
import org.vision.protos.contract.AssetIssueContractOuterClass.AssetIssueContract;

@Slf4j
public class EntropyProcessorTest {

  private static final String dbPath = "EntropyProcessorTest";
  private static final String ASSET_NAME;
  private static final String CONTRACT_PROVIDER_ADDRESS;
  private static final String CONTRACT_PROVIDER_ADDRESS2;
  private static final String USER_ADDRESS;
  private static Manager dbManager;
  private static ChainBaseManager chainBaseManager;
  private static VisionApplicationContext context;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
    ASSET_NAME = "test_token";
    CONTRACT_PROVIDER_ADDRESS =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
    CONTRACT_PROVIDER_ADDRESS2 =
            Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abd";
    USER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    chainBaseManager = context.getBean(ChainBaseManager.class);
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

  /**
   * create temp Capsule test need.
   */
  @Before
  public void createCapsule() {
    AccountCapsule contractProvierCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("owner"),
            ByteString.copyFrom(ByteArray.fromHexString(CONTRACT_PROVIDER_ADDRESS)),
            AccountType.Normal,
            0L);
    contractProvierCapsule.addAsset(ASSET_NAME.getBytes(), 100L);

    AccountCapsule contractProvierCapsule2 =
            new AccountCapsule(
                    ByteString.copyFromUtf8("owner"),
                    ByteString.copyFrom(ByteArray.fromHexString(CONTRACT_PROVIDER_ADDRESS2)),
                    AccountType.Normal,
                    0L);
    contractProvierCapsule2.addAsset(ASSET_NAME.getBytes(), 100L);

    AccountCapsule userCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("asset"),
            ByteString.copyFrom(ByteArray.fromHexString(USER_ADDRESS)),
            AccountType.AssetIssue,
            dbManager.getDynamicPropertiesStore().getAssetIssueFee());

    dbManager.getAccountStore().reset();
    dbManager.getAccountStore()
        .put(contractProvierCapsule.getAddress().toByteArray(), contractProvierCapsule);
    dbManager.getAccountStore()
            .put(contractProvierCapsule2.getAddress().toByteArray(), contractProvierCapsule2);
    dbManager.getAccountStore().put(userCapsule.getAddress().toByteArray(), userCapsule);

  }


  //todo ,replaced by smartContract later
  private AssetIssueContract getAssetIssueContract() {
    return AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(USER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(ASSET_NAME))
        .setFreeAssetPhotonLimit(1000L)
        .setPublicFreeAssetPhotonLimit(1000L)
        .build();
  }

  @Test
  public void testUseContractCreatorEntropy() throws Exception {
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526647838000L);
    dbManager.getDynamicPropertiesStore().saveTotalEntropyWeight(10_000_000L);

    AccountCapsule ownerCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(CONTRACT_PROVIDER_ADDRESS2));
    dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);

    EntropyProcessor processor = new EntropyProcessor(dbManager.getDynamicPropertiesStore(),
        dbManager.getAccountStore());
    long entropy = 1000;
    long now = 1526647838000L;

    boolean result = processor.useEntropy(ownerCapsule, entropy, now);
    Assert.assertEquals(false, result);

    ownerCapsule.setFrozenForEntropy(10_000_000L, 0L);
    result = processor.useEntropy(ownerCapsule, entropy, now);
    Assert.assertEquals(true, result);

    AccountCapsule ownerCapsuleNew = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(CONTRACT_PROVIDER_ADDRESS2));

    Assert.assertEquals(1526647838000L, ownerCapsuleNew.getLatestOperationTime());
    Assert.assertEquals(1526647838000L,
        ownerCapsuleNew.getAccountResource().getLatestConsumeTimeForEntropy());
    Assert.assertEquals(1000L, ownerCapsuleNew.getAccountResource().getEntropyUsage());

  }

  @Test
  public void updateAdaptiveTotalEntropyLimit() {
    EntropyProcessor processor = new EntropyProcessor(dbManager.getDynamicPropertiesStore(),
        dbManager.getAccountStore());

    // open
    dbManager.getDynamicPropertiesStore().saveAllowAdaptiveEntropy(1);

    // Test resource usage auto reply
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526647838000L);
    long now = chainBaseManager.getHeadSlot();
    dbManager.getDynamicPropertiesStore().saveTotalEntropyAverageTime(now);
    dbManager.getDynamicPropertiesStore().saveTotalEntropyAverageUsage(4000L);

    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(
        1526647838000L + Parameter.AdaptiveResourceLimitConstants.PERIODS_MS / 2);
    processor.updateTotalEntropyAverageUsage();
    Assert.assertEquals(2000L,
        dbManager.getDynamicPropertiesStore().getTotalEntropyAverageUsage());

    // test saveTotalEntropyLimit
    long ratio = Parameter.ChainConstant.WINDOW_SIZE_MS / Parameter.AdaptiveResourceLimitConstants.PERIODS_MS;
    dbManager.getDynamicPropertiesStore().saveTotalEntropyLimit(10000L * ratio);
    Assert.assertEquals(1000L,
        dbManager.getDynamicPropertiesStore().getTotalEntropyTargetLimit());

    //Test exceeds resource limit
    dbManager.getDynamicPropertiesStore().saveTotalEntropyCurrentLimit(10000L * ratio);
    dbManager.getDynamicPropertiesStore().saveTotalEntropyAverageUsage(3000L);
    processor.updateAdaptiveTotalEntropyLimit();
    Assert.assertEquals(10000L * ratio,
        dbManager.getDynamicPropertiesStore().getTotalEntropyCurrentLimit());

    //Test exceeds resource limit 2
    dbManager.getDynamicPropertiesStore().saveTotalEntropyCurrentLimit(20000L * ratio);
    dbManager.getDynamicPropertiesStore().saveTotalEntropyAverageUsage(3000L);
    processor.updateAdaptiveTotalEntropyLimit();
    Assert.assertEquals(20000L * ratio * 99 / 100L,
        dbManager.getDynamicPropertiesStore().getTotalEntropyCurrentLimit());

    //Test less than resource limit
    dbManager.getDynamicPropertiesStore().saveTotalEntropyCurrentLimit(20000L * ratio);
    dbManager.getDynamicPropertiesStore().saveTotalEntropyAverageUsage(500L);
    processor.updateAdaptiveTotalEntropyLimit();
    Assert.assertEquals(20000L * ratio * 1000 / 999L,
        dbManager.getDynamicPropertiesStore().getTotalEntropyCurrentLimit());
  }


}
