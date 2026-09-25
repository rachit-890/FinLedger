package com.example.finledger.security;

import com.example.finledger.Security.jwt.AuthAccessDeniedHandler;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthAccessDeniedHandlerTest {
	
	@Test
	void handle_shouldReturn403JsonBody() throws Exception {
		AuthAccessDeniedHandler handler = new AuthAccessDeniedHandler();
		
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		AccessDeniedException accessDeniedException = mock(AccessDeniedException.class);
		
		when(request.getServletPath()).thenReturn("/api/admin/audit/123e4567-e89b-12d3-a456-426614174000");
		when(accessDeniedException.getMessage()).thenReturn("Access Denied");
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		when(response.getOutputStream()).thenReturn(new TestServletOutputStream(out));
		
		handler.handle(request, response, accessDeniedException);
		
		verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
		verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
		
		String body = out.toString(StandardCharsets.UTF_8);
		assertTrue(body.contains("\"status\":403"));
		assertTrue(body.contains("\"error\":\"Forbidden\""));
		assertTrue(body.contains("\"message\":\"Access denied\""));
		assertTrue(body.contains("\"path\":\"/api/admin/audit/123e4567-e89b-12d3-a456-426614174000\""));
	}
	
	// Minimal ServletOutputStream wrapper to capture written bytes
	static class TestServletOutputStream extends ServletOutputStream {
		private final ByteArrayOutputStream delegate;
		
		TestServletOutputStream(ByteArrayOutputStream delegate) {
			this.delegate = delegate;
		}
		
		@Override
		public void write(int b) throws IOException {
			delegate.write(b);
		}
		
		@Override
		public boolean isReady() {
			return true;
		}
		
		@Override
		public void setWriteListener(WriteListener writeListener) {
			// not needed for unit tests
		}
	}
}