package org.vision.core.actuator;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.apache.commons.lang3.ArrayUtils.getLength;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
import static org.vision.core.vm.utils.MUtil.transfer;
import static org.vision.core.vm.utils.MUtil.transferToken;

import com.google.protobuf.ByteString;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.vision.common.logsfilter.trigger.ContractTrigger;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.runtime.InternalTransaction;
import org.vision.common.runtime.InternalTransaction.ExecutorType;
import org.vision.common.runtime.InternalTransaction.TrxType;
import org.vision.common.runtime.ProgramResult;
import org.vision.common.utils.StorageUtils;
import org.vision.common.utils.StringUtil;
import org.vision.common.utils.WalletUtil;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.capsule.ContractCapsule;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.db.TransactionContext;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.utils.TransactionUtil;
import org.vision.core.vm.EntropyCost;
import org.vision.core.vm.LogInfoTriggerParser;
import org.vision.core.vm.VM;
import org.vision.core.vm.VMConstant;
import org.vision.core.vm.VMUtils;
import org.vision.core.vm.config.ConfigLoader;
import org.vision.core.vm.config.VMConfig;
import org.vision.core.vm.program.Program;
import org.vision.core.vm.program.Program.JVMStackOverFlowException;
import org.vision.core.vm.program.Program.OutOfTimeException;
import org.vision.core.vm.program.Program.TransferException;
import org.vision.core.vm.program.ProgramPrecompile;
import org.vision.core.vm.program.invoke.ProgramInvoke;
import org.vision.core.vm.program.invoke.ProgramInvokeFactory;
import org.vision.core.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.vision.core.vm.repository.Repository;
import org.vision.core.vm.repository.RepositoryImpl;
import org.vision.protos.Protocol;
import org.vision.protos.Protocol.Block;
import org.vision.protos.Protocol.Transaction;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result.contractResult;
import org.vision.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;
import org.vision.protos.contract.SmartContractOuterClass.TriggerSmartContract;

@Slf4j(topic = "VM")
public class VMActuator implements Actuator2 {

  private Transaction trx;
  private BlockCapsule blockCap;
  private Repository repository;
  private InternalTransaction rootInternalTransaction;
  private ProgramInvokeFactory programInvokeFactory;


  private VM vm;
  private Program program;
  private VMConfig vmConfig = VMConfig.getInstance();

  @Getter
  @Setter
  private InternalTransaction.TrxType trxType;
  private ExecutorType executorType;

  @Getter
  @Setter
  private boolean isConstantCall = false;

  @Setter
  private boolean enableEventListener;

  private LogInfoTriggerParser logInfoTriggerParser;

  public VMActuator(boolean isConstantCall) {
    this.isConstantCall = isConstantCall;
    programInvokeFactory = new ProgramInvokeFactoryImpl();
  }

  private static long getEntropyFee(long callerEntropyUsage, long callerEntropyFrozen,
                                    long callerEntropyTotal) {
    if (callerEntropyTotal <= 0) {
      return 0;
    }
    return BigInteger.valueOf(callerEntropyFrozen).multiply(BigInteger.valueOf(callerEntropyUsage))
        .divide(BigInteger.valueOf(callerEntropyTotal)).longValueExact();
  }

  @Override
  public void validate(Object object) throws ContractValidateException {

    TransactionContext context = (TransactionContext) object;
    if (Objects.isNull(context)) {
      throw new RuntimeException("TransactionContext is null");
    }

    //Load Config
    ConfigLoader.load(context.getStoreFactory());
    trx = context.getTrxCap().getInstance();
    blockCap = context.getBlockCap();
    //Route Type
    ContractType contractType = this.trx.getRawData().getContract(0).getType();
    //Prepare Repository
    repository = RepositoryImpl.createRoot(context.getStoreFactory());

    enableEventListener = context.isEventPluginLoaded();

    //set executorType type
    if (Objects.nonNull(blockCap)) {
      this.executorType = ExecutorType.ET_NORMAL_TYPE;
    } else {
      this.blockCap = new BlockCapsule(Block.newBuilder().build());
      this.executorType = ExecutorType.ET_PRE_TYPE;
    }
    if (isConstantCall) {
      this.executorType = ExecutorType.ET_PRE_TYPE;
    }

    switch (contractType.getNumber()) {
      case ContractType.TriggerSmartContract_VALUE:
        trxType = TrxType.TRX_CONTRACT_CALL_TYPE;
        call();
        break;
      case ContractType.CreateSmartContract_VALUE:
        trxType = TrxType.TRX_CONTRACT_CREATION_TYPE;
        create();
        break;
      default:
        throw new ContractValidateException("Unknown contract type");
    }
  }

