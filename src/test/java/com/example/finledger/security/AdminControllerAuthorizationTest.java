package com.example.finledger.security;

import com.example.finledger.Payloads.Auditresponse;
import com.example.finledger.Security.Config.WebSecurityConfig;
import com.example.finledger.Security.Services.UserDetailsServiceImpl;
import com.example.finledger.Security.jwt.AuthAccessDeniedHandler;
import com.example.finledger.Security.jwt.AuthEntryPointJwt;
import com.example.finledger.Security.jwt.JwtUtils;
import com.example.finledger.controller.AdminController;
import com.example.finledger.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminController.class)
@Import({WebSecurityConfig.class, AuthEntryPointJwt.class, AuthAccessDeniedHandler.class})
class AdminControllerAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private AdminService adminService;

	@MockBean
	private JwtUtils jwtUtils;

	@MockBean
	private UserDetailsServiceImpl userDetailsServiceImpl;

	@Test
	void adminEndpoint_withoutAuthentication_shouldReturn401() throws Exception {
		mockMvc.perform(get("/api/admin/audit/" + UUID.randomUUID()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Authentication Failed"))
				.andExpect(jsonPath("$.message").value("Full authentication is required to access this resource"));
	}

	@Test
	@WithMockUser(username = "alice", roles = "USER")
	void adminEndpoint_withAuthenticatedUserRole_shouldReturn403() throws Exception {
		mockMvc.perform(get("/api/admin/audit/" + UUID.randomUUID()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.error").value("Forbidden"))
				.andExpect(jsonPath("$.message").value("Access denied"));
	}

	@Test
	@WithMockUser(username = "systemAdmin", roles = "ADMIN")
	void adminEndpoint_withAdminRole_shouldReturn200() throws Exception {
		when(adminService.audit(any())).thenReturn(new Auditresponse("VALID", null));
		String url = "/api/admin/audit/" + UUID.randomUUID();
		mockMvc.perform(get(url))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("VALID"));
	}
}