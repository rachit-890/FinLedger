package com.example.finledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class FinLedgerApplication {
	
	public static void main(String[] args) {
		SpringApplication.run(FinLedgerApplication.class, args);
	}
	
}
