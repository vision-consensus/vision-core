/*
 * vision-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * vision-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.vision.core.db;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.File;
import java.util.Objects;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.vision.common.runtime.VvmTestUtils;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Commons;
import org.vision.common.utils.FileUtil;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.ContractCapsule;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.core.exception.BalanceInsufficientException;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.exception.VMIllegalException;
import org.vision.core.store.StoreFactory;
import org.vision.core.vm.config.VMConfig;
import org.vision.common.application.VisionApplicationContext;
import org.vision.common.runtime.RuntimeImpl;
import org.vision.protos.Protocol.Account;
import org.vision.protos.Protocol.Account.AccountResource;
import org.vision.protos.Protocol.Account.Frozen;
import org.vision.protos.Protocol.AccountType;
import org.vision.protos.Protocol.Transaction;
import org.vision.protos.Protocol.Transaction.Contract;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.raw;
import org.vision.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;
import org.vision.protos.contract.SmartContractOuterClass.TriggerSmartContract;

public class TransactionTraceTest {

  public static final long totalBalance = 1000_0000_000_000L;
  private static String dbPath = "output_TransactionTrace_test";
  private static String dbDirectory = "db_TransactionTrace_test";
  private static String indexDirectory = "index_TransactionTrace_test";
  private static AnnotationConfigApplicationContext context;
  private static Manager dbManager;
  private static ByteString ownerAddress = ByteString.copyFrom(ByteArray.fromInt(1));
  private static ByteString contractAddress = ByteString.copyFrom(ByteArray.fromInt(2));

  /*
   * DeployContract tracetestContract [{"constant":false,"inputs":[{"name":"accountId","type":
   * "uint256"}],"name":"getVoters","outputs":[{"name":"","type":"uint256"}],"payable":false,"
   * stateMutability":"nonpayable","type":"function"},{"constant":true,"inputs":[{"name":"","type"
   * :"uint256"}],"name":"voters","outputs":[{"name":"","type":"uint256"}],"payable":false,
   * "stateMutability":"view","type":"function"},{"constant":false,"inputs":[{"name":"vote","type"
   * :"uint256"}],"name":"addVoters","outputs":[],"payable":false,"stateMutability":"nonpayable",
   * "type":"function"},{"inputs":[],"payable":false,"stateMutability":"nonpayable","type":
   * "constructor"}] 608060405234801561001057600080fd5b5060015b620186a081101561003857600081815260
   * 2081905260409020819055600a01610014565b5061010b806100486000396000f300608060405260043610605257
   * 63ffffffff7c010000000000000000000000000000000000000000000000000000000060003504166386b646f281
   * 146057578063da58c7d914607e578063eb91a5ff146093575b600080fd5b348015606257600080fd5b50606c6004
   * 3560aa565b60408051918252519081900360200190f35b348015608957600080fd5b50606c60043560bc565b3480
   * 15609e57600080fd5b5060a860043560ce565b005b60009081526020819052604090205490565b60006020819052
   * 908152604090205481565b6000818152602081905260409020555600a165627a7a72305820f9935f89890e51bcf3
   * ea98fa4841c91ac5957a197d99eeb7879a775b30ee9a2d0029   1000000000 100
   * */
  /*
   * DeployContract tracetestContract [{"constant":false,"inputs":[{"name":"accountId","type":
   * "uint256"}],"name":"getVoters","outputs":[{"name":"","type":"uint256"}],"payable":false,
   * "stateMutability":"nonpayable","type":"function"},{"constant":true,"inputs":[{"name":"",
   * "type":"uint256"}],"name":"voters","outputs":[{"name":"","type":"uint256"}],"payable":false,
   * "stateMutability":"view","type":"function"},{"constant":false,"inputs":[{"name":"vote","type"
   * :"uint256"}],"name":"addVoters","outputs":[],"payable":false,"stateMutability":"nonpayable",
   * "type":"function"},{"inputs":[],"payable":false,"stateMutability":"nonpayable","type":
   * "constructor"}] 608060405234801561001057600080fd5b5060015b620186a08110156100385760008181526020
   * 81905260409020819055600a01610014565b5061010b806100486000396000f30060806040526004361060525763ff
   * ffffff7c010000000000000000000000000000000000000000000000000000000060003504166386b646f281146057
   * 578063da58c7d914607e578063eb91a5ff146093575b600080fd5b348015606257600080fd5b50606c60043560aa56
   * 5b60408051918252519081900360200190f35b348015608957600080fd5b50606c60043560bc565b348015609e576
   * 00080fd5b5060a860043560ce565b005b60009081526020819052604090205490565b6000602081905290815260409
   * 0205481565b6000818152602081905260409020555600a165627a7a72305820f9935f89890e51bcf3ea98fa4841c91
   * ac5957a197d99eeb7879a775b30ee9a2d0029   1000000000 40
   * */
  private static String OwnerAddress = "27RcahhCafmmnte3FmtTw39gn2bdDV9uADR";
  private static String TriggerOwnerAddress = "27RYzBpuBqSiSXrkfHNqbf1b2s69z3wnqfo";
  /*
   * triggercontract TPMBUANrTwwQAPwShn7ZZjTJz1f3F8jknj addVoters(uint256) 113 false 1000000000 0
   * */

  static {
    Args.setParam(
        new String[]{
            "--output-directory", dbPath,
            "--storage-db-directory", dbDirectory,
            "--storage-index-directory", indexDirectory,
            "-w",
            "--debug"
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
    //init entropy
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526647838000L);
    dbManager.getDynamicPropertiesStore().saveTotalEntropyWeight(100_000L);
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(0);
    VMConfig.initVmHardFork(false);

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
  public void testUseFee()
      throws InvalidProtocolBufferException, VMIllegalException, BalanceInsufficientException,
          ContractExeException, ContractValidateException {
    String contractName = "tracetestContract";
    String code = "608060405234801561001057600080fd5b5060005b6103e88110156100375760008181526020819"
        + "05260409020819055600a01610014565b5061010f806100476000396000f300608060405260043610605257"
        + "63ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416634903b"
        + "0d181146057578063da31158814607e578063fe4ba936146093575b600080fd5b348015606257600080fd5b"
        + "50606c60043560ad565b60408051918252519081900360200190f35b348015608957600080fd5b50606c600"
        + "43560bf565b348015609e57600080fd5b5060ab60043560243560d1565b005b600060208190529081526040"
        + "90205481565b60009081526020819052604090205490565b600091825260208290526040909120555600a16"
        + "5627a7a723058200596e6c0a5371c2c533eb97ba4c1c19b0521750a5624cb5d2e93249c8b7219d20029";
    String abi = "[{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"name\":"
        + "\"balances\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"st"
        + "ateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name"
        + "\":\"account\",\"type\":\"uint256\"}],\"name\":\"getCoin\",\"outputs\":[{\"name\":\"\""
        + ",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"funct"
        + "ion\"},{\"constant\":false,\"inputs\":[{\"name\":\"receiver\",\"type\":\"uint256\"},{\""
        + "name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"setCoin\",\"outputs\":[],\"payable"
        + "\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[],\"pay"
        + "able\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}]";
    CreateSmartContract smartContract = VvmTestUtils.createSmartContract(
            Objects.requireNonNull(Commons.decodeFromBase58Check(OwnerAddress)), contractName, abi, code, 0,
        100);
    Transaction transaction = Transaction.newBuilder().setRawData(raw.newBuilder().addContract(
        Contract.newBuilder().setParameter(Any.pack(smartContract))
            .setType(ContractType.CreateSmartContract)).setFeeLimit(1000000000)).build();

    deployInit(transaction);
  }

  @Test
  public void testUse(){
    String abi = "[{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"name\":"
            + "\"balances\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"st"
            + "ateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name"
            + "\":\"account\",\"type\":\"uint256\"}],\"name\":\"getCoin\",\"outputs\":[{\"name\":\"\""
            + ",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"funct"
            + "ion\"},{\"constant\":false,\"inputs\":[{\"name\":\"receiver\",\"type\":\"uint256\"},{\""
            + "name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"setCoin\",\"outputs\":[],\"payable"
            + "\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[],\"pay"
            + "able\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}]";
    System.out.println(abi);
  }

  @Test
  public void testUseUsage()
      throws VMIllegalException, BalanceInsufficientException,
      ContractValidateException, ContractExeException {

    AccountCapsule accountCapsule = new AccountCapsule(ByteString.copyFrom("owner".getBytes()),
        ByteString.copyFrom(Commons.decodeFromBase58Check(OwnerAddress)), AccountType.Normal,
        totalBalance);

    accountCapsule.setFrozenForEntropy(5_000_000_000L, 0L);
    dbManager.getAccountStore()
        .put(Commons.decodeFromBase58Check(OwnerAddress), accountCapsule);
    String contractName = "tracetestContract";
    String code = "608060405234801561001057600080fd5b5060005b6103e88110156100375760008181526020819"
        + "05260409020819055600a01610014565b5061010f806100476000396000f300608060405260043610605257"
        + "63ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416634903b"
        + "0d181146057578063da31158814607e578063fe4ba936146093575b600080fd5b348015606257600080fd5b"
        + "50606c60043560ad565b60408051918252519081900360200190f35b348015608957600080fd5b50606c600"
        + "43560bf565b348015609e57600080fd5b5060ab60043560243560d1565b005b600060208190529081526040"
        + "90205481565b60009081526020819052604090205490565b600091825260208290526040909120555600a16"
        + "5627a7a723058200596e6c0a5371c2c533eb97ba4c1c19b0521750a5624cb5d2e93249c8b7219d20029";
    String abi = "[{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"name\":"
        + "\"balances\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\""
        + "stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\""
        + "name\":\"account\",\"type\":\"uint256\"}],\"name\":\"getCoin\",\"outputs\":[{\"name\""
        + ":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":"
        + "\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"receiver\",\"type\":\""
        + "uint256\"},{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"setCoin\",\"outputs\""
        + ":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inpu"
        + "ts\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}]";
    CreateSmartContract smartContract = VvmTestUtils.createSmartContract(
        Commons.decodeFromBase58Check(OwnerAddress), contractName, abi, code, 0,
        100);
    Transaction transaction = Transaction.newBuilder().setRawData(raw.newBuilder().addContract(
        Contract.newBuilder().setParameter(Any.pack(smartContract))
            .setType(ContractType.CreateSmartContract)).setFeeLimit(1000000000)
        .setTimestamp(System.currentTimeMillis()))
        .build();

    TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
    TransactionTrace trace = new TransactionTrace(transactionCapsule, StoreFactory
        .getInstance(), new RuntimeImpl());

    trace.init(null);
    trace.exec();
    trace.pay();
    Assert.assertEquals(2050831L, trace.getReceipt().getEntropyUsage());
    Assert.assertEquals(0L, trace.getReceipt().getEntropyFee());
    Assert.assertEquals(205083100L,
        trace.getReceipt().getEntropyUsage() * 100 + trace.getReceipt().getEntropyFee());
    accountCapsule = dbManager.getAccountStore().get(accountCapsule.getAddress().toByteArray());
    Assert.assertEquals(totalBalance,
        accountCapsule.getBalance() + trace.getReceipt().getEntropyFee());

  }

  @Test
  public void testTriggerUseFee()
      throws InvalidProtocolBufferException, VMIllegalException, ContractExeException,
      ContractValidateException, BalanceInsufficientException {
    String contractName = "tracetestContract";
    String code = "608060405234801561001057600080fd5b5060005b6103e88110156100375760008181526020819"
        + "05260409020819055600a01610014565b5061010f806100476000396000f300608060405260043610605257"
        + "63ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416634903b"
        + "0d181146057578063da31158814607e578063fe4ba936146093575b600080fd5b348015606257600080fd5b"
        + "50606c60043560ad565b60408051918252519081900360200190f35b348015608957600080fd5b50606c600"
        + "43560bf565b348015609e57600080fd5b5060ab60043560243560d1565b005b600060208190529081526040"
        + "90205481565b60009081526020819052604090205490565b600091825260208290526040909120555600a16"
        + "5627a7a723058200596e6c0a5371c2c533eb97ba4c1c19b0521750a5624cb5d2e93249c8b7219d20029";
    String abi = "[{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"name\":"
        + "\"balances\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\""
        + "stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\""
        + "name\":\"account\",\"type\":\"uint256\"}],\"name\":\"getCoin\",\"outputs\":[{\"name\""
        + ":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":"
        + "\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"receiver\",\"type\":\""
        + "uint256\"},{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"setCoin\",\"outputs\""
        + ":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inpu"
        + "ts\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}]";
    CreateSmartContract smartContract = VvmTestUtils.createSmartContract(
        Commons.decodeFromBase58Check(OwnerAddress), contractName, abi, code, 0,
        100);
    Transaction transaction = Transaction.newBuilder().setRawData(raw.newBuilder().addContract(
        Contract.newBuilder().setParameter(Any.pack(smartContract))
            .setType(ContractType.CreateSmartContract)).setFeeLimit(1000000000)
        .setTimestamp(System.currentTimeMillis())).build();

    byte[] contractAddress = deployInit(transaction);
    AccountCapsule ownerCapsule = new AccountCapsule(ByteString.copyFrom("owner".getBytes()),
        ByteString.copyFrom(Commons.decodeFromBase58Check(TriggerOwnerAddress)), AccountType.Normal,
        totalBalance);
    AccountCapsule originCapsule = new AccountCapsule(ByteString.copyFrom("origin".getBytes()),
        ByteString.copyFrom(Commons.decodeFromBase58Check(OwnerAddress)), AccountType.Normal,
        totalBalance);
    ownerCapsule.setFrozenForEntropy(5_000_000_000L, 0L);
    originCapsule.setFrozenForEntropy(5_000_000_000L, 0L);
    dbManager.getAccountStore()
        .put(Commons.decodeFromBase58Check(TriggerOwnerAddress), ownerCapsule);
    dbManager.getAccountStore()
        .put(Commons.decodeFromBase58Check(TriggerOwnerAddress), originCapsule);
    TriggerSmartContract triggerContract = VvmTestUtils.createTriggerContract(contractAddress,
        "setCoin(uint256,uint256)", "133,133", false,
        0, Commons.decodeFromBase58Check(TriggerOwnerAddress));
    Transaction transaction2 = Transaction.newBuilder().setRawData(raw.newBuilder().addContract(
        Contract.newBuilder().setParameter(Any.pack(triggerContract))
            .setType(ContractType.TriggerSmartContract)).setFeeLimit(1000000000L)).build();
    TransactionCapsule transactionCapsule = new TransactionCapsule(transaction2);
    TransactionTrace trace = new TransactionTrace(transactionCapsule, StoreFactory
        .getInstance(), new RuntimeImpl());
    trace.init(null);
    trace.exec();
    trace.pay();
    Assert.assertEquals(20252, trace.getReceipt().getEntropyUsage());
    Assert.assertEquals(0, trace.getReceipt().getEntropyFee());
    ownerCapsule = dbManager.getAccountStore().get(ownerCapsule.getAddress().toByteArray());
    Assert.assertEquals(totalBalance,
        trace.getReceipt().getEntropyFee() + ownerCapsule
            .getBalance());
  }

  @Test
  public void testTriggerUseUsage()
      throws VMIllegalException, ContractExeException,
      ContractValidateException, BalanceInsufficientException {
    String contractName = "tracetestContract";
    String code = "608060405234801561001057600080fd5b5060005b6103e88110156100375760008181526020819"
        + "05260409020819055600a01610014565b5061010f806100476000396000f300608060405260043610605257"
        + "63ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416634903b"
        + "0d181146057578063da31158814607e578063fe4ba936146093575b600080fd5b348015606257600080fd5b"
        + "50606c60043560ad565b60408051918252519081900360200190f35b348015608957600080fd5b50606c600"
        + "43560bf565b348015609e57600080fd5b5060ab60043560243560d1565b005b600060208190529081526040"
        + "90205481565b60009081526020819052604090205490565b600091825260208290526040909120555600a16"
        + "5627a7a723058200596e6c0a5371c2c533eb97ba4c1c19b0521750a5624cb5d2e93249c8b7219d20029";
    String abi = "[{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"name\":"
        + "\"balances\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"st"
        + "ateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name"
        + "\":\"account\",\"type\":\"uint256\"}],\"name\":\"getCoin\",\"outputs\":[{\"name\":\"\""
        + ",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"func"
        + "tion\"},{\"constant\":false,\"inputs\":[{\"name\":\"receiver\",\"type\":\"uint256\"},{"
        + "\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"setCoin\",\"outputs\":[],\"payab"
        + "le\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[],\""
        + "payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}]";
    CreateSmartContract smartContract = VvmTestUtils.createSmartContract(
        Commons.decodeFromBase58Check(OwnerAddress), contractName, abi, code, 0,
        100);
    Transaction transaction = Transaction.newBuilder().setRawData(raw.newBuilder().addContract(
        Contract.newBuilder().setParameter(Any.pack(smartContract))
            .setType(ContractType.CreateSmartContract)).setFeeLimit(1000000000)
        .setTimestamp(System.currentTimeMillis()))
        .build();

    byte[] contractAddress = deployInit(transaction);
    AccountCapsule accountCapsule = new AccountCapsule(ByteString.copyFrom("owner".getBytes()),
        ByteString.copyFrom(Commons.decodeFromBase58Check(TriggerOwnerAddress)),
        AccountType.Normal,
        totalBalance);

    accountCapsule.setFrozenForEntropy(10_000_000L, 0L);
    dbManager.getAccountStore()
        .put(Commons.decodeFromBase58Check(TriggerOwnerAddress), accountCapsule);
    TriggerSmartContract triggerContract = VvmTestUtils.createTriggerContract(contractAddress,
        "setCoin(uint256,uint256)", "133,133", false,
        0, Commons.decodeFromBase58Check(TriggerOwnerAddress));
    Transaction transaction2 = Transaction.newBuilder().setRawData(raw.newBuilder().addContract(
        Contract.newBuilder().setParameter(Any.pack(triggerContract))
            .setType(ContractType.TriggerSmartContract)).setFeeLimit(1000000000L)).build();
    TransactionCapsule transactionCapsule = new TransactionCapsule(transaction2);
    TransactionTrace trace = new TransactionTrace(transactionCapsule, StoreFactory
        .getInstance(), new RuntimeImpl());
    trace.init(null);
    trace.exec();
    trace.pay();
    Assert.assertEquals(20252, trace.getReceipt().getEntropyUsage());
    Assert.assertEquals(0, trace.getReceipt().getEntropyFee());
    Assert.assertEquals(2025200,
        trace.getReceipt().getEntropyUsage() * 100 + trace.getReceipt().getEntropyFee());
    accountCapsule = dbManager.getAccountStore().get(accountCapsule.getAddress().toByteArray());
    Assert.assertEquals(totalBalance,
        accountCapsule.getBalance() + trace.getReceipt().getEntropyFee());

  }

  private byte[] deployInit(Transaction transaction)
      throws VMIllegalException, ContractExeException,
      ContractValidateException, BalanceInsufficientException {

    AccountCapsule accountCapsule = new AccountCapsule(ByteString.copyFrom("owner".getBytes()),
        ByteString.copyFrom(Commons.decodeFromBase58Check(OwnerAddress)), AccountType.Normal,
        totalBalance);
    dbManager.getAccountStore()
        .put(Commons.decodeFromBase58Check(OwnerAddress), accountCapsule);

    TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
    TransactionTrace trace = new TransactionTrace(transactionCapsule, StoreFactory
        .getInstance(), new RuntimeImpl());
    trace.init(null);
    trace.exec();
    trace.pay();
    Assert.assertEquals(0, trace.getReceipt().getEntropyUsage());
    Assert.assertEquals(10254155L, trace.getReceipt().getEntropyFee());
    accountCapsule = dbManager.getAccountStore().get(accountCapsule.getAddress().toByteArray());
    Assert.assertEquals(totalBalance,
        trace.getReceipt().getEntropyFee() + accountCapsule
            .getBalance());
    return trace.getRuntime().getResult().getContractAddress();

  }

  @Test
  public void testPay() throws BalanceInsufficientException {
    Account account = Account.newBuilder()
        .setAddress(ownerAddress)
        .setBalance(1000000)
        .setAccountResource(
            AccountResource.newBuilder()
                .setEntropyUsage(1111111L)
                .setFrozenBalanceForEntropy(
                    Frozen.newBuilder()
                        .setExpireTime(100000)
                        .setFrozenBalance(100000)
                        .build())
                .build()).build();

    AccountCapsule accountCapsule = new AccountCapsule(account);
    dbManager.getAccountStore().put(accountCapsule.getAddress().toByteArray(), accountCapsule);
    TriggerSmartContract contract = TriggerSmartContract.newBuilder()
        .setContractAddress(contractAddress)
        .setOwnerAddress(ownerAddress)
        .build();

    SmartContract smartContract = SmartContract.newBuilder()
        .setOriginAddress(ownerAddress)
        .setContractAddress(contractAddress)
        .build();

    CreateSmartContract createSmartContract = CreateSmartContract.newBuilder()
        .setOwnerAddress(ownerAddress)
        .setNewContract(smartContract)
        .build();

    Transaction transaction = Transaction.newBuilder()
        .setRawData(
            raw.newBuilder()
                .addContract(
                    Contract.newBuilder()
                        .setParameter(Any.pack(contract))
                        .setType(ContractType.TriggerSmartContract)
                        .build())
                .build()
        )
        .build();

    dbManager.getContractStore().put(
        contractAddress.toByteArray(),
        new ContractCapsule(smartContract));

    TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
    TransactionTrace transactionTrace = new TransactionTrace(transactionCapsule, StoreFactory
        .getInstance(), new RuntimeImpl());
    transactionTrace.setBill(0L);
    transactionTrace.pay();
    AccountCapsule accountCapsule1 = dbManager.getAccountStore().get(ownerAddress.toByteArray());
  }
}
