package com.example.demo.config;

import com.example.demo.model.Admin;
import com.example.demo.model.Prof;
import com.example.demo.model.User;
import com.example.demo.model.UserStatus;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.JwtService;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Volontairement PAS un @Component : il est créé dans SecurityConfig.
// Sinon Spring Boot l'enregistrerait aussi comme filtre servlet et l'authentification serait perdue.
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")) {
            try {
                String email = jwtService.extractEmail(header.substring(7));

                // Le compte doit toujours exister ET être approuvé (il peut avoir été refusé/supprimé depuis)
                userRepository.findByEmail(email)
                        .filter(u -> u.getStatus() == UserStatus.APPROVED)
                        .ifPresent(user -> {
                            // Le principal est l'entité User : c'est ce que reçoivent les
                            // contrôleurs via @AuthenticationPrincipal User current
                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(user, null, authoritiesOf(user));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
            } catch (JwtException | IllegalArgumentException e) {
                // Token invalide ou expiré : la requête reste anonyme (=> 401 si la route est protégée)
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private List<GrantedAuthority> authoritiesOf(User user) {
        if (user instanceof Admin) {
            return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_PROF"));
        }
        if (user instanceof Prof) {
            return List.of(new SimpleGrantedAuthority("ROLE_PROF"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }
}
