package org.vision.core.config;

import lombok.Getter;

public class Parameter {

  public enum ForkBlockVersionEnum {
    ENTROPY_LIMIT(5, 0L, 0),
    VERSION_1_0_0(20, 0L, 0),
    VERSION_1_2_0(21, 1654444800000L, 80);//GMT 2022-06-06 00:00:00, 80 means 22 FV upgrade

    @Getter
    private int value;
    @Getter
    private long hardForkTime;
    @Getter
    private int hardForkRate;

    ForkBlockVersionEnum(int value, long hardForkTime, int hardForkRate) {
      this.value = value;
      this.hardForkTime = hardForkTime;
      this.hardForkRate = hardForkRate;
    }

    public static ForkBlockVersionEnum getForkBlockVersionEnum(int value) {
      for (ForkBlockVersionEnum versionEnum : values()) {
        if (versionEnum.getValue() == value) {
          return versionEnum;
        }
      }
      return null;
    }
  }

  public static class ChainSymbol {

    public static final byte[] VS_SYMBOL_BYTES = "_".getBytes(); // VS symbol
  }

  public class ChainConstant {
    public static final int MAX_ACTIVE_WITNESS_NUM = 3;
    public static final int WITNESS_STANDBY_LENGTH = 103;
    public static final long TRANSFER_FEE = 0; // free
    public static final int BLOCK_PRODUCED_INTERVAL = 3000; //ms,produce block period, must be divisible by 60. millisecond
    public static final int MAX_VOTE_NUMBER = 30;
    public static final int SOLIDIFIED_THRESHOLD = 70; // 70%
    public static final int PRIVATE_KEY_LENGTH = 64;
    public static final int BLOCK_SIZE = 2_000_000;
    public static final long CLOCK_MAX_DELAY = 3600000; // 3600 * 1000 ms
    public static final int BLOCK_PRODUCE_TIMEOUT_PERCENT = 50; // 50%
    public static final long PRECISION = 1_000_000;
    public static final long WINDOW_SIZE_MS = 24 * 3600 * 1000L;
    public static final long MAINTENANCE_SKIP_SLOTS = 2;
    public static final int SINGLE_REPEAT = 1;
    public static final int BLOCK_FILLED_SLOTS_NUMBER = 128;
    public static final int MAX_FROZEN_NUMBER = 1;
    public static final int BLOCK_VERSION = 21;
    public static final long FROZEN_PERIOD = 86_400_000L;
    public static final long VS_PRECISION = 1000_000L;
    public static final long UN_FREEZE_FVGUARANTEE_LIMIT = 23L;
    public static final long FIRST_ECONOMY_CYCLE = 42L;
    public static final int QUERY_SPREAD_MINT_PARENT_LEVEL_MAX = 4;
    public static final int ETH_TRANSACTION_RLP_VALID_NONCE_SCOPE = 20;



    public static final long VOTE_FREEZE_STAGE_LEVEL1 = 1000;
    public static final long VOTE_FREEZE_STAGE_LEVEL2 = 10000;
    public static final long VOTE_FREEZE_STAGE_LEVEL3 = 100000;
    public static final int VOTE_FREEZE_PERCENT_LEVEL1 = 108;
    public static final int VOTE_FREEZE_PERCENT_LEVEL2 = 113;
    public static final int VOTE_FREEZE_PERCENT_LEVEL3 = 116;


    public static final long FV_FREEZE_LOWEST = 5000_000_000L;
    public static final int FV_FREEZE_LOWEST_PERCENT = 65;
    public static final int VOTE_PERCENT_PRECISION = 100;
    public static final int FV_FREEZE_LOWEST_PRECISION = 1000;
  }

  public class NodeConstant {

    public static final int MAX_TRANSACTION_PENDING = 2000;
    public static final int MAX_HTTP_CONNECT_NUMBER = 50;
  }

  public class NetConstants {

    public static final long SYNC_FETCH_BATCH_NUM = 2000;
    public static final long ADV_TIME_OUT = 20000L;
    public static final long SYNC_TIME_OUT = 5000L;
    public static final long NET_MAX_TRX_PER_SECOND = 700L;
    public static final int MSG_CACHE_DURATION_IN_BLOCKS = 5;
    public static final int MAX_BLOCK_FETCH_PER_PEER = 100;
    public static final int MAX_TRX_FETCH_PER_PEER = 1000;
  }

  public class DatabaseConstants {

    public static final int TRANSACTIONS_COUNT_LIMIT_MAX = 1000;
    public static final int PROPOSAL_COUNT_LIMIT_MAX = 1000;
    public static final int EXCHANGE_COUNT_LIMIT_MAX = 1000;
    public static final int MARKET_COUNT_LIMIT_MAX = 1000;
  }

  public class AdaptiveResourceLimitConstants {

    public static final int CONTRACT_RATE_NUMERATOR = 99;
    public static final int CONTRACT_RATE_DENOMINATOR = 100;
    public static final int EXPAND_RATE_NUMERATOR = 1000;
    public static final int EXPAND_RATE_DENOMINATOR = 999;
    public static final int PERIODS_MS = 60_000;
    public static final int LIMIT_MULTIPLIER = 1000; //s
  }


  @Deprecated
  public class ForkBlockVersionConsts {

    public static final int START_NEW_TRANSACTION = 4;
    public static final int ENTROPY_LIMIT = 5;
  }

  public class NativeTransactionContractAbi {
    public static final String TRANSACTION_CONTRACT_ADDRESS = "VP79PCA81j1zx76qvj2Yfqw2KuyQFZyk2m";
    public static final String TRANSACTION_CONTRACT_ADDRESS_ETH = "0x8888888888888888888888888888888888888888"; // 0x8888888888888888888888888888888888888888
    public static final int TRANSACTION_FUNCTION_SELECTOR_LENGTH = 8;
    public static final int VALUE_SIZE = 64;

