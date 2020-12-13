//pragma solidity ^0.4.24;

contract Proxy {
  constructor() payable public{}
  address public implementation;
  function upgradeTo(address _address) public {
    implementation = _address;
  }
  function() payable external{
    address addr = implementation;
    require(addr != address(0));
    assembly {
      let freememstart := mload(0x40)
      calldatacopy(freememstart, 0, calldatasize())
      let success := delegatecall(not(0), addr, freememstart, calldatasize(), freememstart, 0)
      returndatacopy(freememstart, 0, returndatasize())
      switch success
      case 0 { revert(freememstart, returndatasize()) }
      default { return(freememstart, returndatasize()) }
    }
  }
}

contract A {
    function trans(uint256 amount, address payable toAddress, vrcToken id) payable public {
        toAddress.transfer(amount);
    }
}
contract B{
    function trans(uint256 amount, address payable toAddress, vrcToken id) payable public {
        toAddress.transferToken(amount,id);
    }
}
