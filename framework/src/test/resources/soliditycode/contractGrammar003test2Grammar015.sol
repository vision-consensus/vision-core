//pragma solidity ^0.4.0;

contract ExecuteFallback{

  event FallbackCalled(bytes data);
  function() external{
    emit FallbackCalled(msg.data);
  }

  event ExistFuncCalled(bytes data, uint256 para);
  function existFunc(uint256 para) public{
    emit ExistFuncCalled(msg.data, para);
  }

  function callExistFunc() public{
    bytes4 funcIdentifier = bytes4(keccak256("existFunc(uint256)"));
    //this.call(funcIdentifier, uint256(1));
    address(this).call(abi.encode(funcIdentifier, uint256(1)));
  }

  function callNonExistFunc() public{
    bytes4 funcIdentifier = bytes4(keccak256("functionNotExist()"));
    //this.call(funcIdentifier);
    address(this).call(abi.encode(funcIdentifier));
  }

  function ExistFuncCalledTopic() view public returns(bytes32){
      return keccak256("ExistFuncCalled(bytes,uint256)");
  }
    function FallbackCalledTopic() view public returns(bytes32){
      return keccak256("FallbackCalled(bytes)");
  }
}