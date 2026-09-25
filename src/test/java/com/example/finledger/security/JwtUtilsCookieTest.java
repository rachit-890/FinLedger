package com.example.finledger.security;

import com.example.finledger.Security.Services.UserDetailsImpl;
import com.example.finledger.Security.jwt.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilsCookieTest {

	private JwtUtils jwtUtils;

	private static final String VALID_SECRET = Base64.getEncoder().encodeToString(
			"A".repeat(64).getBytes(StandardCharsets.UTF_8));

	@BeforeEach
	void setUp() {
		jwtUtils = new JwtUtils();
		ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3600000L);
		ReflectionTestUtils.setField(jwtUtils, "jwtSecret", VALID_SECRET);
		ReflectionTestUtils.setField(jwtUtils, "jwtCookie", "FinLedger");
		ReflectionTestUtils.setField(jwtUtils, "jwtCookiePath", "/api");
		ReflectionTestUtils.setField(jwtUtils, "jwtCookieHttpOnly", true);
		ReflectionTestUtils.setField(jwtUtils, "jwtCookieSecure", false);
		ReflectionTestUtils.setField(jwtUtils, "jwtCookieSameSite", "Lax");
	}

	private UserDetailsImpl user() {
		return new UserDetailsImpl(UUID.randomUUID(), "alice", "password", "alice@example.com", List.of());
	}

	@Test
	void generateJwtCookie_shouldBeHardenedAndAlignedToExpiration() {
		ResponseCookie cookie = jwtUtils.generateJwtCookie(user());
		String value = cookie.toString();

		assertTrue(value.contains("FinLedger="));
		assertTrue(value.contains("Path=/api"));
		assertTrue(value.contains("Max-Age=3600")); // jwtExpirationMs / 1000
		assertTrue(value.contains("HttpOnly"));
		assertTrue(value.contains("SameSite=Lax"));
		assertFalse(value.contains("Secure")); // secure=false by default (plain HTTP dev)
	}

	@Test
	void generateJwtCookie_withSecureEnabled_shouldSetSecureFlag() {
		ReflectionTestUtils.setField(jwtUtils, "jwtCookieSecure", true);
		assertTrue(jwtUtils.generateJwtCookie(user()).toString().contains("Secure"));
	}

	@Test
	void generateJwtCookie_maxAge_shouldFollowExpiration() {
		ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 7200000L);
		assertTrue(jwtUtils.generateJwtCookie(user()).toString().contains("Max-Age=7200"));
	}

	@Test
	void getCleanCookie_shouldExpireImmediately() {
		String value = jwtUtils.getCleanCookie().toString();
		assertTrue(value.contains("FinLedger="));
		assertTrue(value.contains("Path=/api"));
		assertTrue(value.contains("Max-Age=0"));
	}
}