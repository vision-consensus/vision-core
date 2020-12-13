package org.vision.core.actuator;

import static junit.framework.TestCase.fail;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vision.common.args.Witness;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.FileUtil;
import org.vision.common.utils.StringUtil;
import org.vision.core.Constant;
import org.vision.core.Wallet;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.TransactionResultCapsule;
import org.vision.core.capsule.WitnessCapsule;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.core.db.Manager;
import org.vision.core.exception.BalanceInsufficientException;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.common.application.VisionApplicationContext;
import org.vision.protos.Protocol.AccountType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.protos.contract.AssetIssueContractOuterClass;
import org.vision.protos.contract.BalanceContract.WithdrawBalanceContract;

@Slf4j
public class WithdrawBalanceActuatorTest {

  private static final String dbPath = "output_withdraw_balance_test";
  private static final String OWNER_ADDRESS;
  private static final String OWNER_ADDRESS_INVALID = "aaaa";
  private static final String OWNER_ACCOUNT_INVALID;
  private static final long initBalance = 10_000_000_000L;
  private static final long allowance = 32_000_000L;
  private static Manager dbManager;
  private static VisionApplicationContext context;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
    OWNER_ACCOUNT_INVALID =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a3456";
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    //    Args.setParam(new String[]{"--output-directory", dbPath},
    //        "config-junit.conf");
    //    dbManager = new Manager();
    //    dbManager.init();
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
  public void createAccountCapsule() {
    AccountCapsule ownerCapsule = new AccountCapsule(ByteString.copyFromUtf8("owner"),
        ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)), AccountType.Normal,
        initBalance);
    dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);
  }

  private Any getContract(String ownerAddress) {
    return Any.pack(WithdrawBalanceContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress))).build());
  }

  @Test
  public void testWithdrawBalance() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);
    byte[] address = ByteArray.fromHexString(OWNER_ADDRESS);
    try {
      dbManager.getDelegationService()
          .adjustAllowance(dbManager.getAccountStore(), address, allowance);
    } catch (BalanceInsufficientException e) {
      fail("BalanceInsufficientException");
    }
    AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
    Assert.assertEquals(accountCapsule.getAllowance(), allowance);
    Assert.assertEquals(accountCapsule.getLatestWithdrawTime(), 0);

    WitnessCapsule witnessCapsule = new WitnessCapsule(ByteString.copyFrom(address), 100,
        "http://baidu.com");
    dbManager.getWitnessStore().put(address, witnessCapsule);

    WithdrawBalanceActuator actuator = new WithdrawBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS));

    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getBalance(), initBalance + allowance);
      Assert.assertEquals(owner.getAllowance(), 0);
      Assert.assertNotEquals(owner.getLatestWithdrawTime(), 0);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  public void invalidOwnerAddress() {
    WithdrawBalanceActuator actuator = new WithdrawBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS_INVALID));

    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);

      Assert.assertEquals("Invalid address", e.getMessage());

    } catch (ContractExeException e) {
      Assert.assertTrue(e instanceof ContractExeException);
    }

  }

  @Test
  public void invalidOwnerAccount() {
    WithdrawBalanceActuator actuator = new WithdrawBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ACCOUNT_INVALID));

    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + OWNER_ACCOUNT_INVALID + "] not exists", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void notWitness() {
    WithdrawBalanceActuator actuator = new WithdrawBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS));

    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      //Assert.assertEquals("Account[" + OWNER_ADDRESS + "] is not a witnessAccount",
      //    e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void noAllowance() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    byte[] address = ByteArray.fromHexString(OWNER_ADDRESS);

    AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
    Assert.assertEquals(accountCapsule.getAllowance(), 0);

    WitnessCapsule witnessCapsule = new WitnessCapsule(ByteString.copyFrom(address), 100,
        "http://baidu.com");
    dbManager.getWitnessStore().put(address, witnessCapsule);

    WithdrawBalanceActuator actuator = new WithdrawBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS));

    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("witnessAccount does not have any reward", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void isGR() {
    Witness w = Args.getInstance().getGenesisBlock().getWitnesses().get(0);
    byte[] address = w.getAddress();
    AccountCapsule grCapsule = new AccountCapsule(ByteString.copyFromUtf8("gr"),
        ByteString.copyFrom(address), AccountType.Normal, initBalance);
    dbManager.getAccountStore().put(grCapsule.createDbKey(), grCapsule);
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    try {
      dbManager.getDelegationService()
          .adjustAllowance(dbManager.getAccountStore(), address, allowance);
    } catch (BalanceInsufficientException e) {
      fail("BalanceInsufficientException");
    }
    AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
    Assert.assertEquals(accountCapsule.getAllowance(), allowance);

    WitnessCapsule witnessCapsule = new WitnessCapsule(ByteString.copyFrom(address), 100,
        "http://google.com");

    dbManager.getAccountStore().put(address, accountCapsule);
    dbManager.getWitnessStore().put(address, witnessCapsule);

    WithdrawBalanceActuator actuator = new WithdrawBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(ByteArray.toHexString(address)));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    Assert.assertTrue(dbManager.getWitnessStore().has(address));

    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      String readableOwnerAddress = StringUtil.createReadableString(address);
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + readableOwnerAddress
          + "] is a guard representative and is not allowed to withdraw Balance", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void notTimeToWithdraw() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    byte[] address = ByteArray.fromHexString(OWNER_ADDRESS);
    try {
      dbManager.getDelegationService()
          .adjustAllowance(dbManager.getAccountStore(), address, allowance);
    } catch (BalanceInsufficientException e) {
      fail("BalanceInsufficientException");
    }
    AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
    accountCapsule.setLatestWithdrawTime(now);
    Assert.assertEquals(accountCapsule.getAllowance(), allowance);
    Assert.assertEquals(accountCapsule.getLatestWithdrawTime(), now);

    WitnessCapsule witnessCapsule = new WitnessCapsule(ByteString.copyFrom(address), 100,
        "http://baidu.com");

    dbManager.getAccountStore().put(address, accountCapsule);
    dbManager.getWitnessStore().put(address, witnessCapsule);

    WithdrawBalanceActuator actuator = new WithdrawBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS));

    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert
          .assertEquals("The last withdraw time is " + now + ", less than 24 hours",
              e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void commonErrorCheck() {

    WithdrawBalanceActuator actuator = new WithdrawBalanceActuator();
    ActuatorTest actuatorTest = new ActuatorTest(actuator, dbManager);
    actuatorTest.noContract();

    Any invalidContractTypes = Any.pack(AssetIssueContractOuterClass.AssetIssueContract.newBuilder()
        .build());
    actuatorTest.setInvalidContract(invalidContractTypes);
    actuatorTest.setInvalidContractTypeMsg("contract type error",
        "contract type error, expected type [WithdrawBalanceContract], real type[");
    actuatorTest.invalidContractType();

    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);
    byte[] address = ByteArray.fromHexString(OWNER_ADDRESS);
    try {
      dbManager.getDelegationService()
          .adjustAllowance(dbManager.getAccountStore(), address, allowance);
    } catch (BalanceInsufficientException e) {
      fail("BalanceInsufficientException");
    }
    AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
    Assert.assertEquals(accountCapsule.getAllowance(), allowance);
    Assert.assertEquals(accountCapsule.getLatestWithdrawTime(), 0);

    WitnessCapsule witnessCapsule = new WitnessCapsule(ByteString.copyFrom(address), 100,
        "http://google.com");
    dbManager.getWitnessStore().put(address, witnessCapsule);

    actuatorTest.setContract(getContract(OWNER_ADDRESS));
    actuatorTest.nullTransationResult();

    actuatorTest.setNullDBManagerMsg("No account store or dynamic store!");
    actuatorTest.nullDBManger();
  }

}