  @Override
  public void execute(Object object) throws ContractExeException {
    TransactionContext context = (TransactionContext) object;
    if (Objects.isNull(context)) {
      throw new RuntimeException("TransactionContext is null");
    }

    ProgramResult result = context.getProgramResult();
    try {
      if (vm != null) {
        if (null != blockCap && blockCap.generatedByMyself && blockCap.hasWitnessSignature()
            && null != TransactionUtil.getContractRet(trx)
            && contractResult.OUT_OF_TIME == TransactionUtil.getContractRet(trx)) {
          result = program.getResult();
          program.spendAllEntropy();

          OutOfTimeException e = Program.Exception.alreadyTimeOut();
          result.setRuntimeError(e.getMessage());
          result.setException(e);
          throw e;
        }

        vm.play(program);
        result = program.getResult();

        if (isConstantCall) {
          long callValue = TransactionCapsule.getCallValue(trx.getRawData().getContract(0));
          long callTokenValue = TransactionUtil
              .getCallTokenValue(trx.getRawData().getContract(0));
          if (callValue > 0 || callTokenValue > 0) {
            result.setRuntimeError("constant cannot set call value or call token value.");
            result.rejectInternalTransactions();
          }
          if (result.getException() != null) {
            result.setRuntimeError(result.getException().getMessage());
            result.rejectInternalTransactions();
          }
          context.setProgramResult(result);
          return;
        }

        if (TrxType.TRX_CONTRACT_CREATION_TYPE == trxType && !result.isRevert()) {
          byte[] code = program.getResult().getHReturn();
          long saveCodeEntropy = (long) getLength(code) * EntropyCost.getInstance().getCREATE_DATA();
          long afterSpend = program.getEntropyLimitLeft().longValue() - saveCodeEntropy;
          if (afterSpend < 0) {
            if (null == result.getException()) {
              result.setException(Program.Exception
                  .notEnoughSpendEntropy("save just created contract code",
                      saveCodeEntropy, program.getEntropyLimitLeft().longValue()));
            }
          } else {
            result.spendEntropy(saveCodeEntropy);
            if (VMConfig.allowVvmConstantinople()) {
              repository.saveCode(program.getContractAddress().getNoLeadZeroesData(), code);
            }
          }
        }

        if (result.getException() != null || result.isRevert()) {
          result.getDeleteAccounts().clear();
          result.getLogInfoList().clear();
          result.resetFutureRefund();
          result.rejectInternalTransactions();
          result.getDeleteVotes().clear();
          result.getDeleteDelegation().clear();

          if (result.getException() != null) {
            if (!(result.getException() instanceof TransferException)) {
              program.spendAllEntropy();
            }
            result.setRuntimeError(result.getException().getMessage());
            throw result.getException();
          } else {
            result.setRuntimeError("REVERT opcode executed");
          }
        } else {
          repository.commit();

          if (logInfoTriggerParser != null) {
            List<ContractTrigger> triggers = logInfoTriggerParser
                .parseLogInfos(program.getResult().getLogInfoList(), repository);
            program.getResult().setTriggerList(triggers);
          }

        }
      } else {
        repository.commit();
      }
    } catch (JVMStackOverFlowException e) {
      program.spendAllEntropy();
      result = program.getResult();
      result.setException(e);
      result.rejectInternalTransactions();
      result.setRuntimeError(result.getException().getMessage());
      logger.info("JVMStackOverFlowException: {}", result.getException().getMessage());
    } catch (OutOfTimeException e) {
      program.spendAllEntropy();
      result = program.getResult();
      result.setException(e);
      result.rejectInternalTransactions();
      result.setRuntimeError(result.getException().getMessage());
      logger.info("timeout: {}", result.getException().getMessage());
    } catch (Throwable e) {
      if (!(e instanceof TransferException)) {
        program.spendAllEntropy();
      }
      result = program.getResult();
      result.rejectInternalTransactions();
      if (Objects.isNull(result.getException())) {
        logger.error(e.getMessage(), e);
        result.setException(new RuntimeException("Unknown Throwable"));
      }
      if (StringUtils.isEmpty(result.getRuntimeError())) {
        result.setRuntimeError(result.getException().getMessage());
      }
      logger.info("runtime result is :{}", result.getException().getMessage());
    }
    //use program returned fill context
    context.setProgramResult(result);

    if (VMConfig.vmTrace() && program != null) {
      String traceContent = program.getTrace()
          .result(result.getHReturn())
          .error(result.getException())
          .toString();

      if (VMConfig.vmTraceCompressed()) {
        traceContent = VMUtils.zipAndEncode(traceContent);
      }

      String txHash = Hex.toHexString(rootInternalTransaction.getHash());
      VMUtils.saveProgramTraceFile(txHash, traceContent);
    }

  }

