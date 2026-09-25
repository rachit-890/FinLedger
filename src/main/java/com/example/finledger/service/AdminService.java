package com.example.finledger.service;

import com.example.finledger.Payloads.Auditresponse;

import java.util.UUID;

public interface AdminService {
	Auditresponse audit(UUID accountId);
	
}
