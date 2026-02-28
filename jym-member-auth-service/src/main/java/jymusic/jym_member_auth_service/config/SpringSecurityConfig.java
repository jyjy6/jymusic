package jymusic.jym_member_auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SpringSecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Disable CSRF for REST API
            .csrf(AbstractHttpConfigurer::disable)
            
            // 2. Set Session policy to STATELESS
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // 3. Authorization Rules
            .authorizeHttpRequests(auth -> auth
                // Allow Auth related endpoints (Register, Login)
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Allow Swagger UI & OpenAPI Docs
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/openapi.yaml").permitAll()
                // Everything else requires authentication (In MSA, often checked at Gateway)
                .anyRequest().permitAll() // Temporarily permit all for dev, or change to authenticated()
            )
            
            // 4. Form Login and Basic Auth disabled
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
