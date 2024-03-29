package stest.vision.wallet.dailybuild.http;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.utils.HttpMethed;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class HttpTestAccount002 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] freezeBalanceAddress = ecKey1.getAddress();
  String freezeBalanceKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] receiverResourceAddress = ecKey2.getAddress();
  String receiverResourceKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  Long berforeBalance;
  Long afterBalance;
  Long amount = 10000000L;
  Long frozenBalance = 2000000L;
  private JSONObject responseContent;
  private HttpResponse response;
  private String httpnode = Configuration.getByPath("testng.conf").getStringList("httpnode.ip.list")
      .get(1);
  private String httpSoliditynode = Configuration.getByPath("testng.conf")
      .getStringList("httpnode.ip.list").get(2);
  private String httpPbftNode = Configuration.getByPath("testng.conf")
      .getStringList("httpnode.ip.list").get(4);

  /**
   * constructor.
   */
  @Test(enabled = true, description = "FreezeBalance for Photon by http")
  public void test001FreezebalanceForPhoton() {
    PublicMethed.printAddress(freezeBalanceKey);
    //Send vs to test account
    response = HttpMethed.sendCoin(httpnode, fromAddress, freezeBalanceAddress, amount, testKey002);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    berforeBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);

    //Freeze balance
    response = HttpMethed
        .freezeBalance(httpnode, freezeBalanceAddress, frozenBalance, 0, 0, freezeBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);
    Assert.assertTrue(berforeBalance - afterBalance == frozenBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "UnFreezeBalance for Photon by http")
  public void test002UnFreezebalanceForPhoton() {
    berforeBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);

    //UnFreeze balance for Photon
    response = HttpMethed.unFreezeBalance(httpnode, freezeBalanceAddress, 0, freezeBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);
    Assert.assertTrue(afterBalance - berforeBalance == frozenBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "FreezeBalance for entropy by http")
  public void test003FreezebalanceForEntropy() {
    berforeBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);

    //Freeze balance for entropy
    response = HttpMethed
        .freezeBalance(httpnode, freezeBalanceAddress, frozenBalance, 0, 1, freezeBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);
    Assert.assertTrue(berforeBalance - afterBalance == frozenBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "UnFreezeBalance for entropy by http")
  public void test004UnFreezebalanceForEntropy() {

    berforeBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);
    HttpMethed.waitToProduceOneBlock(httpnode);
    //UnFreeze balance for entropy
    response = HttpMethed.unFreezeBalance(httpnode, freezeBalanceAddress, 1, freezeBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);
    Assert.assertTrue(afterBalance - berforeBalance == frozenBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "FreezeBalance with Photon for others by http")
  public void test005FreezebalanceOfPhotonForOthers() {
    response = HttpMethed
        .sendCoin(httpnode, fromAddress, receiverResourceAddress, amount, testKey002);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    berforeBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);

    //Freeze balance with Photon for others
    response = HttpMethed
        .freezeBalance(httpnode, freezeBalanceAddress, frozenBalance, 0, 0, receiverResourceAddress,
            freezeBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);
    Assert.assertTrue(berforeBalance - afterBalance == frozenBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Get Delegated Resource by http")
  public void test006GetDelegatedResource() {
    response = HttpMethed
        .getDelegatedResource(httpnode, freezeBalanceAddress, receiverResourceAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    JSONArray jsonArray = JSONArray.parseArray(responseContent.get("delegatedResource").toString());
    Assert.assertTrue(jsonArray.size() >= 1);
    Assert.assertEquals(jsonArray.getJSONObject(0).getString("from"),
        ByteArray.toHexString(freezeBalanceAddress));
    Assert.assertEquals(jsonArray.getJSONObject(0).getString("to"),
        ByteArray.toHexString(receiverResourceAddress));
    Assert.assertEquals(jsonArray.getJSONObject(0).getLong("frozen_balance_for_photon"),
        frozenBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Get Delegated Resource from solidity by http")
  public void test007GetDelegatedResourceFromSolidity() {
    HttpMethed.waitToProduceOneBlockFromSolidity(httpnode, httpSoliditynode);
    HttpMethed.waitToProduceOneBlockFromPbft(httpnode, httpPbftNode);
    response = HttpMethed.getDelegatedResourceFromSolidity(httpSoliditynode, freezeBalanceAddress,
        receiverResourceAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    JSONArray jsonArray = JSONArray.parseArray(responseContent.get("delegatedResource").toString());
    Assert.assertTrue(jsonArray.size() >= 1);
    Assert.assertEquals(jsonArray.getJSONObject(0).getString("from"),
        ByteArray.toHexString(freezeBalanceAddress));
    Assert.assertEquals(jsonArray.getJSONObject(0).getString("to"),
        ByteArray.toHexString(receiverResourceAddress));
    Assert.assertEquals(jsonArray.getJSONObject(0).getLong("frozen_balance_for_photon"),
        frozenBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Get Delegated Resource from PBFT by http")
  public void test008GetDelegatedResourceFromPbft() {
    HttpMethed.waitToProduceOneBlockFromPbft(httpnode, httpPbftNode);
    response = HttpMethed
        .getDelegatedResourceFromPbft(httpPbftNode, freezeBalanceAddress, receiverResourceAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    JSONArray jsonArray = JSONArray.parseArray(responseContent.get("delegatedResource").toString());
    Assert.assertTrue(jsonArray.size() >= 1);
    Assert.assertEquals(jsonArray.getJSONObject(0).getString("from"),
        ByteArray.toHexString(freezeBalanceAddress));
    Assert.assertEquals(jsonArray.getJSONObject(0).getString("to"),
        ByteArray.toHexString(receiverResourceAddress));
    Assert.assertEquals(jsonArray.getJSONObject(0).getLong("frozen_balance_for_photon"),
        frozenBalance);
  }


  /**
   * constructor.
   */
  @Test(enabled = true, description = "Get Delegated Resource Account Index by http")
  public void test009GetDelegatedResourceAccountIndex() {
    response = HttpMethed.getDelegatedResourceAccountIndex(httpnode, freezeBalanceAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertFalse(responseContent.get("toAccounts").toString().isEmpty());
    String toAddress = responseContent.getJSONArray("toAccounts").get(0).toString();
    Assert.assertEquals(toAddress, ByteArray.toHexString(receiverResourceAddress));
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Get Delegated Resource Account Index from solidity by http")
  public void test010GetDelegatedResourceAccountIndexFromSolidity() {
    response = HttpMethed
        .getDelegatedResourceAccountIndexFromSolidity(httpSoliditynode, freezeBalanceAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertFalse(responseContent.get("toAccounts").toString().isEmpty());
    String toAddress = responseContent.getJSONArray("toAccounts").get(0).toString();
    Assert.assertEquals(toAddress, ByteArray.toHexString(receiverResourceAddress));
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Get Delegated Resource Account Index from PBFT by http")
  public void test011GetDelegatedResourceAccountIndexFromPbft() {
    response = HttpMethed
        .getDelegatedResourceAccountIndexFromPbft(httpPbftNode, freezeBalanceAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertFalse(responseContent.get("toAccounts").toString().isEmpty());
    String toAddress = responseContent.getJSONArray("toAccounts").get(0).toString();
    Assert.assertEquals(toAddress, ByteArray.toHexString(receiverResourceAddress));
  }


  /**
   * constructor.
   */
  @Test(enabled = true, description = "UnFreezeBalance with photon for others by http")
  public void test012UnFreezebalanceOfPhotonForOthers() {
    HttpMethed.waitToProduceOneBlock(httpnode);
    berforeBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);

    //UnFreeze balance with photon for others
    response = HttpMethed
        .unFreezeBalance(httpnode, freezeBalanceAddress, 0, receiverResourceAddress,
            freezeBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);
    Assert.assertTrue(afterBalance - berforeBalance == frozenBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "FreezeBalance with entropy for others by http")
  public void test013FreezebalanceOfEntropyForOthers() {
    response = HttpMethed
        .sendCoin(httpnode, fromAddress, receiverResourceAddress, amount, testKey002);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    berforeBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);

    //Freeze balance with entropy for others
    response = HttpMethed
        .freezeBalance(httpnode, freezeBalanceAddress, frozenBalance, 0, 1, receiverResourceAddress,
            freezeBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);
    Assert.assertTrue(berforeBalance - afterBalance == frozenBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "UnFreezeBalance with entropy for others by http")
  public void test014UnFreezebalanceOfEntropyForOthers() {
    berforeBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);

    //UnFreeze balance with entropy for others
    response = HttpMethed
        .unFreezeBalance(httpnode, freezeBalanceAddress, 1, receiverResourceAddress,
            freezeBalanceKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    afterBalance = HttpMethed.getBalance(httpnode, freezeBalanceAddress);
    Assert.assertTrue(afterBalance - berforeBalance == frozenBalance);
  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    HttpMethed.freedResource(httpnode, freezeBalanceAddress, fromAddress, freezeBalanceKey);
    HttpMethed.disConnect();
  }
}