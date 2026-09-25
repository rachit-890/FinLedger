package com.example.finledger.enums;

public enum TransactionStatus {
	PENDING,      // Initiated but not processed
	PROCESSING,   // Currently being executed
	COMPLETED,    // Successfully finished
	FAILED,       // Error occurred
	REVERSED      // Was completed, then reversed
}
