//pragma solidity ^0.4.16;

interface token {
    function transfer(address receiver, uint amount) external;
}

contract Crowdsale {
    address payable public beneficiary = 0x1b228F5D9f934c7bb18Aaa86F90418932888E7b4;
    uint public fundingGoal = 10000000;
    uint public amountRaised = 1000000;
    uint public deadline;

    uint public price;
    token public tokenReward;

    mapping(address => uint256) public balanceOf;

    bool fundingGoalReached = false;
    bool crowdsaleClosed = false;

    event GoalReached(address recipient, uint totalAmountRaised);
    event FundTransfer(address backer, uint amount, bool isContribution);

    constructor(
        address payable ifSuccessfulSendTo,
        uint fundingGoalInEthers,
        uint durationInMinutes,
        uint finneyCostOfEachToken,
        address addressOfTokenUsedAsReward) public{
            beneficiary = ifSuccessfulSendTo;
            fundingGoal = fundingGoalInEthers * 1 vdt;
            deadline = now + durationInMinutes * 1 minutes;
            price = finneyCostOfEachToken * 1 vs;
            tokenReward = token(addressOfTokenUsedAsReward);
    }

    function () payable external{
        require(!crowdsaleClosed);
        uint amount = msg.value;
        balanceOf[msg.sender] += amount;
        amountRaised += amount;
        tokenReward.transfer(msg.sender, amount / price);
        emit FundTransfer(msg.sender, amount, true);
    }

    modifier afterDeadline() { if (now >= deadline) _; }

    function checkGoalReached() afterDeadline public{
        if (amountRaised >= fundingGoal) {
            fundingGoalReached = true;
            emit GoalReached(beneficiary, amountRaised);
        }
        crowdsaleClosed = true;
    }


    function safeWithdrawal() afterDeadline public{
        if (!fundingGoalReached) {
            uint amount = balanceOf[msg.sender];
            balanceOf[msg.sender] = 0;
            if (amount > 0) {
                if (msg.sender.send(amount)) {
                    emit FundTransfer(msg.sender, amount, false);
                } else {
                    balanceOf[msg.sender] = amount;
                }
            }
        }

        if (fundingGoalReached && beneficiary == msg.sender) {
            if (address(beneficiary).send(amountRaised)) {
                emit FundTransfer(beneficiary, amountRaised, false);
            } else {
                //If we fail to send the funds to beneficiary, unlock funders balance
                fundingGoalReached = false;
            }
        }
    }
}