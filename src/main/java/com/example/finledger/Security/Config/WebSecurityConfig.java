package com.example.finledger.Security.Config;


import com.example.finledger.Security.Services.UserDetailsServiceImpl;
import com.example.finledger.Security.jwt.AuthAccessDeniedHandler;
import com.example.finledger.Security.jwt.AuthEntryPointJwt;
import com.example.finledger.Security.jwt.AuthTokenFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity

public class WebSecurityConfig {

    @Autowired
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    @Autowired
    private AuthAccessDeniedHandler accessDeniedHandler;

    @Value("${spring.app.allowedOrigins:http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthTokenFilter authJwtTokenFilter() {
        return new AuthTokenFilter();
    }

    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider() {
	    // 1. Use the Empty Constructor (as seen in the source code)
	    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
	    
	    // 2. Set the UserDetailsService manually
	    provider.setUserDetailsService(userDetailsServiceImpl); // <--- THIS IS THE FIX
	    
	    // 3. Set the PasswordEncoder
	    provider.setPasswordEncoder(passwordEncoder());
	    
	    return provider;

    }

    @Bean
    public AuthenticationManager authenticationManagerBean(AuthenticationConfiguration auth) throws Exception {
        return auth.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF is REQUIRED because authentication is cookie-based.
        //   * The XSRF-TOKEN cookie (HttpOnly=false) is set eagerly on every
        //     response; the client must echo it back as the X-XSRF-TOKEN header
        //     on state-changing requests (POST/PUT/DELETE/PATCH).
        //   * CSRF is skipped when an Authorization header is present (Bearer
        //     tokens from Swagger/Postman are not susceptible to CSRF) and for
        //     the /api/auth/** endpoints (login/signup/signout must stay
        //     frictionless and cannot be CSRF-attacked in a meaningful way).
        //   * Safe methods (GET/HEAD/OPTIONS) are exempt by the CsrfFilter.
        // Default CsrfTokenRequestAttributeHandler (no attribute-name override):
        // the deferred token is materialized on every response, which eagerly
        // writes the XSRF-TOKEN cookie on the very first request (including
        // the sign-in response) so the browser can immediately echo its value
        // back via the X-XSRF-TOKEN header on state-changing requests.
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new EagerCsrfTokenRequestAttributeHandler();

        http.csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .ignoringRequestMatchers(new OrRequestMatcher(
                                new RequestHeaderRequestMatcher("Authorization"),
                                new AntPathRequestMatcher("/api/auth/**"))))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                //Unauth ko exception mai daal do AuthEntruyPointJwt mai
        .exceptionHandling(exception -> exception
                .authenticationEntryPoint(unauthorizedHandler)
                .accessDeniedHandler(accessDeniedHandler))
                //Stateless bnanae ke liye
                .sessionManagement(session  //cookie nhi bnegi
                -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(requests -> requests
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(("/v3/api-docs/**")).permitAll()
                        .requestMatchers(("/swagger-ui/**")).permitAll()
                        .requestMatchers(("/api/test/**")).permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated());

        //This sets our dao as a worker for AuthManager at login
        http.authenticationProvider(daoAuthenticationProvider());   //Defined for calls at login page at AuthManager

        //Setting our filter before every other chod bhangra
        http.addFilterBefore(authJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);


        http.headers(headers ->   //To disable login on h2
                headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));

        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow the frontend URL(s) explicitly (comma-separated via
        // spring.app.allowedOrigins; default http://localhost:5173). An
        // explicit allowlist is required because cookies are used.
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .collect(Collectors.toList());
        configuration.setAllowedOrigins(origins);

        // Allow all HTTP methods
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));

        // Allow all headers (needed for Authorization and Content-Type, and the
        // X-XSRF-TOKEN CSRF header)
        configuration.setAllowedHeaders(List.of("*"));

        // Allow credentials (cookies/auth headers)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {    //BYpasses Every fuckinn spring filter
        return (web ->  web.ignoring().requestMatchers(
                "/configyration/ui",
                "/swagger-resources/**",
                "/configuration/security",
                "/swagger-ui.html",
                "/webjars/**"));
    }

    /**
     * Materializes the deferred {@link CsrfToken} on EVERY response (not just
     * POSTs), so the {@code XSRF-TOKEN} cookie is written by the
     * {@link CookieCsrfTokenRepository} from the very first response the
     * client receives. A browser can then read the cookie and echo it back as
     * the {@code X-XSRF-TOKEN} header on state-changing requests.
     */
    static class EagerCsrfTokenRequestAttributeHandler extends CsrfTokenRequestAttributeHandler {
        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            csrfToken.get(); // forces RepositoryDeferredCsrfToken.init -> saves the XSRF-TOKEN cookie
            super.handle(request, response, csrfToken);
        }
    }
}
