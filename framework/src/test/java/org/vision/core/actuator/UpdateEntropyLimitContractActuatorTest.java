package org.vision.core.actuator;

import static junit.framework.TestCase.fail;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.File;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.FileUtil;
import org.vision.common.utils.StringUtil;
import org.vision.core.Constant;
import org.vision.core.Wallet;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.ContractCapsule;
import org.vision.core.capsule.TransactionResultCapsule;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.Parameter;
import org.vision.core.config.args.Args;
import org.vision.core.db.Manager;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.exception.VisionException;
import org.vision.core.vm.config.VMConfig;
import org.vision.common.application.VisionApplicationContext;
import org.vision.protos.Protocol;
import org.vision.protos.contract.AssetIssueContractOuterClass;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;
import org.vision.protos.contract.SmartContractOuterClass.UpdateEntropyLimitContract;


@Slf4j
//@Ignore
public class UpdateEntropyLimitContractActuatorTest {

  private static final String dbPath = "output_UpdateEntropyLimitContractActuator_test";
  private static final String OWNER_ADDRESS_ACCOUNT_NAME = "test_account";
  private static final String OWNER_ADDRESS_INVALID = "aaaa";
  private static final String SMART_CONTRACT_NAME = "smart_contarct";
  private static final String CONTRACT_ADDRESS = "111111";
  private static final String NO_EXIST_CONTRACT_ADDRESS = "2222222";
  private static final long SOURCE_ENERGY_LIMIT = 10L;
  private static final long TARGET_ENERGY_LIMIT = 30L;
  private static final long INVALID_ENERGY_LIMIT = -200L;
  private static VisionApplicationContext context;
  private static Manager dbManager;
  private static String OWNER_ADDRESS;
  private static String SECOND_ACCOUNT_ADDRESS;
  private static String OWNER_ADDRESS_NOTEXIST;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    OWNER_ADDRESS =
        Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
    SECOND_ACCOUNT_ADDRESS =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d427122222";
    OWNER_ADDRESS_NOTEXIST =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";

