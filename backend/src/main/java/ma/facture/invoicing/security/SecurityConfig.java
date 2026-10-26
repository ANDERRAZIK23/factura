package ma.facture.invoicing.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.io.IOException;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        var configuration = new CorsConfiguration(); configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(java.util.List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("Authorization","Content-Type")); configuration.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", configuration); return source;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwt, Environment environment) throws Exception {
        http.csrf(csrf -> csrf.disable()).cors(cors -> {}).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request,response,exception) -> problem(response,401,"Authentification requise"))
                        .accessDeniedHandler((request,response,exception) -> problem(response,403,"Accès refusé")))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll();
                    auth.requestMatchers("/api/v1/portal/**").permitAll();
                    if (environment.matchesProfiles("dev")) auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    auth.anyRequest().authenticated();
                }).addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    private static void problem(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + (status==401?"Non authentifié":"Interdit") + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
