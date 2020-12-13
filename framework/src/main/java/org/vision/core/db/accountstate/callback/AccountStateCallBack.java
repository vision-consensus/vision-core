package org.vision.core.db.accountstate.callback;

import com.google.protobuf.ByteString;
import com.google.protobuf.Internal;
import java.util.Arrays;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.core.db.accountstate.storetrie.AccountStateStoreTrie;
import org.vision.core.exception.BadBlockException;
import org.vision.core.trie.TrieImpl;
import org.vision.common.crypto.Hash;
import org.vision.common.utils.ByteArray;
import org.vision.core.ChainBaseManager;
import org.vision.core.capsule.BlockCapsule;
import org.vision.core.db.accountstate.AccountStateCallBackUtils;


@Slf4j(topic = "AccountState")
@Component
public class AccountStateCallBack extends AccountStateCallBackUtils {

  private BlockCapsule blockCapsule;
  private TrieImpl trie;

  @Setter
  private ChainBaseManager chainBaseManager;

  @Autowired
  private AccountStateStoreTrie db;

  public void preExeTrans() {
    trieEntryList.clear();
  }

  public void exeTransFinish() {
    for (TrieEntry trieEntry : trieEntryList) {
      trie.put(Hash.encodeElement(trieEntry.getKey()), trieEntry.getData());
    }
    trieEntryList.clear();
  }

  public void deleteAccount(byte[] key) {
    if (!exe()) {
      return;
    }
    trie.delete(Hash.encodeElement(key));
  }

  public void preExecute(BlockCapsule blockCapsule) {
    this.blockCapsule = blockCapsule;
    this.execute = true;
    this.allowGenerateRoot = chainBaseManager.getDynamicPropertiesStore().allowAccountStateRoot();
    if (!exe()) {
      return;
    }
    byte[] rootHash = null;
    try {
      BlockCapsule parentBlockCapsule =
          chainBaseManager.getBlockById(blockCapsule.getParentBlockId());
      rootHash = parentBlockCapsule.getInstance().getBlockHeader().getRawData()
          .getAccountStateRoot().toByteArray();
    } catch (Exception e) {
      logger.error("", e);
    }
    if (Arrays.equals(Internal.EMPTY_BYTE_ARRAY, rootHash)) {
      rootHash = Hash.EMPTY_TRIE_HASH;
    }
    trie = new TrieImpl(db, rootHash);
  }

  public void executePushFinish() throws BadBlockException {
    if (!exe()) {
      return;
    }
    ByteString oldRoot = blockCapsule.getInstance().getBlockHeader().getRawData()
        .getAccountStateRoot();
    execute = false;
    //
    byte[] newRoot = trie.getRootHash();
    if (ArrayUtils.isEmpty(newRoot)) {
      newRoot = Hash.EMPTY_TRIE_HASH;
    }
    if (!oldRoot.isEmpty() && !Arrays.equals(oldRoot.toByteArray(), newRoot)) {
      logger.error("the accountStateRoot hash is error. {}, oldRoot: {}, newRoot: {}",
          blockCapsule, ByteArray.toHexString(oldRoot.toByteArray()),
          ByteArray.toHexString(newRoot));
      throw new BadBlockException("the accountStateRoot hash is error");
    }
  }

  public void executeGenerateFinish() {
    if (!exe()) {
      return;
    }
    //
    byte[] newRoot = trie.getRootHash();
    if (ArrayUtils.isEmpty(newRoot)) {
      newRoot = Hash.EMPTY_TRIE_HASH;
    }
    blockCapsule.setAccountStateRoot(newRoot);
    execute = false;
  }

  public void exceptionFinish() {
    execute = false;
  }

}
