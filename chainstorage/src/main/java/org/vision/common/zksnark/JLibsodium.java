package org.vision.common.zksnark;

import org.vision.common.parameter.CommonParameter;
import org.vision.common.zksnark.Libsodium;
import org.vision.common.zksnark.LibsodiumWrapper;

public class JLibsodium {

  public static final int CRYPTO_GENERICHASH_BLAKE2B_PERSONALBYTES = 16;
  public static final int CRYPTO_AEAD_CHACHA20POLY1305_IETF_NPUBBYTES = 12;
  private static Libsodium INSTANCE;

  public static int cryptoGenerichashBlake2bInitSaltPersonal(JLibsodiumParam.Blake2bInitSaltPersonalParams params) {
    if (!isOpenZen()) {
      return 0;
    }
    return INSTANCE
        .cryptoGenerichashBlake2BInitSaltPersonal(params.getState(), params.getKey(),
            params.getKeyLen(), params.getOutLen(), params.getSalt(), params.getPersonal());
  }

  public static int cryptoGenerichashBlake2bUpdate(JLibsodiumParam.Blake2bUpdateParams params) {
    if (!isOpenZen()) {
      return 0;
    }
    return INSTANCE
        .cryptoGenerichashBlake2BUpdate(params.getState(), params.getIn(), params.getInLen());
  }

  public static int cryptoGenerichashBlake2bFinal(JLibsodiumParam.Blake2bFinalParams params) {
    if (!isOpenZen()) {
      return 0;
    }
    return INSTANCE.cryptoGenerichashBlake2BFinal(params.getState(),
        params.getOut(), params.getOutLen());
  }

  public static int cryptoGenerichashBlack2bSaltPersonal(JLibsodiumParam.Black2bSaltPersonalParams params) {
    if (!isOpenZen()) {
      return 0;
    }
    return INSTANCE.cryptoGenerichashBlake2BSaltPersonal(params.getOut(), params.getOutLen(),
        params.getIn(), params.getInLen(), params.getKey(), params.getKeyLen(),
        params.getSalt(),
        params.getPersonal());
  }

  public static int cryptoAeadChacha20poly1305IetfDecrypt(
      JLibsodiumParam.Chacha20poly1305IetfDecryptParams params) {
    if (!isOpenZen()) {
      return 0;
    }
    return INSTANCE
        .cryptoAeadChacha20Poly1305IetfDecrypt(params.getM(), params.getMLenP(),
            params.getNSec(),
            params.getC(), params.getCLen(), params.getAd(),
            params.getAdLen(), params.getNPub(), params.getK());
  }

  public static int cryptoAeadChacha20Poly1305IetfEncrypt(
      JLibsodiumParam.Chacha20Poly1305IetfEncryptParams params) {
    if (!isOpenZen()) {
      return 0;
    }
    return INSTANCE
        .cryptoAeadChacha20Poly1305IetfEncrypt(params.getC(), params.getCLenP(), params.getM(),
            params.getMLen(), params.getAd(), params.getAdLen(),
            params.getNSec(), params.getNPub(), params.getK());
  }

  public static long initState() {
    if (!isOpenZen()) {
      return 0;
    }
    return INSTANCE.cryptoGenerichashBlake2BStateInit();
  }

  public static void freeState(long state) {
    if (!isOpenZen()) {
      return;
    }
    INSTANCE.cryptoGenerichashBlake2BStateFree(state);
  }

  private static boolean isOpenZen() {
    boolean res = CommonParameter.getInstance()
        .isFullNodeAllowShieldedTransactionArgs();
    if (res) {
      INSTANCE = LibsodiumWrapper.getInstance();
    }
    return res;
  }
}