  private void create()
          throws ContractValidateException {
    if (!repository.getDynamicPropertiesStore().supportVM()) {
      throw new ContractValidateException("vm work is off, need to be opened by the committee");
    }

    CreateSmartContract contract = ContractCapsule.getSmartContractFromTransaction(trx);
    if (contract == null) {
      throw new ContractValidateException("Cannot get CreateSmartContract from transaction");
    }

    if(contract.getType() == 1 &&
            repository.getDynamicPropertiesStore().getAllowEthereumCompatibleTransaction() == 0){
      throw new ContractValidateException("EthereumCompatibleTransaction is off, need to be opened by proposal");
    }

    SmartContract newSmartContract = contract.getNewContract();
    if (!contract.getOwnerAddress().equals(newSmartContract.getOriginAddress())) {
      logger.info("OwnerAddress not equals OriginAddress");
      throw new ContractValidateException("OwnerAddress is not equals OriginAddress");
    }

    byte[] contractName = newSmartContract.getName().getBytes();

    if (contractName.length > VMConstant.CONTRACT_NAME_LENGTH) {
      throw new ContractValidateException("contractName's length cannot be greater than 32");
    }

    long percent = contract.getNewContract().getConsumeUserResourcePercent();
    if (percent < 0 || percent > VMConstant.ONE_HUNDRED) {
      throw new ContractValidateException("percent must be >= 0 and <= 100");
    }

    byte[] contractAddress = WalletUtil.generateContractAddress(trx);
    // insure the new contract address haven't exist
    if (repository.getAccount(contractAddress) != null) {
      throw new ContractValidateException(
              "Trying to create a contract with existing contract address: " + StringUtil
                      .encode58Check(contractAddress));
    }

    newSmartContract = newSmartContract.toBuilder()
            .setContractAddress(ByteString.copyFrom(contractAddress)).build();
    long callValue = newSmartContract.getCallValue();
    long tokenValue = 0;
    long tokenId = 0;
    if (VMConfig.allowVvmTransferVrc10()) {
      tokenValue = contract.getCallTokenValue();
      tokenId = contract.getTokenId();
    }
    byte[] callerAddress = contract.getOwnerAddress().toByteArray();
    // create vm to constructor smart contract
    try {
      long feeLimit = trx.getRawData().getFeeLimit();
      if (feeLimit < 0 || feeLimit > repository.getDynamicPropertiesStore().getMaxFeeLimit()) {
        logger.info("invalid feeLimit {}", feeLimit);
        throw new ContractValidateException(
                "feeLimit must be >= 0 and <= " + repository.getDynamicPropertiesStore().getMaxFeeLimit());
      }
      AccountCapsule creator = this.repository
              .getAccount(newSmartContract.getOriginAddress().toByteArray());

      long entropyLimit;
      // according to version

      if (StorageUtils.getEntropyLimitHardFork()) {
        if (callValue < 0) {
          throw new ContractValidateException("callValue must be >= 0");
        }
        if (tokenValue < 0) {
          throw new ContractValidateException("tokenValue must be >= 0");
        }
        if (newSmartContract.getOriginEntropyLimit() <= 0) {
          throw new ContractValidateException("The originEntropyLimit must be > 0");
        }
        entropyLimit = getAccountEntropyLimitWithFixRatio(creator, feeLimit, callValue);
      } else {
        entropyLimit = getAccountEntropyLimitWithFloatRatio(creator, feeLimit, callValue);
      }

      checkTokenValueAndId(tokenValue, tokenId);

      byte[] ops = newSmartContract.getBytecode().toByteArray();
      rootInternalTransaction = new InternalTransaction(trx, trxType);

      long maxCpuTimeOfOneTx = repository.getDynamicPropertiesStore()
              .getMaxCpuTimeOfOneTx() * VMConstant.ONE_THOUSAND;
      long thisTxCPULimitInUs = (long) (maxCpuTimeOfOneTx * getCpuLimitInUsRatio());
      long vmStartInUs = System.nanoTime() / VMConstant.ONE_THOUSAND;
      long vmShouldEndInUs = vmStartInUs + thisTxCPULimitInUs;
      ProgramInvoke programInvoke = programInvokeFactory
          .createProgramInvoke(TrxType.TRX_CONTRACT_CREATION_TYPE, executorType, trx,
                      tokenValue, tokenId, blockCap.getInstance(), repository, vmStartInUs,
                      vmShouldEndInUs, entropyLimit);
      this.vm = new VM();
      this.program = new Program(ops, programInvoke, rootInternalTransaction, vmConfig
      );
      byte[] txId = TransactionUtil.getTransactionId(trx).getBytes();
      this.program.setRootTransactionId(txId);
      if (enableEventListener && isCheckTransaction()) {
        logInfoTriggerParser = new LogInfoTriggerParser(blockCap.getNum(), blockCap.getTimeStamp(),
                txId, callerAddress);
      }
    } catch (Exception e) {
      logger.info(e.getMessage());
      throw new ContractValidateException(e.getMessage());
    }
    program.getResult().setContractAddress(contractAddress);

    repository.createAccount(contractAddress, newSmartContract.getName(),
            Protocol.AccountType.Contract);

    repository.createContract(contractAddress, new ContractCapsule(newSmartContract));
    byte[] code = newSmartContract.getBytecode().toByteArray();
    if (!VMConfig.allowVvmConstantinople()) {
      repository.saveCode(contractAddress, ProgramPrecompile.getCode(code));
    }
    // transfer from callerAddress to contractAddress according to callValue
    if (callValue > 0) {
      transfer(this.repository, callerAddress, contractAddress, callValue);
    }
    if (VMConfig.allowVvmTransferVrc10() && tokenValue > 0) {
      transferToken(this.repository, callerAddress, contractAddress, String.valueOf(tokenId),
              tokenValue);
    }

  }

 
  private void call()
      throws ContractValidateException {

    if (!repository.getDynamicPropertiesStore().supportVM()) {
      logger.info("vm work is off, need to be opened by the committee");
      throw new ContractValidateException("VM work is off, need to be opened by the committee");
    }

    TriggerSmartContract contract = ContractCapsule.getTriggerContractFromTransaction(trx);
    if (contract == null) {
      return;
    }

    if(contract.getType() == 1 &&
            repository.getDynamicPropertiesStore().getAllowEthereumCompatibleTransaction() == 0){
      throw new ContractValidateException("EthereumCompatibleTransaction is off, need to be opened by proposal");
    }

    if (contract.getContractAddress() == null) {
      throw new ContractValidateException("Cannot get contract address from TriggerContract");
    }

    byte[] contractAddress = contract.getContractAddress().toByteArray();

    ContractCapsule deployedContract = repository.getContract(contractAddress);
    if (null == deployedContract) {
      logger.info("No contract or not a smart contract");
      throw new ContractValidateException("No contract or not a smart contract");
    }

    long callValue = contract.getCallValue();
    long tokenValue = 0;
    long tokenId = 0;
    if (VMConfig.allowVvmTransferVrc10()) {
      tokenValue = contract.getCallTokenValue();
      tokenId = contract.getTokenId();
    }

    if (StorageUtils.getEntropyLimitHardFork()) {
      if (callValue < 0) {
        throw new ContractValidateException("callValue must be >= 0");
      }
      if (tokenValue < 0) {
        throw new ContractValidateException("tokenValue must be >= 0");
      }
    }

    byte[] callerAddress = contract.getOwnerAddress().toByteArray();
    checkTokenValueAndId(tokenValue, tokenId);

    byte[] code = repository.getCode(contractAddress);
    if (isNotEmpty(code)) {

      long feeLimit = trx.getRawData().getFeeLimit();
      if (feeLimit < 0 || feeLimit > repository.getDynamicPropertiesStore().getMaxFeeLimit()) {
        logger.info("invalid feeLimit {}", feeLimit);
        throw new ContractValidateException(
                "feeLimit must be >= 0 and <= " + repository.getDynamicPropertiesStore().getMaxFeeLimit());
      }
      AccountCapsule caller = repository.getAccount(callerAddress);
      long entropyLimit;
      if (isConstantCall) {
        entropyLimit = VMConstant.ENTROPY_LIMIT_IN_CONSTANT_TX;
      } else {
        AccountCapsule creator = repository
            .getAccount(deployedContract.getInstance().getOriginAddress().toByteArray());
        entropyLimit = getTotalEntropyLimit(creator, caller, contract, feeLimit, callValue);
      }

      long maxCpuTimeOfOneTx = repository.getDynamicPropertiesStore()
          .getMaxCpuTimeOfOneTx() * VMConstant.ONE_THOUSAND;
      long thisTxCPULimitInUs =
          (long) (maxCpuTimeOfOneTx * getCpuLimitInUsRatio());
      long vmStartInUs = System.nanoTime() / VMConstant.ONE_THOUSAND;
      long vmShouldEndInUs = vmStartInUs + thisTxCPULimitInUs;
      ProgramInvoke programInvoke = programInvokeFactory
          .createProgramInvoke(TrxType.TRX_CONTRACT_CALL_TYPE, executorType, trx,
              tokenValue, tokenId, blockCap.getInstance(), repository, vmStartInUs,
              vmShouldEndInUs, entropyLimit);
      if (isConstantCall) {
        programInvoke.setConstantCall();
      }
      this.vm = new VM();
      rootInternalTransaction = new InternalTransaction(trx, trxType);
      this.program = new Program(code, programInvoke, rootInternalTransaction, vmConfig);
      byte[] txId = TransactionUtil.getTransactionId(trx).getBytes();
      this.program.setRootTransactionId(txId);

      if (enableEventListener && isCheckTransaction()) {
        logInfoTriggerParser = new LogInfoTriggerParser(blockCap.getNum(), blockCap.getTimeStamp(),
            txId, callerAddress);
      }
    }

    program.getResult().setContractAddress(contractAddress);
    //transfer from callerAddress to targetAddress according to callValue

    if (callValue > 0) {
      transfer(this.repository, callerAddress, contractAddress, callValue);
    }
    if (VMConfig.allowVvmTransferVrc10() && tokenValue > 0) {
      transferToken(this.repository, callerAddress, contractAddress, String.valueOf(tokenId),
          tokenValue);
    }

  }

