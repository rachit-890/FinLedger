package com.example.finledger.security;

import com.example.finledger.Security.Services.UserDetailsImpl;
import com.example.finledger.Security.jwt.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtSecretValidationTest {

	// Deterministic 64-byte test key (NOT a real secret). It decodes to exactly
	// 64 decoded bytes, which JJWT 0.13.0's Keys.hmacShaKeyFor(...) resolves to HS512.
	private static final String VALID_SECRET = Base64.getEncoder().encodeToString(
			"A".repeat(64).getBytes(StandardCharsets.UTF_8));

	private static JwtUtils jwtUtilsWith(String secret, long expirationMs) {
		JwtUtils jwtUtils = new JwtUtils();
		ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", expirationMs);
		ReflectionTestUtils.setField(jwtUtils, "jwtSecret", secret);
		ReflectionTestUtils.setField(jwtUtils, "jwtCookie", "FinLedger");
		return jwtUtils;
	}

	private static String base64OfNBytes(int length) {
		byte[] bytes = new byte[length];
		Arrays.fill(bytes, (byte) 'x');
		return Base64.getEncoder().encodeToString(bytes);
	}

	private static UserDetailsImpl userDetails() {
		return new UserDetailsImpl(UUID.randomUUID(), "alice", "password", "alice@example.com", List.of());
	}

	@Test
	void validateConfiguration_withoutSecret_shouldThrow() {
		assertThrows(IllegalStateException.class,
				() -> jwtUtilsWith(null, 3600000).validateConfiguration());
		assertThrows(IllegalStateException.class,
				() -> jwtUtilsWith("", 3600000).validateConfiguration());
	}

	@Test
	void validateConfiguration_withShortSecret_shouldThrow() {
		String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));
		assertThrows(IllegalStateException.class,
				() -> jwtUtilsWith(shortSecret, 3600000).validateConfiguration());
	}

	@Test
	void validateConfiguration_withSub64ByteSecret_shouldThrow() {
		for (int length : new int[]{32, 48, 63}) {
			String secret = base64OfNBytes(length);
			assertThrows(IllegalStateException.class,
					() -> jwtUtilsWith(secret, 3600000).validateConfiguration(),
					"secret decoding to " + length + " bytes must be rejected (HS512 requires >= 64 bytes)");
		}
	}

	@Test
	void validateConfiguration_withInvalidBase64_shouldThrow() {
		assertThrows(IllegalStateException.class,
				() -> jwtUtilsWith("not valid base64!!!", 3600000).validateConfiguration());
	}

	@Test
	void validateConfiguration_withValidSecret_shouldSucceed() {
		assertDoesNotThrow(() -> jwtUtilsWith(VALID_SECRET, 3600000).validateConfiguration());
	}

	@Test
	void validateConfiguration_withNonPositiveExpiration_shouldThrow() {
		assertThrows(IllegalStateException.class, () -> jwtUtilsWith(VALID_SECRET, 0).validateConfiguration());
	}

	@Test
	void validSecret_generatesTokenWithHS512Algorithm() {
		JwtUtils jwtUtils = jwtUtilsWith(VALID_SECRET, 3600000);
		String token = jwtUtils.generateTokenFromUsername(userDetails());

		String header = new String(
				Base64.getUrlDecoder().decode(token.split("\\.")[0]),
				StandardCharsets.UTF_8);
		assertTrue(header.contains("\"alg\":\"HS512\""), "JWT header must advertise HS512, was: " + header);
		assertFalse(header.contains("HS256"));
		assertFalse(header.contains("HS384"));

		assertTrue(jwtUtils.validateToken(token),
				"token signed with the 64-byte secret must verify via the real JJWT parser");
	}
}