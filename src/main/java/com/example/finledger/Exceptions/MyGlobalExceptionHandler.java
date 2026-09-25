package com.example.finledger.Exceptions;

import com.example.finledger.Payloads.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class MyGlobalExceptionHandler {
	
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> myMethodArgumentNotValidException(MethodArgumentNotValidException e) {
		Map<String, String> response = new HashMap<>();
		
		// Extract specific field errors
		e.getBindingResult().getAllErrors().forEach(err -> {
			String fieldName = ((FieldError) err).getField();
			String message = err.getDefaultMessage();
			response.put(fieldName, message);
		});
		log.warn("Validation failed: fields={}", response.keySet());
		
		return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
	}
	
	@ExceptionHandler(AccountNotFound.class)  //Our own excep
	public ResponseEntity<ApiResponse> ResNotFound(AccountNotFound e) {
		log.warn("Account not found: {}", e.getMessage());
		String Message = e.getMessage(); //It's in parent class RunTimeExcep that's why we used super
		ApiResponse apires = new ApiResponse(Message, false);
		return new ResponseEntity<>(apires, HttpStatus.NOT_FOUND);
	}
	
	@ExceptionHandler(APIexception.class)   //Our own excep If you try to create an already existing category
	public ResponseEntity<ApiResponse> myapiexcep(APIexception e) {
		log.warn("API exception: {}", e.getMessage());
		String Message = e.getMessage(); //It's in parent class RunTimeExcep that's why we used super
		ApiResponse apires = new ApiResponse(Message, false);
		return new ResponseEntity<>(apires, HttpStatus.BAD_REQUEST);
	}
	
	@ExceptionHandler(DuplicateTransactionException.class)   //Our own excep If you try to create an already existing category
	public ResponseEntity<ApiResponse> duplicTransac(DuplicateTransactionException e) {
		log.warn("Duplicate transaction: {}", e.getMessage());
		String Message = e.getMessage(); //It's in parent class RunTimeExcep that's why we used super
		ApiResponse apires = new ApiResponse(Message, false);
		return new ResponseEntity<>(apires, HttpStatus.CONFLICT);
	}
	
	@ExceptionHandler(InsufficientFundsException.class)   //Our own excep If you try to create an already existing category
	public ResponseEntity<ApiResponse> insufFunda(InsufficientFundsException e) {
		log.warn("Insufficient funds: {}", e.getMessage());
		String Message = e.getMessage(); //It's in parent class RunTimeExcep that's why we used super
		ApiResponse apires = new ApiResponse(Message, false);
		return new ResponseEntity<>(apires, HttpStatus.BAD_REQUEST);
	}

	// ---------------------------------------------------------------------
	// Framework-level errors. These share the SAME response shape as the
	// security handlers ({status, error, message, path}), so the HTTP layer
	// reports errors consistently while business errors keep ApiResponse.
	// ---------------------------------------------------------------------

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<Map<String, Object>> myNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request) {
		log.warn("Resource not found: {}", request.getRequestURI());
		return buildError(HttpStatus.NOT_FOUND, "Not Found",
				"Requested resource does not exist", request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> myHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
		log.warn("Malformed request body on {}: {}", request.getRequestURI(), e.getMessage());
		return buildError(HttpStatus.BAD_REQUEST, "Bad Request",
				"Malformed JSON request body", request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<Map<String, Object>> myHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
		log.warn("Method not supported on {}: {}", request.getRequestURI(), e.getMethod());
		return buildError(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed",
				"HTTP method not supported for this endpoint", request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> myDataIntegrityViolationException(DataIntegrityViolationException e, HttpServletRequest request) {
		log.warn("Data integrity violation on {}: {}", request.getRequestURI(), e.getMostSpecificCause().getMessage());
		return buildError(HttpStatus.CONFLICT, "Conflict",
				"Request violates a uniqueness or data integrity constraint", request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> myException(Exception e, HttpServletRequest request) throws Exception {
		// Do NOT swallow Spring Security exceptions. Method-security exceptions
		// (e.g. AuthorizationDeniedException from @PreAuthorize, AccessDeniedException)
		// are raised inside the DispatcherServlet and would be converted into a
		// 500 here unless re-propagated; the AuthAccessDeniedHandler /
		// AuthEntryPointJwt are responsible for their 403/401 responses.
		if (e instanceof AuthorizationDeniedException
				|| e instanceof AccessDeniedException
				|| e instanceof AuthenticationException) {
			throw e;
		}
		log.error("Unhandled exception on {}", request.getRequestURI(), e);
		return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
				"An unexpected error occurred", request);
	}

	private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String error, String message, HttpServletRequest request) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", status.value());
		body.put("error", error);
		body.put("message", message);
		body.put("path", request.getRequestURI());
		return ResponseEntity.status(status).body(body);
	}
}
