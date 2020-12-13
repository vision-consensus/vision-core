//pragma solidity ^0.4.0;


contract Grammar18{
    function testAddmod() public returns (uint z) {
         z=addmod(2, 2, 3);
         return z;
    }
    function testMulmod() public returns (uint z) {
         z=mulmod(2, 3, 4);
         return z;
    }

  function testKeccak256() public  returns(bytes32){
      return keccak256("11");
  }

    function testSha256()  public returns(bytes32){
      return sha256("11");
  }
    function testSha3() public  returns(bytes32){
      return keccak256("11");
  }

    function testRipemd160()  public returns(bytes32){
      return ripemd160("11");
  }


}