  public long getAccountEntropyLimitWithFixRatio(AccountCapsule account, long feeLimit,
                                                 long callValue) {

    long vdtPerEntropy = VMConstant.VDT_PER_ENTROPY;
    if (repository.getDynamicPropertiesStore().getEntropyFee() > 0) {
      vdtPerEntropy = repository.getDynamicPropertiesStore().getEntropyFee();
    }

    long leftFrozenEntropy = repository.getAccountLeftEntropyFromFreeze(account);

    long entropyFromBalance = max(account.getBalance() - callValue, 0) / vdtPerEntropy;
    long availableEntropy = Math.addExact(leftFrozenEntropy, entropyFromBalance);

    long entropyFromFeeLimit = feeLimit / vdtPerEntropy;
    return min(availableEntropy, entropyFromFeeLimit);

  }
  private long getAccountEntropyLimitWithFloatRatio(AccountCapsule account, long feeLimit,
                                                    long callValue) {

    long vdtPerEntropy = VMConstant.VDT_PER_ENTROPY;
    if (repository.getDynamicPropertiesStore().getEntropyFee() > 0) {
      vdtPerEntropy = repository.getDynamicPropertiesStore().getEntropyFee();
    }
    // can change the calc way
    long leftEntropyFromFreeze = repository.getAccountLeftEntropyFromFreeze(account);
    callValue = max(callValue, 0);
    long entropyFromBalance = Math
            .floorDiv(max(account.getBalance() - callValue, 0), vdtPerEntropy);

    long entropyFromFeeLimit;
    long totalBalanceForEntropyFreeze = account.getAllFrozenBalanceForEntropy();
    if (0 == totalBalanceForEntropyFreeze) {
      entropyFromFeeLimit =
              feeLimit / vdtPerEntropy;
    } else {
      long totalEntropyFromFreeze = repository
              .calculateGlobalEntropyLimit(account);
      long leftBalanceForEntropyFreeze = getEntropyFee(totalBalanceForEntropyFreeze,
              leftEntropyFromFreeze,
              totalEntropyFromFreeze);

      if (leftBalanceForEntropyFreeze >= feeLimit) {
        entropyFromFeeLimit = BigInteger.valueOf(totalEntropyFromFreeze)
                .multiply(BigInteger.valueOf(feeLimit))
                .divide(BigInteger.valueOf(totalBalanceForEntropyFreeze)).longValueExact();
      } else {
        entropyFromFeeLimit = Math
                .addExact(leftEntropyFromFreeze,
                        (feeLimit - leftBalanceForEntropyFreeze) / vdtPerEntropy);
      }
    }

    return min(Math.addExact(leftEntropyFromFreeze, entropyFromBalance), entropyFromFeeLimit);
  }

 


