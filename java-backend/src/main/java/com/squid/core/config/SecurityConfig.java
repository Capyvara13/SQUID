package com.squid.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Security configuration with optional RBAC.
 * When squid.rbac.enabled=false (default), all requests are permitted.
 * When squid.rbac.enabled=true, HTTP Basic auth is enforced with roles:
 *   admin    — full access
 *   auditor  — read-only access to audit/database endpoints
 *   operator — standard crypto/instance operations
 *   system   — internal service-to-service calls
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${squid.rbac.enabled:false}")
    private boolean rbacEnabled;

    @Value("${squid.rbac.default-admin-password:squid_admin}")
    private String adminPassword;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails admin = User.builder()
                .username("admin")
                .password(encoder.encode(adminPassword))
                .roles("ADMIN", "AUDITOR", "OPERATOR")
                .build();
        UserDetails auditor = User.builder()
                .username("auditor")
                .password(encoder.encode("auditor"))
                .roles("AUDITOR")
                .build();
        UserDetails operator = User.builder()
                .username("operator")
                .password(encoder.encode("operator"))
                .roles("OPERATOR")
                .build();
        UserDetails system = User.builder()
                .username("system")
                .password(encoder.encode("system"))
                .roles("SYSTEM", "ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin, auditor, operator, system);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .cors().configurationSource(corsConfigurationSource());

        if (rbacEnabled) {
            http
                .httpBasic()
                .and()
                .authorizeRequests()
                // Health/info endpoints open
                .antMatchers("/api/v1/system/health", "/api/v1/health", "/h2-console/**").permitAll()
                // Database & audit — admin + auditor
                .antMatchers("/api/v1/database/**").hasAnyRole("ADMIN", "AUDITOR")
                // Crypto & instances — admin + operator
                .antMatchers("/api/v1/crypto/**", "/api/v1/instances/**").hasAnyRole("ADMIN", "OPERATOR")
                // Model management — admin only
                .antMatchers("/api/v1/models/**").hasRole("ADMIN")
                // Everything else requires authentication
                .anyRequest().authenticated();
            // Allow H2 console frames
            http.headers().frameOptions().sameOrigin();
        } else {
            http
                .httpBasic().disable()
                .formLogin().disable()
                .authorizeRequests()
                .anyRequest().permitAll();
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Permite qualquer origem, inclusive "null" (file://)
        configuration.setAllowedOrigins(Arrays.asList("*", "null"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // NÃO incluir allowCredentials(true) quando usar "*"
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