    byte[] stats = new byte[27];
    Arrays.fill(stats, (byte) 1);
    dbManager.getDynamicPropertiesStore()
        .statsByVersion(Parameter.ForkBlockVersionConsts.ENERGY_LIMIT, stats);
    VMConfig.initVmHardFork(true);
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
    CommonParameter.setENERGY_LIMIT_HARD_FORK(false);
  }

  /**
   * create temp Capsule test need.
   */
  @Before
  public void createCapsule() {
    // address in accountStore and the owner of contract
    AccountCapsule accountCapsule =
        new AccountCapsule(
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
            ByteString.copyFromUtf8(OWNER_ADDRESS_ACCOUNT_NAME),
            Protocol.AccountType.Normal);
    dbManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS), accountCapsule);

    // smartContract in contractStore
    SmartContract.Builder builder = SmartContract.newBuilder();
    builder.setName(SMART_CONTRACT_NAME);
    builder.setOriginAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
    builder.setContractAddress(ByteString.copyFrom(ByteArray.fromHexString(CONTRACT_ADDRESS)));
    builder.setOriginEntropyLimit(SOURCE_ENERGY_LIMIT);
    dbManager.getContractStore().put(
        ByteArray.fromHexString(CONTRACT_ADDRESS),
        new ContractCapsule(builder.build()));

    // address in accountStore not the owner of contract
    AccountCapsule secondAccount =
        new AccountCapsule(
            ByteString.copyFrom(ByteArray.fromHexString(SECOND_ACCOUNT_ADDRESS)),
            ByteString.copyFromUtf8(OWNER_ADDRESS_ACCOUNT_NAME),
            Protocol.AccountType.Normal);
    dbManager.getAccountStore().put(ByteArray.fromHexString(SECOND_ACCOUNT_ADDRESS), secondAccount);

    // address does not exist in accountStore
    dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS_NOTEXIST));
  }

  private Any getContract(String accountAddress, String contractAddress, long originEnergyLimit) {
    return Any.pack(
        UpdateEntropyLimitContract.newBuilder()
            .setOwnerAddress(StringUtil.hexString2ByteString(accountAddress))
            .setContractAddress(StringUtil.hexString2ByteString(contractAddress))
            .setOriginEntropyLimit(originEnergyLimit).build());
  }

  @Test
  public void successUpdateEntropyLimitContract() throws InvalidProtocolBufferException {
    UpdateEntropyLimitContractActuator actuator = new UpdateEntropyLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, TARGET_ENERGY_LIMIT));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      // assert result state and energy_limit
      Assert.assertEquals(OWNER_ADDRESS,
          ByteArray.toHexString(actuator.getOwnerAddress().toByteArray()));
      Assert.assertEquals(ret.getInstance().getRet(), Protocol.Transaction.Result.code.SUCESS);
      Assert.assertEquals(
          dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS))
              .getOriginEnergyLimit(), TARGET_ENERGY_LIMIT);
    } catch (ContractValidateException | ContractExeException e) {
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void invalidAddress() {
    UpdateEntropyLimitContractActuator actuator = new UpdateEntropyLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS_INVALID, CONTRACT_ADDRESS, TARGET_ENERGY_LIMIT));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("Invalid address");
    } catch (VisionException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid address", e.getMessage());
    }
  }

  @Test
  public void noExistAccount() {
    UpdateEntropyLimitContractActuator actuator = new UpdateEntropyLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS_NOTEXIST, CONTRACT_ADDRESS, TARGET_ENERGY_LIMIT));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("Account[" + OWNER_ADDRESS_NOTEXIST + "] not exists");
    } catch (VisionException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + OWNER_ADDRESS_NOTEXIST + "] does not exist", e.getMessage());
    }
  }

  @Test
  public void invalidResourceEnergyLimit() {
    UpdateEntropyLimitContractActuator actuator = new UpdateEntropyLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, INVALID_ENERGY_LIMIT));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("origin energy limit less than 0");
    } catch (VisionException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("origin energy limit must be > 0", e.getMessage());
    }
  }

  @Test
  public void noExistContract() {
    UpdateEntropyLimitContractActuator actuator = new UpdateEntropyLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, NO_EXIST_CONTRACT_ADDRESS, TARGET_ENERGY_LIMIT));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("Contract not exists");
    } catch (VisionException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Contract does not exist", e.getMessage());
    }
  }

  @Test
  public void callerNotContractOwner() {
    UpdateEntropyLimitContractActuator actuator = new UpdateEntropyLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(SECOND_ACCOUNT_ADDRESS, CONTRACT_ADDRESS, TARGET_ENERGY_LIMIT));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("Account[" + SECOND_ACCOUNT_ADDRESS + "] is not the owner of the contract");
    } catch (VisionException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals(
          "Account[" + SECOND_ACCOUNT_ADDRESS + "] is not the owner of the contract",
          e.getMessage());
    }
  }

  @Test
  public void twiceUpdateEntropyLimitContract() throws InvalidProtocolBufferException {
    UpdateEntropyLimitContractActuator actuator = new UpdateEntropyLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, TARGET_ENERGY_LIMIT));

    UpdateEntropyLimitContractActuator secondActuator = new UpdateEntropyLimitContractActuator();
    secondActuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, 90L));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      // first
      actuator.validate();
      actuator.execute(ret);

      Assert.assertEquals(OWNER_ADDRESS,
          ByteArray.toHexString(actuator.getOwnerAddress().toByteArray()));
      Assert.assertEquals(ret.getInstance().getRet(), Protocol.Transaction.Result.code.SUCESS);
      Assert.assertEquals(
          dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS))
              .getOriginEnergyLimit(), TARGET_ENERGY_LIMIT);

      // second
      secondActuator.validate();
      secondActuator.execute(ret);

      Assert.assertEquals(ret.getInstance().getRet(), Protocol.Transaction.Result.code.SUCESS);
      Assert.assertEquals(
          dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS))
              .getOriginEnergyLimit(), 90L);
    } catch (ContractValidateException | ContractExeException e) {
      Assert.fail(e.getMessage());
    }
  }


  @Test
  public void nullDBManger() {
    UpdateEntropyLimitContractActuator actuator = new UpdateEntropyLimitContractActuator();
    actuator.setChainBaseManager(null)
        .setAny(getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, TARGET_ENERGY_LIMIT));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    processAndCheckInvalid(actuator, ret, "No account store or dynamic store!",
        "No account store or dynamic store!");
  }

  @Test
  public void noContract() {

    UpdateEntropyLimitContractActuator actuator = new UpdateEntropyLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(null);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    processAndCheckInvalid(actuator, ret, "No contract!", "No contract!");
  }

  @Test
  public void invalidContractType() {
    UpdateEntropyLimitContractActuator actuator = new UpdateEntropyLimitContractActuator();
    // create AssetIssueContract, not a valid UpdateEntropyLimitContract contract , which will
    // throw e exception
    Any invalidContractTypes = Any.pack(AssetIssueContractOuterClass.AssetIssueContract.newBuilder()
        .build());
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(invalidContractTypes);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    processAndCheckInvalid(actuator, ret, "contract type error",
        "contract type error, expected type [UpdateEntropyLimitContract],real type["
            + invalidContractTypes.getClass() + "]");
  }

  @Test
  public void nullTransactionResult() {
    UpdateEntropyLimitContractActuator actuator = new UpdateEntropyLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, TARGET_ENERGY_LIMIT));
    TransactionResultCapsule ret = null;
    processAndCheckInvalid(actuator, ret, "TransactionResultCapsule is null",
        "TransactionResultCapsule is null");
  }

  private void processAndCheckInvalid(UpdateEntropyLimitContractActuator actuator,
      TransactionResultCapsule ret,
      String failMsg,
      String expectedMsg) {
    try {
      actuator.validate();
      actuator.execute(ret);
      fail(failMsg);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals(expectedMsg, e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } catch (RuntimeException e) {
      Assert.assertTrue(e instanceof RuntimeException);
      Assert.assertEquals(expectedMsg, e.getMessage());
    }
  }


}