  public long getTotalEntropyLimit(AccountCapsule creator, AccountCapsule caller,
                                   TriggerSmartContract contract, long feeLimit, long callValue)
      throws ContractValidateException {
    if (Objects.isNull(creator) && VMConfig.allowVvmConstantinople()) {
      return getAccountEntropyLimitWithFixRatio(caller, feeLimit, callValue);
    }
    //  according to version
    if (StorageUtils.getEntropyLimitHardFork()) {
      return getTotalEntropyLimitWithFixRatio(creator, caller, contract, feeLimit, callValue);
    } else {
      return getTotalEntropyLimitWithFloatRatio(creator, caller, contract, feeLimit, callValue);
    }
  }


  public void checkTokenValueAndId(long tokenValue, long tokenId) throws ContractValidateException {
    if (VMConfig.allowVvmTransferVrc10() && VMConfig.allowMultiSign()) {
      // tokenid can only be 0
      // or (MIN_TOKEN_ID, Long.Max]
      if (tokenId <= VMConstant.MIN_TOKEN_ID && tokenId != 0) {
        throw new ContractValidateException("tokenId must be > " + VMConstant.MIN_TOKEN_ID);
      }
      // tokenid can only be 0 when tokenvalue = 0,
      // or (MIN_TOKEN_ID, Long.Max]
      if (tokenValue > 0 && tokenId == 0) {
        throw new ContractValidateException("invalid arguments with tokenValue = " + tokenValue +
            ", tokenId = " + tokenId);
      }
    }
  }


