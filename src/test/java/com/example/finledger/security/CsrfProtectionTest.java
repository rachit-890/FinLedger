package com.example.finledger.security;

import com.example.finledger.Payloads.ApiResponse;
import com.example.finledger.Security.Config.WebSecurityConfig;
import com.example.finledger.Security.Services.UserDetailsServiceImpl;
import com.example.finledger.Security.jwt.AuthAccessDeniedHandler;
import com.example.finledger.Security.jwt.AuthEntryPointJwt;
import com.example.finledger.Security.jwt.JwtUtils;
import com.example.finledger.controller.AccountController;
import com.example.finledger.model.User;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountController.class)
@Import({WebSecurityConfig.class, AuthEntryPointJwt.class, AuthAccessDeniedHandler.class})
class CsrfProtectionTest {

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
	void stateChangingRequest_withoutCsrfToken_shouldReturn403() throws Exception {
		mockMvc.perform(post("/api/account/create")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountName\":\"Savings\",\"currency\":\"USD\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.error").value("Forbidden"))
				.andExpect(jsonPath("$.message").value("Access denied"));
	}

	@Test
	@WithMockUser
	void stateChangingRequest_withCsrfToken_shouldSucceed() throws Exception {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mock(User.class)));
		mockMvc.perform(post("/api/account/create")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountName\":\"Savings\",\"currency\":\"USD\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	@WithMockUser
	void stateChangingRequest_withAuthorizationHeader_shouldBypassCsrf() throws Exception {
		when(accountService.deposit(any(), any(), any(), any()))
				.thenReturn(new ApiResponse("Deposited", true));
		mockMvc.perform(post("/api/deposit")
						.header("Authorization", "Bearer dummy-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"toAccountId\":\"22222222-2222-2222-2222-222222222222\","
								+ "\"amount\":10.00,\"referenceId\":\"csrf-bypass-1\"}"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser
	void safeGetRequest_withoutCsrfToken_shouldSucceed() throws Exception {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mock(User.class)));
		when(accountRepository.findAllByUser(any())).thenReturn(List.of());
		mockMvc.perform(get("/api/account/list"))
				.andExpect(status().isOk());
	}
}