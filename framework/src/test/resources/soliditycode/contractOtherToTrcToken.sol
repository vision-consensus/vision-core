//pragma solidity ^0.4.24;

contract ConvertType {

constructor() payable public{}

function() payable external{}

//function stringToTrctoken(address payable toAddress, string memory tokenStr, uint256 tokenValue) public {
// vrcToken t = vrcToken(tokenStr); // ERROR
// toAddress.transferToken(tokenValue, tokenStr); // ERROR
//}

function uint256ToTrctoken(address payable toAddress, uint256 tokenValue, uint256 tokenInt)  public {
  vrcToken t = vrcToken(tokenInt); // OK
  toAddress.transferToken(tokenValue, t); // OK
  toAddress.transferToken(tokenValue, tokenInt); // OK
}

function addressToTrctoken(address payable toAddress, uint256 tokenValue, address adr) public {
  vrcToken t = vrcToken(adr); // OK
  toAddress.transferToken(tokenValue, t); // OK
//toAddress.transferToken(tokenValue, adr); // ERROR
}

//function bytesToTrctoken(address payable toAddress, bytes memory b, uint256 tokenValue) public {
 // vrcToken t = vrcToken(b); // ERROR
 // toAddress.transferToken(tokenValue, b); // ERROR
//}

function bytes32ToTrctoken(address payable toAddress, uint256 tokenValue, bytes32 b32) public {
  vrcToken t = vrcToken(b32); // OK
  toAddress.transferToken(tokenValue, t); // OK
// toAddress.transferToken(tokenValue, b32); // ERROR
}

//function arrayToTrctoken(address payable toAddress, uint256[] memory arr, uint256 tokenValue) public {
//vrcToken t = vrcToken(arr); // ERROR
// toAddress.transferToken(tokenValue, arr); // ERROR
//}
}