  private double getCpuLimitInUsRatio() {

    double cpuLimitRatio;

    if (ExecutorType.ET_NORMAL_TYPE == executorType) {
      // self witness generates block
      if (this.blockCap != null && blockCap.generatedByMyself &&
          !this.blockCap.hasWitnessSignature()) {
        cpuLimitRatio = 1.0;
      } else {
        // self witness or other witness or fullnode verifies block
        if (trx.getRet(0).getContractRet() == contractResult.OUT_OF_TIME) {
          cpuLimitRatio = CommonParameter.getInstance().getMinTimeRatio();
        } else {
          cpuLimitRatio = CommonParameter.getInstance().getMaxTimeRatio();
        }
      }
    } else {
      // self witness or other witness or fullnode receives tx
      cpuLimitRatio = 1.0;
    }

    return cpuLimitRatio;
  }

  public long getTotalEntropyLimitWithFixRatio(AccountCapsule creator, AccountCapsule caller,
                                               TriggerSmartContract contract, long feeLimit, long callValue)
      throws ContractValidateException {

    long callerEntropyLimit = getAccountEntropyLimitWithFixRatio(caller, feeLimit, callValue);
    if (Arrays.equals(creator.getAddress().toByteArray(), caller.getAddress().toByteArray())) {
      // when the creator calls his own contract, this logic will be used.
      // so, the creator must use a BIG feeLimit to call his own contract,
      // which will cost the feeLimit VS when the creator's frozen entropy is 0.
      return callerEntropyLimit;
    }

    long creatorEntropyLimit = 0;
    ContractCapsule contractCapsule = repository
        .getContract(contract.getContractAddress().toByteArray());
    long consumeUserResourcePercent = contractCapsule.getConsumeUserResourcePercent();

    long originEntropyLimit = contractCapsule.getOriginEntropyLimit();
    if (originEntropyLimit < 0) {
      throw new ContractValidateException("originEntropyLimit can't be < 0");
    }

    if (consumeUserResourcePercent <= 0) {
      creatorEntropyLimit = min(repository.getAccountLeftEntropyFromFreeze(creator),
          originEntropyLimit);
    } else {
      if (consumeUserResourcePercent < VMConstant.ONE_HUNDRED) {
        // creatorEntropyLimit =
        // min(callerEntropyLimit * (100 - percent) / percent, creatorLeftFrozenEntropy, originEntropyLimit)

        creatorEntropyLimit = min(
            BigInteger.valueOf(callerEntropyLimit)
                .multiply(BigInteger.valueOf(VMConstant.ONE_HUNDRED - consumeUserResourcePercent))
                .divide(BigInteger.valueOf(consumeUserResourcePercent)).longValueExact(),
            min(repository.getAccountLeftEntropyFromFreeze(creator), originEntropyLimit)
        );
      }
    }
    return Math.addExact(callerEntropyLimit, creatorEntropyLimit);
  }

