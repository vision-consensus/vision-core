package org.vision.core.db;

import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import com.google.common.reflect.TypeToken;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import javax.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteOptions;
import org.rocksdb.DirectComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.vision.common.storage.leveldb.LevelDbDataSourceImpl;
import org.vision.common.storage.rocksdb.RocksDbDataSourceImpl;
import org.vision.common.utils.StorageUtils;
import org.vision.core.capsule.ProtoCapsule;
import org.vision.core.db2.common.DB;
import org.vision.core.db2.common.IRevokingDB;
import org.vision.core.db2.common.LevelDB;
import org.vision.core.db2.common.RocksDB;
import org.vision.core.db2.core.Chainbase;
import org.vision.core.db2.core.IVisionChainBase;
import org.vision.core.db2.core.RevokingDBWithCachingOldValue;
import org.vision.core.db2.core.SnapshotRoot;
import org.vision.common.parameter.CommonParameter;
import org.vision.core.exception.BadItemException;
import org.vision.core.exception.ItemNotFoundException;


@Slf4j(topic = "DB")
public abstract class VisionStoreWithRevoking<T extends ProtoCapsule> implements IVisionChainBase<T> {

  @Getter // only for unit test
  protected IRevokingDB revokingDB;
  private TypeToken<T> token = new TypeToken<T>(getClass()) {
  };

  @Autowired
  private RevokingDatabase revokingDatabase;

  protected VisionStoreWithRevoking(String dbName) {
    int dbVersion = CommonParameter.getInstance().getStorage().getDbVersion();
    String dbEngine = CommonParameter.getInstance().getStorage().getDbEngine();
    if (dbVersion == 1) {
      this.revokingDB = new RevokingDBWithCachingOldValue(dbName,
          getOptionsByDbNameForLevelDB(dbName));
    } else if (dbVersion == 2) {
      if ("LEVELDB".equals(dbEngine.toUpperCase())) {
        this.revokingDB = new Chainbase(new SnapshotRoot(
            new LevelDB(
                new LevelDbDataSourceImpl(StorageUtils.getOutputDirectoryByDbName(dbName),
                    dbName,
                    getOptionsByDbNameForLevelDB(dbName),
                    new WriteOptions().sync(CommonParameter.getInstance()
                        .getStorage().isDbSync())))));
      } else if ("ROCKSDB".equals(dbEngine.toUpperCase())) {
        String parentPath = Paths
            .get(StorageUtils.getOutputDirectoryByDbName(dbName), CommonParameter
                .getInstance().getStorage().getDbDirectory()).toString();

        this.revokingDB = new Chainbase(new SnapshotRoot(
            new RocksDB(
                new RocksDbDataSourceImpl(parentPath,
                    dbName, CommonParameter.getInstance()
                    .getRocksDBCustomSettings(), getDirectComparator()))));
      }
    } else {
      throw new RuntimeException("db version is error.");
    }
  }

  protected org.iq80.leveldb.Options getOptionsByDbNameForLevelDB(String dbName) {
    return StorageUtils.getOptionsByDbName(dbName);
  }

  protected DirectComparator getDirectComparator() {
    return null;
  }

  protected VisionStoreWithRevoking(DB<byte[], byte[]> db) {
    int dbVersion = CommonParameter.getInstance().getStorage().getDbVersion();
    if (dbVersion == 2) {
      this.revokingDB = new Chainbase(new SnapshotRoot(db));
    } else {
      throw new RuntimeException("db version is only 2.(" + dbVersion + ")");
    }
  }

  // only for test
  protected VisionStoreWithRevoking(String dbName, RevokingDatabase revokingDatabase) {
    this.revokingDB = new RevokingDBWithCachingOldValue(dbName,
        (AbstractRevokingStore) revokingDatabase);
  }

  // only for test
  protected VisionStoreWithRevoking(String dbName, Options options,
                                    RevokingDatabase revokingDatabase) {
    this.revokingDB = new RevokingDBWithCachingOldValue(dbName, options,
        (AbstractRevokingStore) revokingDatabase);
  }

  @Override
  public String getDbName() {
    return null;
  }

  @PostConstruct
  private void init() {
    revokingDatabase.add(revokingDB);
  }

  @Override
  public void put(byte[] key, T item) {
    if (Objects.isNull(key) || Objects.isNull(item)) {
      return;
    }

    revokingDB.put(key, item.getData());
  }

  @Override
  public void delete(byte[] key) {
    revokingDB.delete(key);
  }

  @Override
  public T get(byte[] key) throws ItemNotFoundException, BadItemException {
    return of(revokingDB.get(key));
  }

  @Override
  public T getUnchecked(byte[] key) {
    byte[] value = revokingDB.getUnchecked(key);

    try {
      return of(value);
    } catch (BadItemException e) {
      return null;
    }
  }

  public T of(byte[] value) throws BadItemException {
    try {
      Constructor constructor = token.getRawType().getConstructor(byte[].class);
      @SuppressWarnings("unchecked")
      T t = (T) constructor.newInstance((Object) value);
      return t;
    } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
      throw new BadItemException(e.getMessage());
    }
  }

  @Override
  public boolean has(byte[] key) {
    return revokingDB.has(key);
  }

  @Override
  public String getName() {
    return getClass().getSimpleName();
  }

  @Override
  public void close() {
    revokingDB.close();
  }

  @Override
  public void reset() {
    revokingDB.reset();
  }

  @Override
  public Iterator<Map.Entry<byte[], T>> iterator() {
    return Iterators.transform(revokingDB.iterator(), e -> {
      try {
        return Maps.immutableEntry(e.getKey(), of(e.getValue()));
      } catch (BadItemException e1) {
        throw new RuntimeException(e1);
      }
    });
  }

  public long size() {
    return Streams.stream(revokingDB.iterator()).count();
  }

  public void setCursor(Chainbase.Cursor cursor) {
    revokingDB.setCursor(cursor);
  }
}
