package org.vision.core;

import com.google.protobuf.ByteString;
import java.io.File;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.vision.api.GrpcAPI;
import org.vision.api.GrpcAPI.BytesMessage;
import org.vision.api.GrpcAPI.PrivateShieldedVRC20Parameters;
import org.vision.api.GrpcAPI.PrivateShieldedVRC20ParametersWithoutAsk;
import org.vision.api.GrpcAPI.ShieldedVRC20Parameters;
import org.vision.api.GrpcAPI.ShieldedVRC20TriggerContractParameters;
import org.vision.api.GrpcAPI.SpendAuthSigParameters;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.ByteUtil;
import org.vision.common.utils.FileUtil;
import org.vision.common.zksnark.IncrementalMerkleTreeContainer;
import org.vision.common.zksnark.IncrementalMerkleVoucherContainer;
import org.vision.common.zksnark.JLibrustzcash;
import org.vision.common.zksnark.LibrustzcashParam;
import org.vision.core.capsule.IncrementalMerkleTreeCapsule;
import org.vision.core.capsule.PedersenHashCapsule;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.args.Args;
import org.vision.core.db.BlockGenerate;
import org.vision.core.db.Manager;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.exception.ZksnarkException;
import org.vision.core.services.http.FullNodeHttpApiService;
import org.vision.core.vm.PrecompiledContracts;
import org.vision.core.zen.note.Note;
import org.vision.core.zksnark.LibrustzcashTest;
import org.vision.common.application.VisionApplicationContext;
import org.vision.core.zen.address.DiversifierT;
import org.vision.core.zen.address.ExpandedSpendingKey;
import org.vision.core.zen.address.FullViewingKey;
import org.vision.core.zen.address.IncomingViewingKey;
import org.vision.core.zen.address.KeyIo;
import org.vision.core.zen.address.PaymentAddress;
import org.vision.core.zen.address.SpendingKey;
import org.vision.protos.contract.ShieldContract;
import org.vision.protos.contract.ShieldContract.SpendDescription;
import stest.vision.wallet.common.client.WalletClient;

@Slf4j
public class ShieldedVRC20BuilderTest extends BlockGenerate {