  private long getTotalEntropyLimitWithFloatRatio(AccountCapsule creator, AccountCapsule caller,
                                                  TriggerSmartContract contract, long feeLimit, long callValue) {

    long callerEntropyLimit = getAccountEntropyLimitWithFloatRatio(caller, feeLimit, callValue);
    if (Arrays.equals(creator.getAddress().toByteArray(), caller.getAddress().toByteArray())) {
      return callerEntropyLimit;
    }

    // creatorEntropyFromFreeze
    long creatorEntropyLimit = repository.getAccountLeftEntropyFromFreeze(creator);

    ContractCapsule contractCapsule = repository
        .getContract(contract.getContractAddress().toByteArray());
    long consumeUserResourcePercent = contractCapsule.getConsumeUserResourcePercent();

    if (creatorEntropyLimit * consumeUserResourcePercent
        > (VMConstant.ONE_HUNDRED - consumeUserResourcePercent) * callerEntropyLimit) {
      return Math.floorDiv(callerEntropyLimit * VMConstant.ONE_HUNDRED, consumeUserResourcePercent);
    } else {
      return Math.addExact(callerEntropyLimit, creatorEntropyLimit);
    }
  }

  private boolean isCheckTransaction() {
    return this.blockCap != null && !this.blockCap.getInstance().getBlockHeader()
        .getWitnessSignature().isEmpty();
  }


}
