package edu.eci.arsw.raceflow.realtime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration: stateless, CORS for the allowed frontend
 * origins, and public routes for REST, WebSocket, invitations, health, and
 * the incident-simulation endpoint (JWT validation for WS happens at the
 * handshake via {@link edu.eci.arsw.raceflow.realtime.websocket.WebSocketAuthInterceptor},
 * not through this filter chain).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * @param http the security builder provided by Spring
     * @return the configured filter chain
     * @throws Exception if the security configuration cannot be built
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF relies on ambient browser credentials (cookies); this API is stateless and
                // authenticates via an explicit Authorization: Bearer header (REST) or a token query
                // param verified at the WS handshake, neither of which browsers attach automatically
                // cross-origin, so CSRF does not apply here.
                .csrf(csrf -> csrf.disable()) // NOSONAR java:S4502 -- stateless JWT API, no session cookies
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**", "/rooms/**", "/invitations/**", "/api/simulate/**").permitAll()
                        // Explicit paths, not a /actuator/** wildcard: only what Prometheus and
                        // uptime checks actually need is public.
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers
                        .contentTypeOptions(withDefaults -> {})
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer -> referrer
                                .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                );

        return http.build();
    }

    /**
     * @return the CORS configuration source allowlisting local dev and the
     *         production frontend on Azure Static Web Apps
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "https://lively-rock-0066b1e0f.7.azurestaticapps.net"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
