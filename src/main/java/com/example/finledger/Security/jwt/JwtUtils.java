package com.example.finledger.Security.jwt;

import com.example.finledger.Security.Services.UserDetailsImpl;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;


@Component
@Slf4j
public class JwtUtils {

    @Value("${spring.app.jwtExpirationMs}")
    private long jwtExpirationMs;

    @Value("${spring.app.jwtSecret}")
    private String jwtSecret;

    @Value("${spring.app.jwtCookieName}")
    private String jwtCookie;

    @Value("${spring.app.jwtCookiePath:/api}")
    private String jwtCookiePath;

    @Value("${spring.app.jwtCookieHttpOnly:true}")
    private boolean jwtCookieHttpOnly;

    @Value("${spring.app.jwtCookieSecure:false}")
    private boolean jwtCookieSecure;

    @Value("${spring.app.jwtCookieSameSite:Lax}")
    private String jwtCookieSameSite;

    @PostConstruct
    public void validateConfiguration() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("spring.app.jwtSecret must be provided (base64-encoded signing key)");
        }
        try {
            if (Decoders.BASE64.decode(jwtSecret).length < 64) {
                throw new IllegalStateException(
                        "spring.app.jwtSecret must decode to at least 64 bytes (512 bits) for HS512. "
                        + "Generate one with: openssl rand -base64 64");
            }
        } catch (DecodingException | IllegalArgumentException e) {
            throw new IllegalStateException("spring.app.jwtSecret must be valid base64. "
                    + "Generate one with: openssl rand -base64 64", e);
        }
        if (jwtExpirationMs <= 0) {
            throw new IllegalStateException("spring.app.jwtExpirationMs must be greater than zero");
        }
        log.info("JWT configuration valid (cookie path={}, httpOnly={}, secure={}, sameSite={})",
                jwtCookiePath, jwtCookieHttpOnly, jwtCookieSecure, jwtCookieSameSite);
    }

    //Getting JWT from header
    //Old Method without cookie
    //FOR SWAGGER AS VO COOKIE NHI SAMJHTA
    public String getJwtFromHeader(HttpServletRequest request) {
        String bearerToken  = request.getHeader("Authorization");
        log.debug("Authorization Header: {}", bearerToken);
        if(bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); //Remove bearer prefix
        }
        return null;
    }

    public String getJwtFromCookies(HttpServletRequest request) {       //Used in AuthTokenFilter
        Cookie cookie = WebUtils.getCookie(request, jwtCookie);  //Give us the cookie with name jwtCookie type
        if (cookie != null) {
            return cookie.getValue();
        }else{
            return null;
        }
    }

    public ResponseCookie generateJwtCookie(UserDetailsImpl userDetails) { //Used in sign in
        String jwt = generateTokenFromUsername(userDetails);
        log.debug("JWT cookie generated for user: {}", userDetails.getUsername());
        // Cookie lifetime is intentionally aligned to the JWT lifetime so the
        // browser never holds an expired token (no cookie/JWT mismatch).
        long maxAgeSeconds = jwtExpirationMs / 1000;
        ResponseCookie cookie = ResponseCookie.from(jwtCookie, jwt)
                .path(jwtCookiePath)
                .maxAge(maxAgeSeconds)
                .httpOnly(jwtCookieHttpOnly)
                .secure(jwtCookieSecure)
                .sameSite(jwtCookieSameSite)
                .build();
        return cookie;
    }

    public ResponseCookie getCleanCookie() { //Used in sign in
        ResponseCookie cookie = ResponseCookie.from(jwtCookie, null)
                .path(jwtCookiePath)
                .maxAge(0)
                .httpOnly(jwtCookieHttpOnly)
                .secure(jwtCookieSecure)
                .sameSite(jwtCookieSameSite)
                .build();
        return cookie;
    }

    //Generate token from username
    public String generateTokenFromUsername(UserDetailsImpl userDetails) {
        String username = userDetails.getUsername();
        log.debug("Generating JWT token for user: {}", username);
        return Jwts.builder()
                .subject(username) //setting data
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + jwtExpirationMs)) //Current date + time
                .signWith(key()) //key assign kardi usse
                .compact();
    }

    //Generate Username from JWT Token
    public String getUserNameFromToken(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) key()) //verify the key
                .build().parseSignedClaims(token) //preparing for extracting data
                .getPayload().getSubject();
    }

    //Generate Signed key
    public Key key(){
        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(jwtSecret)
        );
    }

    //Validate JWT Token
    public boolean validateToken(String token) {
        try{
            log.debug("Validating JWT token");
            Jwts.parser()
                    .verifyWith((SecretKey) key())
                    .build()
                    .parseSignedClaims(token);
            return true;
        }catch(MalformedJwtException exception){
            log.warn("Invalid JWT token: {}", exception.getMessage());
        } catch (ExpiredJwtException e){
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e){
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e){
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }
}
