package com.example.finledger.enums;

public enum TransactionType {
	DEPOSIT,      // Money coming in
	WITHDRAWAL,   // Money going out
	TRANSFER,     // Between two accounts
	REVERSAL      // Undo a previous transaction
}