  private static String dbPath = "output_Shielded_VRC20_Api_test";
  private static AnnotationConfigApplicationContext context;
  private static Manager dbManager;
  private static Wallet wallet;
  private String privateKey = "650950B193DDDDB35B6E48912DD28F7AB0E7140C1BFDEFD493348F02295BD812";
  private String pubAddress = "TFsrP7YcSSRwHzLPwaCnXyTKagHs8rXKNJ";
  private static final String SHIELDED_CONTRACT_ADDRESS_STR = "TGAmX5AqVUoXCf8MoHxbuhQPmhGfWTnEgA";
  private static final byte[] SHIELDED_CONTRACT_ADDRESS;
  private static final byte[] DEFAULT_OVK;
  private static final String PUBLIC_TO_ADDRESS_STR = "TBaBXpRAeBhs75TZT751LwyhrcR25XeUot";
  private static final byte[] PUBLIC_TO_ADDRESS;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, "config-test-mainnet.conf");
    context = new VisionApplicationContext(DefaultConfig.class);
    SHIELDED_CONTRACT_ADDRESS = WalletClient.decodeFromBase58Check(SHIELDED_CONTRACT_ADDRESS_STR);
    DEFAULT_OVK = ByteArray
        .fromHexString("030c8c2bc59fb3eb8afb047a8ea4b028743d23e7d38c6fa30908358431e2314d");
    FullNodeHttpApiService.librustzcashInitZksnarkParams();
    PUBLIC_TO_ADDRESS = WalletClient.decodeFromBase58Check(PUBLIC_TO_ADDRESS_STR);
  }

  PrecompiledContracts.VerifyMintProof mintContract = new PrecompiledContracts.VerifyMintProof();
  PrecompiledContracts.VerifyTransferProof transferContract = new PrecompiledContracts.VerifyTransferProof();
  PrecompiledContracts.VerifyBurnProof burnContract = new PrecompiledContracts.VerifyBurnProof();

  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    wallet = context.getBean(Wallet.class);
    dbManager.getDynamicPropertiesStore().saveAllowShieldedVRC20Transaction(1);
    dbManager.getDynamicPropertiesStore().saveAllowShieldedTransaction(1);
  }

  @AfterClass
  public static void removeDb() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  @Before
  public void before() {
  }

  @Ignore
  @Test
  public void createShieldedContractParametersForMint()
      throws ZksnarkException, ContractValidateException, ContractExeException {
    int totalCountNum = 2;
    long leafCount = 0;
    long value = 100L;
    byte[] frontier = new byte[32 * 33];

    for (int countNum = 0; countNum < totalCountNum; countNum++) {
      GrpcAPI.PrivateShieldedVRC20Parameters mintPrivateParam1 = mintParams(
          privateKey, value, SHIELDED_CONTRACT_ADDRESS_STR, null);
      GrpcAPI.ShieldedVRC20Parameters vrc20MintParams = wallet
          .createShieldedContractParameters(mintPrivateParam1);

      byte[] inputData = abiEncodeForMint(vrc20MintParams, value, frontier, leafCount);
      Pair<Boolean, byte[]> contractResult = mintContract.execute(inputData);
      byte[] result = contractResult.getRight();

      Assert.assertEquals(1, result[31]);

      //update frontier and leafCount
      //if slot == 0, frontier[0:31]=noteCommitment
      int slot = result[63];
      if (slot == 0) {
        System.arraycopy(inputData, 0, frontier, 0, 32);
      } else {
        int srcPos = (slot + 1) * 32;
        int destPos = slot * 32;
        System.arraycopy(result, srcPos, frontier, destPos, 32);
      }
      leafCount++;
    }
  }

  @Ignore
  @Test
  public void createShieldedContractParametersForTransfer1v1()
      throws ZksnarkException, ContractValidateException, ContractExeException {
    int totalCountNum = 2;
    long leafCount = 0;
    byte[] frontier = new byte[32 * 33];

    IncrementalMerkleTreeContainer tree = new IncrementalMerkleTreeContainer(
        new IncrementalMerkleTreeCapsule());
    for (int countNum = 0; countNum < totalCountNum; countNum++) {
      SpendingKey senderSk = SpendingKey.decode(privateKey);
      FullViewingKey senderFvk = senderSk.fullViewingKey();
      IncomingViewingKey senderIvk = senderFvk.inViewingKey();
      byte[] rcm1 = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm1);
      PaymentAddress senderPaymentAddress1 = senderIvk.address(DiversifierT.random()).get();
      String senderPaymentAddressStr = KeyIo.encodePaymentAddress(senderPaymentAddress1);

      { //for mint1
        GrpcAPI.Note note = getNote(100, senderPaymentAddressStr, rcm1, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(senderFvk.getOvk()));
        paramBuilder.setFromAmount(BigInteger.valueOf(100).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();

        ShieldedVRC20Parameters minParam = wallet.createShieldedContractParameters(privMintParams);
        byte[] mintInputData1 = abiEncodeForMint(minParam, 100, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for transfer
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder privateVRC20Builder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder = GrpcAPI.SpendNoteVRC20.newBuilder();
        GrpcAPI.Note note = getNote(100, senderPaymentAddressStr, rcm1, new byte[512]);
        byte[][] cm1 = new byte[1][32];
        //spendNote1
        Note senderNote1 = new Note(senderPaymentAddress1.getD(), senderPaymentAddress1.getPkD(),
            100, rcm1, new byte[512]);
        System.arraycopy(senderNote1.cm(), 0, cm1[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher1 = addSimpleMerkleVoucherContainer(tree, cm1);
        byte[] path1 = decodePath(voucher1.path().encode());
        byte[] anchor1 = voucher1.root().getContent().toByteArray();
        long position1 = voucher1.position();
        spendNoteBuilder.setRoot(ByteString.copyFrom(anchor1));
        spendNoteBuilder.setPath(ByteString.copyFrom(path1));
        spendNoteBuilder.setPos(position1);
        spendNoteBuilder.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder.setNote(note);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder.build());

        //receiveNote1
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        SpendingKey receiveSk1 = SpendingKey.random();
        FullViewingKey receiveFvk1 = receiveSk1.fullViewingKey();
        IncomingViewingKey receiveIvk1 = receiveFvk1.inViewingKey();
        PaymentAddress receivePaymentAddress1 = receiveIvk1.address(new DiversifierT()).get();
        String recPaymentAddressStr = KeyIo.encodePaymentAddress(receivePaymentAddress1);
        byte[] memo = new byte[512];
        byte[] rcm = Note.generateR();
        GrpcAPI.Note revNote = getNote(100, recPaymentAddressStr, rcm, memo);
        revNoteBuilder.setNote(revNote);
        privateVRC20Builder.addShieldedReceives(revNoteBuilder.build());

        ExpandedSpendingKey expsk = senderSk.expandedSpendingKey();
        privateVRC20Builder.setAsk(ByteString.copyFrom(expsk.getAsk()));
        privateVRC20Builder.setNsk(ByteString.copyFrom(expsk.getNsk()));
        privateVRC20Builder.setOvk(ByteString.copyFrom(expsk.getOvk()));
        privateVRC20Builder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        GrpcAPI.ShieldedVRC20Parameters transferParam = wallet
            .createShieldedContractParameters(privateVRC20Builder.build());

        byte[] inputData = abiEncodeForTransfer(transferParam, frontier, leafCount);
        Pair<Boolean, byte[]> contractResult = verifyTransfer(inputData);
        byte[] result = contractResult.getRight();
        Assert.assertEquals(1, result[31]);

        //update frontier and leafCount
        int idx = 63;
        int slot = result[idx];
        if (slot == 0) {
          byte[] noteCommitment = transferParam.getReceiveDescription(0).getNoteCommitment()
              .toByteArray();
          System.arraycopy(noteCommitment, 0, frontier, 0, 32);
        } else {
          int destPos = slot * 32;
          int srcPos = (slot - 1) * 32 + idx + 1;
          System.arraycopy(result, srcPos, frontier, destPos, 32);
        }
        idx += slot * 32 + 1;
        leafCount++;

        byte[][] cm = new byte[1][32];
        for (int i = 0; i < 1; i++) {
          byte[] noteCommitment = transferParam.getReceiveDescription(i).getNoteCommitment()
              .toByteArray();
          System.arraycopy(noteCommitment, 0, cm[i], 0, 32);
        }
        IncrementalMerkleVoucherContainer voucher = addSimpleMerkleVoucherContainer(tree, cm);
        byte[] root = voucher.root().getContent().toByteArray();
        Assert.assertArrayEquals(root, Arrays.copyOfRange(result, idx, idx + 32));
      }
    }
  }

  @Ignore
  @Test
  public void createShieldedContractParametersForTransfer1v2()
      throws ZksnarkException, ContractValidateException, ContractExeException {
    int totalCountNum = 2;
    long leafCount = 0;
    byte[] frontier = new byte[32 * 33];

    IncrementalMerkleTreeContainer tree = new IncrementalMerkleTreeContainer(
        new IncrementalMerkleTreeCapsule());
    for (int countNum = 0; countNum < totalCountNum; countNum++) {
      SpendingKey senderSk = SpendingKey.random();
      ExpandedSpendingKey senderExpsk = senderSk.expandedSpendingKey();
      FullViewingKey senderFvk = senderSk.fullViewingKey();
      byte[] senderOvk = senderFvk.getOvk();
      IncomingViewingKey senderIvk = senderFvk.inViewingKey();
      byte[] rcm1 = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm1);
      PaymentAddress senderPaymentAddress1 = senderIvk.address(DiversifierT.random()).get();
      String senderPaymentAddressStr = KeyIo.encodePaymentAddress(senderPaymentAddress1);

      { //for mint1
        GrpcAPI.Note note = getNote(100, senderPaymentAddressStr, rcm1, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(senderFvk.getOvk()));
        paramBuilder.setFromAmount(BigInteger.valueOf(100).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();
        ShieldedVRC20Parameters mintParam = wallet.createShieldedContractParameters(privMintParams);

        byte[] mintInputData1 = abiEncodeForMint(mintParam, 100, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for transfer
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder privateVRC20Builder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder = GrpcAPI.SpendNoteVRC20.newBuilder();
        GrpcAPI.Note note = getNote(100, senderPaymentAddressStr, rcm1, new byte[512]);
        byte[][] cm1 = new byte[1][32];
        //spendNote1
        Note senderNote1 = new Note(senderPaymentAddress1.getD(), senderPaymentAddress1.getPkD(),
            100, rcm1, new byte[512]);
        System.arraycopy(senderNote1.cm(), 0, cm1[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher1 = addSimpleMerkleVoucherContainer(tree, cm1);
        byte[] path1 = decodePath(voucher1.path().encode());
        byte[] anchor1 = voucher1.root().getContent().toByteArray();
        long position1 = voucher1.position();
        spendNoteBuilder.setRoot(ByteString.copyFrom(anchor1));
        spendNoteBuilder.setPath(ByteString.copyFrom(path1));
        spendNoteBuilder.setPos(position1);
        spendNoteBuilder.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder.setNote(note);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder.build());

        //receiveNote1
        GrpcAPI.ReceiveNote.Builder revNoteBuilder1 = GrpcAPI.ReceiveNote.newBuilder();
        SpendingKey receiveSk1 = SpendingKey.random();
        FullViewingKey receiveFvk1 = receiveSk1.fullViewingKey();
        IncomingViewingKey receiveIvk1 = receiveFvk1.inViewingKey();
        PaymentAddress receivePaymentAddress1 = receiveIvk1.address(new DiversifierT()).get();
        String recPaymentAddressStr = KeyIo.encodePaymentAddress(receivePaymentAddress1);
        byte[] memo = new byte[512];
        byte[] rcm = Note.generateR();
        GrpcAPI.Note revNote = getNote(60, recPaymentAddressStr, rcm, memo);
        revNoteBuilder1.setNote(revNote);
        privateVRC20Builder.addShieldedReceives(revNoteBuilder1.build());

        //receiveNote2
        GrpcAPI.ReceiveNote.Builder revNoteBuilder2 = GrpcAPI.ReceiveNote.newBuilder();
        SpendingKey receiveSk2 = SpendingKey.random();
        FullViewingKey receiveFvk2 = receiveSk2.fullViewingKey();
        IncomingViewingKey receiveIvk2 = receiveFvk2.inViewingKey();
        PaymentAddress receivePaymentAddress2 = receiveIvk2.address(new DiversifierT()).get();
        String recPaymentAddressStr2 = KeyIo.encodePaymentAddress(receivePaymentAddress2);
        byte[] rcm2 = Note.generateR();
        GrpcAPI.Note revNote2 = getNote(40, recPaymentAddressStr2, rcm2, memo);
        revNoteBuilder2.setNote(revNote2);
        privateVRC20Builder.addShieldedReceives(revNoteBuilder2.build());

        ExpandedSpendingKey expsk = senderSk.expandedSpendingKey();
        privateVRC20Builder.setAsk(ByteString.copyFrom(expsk.getAsk()));
        privateVRC20Builder.setNsk(ByteString.copyFrom(expsk.getNsk()));
        privateVRC20Builder.setOvk(ByteString.copyFrom(expsk.getOvk()));
        privateVRC20Builder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        GrpcAPI.ShieldedVRC20Parameters transferParam = wallet
            .createShieldedContractParameters(privateVRC20Builder.build());

        byte[] inputData = abiEncodeForTransfer(transferParam, frontier, leafCount);
        Pair<Boolean, byte[]> contractResult = verifyTransfer(inputData);
        byte[] result = contractResult.getRight();
        Assert.assertEquals(1, result[31]);

        //update frontier and leafCount
        int idx = 32;
        for (int i = 0; i < 2; i++) {
          idx += 31;
          int slot = result[idx];
          idx += 1;
          if (slot == 0) {
            byte[] noteCommitment = transferParam.getReceiveDescription(i).getNoteCommitment()
                .toByteArray();
            System.arraycopy(noteCommitment, 0, frontier, 0, 32);
          } else {
            int destPos = slot * 32;
            int srcPos = (slot - 1) * 32 + idx;
            System.arraycopy(result, srcPos, frontier, destPos, 32);
          }
          idx += slot * 32;
          leafCount++;
        }

        byte[][] cm = new byte[2][32];
        for (int i = 0; i < 2; i++) {
          byte[] noteCommitment = transferParam.getReceiveDescription(i).getNoteCommitment()
              .toByteArray();
          System.arraycopy(noteCommitment, 0, cm[i], 0, 32);
        }
        IncrementalMerkleVoucherContainer voucher = addSimpleMerkleVoucherContainer(tree, cm);
        byte[] root = voucher.root().getContent().toByteArray();

        Assert.assertArrayEquals(root, Arrays.copyOfRange(result, idx, idx + 32));
      }
    }
  }

  @Ignore
  @Test
  public void createShieldedContractParametersForTransfer2v1()
      throws ZksnarkException, ContractValidateException, ContractExeException {
    int totalCountNum = 2;
    long leafCount = 0;
    byte[] frontier = new byte[32 * 33];

    IncrementalMerkleTreeContainer tree = new IncrementalMerkleTreeContainer(
        new IncrementalMerkleTreeCapsule());
    for (int countNum = 0; countNum < totalCountNum; countNum++) {
      SpendingKey senderSk = SpendingKey.random();
      ExpandedSpendingKey senderExpsk = senderSk.expandedSpendingKey();
      FullViewingKey senderFvk = senderSk.fullViewingKey();
      byte[] senderOvk = senderFvk.getOvk();
      IncomingViewingKey senderIvk = senderFvk.inViewingKey();
      byte[] rcm1 = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm1);
      byte[] rcm2 = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm2);
      PaymentAddress senderPaymentAddress1 = senderIvk.address(DiversifierT.random()).get();
      PaymentAddress senderPaymentAddress2 = senderIvk.address(DiversifierT.random()).get();
      String senderPaymentAddressStr1 = KeyIo.encodePaymentAddress(senderPaymentAddress1);
      String senderPaymentAddressStr2 = KeyIo.encodePaymentAddress(senderPaymentAddress2);

      { //for mint1
        GrpcAPI.Note note = getNote(30, senderPaymentAddressStr1, rcm1, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(DEFAULT_OVK));
        paramBuilder.setFromAmount(BigInteger.valueOf(30).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();
        ShieldedVRC20Parameters mintParam = wallet.createShieldedContractParameters(privMintParams);

        byte[] mintInputData1 = abiEncodeForMint(mintParam, 30, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for mint2
        GrpcAPI.Note note = getNote(70, senderPaymentAddressStr2, rcm2, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(DEFAULT_OVK));
        paramBuilder.setFromAmount(BigInteger.valueOf(70).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();
        ShieldedVRC20Parameters mintParam = wallet.createShieldedContractParameters(privMintParams);

        byte[] mintInputData1 = abiEncodeForMint(mintParam, 70, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for transfer
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder privateVRC20Builder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder1 = GrpcAPI.SpendNoteVRC20.newBuilder();
        GrpcAPI.Note note1 = getNote(30, senderPaymentAddressStr1, rcm1, new byte[512]);
        byte[][] cm1 = new byte[1][32];
        //spendNote1
        Note senderNote1 = new Note(senderPaymentAddress1.getD(), senderPaymentAddress1.getPkD(),
            30, rcm1, new byte[512]);
        System.arraycopy(senderNote1.cm(), 0, cm1[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher1 = addSimpleMerkleVoucherContainer(tree, cm1);
        byte[] path1 = decodePath(voucher1.path().encode());
        byte[] anchor1 = voucher1.root().getContent().toByteArray();
        long position1 = voucher1.position();
        spendNoteBuilder1.setRoot(ByteString.copyFrom(anchor1));
        spendNoteBuilder1.setPath(ByteString.copyFrom(path1));
        spendNoteBuilder1.setPos(position1);
        spendNoteBuilder1.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder1.setNote(note1);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder1.build());

        //spendNote2
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder2 = GrpcAPI.SpendNoteVRC20.newBuilder();
        Note senderNote2 = new Note(senderPaymentAddress2.getD(), senderPaymentAddress2.getPkD(),
            70, rcm2, new byte[512]);
        GrpcAPI.Note note2 = getNote(70, senderPaymentAddressStr2, rcm2, new byte[512]);
        byte[][] cm2 = new byte[1][32];
        System.arraycopy(senderNote2.cm(), 0, cm2[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher2 = addSimpleMerkleVoucherContainer(tree, cm2);
        byte[] path2 = decodePath(voucher2.path().encode());
        byte[] anchor2 = voucher2.root().getContent().toByteArray();
        long position2 = voucher2.position();
        spendNoteBuilder2.setRoot(ByteString.copyFrom(anchor2));
        spendNoteBuilder2.setPath(ByteString.copyFrom(path2));
        spendNoteBuilder2.setPos(position2);
        spendNoteBuilder2.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder2.setNote(note2);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder2.build());

        //receiveNote1
        GrpcAPI.ReceiveNote.Builder revNoteBuilder1 = GrpcAPI.ReceiveNote.newBuilder();
        SpendingKey receiveSk1 = SpendingKey.random();
        FullViewingKey receiveFvk1 = receiveSk1.fullViewingKey();
        IncomingViewingKey receiveIvk1 = receiveFvk1.inViewingKey();
        PaymentAddress receivePaymentAddress1 = receiveIvk1.address(new DiversifierT()).get();
        String recPaymentAddressStr = KeyIo.encodePaymentAddress(receivePaymentAddress1);
        byte[] memo = new byte[512];
        byte[] rcm3 = Note.generateR();
        GrpcAPI.Note revNote = getNote(100, recPaymentAddressStr, rcm3, memo);
        revNoteBuilder1.setNote(revNote);
        privateVRC20Builder.addShieldedReceives(revNoteBuilder1.build());

        ExpandedSpendingKey expsk = senderSk.expandedSpendingKey();
        privateVRC20Builder.setAsk(ByteString.copyFrom(expsk.getAsk()));
        privateVRC20Builder.setNsk(ByteString.copyFrom(expsk.getNsk()));
        privateVRC20Builder.setOvk(ByteString.copyFrom(expsk.getOvk()));
        privateVRC20Builder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        GrpcAPI.ShieldedVRC20Parameters transferParam = wallet
            .createShieldedContractParameters(privateVRC20Builder.build());

        byte[] inputData = abiEncodeForTransfer(transferParam, frontier, leafCount);
        Pair<Boolean, byte[]> contractResult = verifyTransfer(inputData);
        byte[] result = contractResult.getRight();
        Assert.assertEquals(1, result[31]);

        //update frontier and leafCount
        int idx = 63;
        int slot = result[idx];
        if (slot == 0) {
          byte[] noteCommitment = transferParam.getReceiveDescription(0).getNoteCommitment()
              .toByteArray();
          System.arraycopy(noteCommitment, 0, frontier, 0, 32);
        } else {
          int destPos = slot * 32;
          int srcPos = (slot - 1) * 32 + idx + 1;
          System.arraycopy(result, srcPos, frontier, destPos, 32);
        }
        idx += slot * 32 + 1;
        leafCount++;

        byte[][] cm = new byte[1][32];
        for (int i = 0; i < 1; i++) {
          byte[] noteCommitment = transferParam.getReceiveDescription(i).getNoteCommitment()
              .toByteArray();
          System.arraycopy(noteCommitment, 0, cm[i], 0, 32);
        }
        IncrementalMerkleVoucherContainer voucher = addSimpleMerkleVoucherContainer(tree, cm);
        byte[] root = voucher.root().getContent().toByteArray();

        Assert.assertArrayEquals(root, Arrays.copyOfRange(result, idx, idx + 32));
      }
    }
  }


  @Ignore
  @Test
  public void createShieldedContractParametersForTransfer2v2()
      throws ZksnarkException, ContractValidateException, ContractExeException {
    int totalCountNum = 2;
    long leafCount = 0;
    byte[] frontier = new byte[32 * 33];

    IncrementalMerkleTreeContainer tree = new IncrementalMerkleTreeContainer(
        new IncrementalMerkleTreeCapsule());
    for (int countNum = 0; countNum < totalCountNum; countNum++) {
      SpendingKey senderSk = SpendingKey.random();
      ExpandedSpendingKey senderExpsk = senderSk.expandedSpendingKey();
      FullViewingKey senderFvk = senderSk.fullViewingKey();
      byte[] senderOvk = senderFvk.getOvk();
      IncomingViewingKey senderIvk = senderFvk.inViewingKey();
      byte[] rcm1 = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm1);
      byte[] rcm2 = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm2);
      PaymentAddress senderPaymentAddress1 = senderIvk.address(DiversifierT.random()).get();
      PaymentAddress senderPaymentAddress2 = senderIvk.address(DiversifierT.random()).get();
      String senderPaymentAddressStr1 = KeyIo.encodePaymentAddress(senderPaymentAddress1);
      String senderPaymentAddressStr2 = KeyIo.encodePaymentAddress(senderPaymentAddress2);

      { //for mint1
        GrpcAPI.Note note = getNote(30, senderPaymentAddressStr1, rcm1, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(DEFAULT_OVK));
        paramBuilder.setFromAmount(BigInteger.valueOf(30).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();
        ShieldedVRC20Parameters mintParam = wallet.createShieldedContractParameters(privMintParams);

        byte[] mintInputData1 = abiEncodeForMint(mintParam, 30, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for mint2
        GrpcAPI.Note note = getNote(70, senderPaymentAddressStr2, rcm2, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(DEFAULT_OVK));
        paramBuilder.setFromAmount(BigInteger.valueOf(70).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();
        ShieldedVRC20Parameters mintParam = wallet.createShieldedContractParameters(privMintParams);

        byte[] mintInputData1 = abiEncodeForMint(mintParam, 70, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for transfer
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder privateVRC20Builder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder1 = GrpcAPI.SpendNoteVRC20.newBuilder();
        GrpcAPI.Note note1 = getNote(30, senderPaymentAddressStr1, rcm1, new byte[512]);
        byte[][] cm1 = new byte[1][32];
        //spendNote1
        Note senderNote1 = new Note(senderPaymentAddress1.getD(), senderPaymentAddress1.getPkD(),
            30, rcm1, new byte[512]);
        System.arraycopy(senderNote1.cm(), 0, cm1[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher1 = addSimpleMerkleVoucherContainer(tree, cm1);
        byte[] path1 = decodePath(voucher1.path().encode());
        byte[] anchor1 = voucher1.root().getContent().toByteArray();
        long position1 = voucher1.position();
        spendNoteBuilder1.setRoot(ByteString.copyFrom(anchor1));
        spendNoteBuilder1.setPath(ByteString.copyFrom(path1));
        spendNoteBuilder1.setPos(position1);
        spendNoteBuilder1.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder1.setNote(note1);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder1.build());

        //spendNote2
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder2 = GrpcAPI.SpendNoteVRC20.newBuilder();
        Note senderNote2 = new Note(senderPaymentAddress2.getD(), senderPaymentAddress2.getPkD(),
            70, rcm2, new byte[512]);
        GrpcAPI.Note note2 = getNote(70, senderPaymentAddressStr2, rcm2, new byte[512]);
        byte[][] cm2 = new byte[1][32];
        System.arraycopy(senderNote2.cm(), 0, cm2[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher2 = addSimpleMerkleVoucherContainer(tree, cm2);
        byte[] path2 = decodePath(voucher2.path().encode());
        byte[] anchor2 = voucher2.root().getContent().toByteArray();
        long position2 = voucher2.position();
        spendNoteBuilder2.setRoot(ByteString.copyFrom(anchor2));
        spendNoteBuilder2.setPath(ByteString.copyFrom(path2));
        spendNoteBuilder2.setPos(position2);
        spendNoteBuilder2.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder2.setNote(note2);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder2.build());

        //receiveNote1
        GrpcAPI.ReceiveNote.Builder revNoteBuilder1 = GrpcAPI.ReceiveNote.newBuilder();
        SpendingKey receiveSk1 = SpendingKey.random();
        FullViewingKey receiveFvk1 = receiveSk1.fullViewingKey();
        IncomingViewingKey receiveIvk1 = receiveFvk1.inViewingKey();
        PaymentAddress receivePaymentAddress1 = receiveIvk1.address(new DiversifierT()).get();
        String recPaymentAddressStr = KeyIo.encodePaymentAddress(receivePaymentAddress1);
        byte[] memo = new byte[512];
        byte[] rcm3 = Note.generateR();
        GrpcAPI.Note revNote = getNote(60, recPaymentAddressStr, rcm3, memo);
        revNoteBuilder1.setNote(revNote);
        privateVRC20Builder.addShieldedReceives(revNoteBuilder1.build());

        //receiveNote2
        GrpcAPI.ReceiveNote.Builder revNoteBuilder2 = GrpcAPI.ReceiveNote.newBuilder();
        SpendingKey receiveSk2 = SpendingKey.random();
        FullViewingKey receiveFvk2 = receiveSk2.fullViewingKey();
        IncomingViewingKey receiveIvk2 = receiveFvk2.inViewingKey();
        PaymentAddress receivePaymentAddress2 = receiveIvk2.address(new DiversifierT()).get();
        String recPaymentAddressStr2 = KeyIo.encodePaymentAddress(receivePaymentAddress2);
        byte[] rcm4 = Note.generateR();
        GrpcAPI.Note revNote2 = getNote(40, recPaymentAddressStr2, rcm4, memo);
        revNoteBuilder2.setNote(revNote2);
        privateVRC20Builder.addShieldedReceives(revNoteBuilder2.build());

        ExpandedSpendingKey expsk = senderSk.expandedSpendingKey();
        privateVRC20Builder.setAsk(ByteString.copyFrom(expsk.getAsk()));
        privateVRC20Builder.setNsk(ByteString.copyFrom(expsk.getNsk()));
        privateVRC20Builder.setOvk(ByteString.copyFrom(expsk.getOvk()));
        privateVRC20Builder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        GrpcAPI.ShieldedVRC20Parameters transferParam = wallet
            .createShieldedContractParameters(privateVRC20Builder.build());

        byte[] inputData = abiEncodeForTransfer(transferParam, frontier, leafCount);
        Pair<Boolean, byte[]> contractResult = verifyTransfer(inputData);
        byte[] result = contractResult.getRight();
        Assert.assertEquals(1, result[31]);

        //update frontier and leafCount
        int idx = 32;
        for (int i = 0; i < 2; i++) {
          idx += 31;
          int slot = result[idx];
          idx += 1;
          if (slot == 0) {
            byte[] noteCommitment = transferParam.getReceiveDescription(i).getNoteCommitment()
                .toByteArray();
            System.arraycopy(noteCommitment, 0, frontier, 0, 32);
          } else {
            int destPos = slot * 32;
            int srcPos = (slot - 1) * 32 + idx;
            System.arraycopy(result, srcPos, frontier, destPos, 32);
          }
          idx += slot * 32;
          leafCount++;
        }

        byte[][] cm = new byte[2][32];
        for (int i = 0; i < 2; i++) {
          byte[] noteCommitment = transferParam.getReceiveDescription(i).getNoteCommitment()
              .toByteArray();
          System.arraycopy(noteCommitment, 0, cm[i], 0, 32);
        }
        IncrementalMerkleVoucherContainer voucher = addSimpleMerkleVoucherContainer(tree, cm);
        byte[] root = voucher.root().getContent().toByteArray();

        Assert.assertArrayEquals(root, Arrays.copyOfRange(result, idx, idx + 32));
      }
    }
  }

  @Ignore
  @Test
  public void createShieldedContractParametersForBurn()
      throws ZksnarkException, ContractValidateException, ContractExeException {
    int totalCountNum = 2;
    long leafCount = 0;
    long value = 100L;
    byte[] frontier = new byte[32 * 33];

    IncrementalMerkleTreeContainer tree = new IncrementalMerkleTreeContainer(
        new IncrementalMerkleTreeCapsule());
    for (int countNum = 0; countNum < totalCountNum; countNum++) {
      SpendingKey senderSk = SpendingKey.random();
      ExpandedSpendingKey senderExpsk = senderSk.expandedSpendingKey();
      FullViewingKey senderFvk = senderSk.fullViewingKey();
      byte[] senderOvk = senderFvk.getOvk();
      IncomingViewingKey senderIvk = senderFvk.inViewingKey();
      byte[] rcm = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm);
      PaymentAddress senderPaymentAddress = senderIvk.address(DiversifierT.random()).get();
      String senderPaymentAddressStr = KeyIo.encodePaymentAddress(senderPaymentAddress);

      { //for mint1
        GrpcAPI.Note note = getNote(value, senderPaymentAddressStr, rcm, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(senderFvk.getOvk()));
        paramBuilder.setFromAmount(BigInteger.valueOf(value).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();

        ShieldedVRC20Parameters minParam = wallet.createShieldedContractParameters(privMintParams);
        byte[] mintInputData1 = abiEncodeForMint(minParam, value, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for burn
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder privateVRC20Builder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder = GrpcAPI.SpendNoteVRC20.newBuilder();
        GrpcAPI.Note note = getNote(value, senderPaymentAddressStr, rcm, new byte[512]);
        byte[][] cm1 = new byte[1][32];
        //spendNote1
        Note senderNote1 = new Note(senderPaymentAddress.getD(), senderPaymentAddress.getPkD(),
            value, rcm, new byte[512]);
        System.arraycopy(senderNote1.cm(), 0, cm1[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher1 = addSimpleMerkleVoucherContainer(tree, cm1);
        byte[] path1 = decodePath(voucher1.path().encode());
        byte[] anchor1 = voucher1.root().getContent().toByteArray();
        long position1 = voucher1.position();
        spendNoteBuilder.setRoot(ByteString.copyFrom(anchor1));
        spendNoteBuilder.setPath(ByteString.copyFrom(path1));
        spendNoteBuilder.setPos(position1);
        spendNoteBuilder.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder.setNote(note);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder.build());

        ExpandedSpendingKey expsk = senderSk.expandedSpendingKey();
        privateVRC20Builder.setAsk(ByteString.copyFrom(expsk.getAsk()));
        privateVRC20Builder.setNsk(ByteString.copyFrom(expsk.getNsk()));
        privateVRC20Builder.setToAmount(BigInteger.valueOf(value).toString());
        privateVRC20Builder.setTransparentToAddress(ByteString.copyFrom(PUBLIC_TO_ADDRESS));
        privateVRC20Builder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        GrpcAPI.ShieldedVRC20Parameters burnParam = wallet
            .createShieldedContractParameters(privateVRC20Builder.build());

        byte[] inputData = abiEncodeForBurn(burnParam, value);
        Pair<Boolean, byte[]> contractResult = burnContract.execute(inputData);
        byte[] result = contractResult.getRight();
        Assert.assertEquals(1, result[31]);
      }
    }
  }

  @Ignore
  @Test
  public void createShieldedContractParametersForBurn1v2()
      throws ZksnarkException, ContractValidateException, ContractExeException {
    int totalCountNum = 2;
    long leafCount = 0;
    long value = 100L;
    byte[] frontier = new byte[32 * 33];

    IncrementalMerkleTreeContainer tree = new IncrementalMerkleTreeContainer(
        new IncrementalMerkleTreeCapsule());
    for (int countNum = 0; countNum < totalCountNum; countNum++) {
      SpendingKey senderSk = SpendingKey.random();
      ExpandedSpendingKey senderExpsk = senderSk.expandedSpendingKey();
      FullViewingKey senderFvk = senderSk.fullViewingKey();
      byte[] senderOvk = senderFvk.getOvk();
      IncomingViewingKey senderIvk = senderFvk.inViewingKey();
      byte[] rcm = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm);
      PaymentAddress senderPaymentAddress = senderIvk.address(DiversifierT.random()).get();
      String senderPaymentAddressStr = KeyIo.encodePaymentAddress(senderPaymentAddress);

      { //for mint1
        GrpcAPI.Note note = getNote(value, senderPaymentAddressStr, rcm, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(senderFvk.getOvk()));
        paramBuilder.setFromAmount(BigInteger.valueOf(value).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();

        ShieldedVRC20Parameters minParam = wallet.createShieldedContractParameters(privMintParams);
        byte[] mintInputData1 = abiEncodeForMint(minParam, value, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for burn
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder privateVRC20Builder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder = GrpcAPI.SpendNoteVRC20.newBuilder();
        GrpcAPI.Note note = getNote(value, senderPaymentAddressStr, rcm, new byte[512]);
        byte[][] cm1 = new byte[1][32];
        //spendNote1
        Note senderNote1 = new Note(senderPaymentAddress.getD(), senderPaymentAddress.getPkD(),
            value, rcm, new byte[512]);
        System.arraycopy(senderNote1.cm(), 0, cm1[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher1 = addSimpleMerkleVoucherContainer(tree, cm1);
        byte[] path1 = decodePath(voucher1.path().encode());
        byte[] anchor1 = voucher1.root().getContent().toByteArray();
        long position1 = voucher1.position();
        spendNoteBuilder.setRoot(ByteString.copyFrom(anchor1));
        spendNoteBuilder.setPath(ByteString.copyFrom(path1));
        spendNoteBuilder.setPos(position1);
        spendNoteBuilder.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder.setNote(note);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder.build());

        ExpandedSpendingKey expsk = senderSk.expandedSpendingKey();
        privateVRC20Builder.setAsk(ByteString.copyFrom(expsk.getAsk()));
        privateVRC20Builder.setNsk(ByteString.copyFrom(expsk.getNsk()));
        privateVRC20Builder.setToAmount(BigInteger.valueOf(60).toString());
        privateVRC20Builder.setTransparentToAddress(ByteString.copyFrom(PUBLIC_TO_ADDRESS));
        privateVRC20Builder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));

        //receiveNote
        GrpcAPI.ReceiveNote.Builder revNoteBuilder2 = GrpcAPI.ReceiveNote.newBuilder();
        SpendingKey receiveSk2 = SpendingKey.random();
        FullViewingKey receiveFvk2 = receiveSk2.fullViewingKey();
        IncomingViewingKey receiveIvk2 = receiveFvk2.inViewingKey();
        PaymentAddress receivePaymentAddress2 = receiveIvk2.address(new DiversifierT()).get();
        String recPaymentAddressStr2 = KeyIo.encodePaymentAddress(receivePaymentAddress2);
        byte[] rcm4 = Note.generateR();
        byte[] memo = new byte[512];
        GrpcAPI.Note revNote2 = getNote(40, recPaymentAddressStr2, rcm4, memo);
        revNoteBuilder2.setNote(revNote2);
        privateVRC20Builder.addShieldedReceives(revNoteBuilder2.build());

        GrpcAPI.ShieldedVRC20Parameters burnParam = wallet
            .createShieldedContractParameters(privateVRC20Builder.build());

        byte[] inputData = abiEncodeForBurn(burnParam, value);
        Pair<Boolean, byte[]> contractResult = burnContract.execute(inputData);
        byte[] result = contractResult.getRight();
        Assert.assertEquals(1, result[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = result[63];
        if (slot == 0) {
          System.arraycopy(result, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(result, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }
    }
  }


  @Ignore
  @Test
  public void createShieldedContractParametersWithouAskForTransfer1v1()
      throws Exception {
    int totalCountNum = 2;
    long leafCount = 0;
    byte[] frontier = new byte[32 * 33];

    IncrementalMerkleTreeContainer tree = new IncrementalMerkleTreeContainer(
        new IncrementalMerkleTreeCapsule());
    for (int countNum = 0; countNum < totalCountNum; countNum++) {
      SpendingKey senderSk = SpendingKey.random();
      ExpandedSpendingKey senderExpsk = senderSk.expandedSpendingKey();
      FullViewingKey senderFvk = senderSk.fullViewingKey();
      byte[] senderOvk = senderFvk.getOvk();
      IncomingViewingKey senderIvk = senderFvk.inViewingKey();
      byte[] rcm1 = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm1);
      PaymentAddress senderPaymentAddress1 = senderIvk.address(DiversifierT.random()).get();
      String senderPaymentAddressStr1 = KeyIo.encodePaymentAddress(senderPaymentAddress1);

      { //for mint1
        GrpcAPI.Note note = getNote(30, senderPaymentAddressStr1, rcm1, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(DEFAULT_OVK));
        paramBuilder.setFromAmount(BigInteger.valueOf(30).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();
        ShieldedVRC20Parameters mintParam = wallet.createShieldedContractParameters(privMintParams);

        byte[] mintInputData1 = abiEncodeForMint(mintParam, 30, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for transfer
        GrpcAPI.PrivateShieldedVRC20ParametersWithoutAsk.Builder privateVRC20Builder = GrpcAPI
            .PrivateShieldedVRC20ParametersWithoutAsk.newBuilder();
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder1 = GrpcAPI.SpendNoteVRC20.newBuilder();
        GrpcAPI.Note note1 = getNote(30, senderPaymentAddressStr1, rcm1, new byte[512]);
        byte[][] cm1 = new byte[1][32];
        //spendNote1
        Note senderNote1 = new Note(senderPaymentAddress1.getD(), senderPaymentAddress1.getPkD(),
            30, rcm1, new byte[512]);
        System.arraycopy(senderNote1.cm(), 0, cm1[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher1 = addSimpleMerkleVoucherContainer(tree, cm1);
        byte[] path1 = decodePath(voucher1.path().encode());
        byte[] anchor1 = voucher1.root().getContent().toByteArray();
        long position1 = voucher1.position();
        spendNoteBuilder1.setRoot(ByteString.copyFrom(anchor1));
        spendNoteBuilder1.setPath(ByteString.copyFrom(path1));
        spendNoteBuilder1.setPos(position1);
        spendNoteBuilder1.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder1.setNote(note1);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder1.build());

        //receiveNote1
        GrpcAPI.ReceiveNote.Builder revNoteBuilder1 = GrpcAPI.ReceiveNote.newBuilder();
        SpendingKey receiveSk1 = SpendingKey.random();
        FullViewingKey receiveFvk1 = receiveSk1.fullViewingKey();
        IncomingViewingKey receiveIvk1 = receiveFvk1.inViewingKey();
        PaymentAddress receivePaymentAddress1 = receiveIvk1.address(new DiversifierT()).get();
        String recPaymentAddressStr = KeyIo.encodePaymentAddress(receivePaymentAddress1);
        byte[] memo = new byte[512];
        byte[] rcm3 = Note.generateR();
        GrpcAPI.Note revNote = getNote(30, recPaymentAddressStr, rcm3, memo);
        revNoteBuilder1.setNote(revNote);
        privateVRC20Builder.addShieldedReceives(revNoteBuilder1.build());

        ExpandedSpendingKey expsk = senderSk.expandedSpendingKey();
        privateVRC20Builder.setAk(ByteString.copyFrom(senderFvk.getAk()));
        privateVRC20Builder.setNsk(ByteString.copyFrom(expsk.getNsk()));
        privateVRC20Builder.setOvk(ByteString.copyFrom(expsk.getOvk()));
        privateVRC20Builder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        GrpcAPI.ShieldedVRC20Parameters transferParam = wallet
            .createShieldedContractParametersWithoutAsk(privateVRC20Builder.build());

        //get the binding signature
        PrivateShieldedVRC20ParametersWithoutAsk ShieldedVRC20ParametersWithoutAsk =
            privateVRC20Builder
                .build();
        SpendAuthSigParameters.Builder signParamerters1 = SpendAuthSigParameters.newBuilder();
        signParamerters1
            .setAlpha(ShieldedVRC20ParametersWithoutAsk.getShieldedSpends(0).getAlpha());
        signParamerters1.setAsk(ByteString.copyFrom(expsk.getAsk()));
        signParamerters1.setTxHash(transferParam.getMessageHash());
        BytesMessage signMsg1 = wallet.createSpendAuthSig(signParamerters1.build());

        ShieldedVRC20TriggerContractParameters.Builder triggerParam =
            ShieldedVRC20TriggerContractParameters
                .newBuilder();
        triggerParam.setShieldedVRC20Parameters(transferParam);
        triggerParam.addSpendAuthoritySignature(signMsg1);
        BytesMessage triggerInput = wallet
            .getTriggerInputForShieldedVRC20Contract(triggerParam.build());
        logger.info(
            "trigger contract input: " + Hex.toHexString(triggerInput.getValue().toByteArray()));

        SpendDescription.Builder spendDesBuilder1 = SpendDescription.newBuilder();
        spendDesBuilder1.setSpendAuthoritySignature(signMsg1.getValue());
        spendDesBuilder1.setAnchor(transferParam.getSpendDescription(0).getAnchor());
        spendDesBuilder1.setRk(transferParam.getSpendDescription(0).getRk());
        spendDesBuilder1
            .setValueCommitment(transferParam.getSpendDescription(0).getValueCommitment());
        spendDesBuilder1.setZkproof(transferParam.getSpendDescription(0).getZkproof());
        spendDesBuilder1.setNullifier(transferParam.getSpendDescription(0).getNullifier());

        ShieldedVRC20Parameters.Builder bindingSigBuilder = ShieldedVRC20Parameters.newBuilder();
        bindingSigBuilder.addSpendDescription(spendDesBuilder1.build());
        bindingSigBuilder.addReceiveDescription(transferParam.getReceiveDescription(0));
        bindingSigBuilder.setMessageHash(transferParam.getMessageHash());
        bindingSigBuilder.setBindingSignature(transferParam.getBindingSignature());
        bindingSigBuilder.setParameterType(transferParam.getParameterType());
        transferParam = bindingSigBuilder.build();

        byte[] inputData = abiEncodeForTransfer(transferParam, frontier, leafCount);
        Pair<Boolean, byte[]> contractResult = verifyTransfer(inputData);
        byte[] result = contractResult.getRight();
        Assert.assertEquals(1, result[31]);

        //update frontier and leafCount
        int idx = 63;
        int slot = result[idx];
        if (slot == 0) {
          byte[] noteCommitment = transferParam.getReceiveDescription(0).getNoteCommitment()
              .toByteArray();
          System.arraycopy(noteCommitment, 0, frontier, 0, 32);
        } else {
          int destPos = slot * 32;
          int srcPos = (slot - 1) * 32 + idx + 1;
          System.arraycopy(result, srcPos, frontier, destPos, 32);
        }
        idx += slot * 32 + 1;
        leafCount++;

        byte[][] cm = new byte[1][32];
        for (int i = 0; i < 1; i++) {
          byte[] noteCommitment = transferParam.getReceiveDescription(i).getNoteCommitment()
              .toByteArray();
          System.arraycopy(noteCommitment, 0, cm[i], 0, 32);
        }
        IncrementalMerkleVoucherContainer voucher = addSimpleMerkleVoucherContainer(tree, cm);
        byte[] root = voucher.root().getContent().toByteArray();

        Assert.assertArrayEquals(root, Arrays.copyOfRange(result, idx, idx + 32));

      }
    }
  }

  @Ignore
  @Test
  public void createShieldedContractParametersWithouAskForTransfer1v2()
      throws Exception {
    int totalCountNum = 2;
    long leafCount = 0;
    byte[] frontier = new byte[32 * 33];

    IncrementalMerkleTreeContainer tree = new IncrementalMerkleTreeContainer(
        new IncrementalMerkleTreeCapsule());
    for (int countNum = 0; countNum < totalCountNum; countNum++) {
      SpendingKey senderSk = SpendingKey.random();
      ExpandedSpendingKey senderExpsk = senderSk.expandedSpendingKey();
      FullViewingKey senderFvk = senderSk.fullViewingKey();
      byte[] senderOvk = senderFvk.getOvk();
      IncomingViewingKey senderIvk = senderFvk.inViewingKey();
      byte[] rcm1 = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm1);
      PaymentAddress senderPaymentAddress1 = senderIvk.address(DiversifierT.random()).get();
      String senderPaymentAddressStr1 = KeyIo.encodePaymentAddress(senderPaymentAddress1);

      { //for mint1
        GrpcAPI.Note note = getNote(100, senderPaymentAddressStr1, rcm1, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(DEFAULT_OVK));
        paramBuilder.setFromAmount(BigInteger.valueOf(100).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();
        ShieldedVRC20Parameters mintParam = wallet.createShieldedContractParameters(privMintParams);

        byte[] mintInputData1 = abiEncodeForMint(mintParam, 100, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for transfer
        GrpcAPI.PrivateShieldedVRC20ParametersWithoutAsk.Builder privateVRC20Builder = GrpcAPI
            .PrivateShieldedVRC20ParametersWithoutAsk.newBuilder();
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder1 = GrpcAPI.SpendNoteVRC20.newBuilder();
        GrpcAPI.Note note1 = getNote(100, senderPaymentAddressStr1, rcm1, new byte[512]);
        byte[][] cm1 = new byte[1][32];
        //spendNote1
        Note senderNote1 = new Note(senderPaymentAddress1.getD(), senderPaymentAddress1.getPkD(),
            100, rcm1, new byte[512]);
        System.arraycopy(senderNote1.cm(), 0, cm1[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher1 = addSimpleMerkleVoucherContainer(tree, cm1);
        byte[] path1 = decodePath(voucher1.path().encode());
        byte[] anchor1 = voucher1.root().getContent().toByteArray();
        long position1 = voucher1.position();
        spendNoteBuilder1.setRoot(ByteString.copyFrom(anchor1));
        spendNoteBuilder1.setPath(ByteString.copyFrom(path1));
        spendNoteBuilder1.setPos(position1);
        spendNoteBuilder1.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder1.setNote(note1);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder1.build());

        //receiveNote1
        GrpcAPI.ReceiveNote.Builder revNoteBuilder1 = GrpcAPI.ReceiveNote.newBuilder();
        SpendingKey receiveSk1 = SpendingKey.random();
        FullViewingKey receiveFvk1 = receiveSk1.fullViewingKey();
        IncomingViewingKey receiveIvk1 = receiveFvk1.inViewingKey();
        PaymentAddress receivePaymentAddress1 = receiveIvk1.address(new DiversifierT()).get();
        String recPaymentAddressStr = KeyIo.encodePaymentAddress(receivePaymentAddress1);
        byte[] memo = new byte[512];
        byte[] rcm3 = Note.generateR();
        GrpcAPI.Note revNote = getNote(60, recPaymentAddressStr, rcm3, memo);
        revNoteBuilder1.setNote(revNote);
        privateVRC20Builder.addShieldedReceives(revNoteBuilder1.build());

        //receiveNote2
        GrpcAPI.ReceiveNote.Builder revNoteBuilder2 = GrpcAPI.ReceiveNote.newBuilder();
        SpendingKey receiveSk2 = SpendingKey.random();
        FullViewingKey receiveFvk2 = receiveSk2.fullViewingKey();
        IncomingViewingKey receiveIvk2 = receiveFvk2.inViewingKey();
        PaymentAddress receivePaymentAddress2 = receiveIvk2.address(new DiversifierT()).get();
        String recPaymentAddressStr2 = KeyIo.encodePaymentAddress(receivePaymentAddress2);
        byte[] rcm4 = Note.generateR();
        GrpcAPI.Note revNote2 = getNote(40, recPaymentAddressStr2, rcm4, memo);
        revNoteBuilder2.setNote(revNote2);
        privateVRC20Builder.addShieldedReceives(revNoteBuilder2.build());

        ExpandedSpendingKey expsk = senderSk.expandedSpendingKey();
        privateVRC20Builder.setAk(ByteString.copyFrom(senderFvk.getAk()));
        privateVRC20Builder.setNsk(ByteString.copyFrom(expsk.getNsk()));
        privateVRC20Builder.setOvk(ByteString.copyFrom(expsk.getOvk()));
        privateVRC20Builder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        GrpcAPI.ShieldedVRC20Parameters transferParam = wallet
            .createShieldedContractParametersWithoutAsk(privateVRC20Builder.build());

        //get the binding signature
        PrivateShieldedVRC20ParametersWithoutAsk ShieldedVRC20ParametersWithoutAsk =
            privateVRC20Builder
                .build();
        SpendAuthSigParameters.Builder signParamerters1 = SpendAuthSigParameters.newBuilder();
        signParamerters1
            .setAlpha(ShieldedVRC20ParametersWithoutAsk.getShieldedSpends(0).getAlpha());
        signParamerters1.setAsk(ByteString.copyFrom(expsk.getAsk()));
        signParamerters1.setTxHash(transferParam.getMessageHash());
        BytesMessage signMsg1 = wallet.createSpendAuthSig(signParamerters1.build());

        ShieldedVRC20TriggerContractParameters.Builder triggerParam =
            ShieldedVRC20TriggerContractParameters
                .newBuilder();
        triggerParam.setShieldedVRC20Parameters(transferParam);
        triggerParam.addSpendAuthoritySignature(signMsg1);
        BytesMessage triggerInput = wallet
            .getTriggerInputForShieldedVRC20Contract(triggerParam.build());
        logger.info(
            "trigger contract input: " + Hex.toHexString(triggerInput.getValue().toByteArray()));

        SpendDescription.Builder spendDesBuilder1 = SpendDescription.newBuilder();
        spendDesBuilder1.setSpendAuthoritySignature(signMsg1.getValue());
        spendDesBuilder1.setAnchor(transferParam.getSpendDescription(0).getAnchor());
        spendDesBuilder1.setRk(transferParam.getSpendDescription(0).getRk());
        spendDesBuilder1
            .setValueCommitment(transferParam.getSpendDescription(0).getValueCommitment());
        spendDesBuilder1.setZkproof(transferParam.getSpendDescription(0).getZkproof());
        spendDesBuilder1.setNullifier(transferParam.getSpendDescription(0).getNullifier());

        ShieldedVRC20Parameters.Builder bindingSigBuilder = ShieldedVRC20Parameters.newBuilder();
        bindingSigBuilder.addSpendDescription(spendDesBuilder1.build());
        bindingSigBuilder.addReceiveDescription(transferParam.getReceiveDescription(0));
        bindingSigBuilder.addReceiveDescription(transferParam.getReceiveDescription(1));
        bindingSigBuilder.setMessageHash(transferParam.getMessageHash());
        bindingSigBuilder.setBindingSignature(transferParam.getBindingSignature());
        bindingSigBuilder.setParameterType(transferParam.getParameterType());
        transferParam = bindingSigBuilder.build();

        byte[] inputData = abiEncodeForTransfer(transferParam, frontier, leafCount);
        Pair<Boolean, byte[]> contractResult = verifyTransfer(inputData);
        byte[] result = contractResult.getRight();
        Assert.assertEquals(1, result[31]);

        //update frontier and leafCount
        int idx = 32;
        for (int i = 0; i < 2; i++) {
          idx += 31;
          int slot = result[idx];
          idx += 1;
          if (slot == 0) {
            byte[] noteCommitment = transferParam.getReceiveDescription(i).getNoteCommitment()
                .toByteArray();
            System.arraycopy(noteCommitment, 0, frontier, 0, 32);
          } else {
            int destPos = slot * 32;
            int srcPos = (slot - 1) * 32 + idx;
            System.arraycopy(result, srcPos, frontier, destPos, 32);
          }
          idx += slot * 32;
          leafCount++;
        }

        byte[][] cm = new byte[2][32];
        for (int i = 0; i < 2; i++) {
          byte[] noteCommitment = transferParam.getReceiveDescription(i).getNoteCommitment()
              .toByteArray();
          System.arraycopy(noteCommitment, 0, cm[i], 0, 32);
        }
        IncrementalMerkleVoucherContainer voucher = addSimpleMerkleVoucherContainer(tree, cm);
        byte[] root = voucher.root().getContent().toByteArray();

        Assert.assertArrayEquals(root, Arrays.copyOfRange(result, idx, idx + 32));
      }
    }
  }

  @Ignore
  @Test
  public void createShieldedContractParametersWithouAskForTransfer2v1()
      throws Exception {
    int totalCountNum = 2;
    long leafCount = 0;
    byte[] frontier = new byte[32 * 33];

    IncrementalMerkleTreeContainer tree = new IncrementalMerkleTreeContainer(
        new IncrementalMerkleTreeCapsule());
    for (int countNum = 0; countNum < totalCountNum; countNum++) {
      SpendingKey senderSk = SpendingKey.random();
      ExpandedSpendingKey senderExpsk = senderSk.expandedSpendingKey();
      FullViewingKey senderFvk = senderSk.fullViewingKey();
      byte[] senderOvk = senderFvk.getOvk();
      IncomingViewingKey senderIvk = senderFvk.inViewingKey();
      byte[] rcm1 = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm1);
      byte[] rcm2 = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm2);
      PaymentAddress senderPaymentAddress1 = senderIvk.address(DiversifierT.random()).get();
      PaymentAddress senderPaymentAddress2 = senderIvk.address(DiversifierT.random()).get();
      String senderPaymentAddressStr1 = KeyIo.encodePaymentAddress(senderPaymentAddress1);
      String senderPaymentAddressStr2 = KeyIo.encodePaymentAddress(senderPaymentAddress2);

      { //for mint1
        GrpcAPI.Note note = getNote(30, senderPaymentAddressStr1, rcm1, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(DEFAULT_OVK));
        paramBuilder.setFromAmount(BigInteger.valueOf(30).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();
        ShieldedVRC20Parameters mintParam = wallet.createShieldedContractParameters(privMintParams);

        byte[] mintInputData1 = abiEncodeForMint(mintParam, 30, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for mint2
        GrpcAPI.Note note = getNote(70, senderPaymentAddressStr2, rcm2, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(DEFAULT_OVK));
        paramBuilder.setFromAmount(BigInteger.valueOf(70).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();
        ShieldedVRC20Parameters mintParam = wallet.createShieldedContractParameters(privMintParams);

        byte[] mintInputData1 = abiEncodeForMint(mintParam, 70, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for transfer
        GrpcAPI.PrivateShieldedVRC20ParametersWithoutAsk.Builder privateVRC20Builder = GrpcAPI
            .PrivateShieldedVRC20ParametersWithoutAsk.newBuilder();
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder1 = GrpcAPI.SpendNoteVRC20.newBuilder();
        GrpcAPI.Note note1 = getNote(30, senderPaymentAddressStr1, rcm1, new byte[512]);
        byte[][] cm1 = new byte[1][32];
        //spendNote1
        Note senderNote1 = new Note(senderPaymentAddress1.getD(), senderPaymentAddress1.getPkD(),
            30, rcm1, new byte[512]);
        System.arraycopy(senderNote1.cm(), 0, cm1[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher1 = addSimpleMerkleVoucherContainer(tree, cm1);
        byte[] path1 = decodePath(voucher1.path().encode());
        byte[] anchor1 = voucher1.root().getContent().toByteArray();
        long position1 = voucher1.position();
        spendNoteBuilder1.setRoot(ByteString.copyFrom(anchor1));
        spendNoteBuilder1.setPath(ByteString.copyFrom(path1));
        spendNoteBuilder1.setPos(position1);
        spendNoteBuilder1.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder1.setNote(note1);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder1.build());

        //spendNote2
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder2 = GrpcAPI.SpendNoteVRC20.newBuilder();
        Note senderNote2 = new Note(senderPaymentAddress2.getD(), senderPaymentAddress2.getPkD(),
            70, rcm2, new byte[512]);
        GrpcAPI.Note note2 = getNote(70, senderPaymentAddressStr2, rcm2, new byte[512]);
        byte[][] cm2 = new byte[1][32];
        System.arraycopy(senderNote2.cm(), 0, cm2[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher2 = addSimpleMerkleVoucherContainer(tree, cm2);
        byte[] path2 = decodePath(voucher2.path().encode());
        byte[] anchor2 = voucher2.root().getContent().toByteArray();
        long position2 = voucher2.position();
        spendNoteBuilder2.setRoot(ByteString.copyFrom(anchor2));
        spendNoteBuilder2.setPath(ByteString.copyFrom(path2));
        spendNoteBuilder2.setPos(position2);
        spendNoteBuilder2.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder2.setNote(note2);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder2.build());

        //receiveNote1
        GrpcAPI.ReceiveNote.Builder revNoteBuilder1 = GrpcAPI.ReceiveNote.newBuilder();
        SpendingKey receiveSk1 = SpendingKey.random();
        FullViewingKey receiveFvk1 = receiveSk1.fullViewingKey();
        IncomingViewingKey receiveIvk1 = receiveFvk1.inViewingKey();
        PaymentAddress receivePaymentAddress1 = receiveIvk1.address(new DiversifierT()).get();
        String recPaymentAddressStr = KeyIo.encodePaymentAddress(receivePaymentAddress1);
        byte[] memo = new byte[512];
        byte[] rcm3 = Note.generateR();
        GrpcAPI.Note revNote = getNote(100, recPaymentAddressStr, rcm3, memo);
        revNoteBuilder1.setNote(revNote);
        privateVRC20Builder.addShieldedReceives(revNoteBuilder1.build());

        ExpandedSpendingKey expsk = senderSk.expandedSpendingKey();
        privateVRC20Builder.setAk(ByteString.copyFrom(senderFvk.getAk()));
        privateVRC20Builder.setNsk(ByteString.copyFrom(expsk.getNsk()));
        privateVRC20Builder.setOvk(ByteString.copyFrom(expsk.getOvk()));
        privateVRC20Builder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        GrpcAPI.ShieldedVRC20Parameters transferParam = wallet
            .createShieldedContractParametersWithoutAsk(privateVRC20Builder.build());

        //get the binding signature
        PrivateShieldedVRC20ParametersWithoutAsk ShieldedVRC20ParametersWithoutAsk =
            privateVRC20Builder
                .build();
        SpendAuthSigParameters.Builder signParamerters1 = SpendAuthSigParameters.newBuilder();
        signParamerters1
            .setAlpha(ShieldedVRC20ParametersWithoutAsk.getShieldedSpends(0).getAlpha());
        signParamerters1.setAsk(ByteString.copyFrom(expsk.getAsk()));
        signParamerters1.setTxHash(transferParam.getMessageHash());
        BytesMessage signMsg1 = wallet.createSpendAuthSig(signParamerters1.build());
        SpendDescription.Builder spendDesBuilder1 = SpendDescription.newBuilder();
        spendDesBuilder1.setSpendAuthoritySignature(signMsg1.getValue());
        spendDesBuilder1.setAnchor(transferParam.getSpendDescription(0).getAnchor());
        spendDesBuilder1.setRk(transferParam.getSpendDescription(0).getRk());
        spendDesBuilder1
            .setValueCommitment(transferParam.getSpendDescription(0).getValueCommitment());
        spendDesBuilder1.setZkproof(transferParam.getSpendDescription(0).getZkproof());
        spendDesBuilder1.setNullifier(transferParam.getSpendDescription(0).getNullifier());

        SpendAuthSigParameters.Builder signParamerters2 = SpendAuthSigParameters.newBuilder();
        signParamerters2
            .setAlpha(ShieldedVRC20ParametersWithoutAsk.getShieldedSpends(1).getAlpha());
        signParamerters2.setAsk(ByteString.copyFrom(expsk.getAsk()));
        signParamerters2.setTxHash(transferParam.getMessageHash());
        BytesMessage signMsg2 = wallet.createSpendAuthSig(signParamerters2.build());

        ShieldedVRC20TriggerContractParameters.Builder triggerParam =
            ShieldedVRC20TriggerContractParameters
                .newBuilder();
        triggerParam.setShieldedVRC20Parameters(transferParam);
        triggerParam.addSpendAuthoritySignature(signMsg1);
        triggerParam.addSpendAuthoritySignature(signMsg2);
        BytesMessage triggerInput = wallet
            .getTriggerInputForShieldedVRC20Contract(triggerParam.build());
        logger.info(
            "trigger contract input: " + Hex.toHexString(triggerInput.getValue().toByteArray()));

        SpendDescription.Builder spendDesBuilder2 = SpendDescription.newBuilder();
        spendDesBuilder2.setSpendAuthoritySignature(signMsg2.getValue());
        spendDesBuilder2.setAnchor(transferParam.getSpendDescription(1).getAnchor());
        spendDesBuilder2.setRk(transferParam.getSpendDescription(1).getRk());
        spendDesBuilder2
            .setValueCommitment(transferParam.getSpendDescription(1).getValueCommitment());
        spendDesBuilder2.setZkproof(transferParam.getSpendDescription(1).getZkproof());
        spendDesBuilder2.setNullifier(transferParam.getSpendDescription(1).getNullifier());

        ShieldedVRC20Parameters.Builder bindingSigBuilder = ShieldedVRC20Parameters.newBuilder();
        bindingSigBuilder.addSpendDescription(spendDesBuilder1.build());
        bindingSigBuilder.addSpendDescription(spendDesBuilder2.build());
        bindingSigBuilder.addReceiveDescription(transferParam.getReceiveDescription(0));
        bindingSigBuilder.setMessageHash(transferParam.getMessageHash());
        bindingSigBuilder.setBindingSignature(transferParam.getBindingSignature());
        bindingSigBuilder.setParameterType(transferParam.getParameterType());
        transferParam = bindingSigBuilder.build();

        byte[] inputData = abiEncodeForTransfer(transferParam, frontier, leafCount);
        Pair<Boolean, byte[]> contractResult = verifyTransfer(inputData);
        byte[] result = contractResult.getRight();
        Assert.assertEquals(1, result[31]);

        //update frontier and leafCount
        int idx = 63;
        int slot = result[idx];
        if (slot == 0) {
          byte[] noteCommitment = transferParam.getReceiveDescription(0).getNoteCommitment()
              .toByteArray();
          System.arraycopy(noteCommitment, 0, frontier, 0, 32);
        } else {
          int destPos = slot * 32;
          int srcPos = (slot - 1) * 32 + idx + 1;
          System.arraycopy(result, srcPos, frontier, destPos, 32);
        }
        idx += slot * 32 + 1;
        leafCount++;

        byte[][] cm = new byte[1][32];
        for (int i = 0; i < 1; i++) {
          byte[] noteCommitment = transferParam.getReceiveDescription(i).getNoteCommitment()
              .toByteArray();
          System.arraycopy(noteCommitment, 0, cm[i], 0, 32);
        }
        IncrementalMerkleVoucherContainer voucher = addSimpleMerkleVoucherContainer(tree, cm);
        byte[] root = voucher.root().getContent().toByteArray();

        Assert.assertArrayEquals(root, Arrays.copyOfRange(result, idx, idx + 32));
      }
    }
  }

  @Ignore
  @Test
  public void createShieldedContractParametersWithouAskForTransfer2v2()
      throws Exception {
    int totalCountNum = 2;
    long leafCount = 0;
    byte[] frontier = new byte[32 * 33];

    IncrementalMerkleTreeContainer tree = new IncrementalMerkleTreeContainer(
        new IncrementalMerkleTreeCapsule());
    for (int countNum = 0; countNum < totalCountNum; countNum++) {
      SpendingKey senderSk = SpendingKey.random();
      ExpandedSpendingKey senderExpsk = senderSk.expandedSpendingKey();
      FullViewingKey senderFvk = senderSk.fullViewingKey();
      byte[] senderOvk = senderFvk.getOvk();
      IncomingViewingKey senderIvk = senderFvk.inViewingKey();
      byte[] rcm1 = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm1);
      byte[] rcm2 = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm2);
      PaymentAddress senderPaymentAddress1 = senderIvk.address(DiversifierT.random()).get();
      PaymentAddress senderPaymentAddress2 = senderIvk.address(DiversifierT.random()).get();
      String senderPaymentAddressStr1 = KeyIo.encodePaymentAddress(senderPaymentAddress1);
      String senderPaymentAddressStr2 = KeyIo.encodePaymentAddress(senderPaymentAddress2);

      { //for mint1
        GrpcAPI.Note note = getNote(30, senderPaymentAddressStr1, rcm1, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(DEFAULT_OVK));
        paramBuilder.setFromAmount(BigInteger.valueOf(30).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();
        ShieldedVRC20Parameters mintParam = wallet.createShieldedContractParameters(privMintParams);

        byte[] mintInputData1 = abiEncodeForMint(mintParam, 30, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for mint2
        GrpcAPI.Note note = getNote(70, senderPaymentAddressStr2, rcm2, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(DEFAULT_OVK));
        paramBuilder.setFromAmount(BigInteger.valueOf(70).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();
        ShieldedVRC20Parameters mintParam = wallet.createShieldedContractParameters(privMintParams);

        byte[] mintInputData1 = abiEncodeForMint(mintParam, 70, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for transfer
        GrpcAPI.PrivateShieldedVRC20ParametersWithoutAsk.Builder privateVRC20Builder = GrpcAPI
            .PrivateShieldedVRC20ParametersWithoutAsk.newBuilder();
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder1 = GrpcAPI.SpendNoteVRC20.newBuilder();
        GrpcAPI.Note note1 = getNote(30, senderPaymentAddressStr1, rcm1, new byte[512]);
        byte[][] cm1 = new byte[1][32];
        //spendNote1
        Note senderNote1 = new Note(senderPaymentAddress1.getD(), senderPaymentAddress1.getPkD(),
            30, rcm1, new byte[512]);
        System.arraycopy(senderNote1.cm(), 0, cm1[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher1 = addSimpleMerkleVoucherContainer(tree, cm1);
        byte[] path1 = decodePath(voucher1.path().encode());
        byte[] anchor1 = voucher1.root().getContent().toByteArray();
        long position1 = voucher1.position();
        spendNoteBuilder1.setRoot(ByteString.copyFrom(anchor1));
        spendNoteBuilder1.setPath(ByteString.copyFrom(path1));
        spendNoteBuilder1.setPos(position1);
        spendNoteBuilder1.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder1.setNote(note1);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder1.build());

        //spendNote2
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder2 = GrpcAPI.SpendNoteVRC20.newBuilder();
        Note senderNote2 = new Note(senderPaymentAddress2.getD(), senderPaymentAddress2.getPkD(),
            70, rcm2, new byte[512]);
        GrpcAPI.Note note2 = getNote(70, senderPaymentAddressStr2, rcm2, new byte[512]);
        byte[][] cm2 = new byte[1][32];
        System.arraycopy(senderNote2.cm(), 0, cm2[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher2 = addSimpleMerkleVoucherContainer(tree, cm2);
        byte[] path2 = decodePath(voucher2.path().encode());
        byte[] anchor2 = voucher2.root().getContent().toByteArray();
        long position2 = voucher2.position();
        spendNoteBuilder2.setRoot(ByteString.copyFrom(anchor2));
        spendNoteBuilder2.setPath(ByteString.copyFrom(path2));
        spendNoteBuilder2.setPos(position2);
        spendNoteBuilder2.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder2.setNote(note2);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder2.build());

        //receiveNote1
        GrpcAPI.ReceiveNote.Builder revNoteBuilder1 = GrpcAPI.ReceiveNote.newBuilder();
        SpendingKey receiveSk1 = SpendingKey.random();
        FullViewingKey receiveFvk1 = receiveSk1.fullViewingKey();
        IncomingViewingKey receiveIvk1 = receiveFvk1.inViewingKey();
        PaymentAddress receivePaymentAddress1 = receiveIvk1.address(new DiversifierT()).get();
        String recPaymentAddressStr = KeyIo.encodePaymentAddress(receivePaymentAddress1);
        byte[] memo = new byte[512];
        byte[] rcm3 = Note.generateR();
        GrpcAPI.Note revNote = getNote(60, recPaymentAddressStr, rcm3, memo);
        revNoteBuilder1.setNote(revNote);
        privateVRC20Builder.addShieldedReceives(revNoteBuilder1.build());

        //receiveNote2
        GrpcAPI.ReceiveNote.Builder revNoteBuilder2 = GrpcAPI.ReceiveNote.newBuilder();
        SpendingKey receiveSk2 = SpendingKey.random();
        FullViewingKey receiveFvk2 = receiveSk2.fullViewingKey();
        IncomingViewingKey receiveIvk2 = receiveFvk2.inViewingKey();
        PaymentAddress receivePaymentAddress2 = receiveIvk2.address(new DiversifierT()).get();
        String recPaymentAddressStr2 = KeyIo.encodePaymentAddress(receivePaymentAddress2);
        byte[] rcm4 = Note.generateR();
        GrpcAPI.Note revNote2 = getNote(40, recPaymentAddressStr2, rcm4, memo);
        revNoteBuilder2.setNote(revNote2);
        privateVRC20Builder.addShieldedReceives(revNoteBuilder2.build());

        ExpandedSpendingKey expsk = senderSk.expandedSpendingKey();
        privateVRC20Builder.setAk(ByteString.copyFrom(senderFvk.getAk()));
        privateVRC20Builder.setNsk(ByteString.copyFrom(expsk.getNsk()));
        privateVRC20Builder.setOvk(ByteString.copyFrom(expsk.getOvk()));
        privateVRC20Builder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        GrpcAPI.ShieldedVRC20Parameters transferParam = wallet
            .createShieldedContractParametersWithoutAsk(privateVRC20Builder.build());

        //get the binding signature
        PrivateShieldedVRC20ParametersWithoutAsk ShieldedVRC20ParametersWithoutAsk =
            privateVRC20Builder
                .build();
        SpendAuthSigParameters.Builder signParamerters1 = SpendAuthSigParameters.newBuilder();
        signParamerters1
            .setAlpha(ShieldedVRC20ParametersWithoutAsk.getShieldedSpends(0).getAlpha());
        signParamerters1.setAsk(ByteString.copyFrom(expsk.getAsk()));
        signParamerters1.setTxHash(transferParam.getMessageHash());
        BytesMessage signMsg1 = wallet.createSpendAuthSig(signParamerters1.build());
        SpendDescription.Builder spendDesBuilder1 = SpendDescription.newBuilder();
        spendDesBuilder1.setSpendAuthoritySignature(signMsg1.getValue());
        spendDesBuilder1.setAnchor(transferParam.getSpendDescription(0).getAnchor());
        spendDesBuilder1.setRk(transferParam.getSpendDescription(0).getRk());
        spendDesBuilder1
            .setValueCommitment(transferParam.getSpendDescription(0).getValueCommitment());
        spendDesBuilder1.setZkproof(transferParam.getSpendDescription(0).getZkproof());
        spendDesBuilder1.setNullifier(transferParam.getSpendDescription(0).getNullifier());

        SpendAuthSigParameters.Builder signParamerters2 = SpendAuthSigParameters.newBuilder();
        signParamerters2
            .setAlpha(ShieldedVRC20ParametersWithoutAsk.getShieldedSpends(1).getAlpha());
        signParamerters2.setAsk(ByteString.copyFrom(expsk.getAsk()));
        signParamerters2.setTxHash(transferParam.getMessageHash());
        BytesMessage signMsg2 = wallet.createSpendAuthSig(signParamerters2.build());

        ShieldedVRC20TriggerContractParameters.Builder triggerParam =
            ShieldedVRC20TriggerContractParameters
                .newBuilder();
        triggerParam.setShieldedVRC20Parameters(transferParam);
        triggerParam.addSpendAuthoritySignature(signMsg1);
        triggerParam.addSpendAuthoritySignature(signMsg2);
        BytesMessage triggerInput = wallet
            .getTriggerInputForShieldedVRC20Contract(triggerParam.build());
        logger.info(
            "trigger contract input: " + Hex.toHexString(triggerInput.getValue().toByteArray()));

        SpendDescription.Builder spendDesBuilder2 = SpendDescription.newBuilder();
        spendDesBuilder2.setSpendAuthoritySignature(signMsg2.getValue());
        spendDesBuilder2.setAnchor(transferParam.getSpendDescription(1).getAnchor());
        spendDesBuilder2.setRk(transferParam.getSpendDescription(1).getRk());
        spendDesBuilder2
            .setValueCommitment(transferParam.getSpendDescription(1).getValueCommitment());
        spendDesBuilder2.setZkproof(transferParam.getSpendDescription(1).getZkproof());
        spendDesBuilder2.setNullifier(transferParam.getSpendDescription(1).getNullifier());

        ShieldedVRC20Parameters.Builder bindingSigBuilder = ShieldedVRC20Parameters.newBuilder();
        bindingSigBuilder.addSpendDescription(spendDesBuilder1.build());
        bindingSigBuilder.addSpendDescription(spendDesBuilder2.build());
        bindingSigBuilder.addReceiveDescription(transferParam.getReceiveDescription(0));
        bindingSigBuilder.addReceiveDescription(transferParam.getReceiveDescription(1));
        bindingSigBuilder.setMessageHash(transferParam.getMessageHash());
        bindingSigBuilder.setBindingSignature(transferParam.getBindingSignature());
        bindingSigBuilder.setParameterType(transferParam.getParameterType());
        transferParam = bindingSigBuilder.build();

        byte[] inputData = abiEncodeForTransfer(transferParam, frontier, leafCount);
        Pair<Boolean, byte[]> contractResult = verifyTransfer(inputData);
        byte[] result = contractResult.getRight();
        Assert.assertEquals(1, result[31]);

        //update frontier and leafCount
        int idx = 32;
        for (int i = 0; i < 2; i++) {
          idx += 31;
          int slot = result[idx];
          idx += 1;
          if (slot == 0) {
            byte[] noteCommitment = transferParam.getReceiveDescription(i).getNoteCommitment()
                .toByteArray();
            System.arraycopy(noteCommitment, 0, frontier, 0, 32);
          } else {
            int destPos = slot * 32;
            int srcPos = (slot - 1) * 32 + idx;
            System.arraycopy(result, srcPos, frontier, destPos, 32);
          }
          idx += slot * 32;
          leafCount++;
        }

        byte[][] cm = new byte[2][32];
        for (int i = 0; i < 2; i++) {
          byte[] noteCommitment = transferParam.getReceiveDescription(i).getNoteCommitment()
              .toByteArray();
          System.arraycopy(noteCommitment, 0, cm[i], 0, 32);
        }
        IncrementalMerkleVoucherContainer voucher = addSimpleMerkleVoucherContainer(tree, cm);
        byte[] root = voucher.root().getContent().toByteArray();

        Assert.assertArrayEquals(root, Arrays.copyOfRange(result, idx, idx + 32));
      }
    }
  }

  @Ignore
  @Test
  public void createShieldedContractParametersWithoutAskForBurn()
      throws Exception {
    int totalCountNum = 2;
    long leafCount = 0;
    long value = 100L;
    byte[] frontier = new byte[32 * 33];

    IncrementalMerkleTreeContainer tree = new IncrementalMerkleTreeContainer(
        new IncrementalMerkleTreeCapsule());
    for (int countNum = 0; countNum < totalCountNum; countNum++) {
      SpendingKey senderSk = SpendingKey.random();
      ExpandedSpendingKey senderExpsk = senderSk.expandedSpendingKey();
      FullViewingKey senderFvk = senderSk.fullViewingKey();
      byte[] senderOvk = senderFvk.getOvk();
      IncomingViewingKey senderIvk = senderFvk.inViewingKey();
      byte[] rcm = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm);
      PaymentAddress senderPaymentAddress = senderIvk.address(DiversifierT.random()).get();
      String senderPaymentAddressStr = KeyIo.encodePaymentAddress(senderPaymentAddress);

      { //for mint1
        GrpcAPI.Note note = getNote(value, senderPaymentAddressStr, rcm, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(senderFvk.getOvk()));
        paramBuilder.setFromAmount(BigInteger.valueOf(value).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();

        ShieldedVRC20Parameters minParam = wallet.createShieldedContractParameters(privMintParams);
        byte[] mintInputData1 = abiEncodeForMint(minParam, value, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for burn
        GrpcAPI.PrivateShieldedVRC20ParametersWithoutAsk.Builder privateVRC20Builder = GrpcAPI
            .PrivateShieldedVRC20ParametersWithoutAsk.newBuilder();
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder = GrpcAPI.SpendNoteVRC20.newBuilder();
        GrpcAPI.Note note = getNote(value, senderPaymentAddressStr, rcm, new byte[512]);
        byte[][] cm1 = new byte[1][32];
        //spendNote1
        Note senderNote1 = new Note(senderPaymentAddress.getD(), senderPaymentAddress.getPkD(),
            value, rcm, new byte[512]);
        System.arraycopy(senderNote1.cm(), 0, cm1[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher1 = addSimpleMerkleVoucherContainer(tree, cm1);
        byte[] path1 = decodePath(voucher1.path().encode());
        byte[] anchor1 = voucher1.root().getContent().toByteArray();
        long position1 = voucher1.position();
        spendNoteBuilder.setRoot(ByteString.copyFrom(anchor1));
        spendNoteBuilder.setPath(ByteString.copyFrom(path1));
        spendNoteBuilder.setPos(position1);
        spendNoteBuilder.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder.setNote(note);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder.build());

        ExpandedSpendingKey expsk = senderSk.expandedSpendingKey();
        privateVRC20Builder.setAk(ByteString.copyFrom(senderFvk.getAk()));
        privateVRC20Builder.setNsk(ByteString.copyFrom(expsk.getNsk()));
        privateVRC20Builder.setToAmount(BigInteger.valueOf(value).toString());
        privateVRC20Builder.setTransparentToAddress(ByteString.copyFrom(PUBLIC_TO_ADDRESS));
        privateVRC20Builder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        GrpcAPI.ShieldedVRC20Parameters burnParam = wallet
            .createShieldedContractParametersWithoutAsk(privateVRC20Builder.build());

        //get the binding signature
        PrivateShieldedVRC20ParametersWithoutAsk ShieldedVRC20ParametersWithoutAsk =
            privateVRC20Builder
                .build();
        SpendAuthSigParameters.Builder signParamerters1 = SpendAuthSigParameters.newBuilder();
        signParamerters1
            .setAlpha(ShieldedVRC20ParametersWithoutAsk.getShieldedSpends(0).getAlpha());
        signParamerters1.setAsk(ByteString.copyFrom(expsk.getAsk()));
        signParamerters1.setTxHash(burnParam.getMessageHash());
        BytesMessage signMsg1 = wallet.createSpendAuthSig(signParamerters1.build());

        ShieldedVRC20TriggerContractParameters.Builder triggerParam =
            ShieldedVRC20TriggerContractParameters
                .newBuilder();
        triggerParam.setShieldedVRC20Parameters(burnParam);
        triggerParam.addSpendAuthoritySignature(signMsg1);
        triggerParam.setAmount(BigInteger.valueOf(value).toString());
        triggerParam.setTransparentToAddress(ByteString.copyFrom(PUBLIC_TO_ADDRESS));
        BytesMessage triggerInput = wallet
            .getTriggerInputForShieldedVRC20Contract(triggerParam.build());
        logger.info(
            "trigger contract input: " + Hex.toHexString(triggerInput.getValue().toByteArray()));

        SpendDescription.Builder spendDesBuilder1 = SpendDescription.newBuilder();
        spendDesBuilder1.setSpendAuthoritySignature(signMsg1.getValue());
        spendDesBuilder1.setAnchor(burnParam.getSpendDescription(0).getAnchor());
        spendDesBuilder1.setRk(burnParam.getSpendDescription(0).getRk());
        spendDesBuilder1
            .setValueCommitment(burnParam.getSpendDescription(0).getValueCommitment());
        spendDesBuilder1.setZkproof(burnParam.getSpendDescription(0).getZkproof());
        spendDesBuilder1.setNullifier(burnParam.getSpendDescription(0).getNullifier());

        ShieldedVRC20Parameters.Builder bindingSigBuilder = ShieldedVRC20Parameters.newBuilder();
        bindingSigBuilder.addSpendDescription(spendDesBuilder1.build());
        bindingSigBuilder.setMessageHash(burnParam.getMessageHash());
        bindingSigBuilder.setBindingSignature(burnParam.getBindingSignature());
        bindingSigBuilder.setParameterType(burnParam.getParameterType());
        burnParam = bindingSigBuilder.build();

        byte[] inputData = abiEncodeForBurn(burnParam, value);
        Pair<Boolean, byte[]> contractResult = burnContract.execute(inputData);
        byte[] result = contractResult.getRight();
        Assert.assertEquals(1, result[31]);
      }
    }
  }

  @Ignore
  @Test
  public void createShieldedContractParametersWithoutAskForBurn1v2()
      throws Exception {
    int totalCountNum = 2;
    long leafCount = 0;
    long value = 100L;
    byte[] frontier = new byte[32 * 33];

    IncrementalMerkleTreeContainer tree = new IncrementalMerkleTreeContainer(
        new IncrementalMerkleTreeCapsule());
    for (int countNum = 0; countNum < totalCountNum; countNum++) {
      SpendingKey senderSk = SpendingKey.random();
      ExpandedSpendingKey senderExpsk = senderSk.expandedSpendingKey();
      FullViewingKey senderFvk = senderSk.fullViewingKey();
      byte[] senderOvk = senderFvk.getOvk();
      IncomingViewingKey senderIvk = senderFvk.inViewingKey();
      byte[] rcm = new byte[32];
      JLibrustzcash.librustzcashSaplingGenerateR(rcm);
      PaymentAddress senderPaymentAddress = senderIvk.address(DiversifierT.random()).get();
      String senderPaymentAddressStr = KeyIo.encodePaymentAddress(senderPaymentAddress);

      { //for mint1
        GrpcAPI.Note note = getNote(value, senderPaymentAddressStr, rcm, new byte[512]);
        GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
        GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
            .PrivateShieldedVRC20Parameters.newBuilder();
        revNoteBuilder.setNote(note);
        paramBuilder.setOvk(ByteString.copyFrom(senderFvk.getOvk()));
        paramBuilder.setFromAmount(BigInteger.valueOf(value).toString());
        paramBuilder.addShieldedReceives(revNoteBuilder.build());
        paramBuilder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
        PrivateShieldedVRC20Parameters privMintParams = paramBuilder.build();

        ShieldedVRC20Parameters minParam = wallet.createShieldedContractParameters(privMintParams);
        byte[] mintInputData1 = abiEncodeForMint(minParam, value, frontier, leafCount);
        Pair<Boolean, byte[]> mintContractResult1 = mintContract.execute(mintInputData1);
        byte[] mintResult1 = mintContractResult1.getRight();
        Assert.assertEquals(1, mintResult1[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = mintResult1[63];
        if (slot == 0) {
          System.arraycopy(mintInputData1, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(mintResult1, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }

      { //for burn
        GrpcAPI.PrivateShieldedVRC20ParametersWithoutAsk.Builder privateVRC20Builder = GrpcAPI
            .PrivateShieldedVRC20ParametersWithoutAsk.newBuilder();
        GrpcAPI.SpendNoteVRC20.Builder spendNoteBuilder = GrpcAPI.SpendNoteVRC20.newBuilder();
        GrpcAPI.Note note = getNote(value, senderPaymentAddressStr, rcm, new byte[512]);
        byte[][] cm1 = new byte[1][32];
        //spendNote1
        Note senderNote1 = new Note(senderPaymentAddress.getD(), senderPaymentAddress.getPkD(),
            value, rcm, new byte[512]);
        System.arraycopy(senderNote1.cm(), 0, cm1[0], 0, 32);
        IncrementalMerkleVoucherContainer voucher1 = addSimpleMerkleVoucherContainer(tree, cm1);
        byte[] path1 = decodePath(voucher1.path().encode());
        byte[] anchor1 = voucher1.root().getContent().toByteArray();
        long position1 = voucher1.position();
        spendNoteBuilder.setRoot(ByteString.copyFrom(anchor1));
        spendNoteBuilder.setPath(ByteString.copyFrom(path1));
        spendNoteBuilder.setPos(position1);
        spendNoteBuilder.setAlpha(ByteString.copyFrom(Note.generateR()));
        spendNoteBuilder.setNote(note);
        privateVRC20Builder.addShieldedSpends(spendNoteBuilder.build());

        ExpandedSpendingKey expsk = senderSk.expandedSpendingKey();
        privateVRC20Builder.setAk(ByteString.copyFrom(senderFvk.getAk()));
        privateVRC20Builder.setNsk(ByteString.copyFrom(expsk.getNsk()));
        privateVRC20Builder.setToAmount(BigInteger.valueOf(60).toString());
        privateVRC20Builder.setTransparentToAddress(ByteString.copyFrom(PUBLIC_TO_ADDRESS));
        privateVRC20Builder
            .setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));

        //receiveNote
        GrpcAPI.ReceiveNote.Builder revNoteBuilder2 = GrpcAPI.ReceiveNote.newBuilder();
        SpendingKey receiveSk2 = SpendingKey.random();
        FullViewingKey receiveFvk2 = receiveSk2.fullViewingKey();
        IncomingViewingKey receiveIvk2 = receiveFvk2.inViewingKey();
        PaymentAddress receivePaymentAddress2 = receiveIvk2.address(new DiversifierT()).get();
        String recPaymentAddressStr2 = KeyIo.encodePaymentAddress(receivePaymentAddress2);
        byte[] rcm4 = Note.generateR();
        byte[] memo = new byte[512];
        GrpcAPI.Note revNote2 = getNote(40, recPaymentAddressStr2, rcm4, memo);
        revNoteBuilder2.setNote(revNote2);
        privateVRC20Builder.addShieldedReceives(revNoteBuilder2.build());

        GrpcAPI.ShieldedVRC20Parameters burnParam = wallet
            .createShieldedContractParametersWithoutAsk(privateVRC20Builder.build());

        //get the binding signature
        PrivateShieldedVRC20ParametersWithoutAsk ShieldedVRC20ParametersWithoutAsk =
            privateVRC20Builder
                .build();
        SpendAuthSigParameters.Builder signParamerters1 = SpendAuthSigParameters.newBuilder();
        signParamerters1
            .setAlpha(ShieldedVRC20ParametersWithoutAsk.getShieldedSpends(0).getAlpha());
        signParamerters1.setAsk(ByteString.copyFrom(expsk.getAsk()));
        signParamerters1.setTxHash(burnParam.getMessageHash());
        BytesMessage signMsg1 = wallet.createSpendAuthSig(signParamerters1.build());

        ShieldedVRC20TriggerContractParameters.Builder triggerParam =
            ShieldedVRC20TriggerContractParameters
                .newBuilder();
        triggerParam.setShieldedVRC20Parameters(burnParam);
        triggerParam.addSpendAuthoritySignature(signMsg1);
        triggerParam.setAmount(BigInteger.valueOf(value).toString());
        triggerParam.setTransparentToAddress(ByteString.copyFrom(PUBLIC_TO_ADDRESS));
        BytesMessage triggerInput = wallet
            .getTriggerInputForShieldedVRC20Contract(triggerParam.build());
        logger.info(
            "trigger contract input: " + Hex.toHexString(triggerInput.getValue().toByteArray()));

        SpendDescription.Builder spendDesBuilder1 = SpendDescription.newBuilder();
        spendDesBuilder1.setSpendAuthoritySignature(signMsg1.getValue());
        spendDesBuilder1.setAnchor(burnParam.getSpendDescription(0).getAnchor());
        spendDesBuilder1.setRk(burnParam.getSpendDescription(0).getRk());
        spendDesBuilder1
            .setValueCommitment(burnParam.getSpendDescription(0).getValueCommitment());
        spendDesBuilder1.setZkproof(burnParam.getSpendDescription(0).getZkproof());
        spendDesBuilder1.setNullifier(burnParam.getSpendDescription(0).getNullifier());

        ShieldedVRC20Parameters.Builder bindingSigBuilder = ShieldedVRC20Parameters.newBuilder();
        bindingSigBuilder.addSpendDescription(spendDesBuilder1.build());
        bindingSigBuilder.setMessageHash(burnParam.getMessageHash());
        bindingSigBuilder.setBindingSignature(burnParam.getBindingSignature());
        bindingSigBuilder.setParameterType(burnParam.getParameterType());
        burnParam = bindingSigBuilder.build();

        byte[] inputData = abiEncodeForBurn(burnParam, value);
        Pair<Boolean, byte[]> contractResult = burnContract.execute(inputData);
        byte[] result = contractResult.getRight();
        Assert.assertEquals(1, result[31]);

        //update frontier and leafCount
        //if slot == 0, frontier[0:31]=noteCommitment
        int slot = result[63];
        if (slot == 0) {
          System.arraycopy(result, 0, frontier, 0, 32);
        } else {
          int srcPos = (slot + 1) * 32;
          int destPos = slot * 32;
          System.arraycopy(result, srcPos, frontier, destPos, 32);
        }
        leafCount++;
      }
    }
  }

  @Ignore
  @Test
  public void getTriggerInputForForMint() throws Exception {
    LibrustzcashTest.librustzcashInitZksnarkParams();
    byte[] callerAddress = WalletClient.decodeFromBase58Check(pubAddress);
    SpendingKey sk = SpendingKey.random();
    ExpandedSpendingKey expsk = sk.expandedSpendingKey();
    byte[] ovk = expsk.getOvk();
    //ReceiveNote
    GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
    SpendingKey spendingKey = SpendingKey.random();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(DiversifierT.random()).get();
    long revValue = 50;
    byte[] memo = new byte[512];
    byte[] rcm = Note.generateR();
    String paymentAddressStr = KeyIo.encodePaymentAddress(paymentAddress);
    GrpcAPI.Note revNote = getNote(revValue, paymentAddressStr, rcm, memo);
    revNoteBuilder.setNote(revNote);

    GrpcAPI.PrivateShieldedVRC20ParametersWithoutAsk.Builder paramBuilder = GrpcAPI
        .PrivateShieldedVRC20ParametersWithoutAsk.newBuilder();
    paramBuilder.setOvk(ByteString.copyFrom(ovk));
    paramBuilder.setFromAmount(BigInteger.valueOf(revValue).toString());
    paramBuilder.addShieldedReceives(revNoteBuilder.build());
    paramBuilder.setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
    GrpcAPI.ShieldedVRC20Parameters vrc20MintParams = wallet
        .createShieldedContractParametersWithoutAsk(paramBuilder.build());

    GrpcAPI.PrivateShieldedVRC20ParametersWithoutAsk privateParams = paramBuilder.build();

    //verify receiveProof && bindingSignature
    boolean result;
    long ctx = JLibrustzcash.librustzcashSaplingVerificationCtxInit();
    ShieldContract.ReceiveDescription revDesc = vrc20MintParams.getReceiveDescription(0);
    try {
      result = JLibrustzcash.librustzcashSaplingCheckOutput(
          new LibrustzcashParam.CheckOutputParams(
              ctx,
              revDesc.getValueCommitment().toByteArray(),
              revDesc.getNoteCommitment().toByteArray(),
              revDesc.getEpk().toByteArray(),
              revDesc.getZkproof().toByteArray()));
      long valueBalance = -revValue;
      result &= JLibrustzcash.librustzcashSaplingFinalCheck(
          new LibrustzcashParam.FinalCheckParams(
              ctx,
              valueBalance,
              vrc20MintParams.getBindingSignature().toByteArray(),
              vrc20MintParams.getMessageHash().toByteArray()));
    } catch (Throwable any) {
      result = false;
    } finally {
      JLibrustzcash.librustzcashSaplingVerificationCtxFree(ctx);
    }
    Assert.assertTrue(result);

    ShieldedVRC20TriggerContractParameters.Builder triggerParam =
        ShieldedVRC20TriggerContractParameters
            .newBuilder();
    triggerParam.setShieldedVRC20Parameters(vrc20MintParams);
    triggerParam.setAmount(BigInteger.valueOf(revValue).toString());
    BytesMessage triggerInput = wallet
        .getTriggerInputForShieldedVRC20Contract(triggerParam.build());
    Assert.assertArrayEquals(triggerInput.getValue().toByteArray(),
        Hex.decode(vrc20MintParams.getTriggerContractInput()));
  }

  @Test
  public void testScanShieldedVRC20NotesByIvk() throws Exception {
    int statNum = 1;
    int endNum = 100;
    LibrustzcashTest.librustzcashInitZksnarkParams();
    SpendingKey sk = SpendingKey.decode(privateKey);
    FullViewingKey fvk = sk.fullViewingKey();
    byte[] ivk = fvk.inViewingKey().value;

    GrpcAPI.DecryptNotesVRC20 scannedNotes = wallet.scanShieldedVRC20NotesByIvk(
        statNum, endNum, SHIELDED_CONTRACT_ADDRESS, ivk, fvk.getAk(), fvk.getNk(), null);

    for (GrpcAPI.DecryptNotesVRC20.NoteTx noteTx : scannedNotes.getNoteTxsList()) {
      logger.info(noteTx.toString());
    }
  }

  @Test
  public void testscanShieldedVRC20NotesByOvk() throws Exception {
    int statNum = 9200;
    int endNum = 9240;
    SpendingKey sk = SpendingKey.decode(privateKey);
    FullViewingKey fvk = sk.fullViewingKey();

    GrpcAPI.DecryptNotesVRC20 scannedNotes = wallet.scanShieldedVRC20NotesByOvk(
        statNum, endNum, fvk.getOvk(), SHIELDED_CONTRACT_ADDRESS, null);

    for (GrpcAPI.DecryptNotesVRC20.NoteTx noteTx : scannedNotes.getNoteTxsList()) {
      logger.info(noteTx.toString());
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void isShieldedVRC20ContractNoteSpent() throws Exception {
    int statNum = 9200;
    int endNum = 9240;
    LibrustzcashTest.librustzcashInitZksnarkParams();
    SpendingKey sk = SpendingKey.decode(privateKey);
    FullViewingKey fvk = sk.fullViewingKey();
    byte[] ivk = fvk.inViewingKey().value;

    GrpcAPI.DecryptNotesVRC20 scannedNotes = wallet.scanShieldedVRC20NotesByIvk(
        statNum, endNum, SHIELDED_CONTRACT_ADDRESS, ivk, fvk.getAk(), fvk.getNk(), null);

    for (GrpcAPI.DecryptNotesVRC20.NoteTx noteTx : scannedNotes.getNoteTxsList()) {
      logger.info(noteTx.toString());
    }

    GrpcAPI.NfVRC20Parameters.Builder NfBuilfer;
    NfBuilfer = GrpcAPI.NfVRC20Parameters.newBuilder();
    NfBuilfer.setAk(ByteString.copyFrom(fvk.getAk()));
    NfBuilfer.setNk(ByteString.copyFrom(fvk.getNk()));
    NfBuilfer.setPosition(271);
    NfBuilfer.setShieldedVRC20ContractAddress(ByteString.copyFrom(SHIELDED_CONTRACT_ADDRESS));
    if (scannedNotes.getNoteTxsList().size() > 0) {
      NfBuilfer.setNote(scannedNotes.getNoteTxs(0).getNote());
    }

    GrpcAPI.NullifierResult result = wallet
        .isShieldedVRC20ContractNoteSpent(NfBuilfer.build());
    Assert.assertTrue(result.getIsSpent());
  }


  private byte[] abiEncodeForBurn(ShieldedVRC20Parameters params, long value) {
    byte[] mergedBytes;
    ShieldContract.SpendDescription spendDesc = params.getSpendDescription(0);
    mergedBytes = ByteUtil.merge(
        spendDesc.getNullifier().toByteArray(),
        spendDesc.getAnchor().toByteArray(),
        spendDesc.getValueCommitment().toByteArray(),
        spendDesc.getRk().toByteArray(),
        spendDesc.getZkproof().toByteArray(),
        spendDesc.getSpendAuthoritySignature().toByteArray(),
        longTo32Bytes(value),
        params.getBindingSignature().toByteArray(),
        params.getMessageHash().toByteArray()
    );
    return mergedBytes;
  }

  private Pair<Boolean, byte[]> verifyTransfer(byte[] input) {
    transferContract.getEntropyForData(input);
    transferContract.setVmShouldEndInUs(System.nanoTime() / 1000 + 50 * 1000);
    Pair<Boolean, byte[]> ret = transferContract.execute(input);
    return ret;
  }

  private IncrementalMerkleVoucherContainer addSimpleMerkleVoucherContainer(
      IncrementalMerkleTreeContainer tree, byte[][] cm)
      throws ZksnarkException {
    for (int i = 0; i < cm.length; i++) {
      PedersenHashCapsule compressCapsule = new PedersenHashCapsule();
      compressCapsule.setContent(ByteString.copyFrom(cm[i]));
      ShieldContract.PedersenHash a = compressCapsule.getInstance();
      tree.append(a);
    }
    IncrementalMerkleVoucherContainer voucher = tree.toVoucher();
    return voucher;
  }

  private byte[] decodePath(byte[] encodedPath) {
    Assert.assertEquals(1065, encodedPath.length);
    byte[] path = new byte[32 * 32];
    for (int i = 0; i < 32; i++) {
      System.arraycopy(encodedPath, (i * 33) + 2, path, i * 32, 32);
    }
    return path;
  }

  private GrpcAPI.PrivateShieldedVRC20Parameters mintParams(String privKey,
      long value, String contractAddr, byte[] rcm)
      throws ZksnarkException, ContractValidateException {
    LibrustzcashTest.librustzcashInitZksnarkParams();
    long fromAmount = value;
    SpendingKey sk = SpendingKey.decode(privKey);
    ExpandedSpendingKey expsk = sk.expandedSpendingKey();
    byte[] ask = expsk.getAsk();
    byte[] nsk = expsk.getNsk();
    byte[] ovk = expsk.getOvk();

    // ReceiveNote
    GrpcAPI.ReceiveNote.Builder revNoteBuilder = GrpcAPI.ReceiveNote.newBuilder();
    // SpendingKey spendingKey = SpendingKey.random();
    FullViewingKey fullViewingKey = sk.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(DiversifierT.random()).get();
    byte[] memo = new byte[512];
    if (ArrayUtils.isEmpty(rcm)) {
      rcm = Note.generateR();
    }
    String paymentAddressStr = KeyIo.encodePaymentAddress(paymentAddress);
    GrpcAPI.Note revNote = getNote(value, paymentAddressStr, rcm, memo);
    revNoteBuilder.setNote(revNote);
    byte[] contractAddress = WalletClient.decodeFromBase58Check(contractAddr);

    GrpcAPI.PrivateShieldedVRC20Parameters.Builder paramBuilder = GrpcAPI
        .PrivateShieldedVRC20Parameters.newBuilder();
    paramBuilder.setAsk(ByteString.copyFrom(ask));
    paramBuilder.setNsk(ByteString.copyFrom(nsk));
    paramBuilder.setOvk(ByteString.copyFrom(ovk));
    paramBuilder.setFromAmount(BigInteger.valueOf(fromAmount).toString());
    paramBuilder.addShieldedReceives(revNoteBuilder.build());
    paramBuilder.setShieldedVRC20ContractAddress(ByteString.copyFrom(contractAddress));
    return paramBuilder.build();
  }

  private byte[] abiEncodeForMint(ShieldedVRC20Parameters params, long value,
      byte[] frontier, long leafCount) {
    byte[] mergedBytes;
    ShieldContract.ReceiveDescription revDesc = params.getReceiveDescription(0);
    mergedBytes = ByteUtil.merge(
        revDesc.getNoteCommitment().toByteArray(),
        revDesc.getValueCommitment().toByteArray(),
        revDesc.getEpk().toByteArray(),
        revDesc.getZkproof().toByteArray(),
        params.getBindingSignature().toByteArray(),
        ByteUtil.longTo32Bytes(value),
        params.getMessageHash().toByteArray(),
        frontier,
        ByteUtil.longTo32Bytes(leafCount)
    );
    return mergedBytes;
  }

  private GrpcAPI.Note getNote(long value, String paymentAddress, byte[] rcm, byte[] memo) {
    GrpcAPI.Note.Builder noteBuilder = GrpcAPI.Note.newBuilder();
    noteBuilder.setValue(value);
    noteBuilder.setPaymentAddress(paymentAddress);
    noteBuilder.setRcm(ByteString.copyFrom(rcm));
    noteBuilder.setMemo(ByteString.copyFrom(memo));
    return noteBuilder.build();
  }

  private byte[] abiEncodeForTransfer(ShieldedVRC20Parameters params, byte[] frontier,
      long leafCount) {
    byte[] input = new byte[0];
    byte[] spendAuthSig = new byte[0];
    byte[] output = new byte[0];
    byte[] mergedBytes;
    List<SpendDescription> spendDescs = params.getSpendDescriptionList();
    for (ShieldContract.SpendDescription spendDesc : spendDescs) {
      input = ByteUtil.merge(input,
          spendDesc.getNullifier().toByteArray(),
          spendDesc.getAnchor().toByteArray(),
          spendDesc.getValueCommitment().toByteArray(),
          spendDesc.getRk().toByteArray(),
          spendDesc.getZkproof().toByteArray()
      );
      spendAuthSig = ByteUtil.merge(
          spendAuthSig, spendDesc.getSpendAuthoritySignature().toByteArray());
    }
    byte[] inputOffsetbytes = longTo32Bytes(1280);
    long spendCount = spendDescs.size();
    byte[] spendCountBytes = longTo32Bytes(spendCount);
    byte[] authOffsetBytes = longTo32Bytes(1280 + 32 + 320 * spendCount);
    List<ShieldContract.ReceiveDescription> recvDescs = params.getReceiveDescriptionList();
    for (ShieldContract.ReceiveDescription recvDesc : recvDescs) {
      output = ByteUtil.merge(output,
          recvDesc.getNoteCommitment().toByteArray(),
          recvDesc.getValueCommitment().toByteArray(),
          recvDesc.getEpk().toByteArray(),
          recvDesc.getZkproof().toByteArray()
      );
    }
    long recvCount = recvDescs.size();
    byte[] recvCountBytes = longTo32Bytes(recvCount);
    byte[] outputOffsetbytes = longTo32Bytes(1280 + 32 + 320 * spendCount + 32 + 64 * spendCount);
    mergedBytes = ByteUtil.merge(inputOffsetbytes,
        authOffsetBytes,
        outputOffsetbytes,
        params.getBindingSignature().toByteArray(),
        params.getMessageHash().toByteArray(),
        frontier,
        longTo32Bytes(leafCount),
        spendCountBytes,
        input,
        spendCountBytes,
        spendAuthSig,
        recvCountBytes,
        output
    );
    return mergedBytes;
  }

  private byte[] longTo32Bytes(long value) {
    byte[] longBytes = ByteArray.fromLong(value);
    byte[] zeroBytes = new byte[24];
    return ByteUtil.merge(zeroBytes, longBytes);
  }


}
