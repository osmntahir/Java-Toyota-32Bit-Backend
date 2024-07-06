package com.toyota.saleservice.security;



import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String ADMIN = "ADMIN";
    public static final String CASHIER = "CASHIER";
    public static final String STORE_MANAGER = "STORE_MANAGER";
    private final JwtConverter jwtConverter;
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests((authz) -> authz

                .requestMatchers(HttpMethod.GET, "/sale/**").hasRole(CASHIER)
                .requestMatchers(HttpMethod.POST, "/sale/**").hasRole(CASHIER)
                .requestMatchers(HttpMethod.PUT, "/sale/**").hasRole(CASHIER)
                .requestMatchers(HttpMethod.DELETE, "/sale/**").hasRole(CASHIER)


                .anyRequest().authenticated());


        http.sessionManagement(sess -> sess.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS));
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));

        return http.build();
    }

}