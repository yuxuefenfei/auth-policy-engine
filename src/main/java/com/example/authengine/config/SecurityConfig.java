package com.example.authengine.config;

import com.example.authengine.security.BearerSessionFilter;
import com.example.authengine.security.LoginFlowAuthenticationProvider;
import com.example.authengine.security.SessionTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService users,
                                                       PasswordEncoder encoder, LoginFlowAuthenticationProvider flow) {
        DaoAuthenticationProvider dao = new DaoAuthenticationProvider(users);
        dao.setPasswordEncoder(encoder);
        return new ProviderManager(List.of(flow, dao));
    }

    /**
     * Basic 仅作为后台管理员和“APP 已登录状态”的演示入口，不能绕过网页登录策略。
     */
    @Bean
    @Order(1)
    public SecurityFilterChain privilegedChain(HttpSecurity http, AuthenticationManager manager)
            throws Exception {
        http.securityMatcher("/api/admin/**", "/api/app/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationManager(manager)
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/app/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().denyAll());
        return http.build();
    }

    /**
     * 正式网页登录态只来自已经满足完整 AST 的 Attempt，不能直接 Basic 登录。
     */
    @Bean
    @Order(2)
    public SecurityFilterChain publicApiChain(HttpSecurity http,
                                              AuthenticationManager manager, SessionTokenService tokens,
                                              UserDetailsService users) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable) // JSON API + SameSite=Strict；生产按架构补充 CSRF 保护。
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationManager(manager)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/login/**").permitAll()
                        .requestMatchers("/api/me").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(new BearerSessionFilter(tokens, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
