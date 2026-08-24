package com.builddash.backend.infra.security;

import com.builddash.backend.domain.enums.TokenType;
import com.builddash.backend.domain.model.TokenClaims;
import com.builddash.backend.domain.port.TokenValidator;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.common.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenValidator tokenValidator;
    private final UserRepository userRepository;


    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            boolean authenticated = authenticate(token, TokenType.ACCESS);
            if (!authenticated) {
                authenticate(token, TokenType.GUEST);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * A guest token dies when its guest identity dies: after a login-merge the users row is
     * marked merged_into_user_id and the token must stop authenticating everywhere, not just
     * on cart endpoints.
     * // ponytail: per-request PK lookup on GUEST tokens only — Redis denylist if guest traffic ever gets hot
     */
    private boolean guestIdentityStillActive(UUID guestUserId) {
        return userRepository.findById(guestUserId)
                .map(user -> user.getMergedIntoUserId() == null)
                .orElse(false);
    }

    private boolean authenticate(String token, TokenType type) {
        try {
            TokenClaims claims = tokenValidator.validate(token, type);
            if (type == TokenType.GUEST && !guestIdentityStillActive(claims.userId())) {
                return false;
            }
            AuthenticatedUser principal = new AuthenticatedUser(claims.userId(), claims.deviceId(), claims.roles());
            List<GrantedAuthority> authorities = claims.roles().stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
