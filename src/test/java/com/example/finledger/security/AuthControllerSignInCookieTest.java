package com.example.finledger.security;

import com.example.finledger.Security.Services.UserDetailsImpl;
import com.example.finledger.Security.Services.UserDetailsServiceImpl;
import com.example.finledger.Security.jwt.JwtUtils;
import com.example.finledger.controller.AuthController;
import com.example.finledger.repositories.RoleRepository;
import com.example.finledger.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(JwtUtils.class)
@TestPropertySource(properties = {
		"spring.app.jwtExpirationMs=3600000",
		"spring.app.jwtSecret=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQQ==",
		"spring.app.jwtCookieName=FinLedger",
		"spring.app.jwtCookiePath=/api",
		"spring.app.jwtCookieHttpOnly=true",
		"spring.app.jwtCookieSecure=false",
		"spring.app.jwtCookieSameSite=Lax"
})
class AuthControllerSignInCookieTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private UserRepository userRepository;

	@MockBean
	private RoleRepository roleRepository;

	@MockBean
	private PasswordEncoder passwordEncoder;

	@MockBean
	private AuthenticationManager authenticationManager;

	@MockBean
	private UserDetailsServiceImpl userDetailsServiceImpl;

	@Test
	void signin_shouldReturnHardenedSetCookieAndTokenInBody() throws Exception {
		UserDetailsImpl userDetails = new UserDetailsImpl(UUID.randomUUID(), "alice", "password",
				"alice@example.com", List.of());
		Authentication authentication = mock(Authentication.class);
		when(authentication.getPrincipal()).thenReturn(userDetails);
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenReturn(authentication);

		mockMvc.perform(post("/api/auth/signin")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"alice\",\"password\":\"password\"}"))
				.andExpect(status().isOk())
				// Cookie attributes
				.andExpect(header().string("Set-Cookie", containsString("FinLedger=")))
				.andExpect(header().string("Set-Cookie", containsString("Path=/api")))
				.andExpect(header().string("Set-Cookie", containsString("Max-Age=3600")))
				.andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
				.andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
				.andExpect(header().string("Set-Cookie", not(containsString("Secure"))))
				// Contract: JWT is also returned in the response body
				.andExpect(jsonPath("$.username").value("alice"))
				.andExpect(jsonPath("$.jwtToken").isNotEmpty());
	}

	@Test
	void signout_shouldClearCookie() throws Exception {
		mockMvc.perform(post("/api/auth/signout"))
				.andExpect(status().isOk())
				.andExpect(header().string("Set-Cookie", containsString("FinLedger=")))
				.andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
	}
}