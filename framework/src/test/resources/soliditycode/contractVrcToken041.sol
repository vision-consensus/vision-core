//pragma solidity ^0.4.24;

 contract tokenTest{
     constructor() public payable{}
     // positive case
     function TransferTokenTo(address payable toAddress, vrcToken id,uint256 amount) public payable{
         //vrcToken id = 0x74657374546f6b656e;
         toAddress.transferToken(amount,id);
     }
 }

contract B{
    uint256 public flag = 0;
    constructor() public payable {}
    function() external payable {}

    function setFlag() public payable{
        flag = 1;
    }
}