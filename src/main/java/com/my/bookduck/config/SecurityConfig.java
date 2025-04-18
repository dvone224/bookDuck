package com.my.bookduck.config;

import com.my.bookduck.config.auth.BDUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // 중요
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final BDUserDetailsService bdUserDetailsService;
    private final AuthenticationConfiguration authenticationConfiguration;
    private final UserSuccessHandler userSuccessHandler;
    private final UserAuthFailureHandler userAuthFailureHandler;

    // 시큐리티 기능 비활성화
    @Bean
    public WebSecurityCustomizer configure() { // 스프링 시큐리티 기능 비활성화
        return (web) -> web.ignoring()
                .requestMatchers(
                        new AntPathRequestMatcher("/static/**"),
                        new AntPathRequestMatcher("/img/**"),
                        new AntPathRequestMatcher("/css/**"),
                        new AntPathRequestMatcher("/js/**")
                );
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 다른 설정들...
                .csrf(AbstractHttpConfigurer::disable); // CSRF 보호 비활성화

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/home", "/login","/book","login-form", "/**").permitAll() // 페이지 경로 확정시 추가
                        .requestMatchers("/user","/book").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/admin").hasAnyRole("ADMIN")
                        .requestMatchers("/error").permitAll()

                );

        http
                .formLogin(
                        form ->{
                            form.loginPage("/login-form")
                                    .loginProcessingUrl("/login")
                                    .defaultSuccessUrl("/home", true);
                        }
                ).oauth2Login(
                        oauth2-> oauth2
                                .loginPage("/login-form")
                                .successHandler(userSuccessHandler)
                                .failureHandler(userAuthFailureHandler)
                                .permitAll()
                        );
        http
                .sessionManagement((auth) -> auth
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(true));

        http
                .sessionManagement((auth) -> auth
                        .sessionFixation().changeSessionId());


        return http.build();
    }

    // 패스워드 암호화
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}