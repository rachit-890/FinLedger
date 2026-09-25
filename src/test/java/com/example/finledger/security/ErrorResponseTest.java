package com.example.finledger.security;

import com.example.finledger.Security.Config.WebSecurityConfig;
import com.example.finledger.Security.Services.UserDetailsServiceImpl;
import com.example.finledger.Security.jwt.AuthAccessDeniedHandler;
import com.example.finledger.Security.jwt.AuthEntryPointJwt;
import com.example.finledger.Security.jwt.JwtUtils;
import com.example.finledger.controller.AccountController;
import com.example.finledger.repositories.AccountRepository;
import com.example.finledger.repositories.UserRepository;
import com.example.finledger.service.AccountService;
import com.example.finledger.service.RateLimitingService;
import com.example.finledger.utils.AuthUtils;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountController.class)
@Import({WebSecurityConfig.class, AuthEntryPointJwt.class, AuthAccessDeniedHandler.class})
class ErrorResponseTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private AccountService accountService;

	@MockBean
	private AccountRepository accountRepository;

	@MockBean
	private UserRepository userRepository;

	@MockBean
	private AuthUtils authUtils;

	@MockBean
	private RateLimitingService rateLimitingService;

	@MockBean
	private JwtUtils jwtUtils;

	@MockBean
	private UserDetailsServiceImpl userDetailsServiceImpl;

	private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@BeforeEach
	void setUp() {
		Bucket bucket = mock(Bucket.class);
		when(bucket.tryConsume(1)).thenReturn(true);
		when(rateLimitingService.resolveBucket(any(), any())).thenReturn(bucket);
		when(authUtils.loggedInUserId()).thenReturn(USER_ID);
	}

	@Test
	@WithMockUser
	void unknownPath_authenticated_shouldReturn404Json() throws Exception {
		mockMvc.perform(get("/api/does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").value("Requested resource does not exist"))
				.andExpect(jsonPath("$.path").value("/api/does-not-exist"));
	}

	@Test
	void unknownPath_unauthenticated_shouldReturn401Json() throws Exception {
		mockMvc.perform(get("/api/does-not-exist"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Authentication Failed"));
	}

	@Test
	@WithMockUser
	void malformedRequestBody_shouldReturn400Json() throws Exception {
		mockMvc.perform(post("/api/account/create")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{not-valid-json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("Malformed JSON request body"));
	}

	@Test
	@WithMockUser
	void wrongMethodOnExistingResource_shouldReturn405Json() throws Exception {
		mockMvc.perform(post("/api/account/list")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.status").value(405))
				.andExpect(jsonPath("$.error").value("Method Not Allowed"));
	}

	@Test
	@WithMockUser
	void unexpectedError_shouldReturn500JsonWithoutLeakingDetails() throws Exception {
		when(accountService.deposit(any(), any(), any(), any()))
				.thenThrow(new RuntimeException("boom: <sensitive-internal-detail>"));
		mockMvc.perform(post("/api/deposit")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"toAccountId\":\"22222222-2222-2222-2222-222222222222\","
								+ "\"amount\":5,\"referenceId\":\"err-500\"}"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.error").value("Internal Server Error"))
				.andExpect(jsonPath("$.message").value("An unexpected error occurred"))
				.andExpect(content().string(not(containsString("boom"))));
	}
}