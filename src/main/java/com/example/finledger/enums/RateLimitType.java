package com.example.finledger.enums;

public enum RateLimitType {
	GENERAL,    // For Balance, Statements (Loose)
	TRANSACTION // For Transfer, Withdraw, Deposit (Strict)
}