    /**·
     function voteWitness(address[] memory target, uint256[] memory count) public returns (bytes32){
     return keccak256(abi.encodePacked("dovote(address[],uint256[])"));
     }

     function freezeBalance(uint256 frozen_balance, uint256 frozen_duration, uint8 resource, address receiver_address) public returns (bool){
     return true;
     }

     function freezeBalance(uint256 frozen_balance, uint256 frozen_duration, uint256 resource, address receiver_address, uint256[] memory stages, uint256[] memory frozen_balances) public returns (bool){
     return true;
     }

     function unfreezeBalance(uint8 resource, address receiver_address) public returns (bool){
     return true;
     }

     function unfreezeBalance(uint8 resource, address receiver_address, uint256[] stages) public returns (bool){
     return true;
     }

     function withdrawBalance(uint8 withdraw_type) public returns (bool){
     return true;
     }

     function getReward() public returns(uint256, uint256){
     return (0,0);
     }
     */


    /**
     * signature transaction method
     */
    public static final String VoteWitness = "voteWitness(address[],uint256[])";                 // df126771 25a795121eec67426870e1259d4149d8ce2f8f61a6106718d2932ae8
    public static final String VoteWitness_FunctionSelector = "df126771";

    public static final String FreezeBalance = "freezeBalance(uint256,uint256,uint256,address)";   // c3718670 0a91520cc9a15363af6c66d096179e66e77cb1d1bb45547b2c4ff2d5
    public static final String FreezeBalance_FunctionSelector = "c3718670";

    public static final String FreezeBalanceStage = "freezeBalance(uint256,uint256,uint256,address,uint256[],uint256[])";   // 10a6d73b 741b51421df7c824064dc86f4cd9b70d033ee002604cc61fbe3217af
    public static final String FreezeBalanceStage_FunctionSelector = "10a6d73b";

    public static final String UnfreezeBalance = "unfreezeBalance(uint256,address)";               // 62a60984 ef1f973ed2e0aad55d8777225b517dea86fa6a4396eaea0c6f4a0076
    public static final String UnfreezeBalance_FunctionSelector = "62a60984";

    public static final String UnfreezeBalanceStage = "unfreezeBalance(uint256,address,uint256[])";               // b1c68f74 3ec3afdfe31282ab0b2c9362ef7c5c113743ee22ae0697d1207a7c06
    public static final String UnfreezeBalanceStage_FunctionSelector = "b1c68f74";

    public static final String WithdrawBalance = "withdrawBalance(uint256)";                        // da76d5cd be3baa4ef4f3adb20b7af22c89bcd5dc86bb34ab804e6d35b15874d4
    public static final String WithdrawBalance_FunctionSelector = "da76d5cd";

    public static final String CreateWitness = "createWitness(string)";                                         // aadf8399 34f925791149fec0bdf92d69d83e186f3b855bcfc8a0abfba58b90e1
    public static final String CreateWitness_FunctionSelector = "aadf8399";

    public static final String UpdateWitness = "updateWitness(string)";                                         // 8cf1c37f 1d5df7ae06909b65e38ab4ea3e2c44c14f0ea4abca8d359fdd3af91e
    public static final String UpdateWitness_FunctionSelector = "8cf1c37f";

    public static final String UpdateBrokerage = "updateBrokerage(uint256)";                                         // a9cffe3f 31f225bb675a50eb5bede21129e6519ff796e64333b54f07ca1a6571
    public static final String UpdateBrokerage_FunctionSelector = "a9cffe3f";

    public static final String ProposalApprove = "proposalApprove(uint256,bool)";                                         // 10430496 fb4e39ada0740000372ebaf4c83412a47e5107e5973e67630b13a44c
    public static final String ProposalApprove_FunctionSelector = "10430496";

    public static final String ProposalCreateInteger = "proposalCreate(uint256,uint256)";                                         // 8994f5b0 aeb75d4dc227d3615da415df78b1a89a158913a5e0decabc2ac47ee4
    public static final String ProposalCreateInteger_FunctionSelector = "8994f5b0";

    public static final String ProposalCreateString = "proposalCreate(uint256,string)";                                         // 8ad94a88 2761b758983a828cf8a63b902d05d5348d7951ea34786844816c9c31
    public static final String ProposalCreateString_FunctionSelector = "8ad94a88";

    public static final String ProposalDelete = "proposalDelete(uint256)";                                         // 9a213110 8c518b5d240ad509a1b6adfb2dc6863f1da4b2df93cc873e03d0baff
    public static final String ProposalDelete_FunctionSelector = "9a213110";

    public static final String AccountUpdate = "accountUpdate(string)";                                         // 7e5f519e 800a657de36298214f19e062e3fdff3625565ada9a457d60841bce30
    public static final String AccountUpdate_FunctionSelector = "7e5f519e";


    /**
     * no signature transaction method
     */
    public static final String GetReward = "getReward(address)";                                         // c00007b0 b14ce14d1d8e20828982c1e51944313ec54b52ee46020e0e01677495
    public static final String GetReward_FunctionSelector = "c00007b0";

    public static final String GetBrokerage = "getBrokerage(address)";                                         // 27e5684d b0d9950a0698eb39243a5c48892397fa19f25ab743f91b3a6d4d5895
    public static final String GetBrokerage_FunctionSelector = "27e5684d";

    public static final String GetNextMaintenanceTime = "getNextMaintenanceTime()";                                         // 8321e41c beab214449032e1158a9ab6a4dc7d0867f8c5e2a1bd44f8c97307fcc
    public static final String GetNextMaintenanceTime_FunctionSelector = "8321e41c";

  }

}
