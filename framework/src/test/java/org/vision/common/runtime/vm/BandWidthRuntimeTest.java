/*
 * java-vision is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-vision is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.vision.common.runtime.vm;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.vision.common.runtime.VvmTestUtils;
import org.vision.common.utils.Commons;
import org.vision.common.utils.FileUtil;
import org.vision.core.ChainBaseManager;
import org.vision.core.Constant;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.capsule.ReceiptCapsule;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.core.db.Manager;
import org.vision.core.db.TransactionTrace;
import org.vision.core.store.StoreFactory;
import org.vision.common.application.VisionApplicationContext;
import org.vision.common.runtime.RuntimeImpl;
import org.vision.common.storage.DepositImpl;
import org.vision.core.exception.AccountResourceInsufficientException;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.exception.TooBigTransactionResultException;
import org.vision.core.exception.VisionException;
import org.vision.core.exception.VMIllegalException;
import org.vision.protos.Protocol.AccountType;
import org.vision.protos.Protocol.Transaction;
import org.vision.protos.Protocol.Transaction.Contract;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.raw;
import org.vision.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.vision.protos.contract.SmartContractOuterClass.TriggerSmartContract;

public class BandWidthRuntimeTest {

  public static final long totalBalance = 1000_0000_000_000L;
  private static String dbPath = "output_BandWidthRuntimeTest_test";
  private static String dbDirectory = "db_BandWidthRuntimeTest_test";
  private static String indexDirectory = "index_BandWidthRuntimeTest_test";
  private static AnnotationConfigApplicationContext context;
  private static Manager dbManager;
  private static ChainBaseManager chainBaseManager;

  private static String OwnerAddress = "TCWHANtDDdkZCTo2T2peyEq3Eg9c2XB7ut";
  private static String TriggerOwnerAddress = "TCSgeWapPJhCqgWRxXCKb6jJ5AgNWSGjPA";
  private static String TriggerOwnerTwoAddress = "TPMBUANrTwwQAPwShn7ZZjTJz1f3F8jknj";

  static {
    Args.setParam(
        new String[]{
            "--output-directory", dbPath,
            "--storage-db-directory", dbDirectory,
            "--storage-index-directory", indexDirectory,
            "-w"
        },
        "config-test-mainnet.conf"
    );
    context = new VisionApplicationContext(DefaultConfig.class);
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    chainBaseManager = context.getBean(ChainBaseManager.class);
    //init energy
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526547838000L);
    dbManager.getDynamicPropertiesStore().saveTotalEnergyWeight(10_000_000L);

    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(0);

    AccountCapsule accountCapsule = new AccountCapsule(ByteString.copyFrom("owner".getBytes()),
        ByteString.copyFrom(Commons.decodeFromBase58Check(OwnerAddress)), AccountType.Normal,
        totalBalance);

    accountCapsule.setFrozenForEnergy(10_000_000L, 0L);
    dbManager.getAccountStore()
        .put(Commons.decodeFromBase58Check(OwnerAddress), accountCapsule);

    AccountCapsule accountCapsule2 = new AccountCapsule(
        ByteString.copyFrom("triggerOwner".getBytes()),
        ByteString.copyFrom(Commons.decodeFromBase58Check(TriggerOwnerAddress)), AccountType.Normal,
        totalBalance);

    accountCapsule2.setFrozenForEnergy(10_000_000L, 0L);
    dbManager.getAccountStore()
        .put(Commons.decodeFromBase58Check(TriggerOwnerAddress), accountCapsule2);
    AccountCapsule accountCapsule3 = new AccountCapsule(
        ByteString.copyFrom("triggerOwnerAddress".getBytes()),
        ByteString.copyFrom(Commons.decodeFromBase58Check(TriggerOwnerTwoAddress)),
        AccountType.Normal,
        totalBalance);
    accountCapsule3.setNetUsage(5000L);
    accountCapsule3.setLatestConsumeFreeTime(chainBaseManager.getHeadSlot());
    accountCapsule3.setFrozenForEnergy(10_000_000L, 0L);
    dbManager.getAccountStore()
        .put(Commons.decodeFromBase58Check(TriggerOwnerTwoAddress), accountCapsule3);

    dbManager.getDynamicPropertiesStore()
        .saveLatestBlockHeaderTimestamp(System.currentTimeMillis() / 1000);
  }

  /**
   * destroy clear data of testing.
   */
  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }

  @Test
  public void testSuccess() {
    try {
      byte[] contractAddress = createContract();
      AccountCapsule triggerOwner = dbManager.getAccountStore()
          .get(Commons.decodeFromBase58Check(TriggerOwnerAddress));
      long energy = triggerOwner.getEnergyUsage();
      TriggerSmartContract triggerContract = VvmTestUtils.createTriggerContract(contractAddress,
          "setCoin(uint256)", "3", false,
          0, Commons.decodeFromBase58Check(TriggerOwnerAddress));
      Transaction transaction = Transaction.newBuilder().setRawData(raw.newBuilder().addContract(
          Contract.newBuilder().setParameter(Any.pack(triggerContract))
              .setType(ContractType.TriggerSmartContract)).setFeeLimit(1000000000)).build();
      TransactionCapsule trxCap = new TransactionCapsule(transaction);
      TransactionTrace trace = new TransactionTrace(trxCap, StoreFactory.getInstance(),
          new RuntimeImpl());
      dbManager.consumeBandwidth(trxCap, trace);
      BlockCapsule blockCapsule = null;
      DepositImpl deposit = DepositImpl.createRoot(dbManager);

      trace.init(blockCapsule);
      trace.exec();
      trace.finalization();

      triggerOwner = dbManager.getAccountStore()
          .get(Commons.decodeFromBase58Check(TriggerOwnerAddress));
      energy = triggerOwner.getEnergyUsage();
      long balance = triggerOwner.getBalance();
      Assert.assertEquals(45706, trace.getReceipt().getEnergyUsageTotal());
      Assert.assertEquals(45706, energy);
      Assert.assertEquals(totalBalance, balance);
    } catch (VisionException e) {
      Assert.assertNotNull(e);
    }
  }

  @Test
  public void testSuccessNoBandd() {
    try {
      byte[] contractAddress = createContract();
      TriggerSmartContract triggerContract = VvmTestUtils.createTriggerContract(contractAddress,
          "setCoin(uint256)", "50", false,
          0, Commons.decodeFromBase58Check(TriggerOwnerTwoAddress));
      Transaction transaction = Transaction.newBuilder().setRawData(raw.newBuilder().addContract(
          Contract.newBuilder().setParameter(Any.pack(triggerContract))
              .setType(ContractType.TriggerSmartContract)).setFeeLimit(1000000000)).build();
      TransactionCapsule trxCap = new TransactionCapsule(transaction);
      TransactionTrace trace = new TransactionTrace(trxCap, StoreFactory.getInstance(),
          new RuntimeImpl());
      dbManager.consumeBandwidth(trxCap, trace);
      long bandWidth = trxCap.getSerializedSize() + Constant.MAX_RESULT_SIZE_IN_TX;
      BlockCapsule blockCapsule = null;
      DepositImpl deposit = DepositImpl.createRoot(dbManager);
      trace.init(blockCapsule);
      trace.exec();
      trace.finalization();

      AccountCapsule triggerOwnerTwo = dbManager.getAccountStore()
          .get(Commons.decodeFromBase58Check(TriggerOwnerTwoAddress));
      long balance = triggerOwnerTwo.getBalance();
      ReceiptCapsule receipt = trace.getReceipt();

      Assert.assertEquals(bandWidth, receipt.getNetUsage());
      Assert.assertEquals(522850, receipt.getEnergyUsageTotal());
      Assert.assertEquals(50000, receipt.getEnergyUsage());
      Assert.assertEquals(47285000, receipt.getEnergyFee());
      Assert.assertEquals(totalBalance - receipt.getEnergyFee(),
          balance);
    } catch (VisionException e) {
      Assert.assertNotNull(e);
    }
  }

  private byte[] createContract()
      throws ContractValidateException, AccountResourceInsufficientException,
          TooBigTransactionResultException, ContractExeException, VMIllegalException {
    AccountCapsule owner = dbManager.getAccountStore()
        .get(Commons.decodeFromBase58Check(OwnerAddress));
    long energy = owner.getEnergyUsage();
    long balance = owner.getBalance();

    String contractName = "foriContract";
    String code = "608060405234801561001057600080fd5b50610105806100206000396000f30060806040526004"
        + "36106049576000357c0100000000000000000000000000000000000000000000000000000000900463ffff"
        + "ffff1680637bb98a6814604e578063866edb47146076575b600080fd5b348015605957600080fd5b506060"
        + "60a0565b6040518082815260200191505060405180910390f35b348015608157600080fd5b50609e600480"
        + "3603810190808035906020019092919050505060a6565b005b60005481565b60008090505b8181101560d5"
        + "5760008081548092919060010191905055600081905550808060010191505060ac565b50505600a165627a"
        + "7a72305820f4020a69fb8504d7db776726b19e5101c3216413d7ab8e91a11c4f55f772caed0029";

    String abi = "[{\"constant\":true,\"inputs\":[],\"name\":\"balances\",\"outputs\":"
        + "[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":"
        + "\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":"
        + "\"receiver\",\"type\":\"uint256\"}],\"name\":\"setCoin\",\"outputs\":[],\"payable\""
        + ":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}]";

    CreateSmartContract smartContract = VvmTestUtils.createSmartContract(
        Commons.decodeFromBase58Check(OwnerAddress), contractName, abi, code, 0,
        100);
    Transaction transaction = Transaction.newBuilder().setRawData(raw.newBuilder().addContract(
        Contract.newBuilder().setParameter(Any.pack(smartContract))
            .setType(ContractType.CreateSmartContract)).setFeeLimit(1000000000)).build();
    TransactionCapsule trxCap = new TransactionCapsule(transaction);
    TransactionTrace trace = new TransactionTrace(trxCap, StoreFactory.getInstance(),
        new RuntimeImpl());
    dbManager.consumeBandwidth(trxCap, trace);
    BlockCapsule blockCapsule = null;
    DepositImpl deposit = DepositImpl.createRoot(dbManager);
    trace.init(blockCapsule);
    trace.exec();
    trace.finalization();
    owner = dbManager.getAccountStore()
        .get(Commons.decodeFromBase58Check(OwnerAddress));
    energy = owner.getEnergyUsage() - energy;
    balance = balance - owner.getBalance();
    Assert.assertNull(trace.getRuntimeError());
    Assert.assertEquals(52299, trace.getReceipt().getEnergyUsageTotal());
    Assert.assertEquals(50000, energy);
    Assert.assertEquals(229900, balance);
    Assert
        .assertEquals(52299 * Constant.VDT_PER_ENERGY,
            balance + energy * Constant.VDT_PER_ENERGY);
    Assert.assertNull(trace.getRuntimeError());
    return trace.getRuntimeResult().getContractAddress();
  }
}