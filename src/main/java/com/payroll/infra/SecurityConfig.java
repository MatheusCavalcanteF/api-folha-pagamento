package com.payroll.infra;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // Usuário e senha vêm de spring.security.user.* (variáveis APP_USER / APP_PASSWORD).
    // Os cabeçalhos de segurança padrão do Spring Security (nosniff, X-Frame-Options, cache-control...)
    // ficam como estão.
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF desligado: a API é stateless e autentica a cada requisição pelo cabeçalho
                // Authorization (HTTP Basic). Não há cookie de sessão que um site externo possa
                // reaproveitar, que é o cenário que o CSRF protege.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(withDefaults());
        return http.build();
    }
}
