package org.vision.core.config;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.vision.core.config.args.Args;
import org.vision.core.services.interfaceOnPBFT.RpcApiServiceOnPBFT;
import org.vision.core.services.interfaceOnPBFT.http.PBFT.HttpApiOnPBFTService;
import org.vision.core.services.interfaceOnSolidity.RpcApiServiceOnSolidity;
import org.vision.core.services.interfaceOnSolidity.http.solidity.HttpApiOnSolidityService;
import org.vision.common.utils.StorageUtils;
import org.vision.core.db.RevokingDatabase;
import org.vision.core.db.RevokingStore;
import org.vision.core.db.TransactionCache;
import org.vision.core.db.backup.BackupRocksDBAspect;
import org.vision.core.db.backup.NeedBeanCondition;
import org.vision.core.db2.core.SnapshotManager;

@Slf4j(topic = "app")
@Configuration
@Import(CommonConfig.class)
public class DefaultConfig {

  static {
    RocksDB.loadLibrary();
  }

  @Autowired
  public ApplicationContext appCtx;

  @Autowired
  public CommonConfig commonConfig;

  public DefaultConfig() {
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> logger.error("Uncaught exception", e));
  }

  @Bean
  public RevokingDatabase revokingDatabase() {
    int dbVersion = Args.getInstance().getStorage().getDbVersion();
    RevokingDatabase revokingDatabase;
    try {
      if (dbVersion == 1) {
        revokingDatabase = RevokingStore.getInstance();
      } else if (dbVersion == 2) {
        revokingDatabase = new SnapshotManager(
            StorageUtils.getOutputDirectoryByDbName("block"));
      } else {
        throw new RuntimeException("db version is error.");
      }
      return revokingDatabase;
    } finally {
      logger.info("key-value data source created.");
    }
  }


  @Bean
  public RpcApiServiceOnSolidity getRpcApiServiceOnSolidity() {
    boolean isSolidityNode = Args.getInstance().isSolidityNode();
    int dbVersion = Args.getInstance().getStorage().getDbVersion();
    if (!isSolidityNode && dbVersion == 2) {
      return new RpcApiServiceOnSolidity();
    }

    return null;
  }

  @Bean
  public HttpApiOnSolidityService getHttpApiOnSolidityService() {
    boolean isSolidityNode = Args.getInstance().isSolidityNode();
    int dbVersion = Args.getInstance().getStorage().getDbVersion();
    if (!isSolidityNode && dbVersion == 2) {
      return new HttpApiOnSolidityService();
    }

    return null;
  }

  @Bean
  public RpcApiServiceOnPBFT getRpcApiServiceOnPBFT() {
    boolean isSolidityNode = Args.getInstance().isSolidityNode();
    int dbVersion = Args.getInstance().getStorage().getDbVersion();
    if (!isSolidityNode && dbVersion == 2) {
      return new RpcApiServiceOnPBFT();
    }

    return null;
  }

  @Bean
  public HttpApiOnPBFTService getHttpApiOnPBFTService() {
    boolean isSolidityNode = Args.getInstance().isSolidityNode();
    int dbVersion = Args.getInstance().getStorage().getDbVersion();
    if (!isSolidityNode && dbVersion == 2) {
      return new HttpApiOnPBFTService();
    }

    return null;
  }

  @Bean
  public TransactionCache transactionCache() {
    int dbVersion = Args.getInstance().getStorage().getDbVersion();
    if (dbVersion == 2) {
      return new TransactionCache("trans-cache");
    }

    return null;
  }

  @Bean
  @Conditional(NeedBeanCondition.class)
  public BackupRocksDBAspect backupRocksDBAspect() {
    return new BackupRocksDBAspect();
  }
}
