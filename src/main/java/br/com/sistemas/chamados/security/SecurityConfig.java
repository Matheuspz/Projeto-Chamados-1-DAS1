package br.com.sistemas.chamados.security;

import br.com.sistemas.chamados.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.time.LocalDateTime;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UsuarioRepository usuarios) {
        return email -> usuarios.findByEmail(email)
                .map(UsuarioAutenticado::de)
                // mensagem genérica: não revela se o e-mail existe
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.authenticationEntryPoint(
                        (req, res, ex) -> erro(res, 401, "Não autenticado")))
                .exceptionHandling(e -> e.accessDeniedHandler(
                        (req, res, ex) -> erro(res, 403, "Acesso negado")))
                .authorizeHttpRequests(a -> a
                        // públicos
                        .requestMatchers("/error", "/css/**", "/js/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/clientes").permitAll()

                        // clientes
                        .requestMatchers(HttpMethod.GET, "/clientes").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/clientes/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/clientes/*/chamados").hasAnyRole("ADMIN", "CLIENTE")
                        .requestMatchers("/clientes/*").hasAnyRole("ADMIN", "CLIENTE") // service confere o dono

                        // técnicos e categorias
                        .requestMatchers("/tecnicos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/categorias/**").authenticated()
                        .requestMatchers("/categorias/**").hasRole("ADMIN")

                        // chamados (o service confere dono/atribuído)
                        .requestMatchers(HttpMethod.DELETE, "/chamados/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/chamados/*/atribuir").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/chamados/*/status").hasAnyRole("ADMIN", "TECNICO")

                        // comentários
                        .requestMatchers(HttpMethod.GET, "/comentarios").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/comentarios/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/comentarios/*").hasRole("ADMIN")

                        // painel web
                        .requestMatchers("/").hasAnyRole("ADMIN", "TECNICO")
                        .requestMatchers("/app/chamados/**").hasAnyRole("ADMIN", "TECNICO")
                        .requestMatchers("/app/**").hasRole("ADMIN")

                        .anyRequest().authenticated());
        return http.build();
    }

    private static void erro(HttpServletResponse res, int status, String mensagem) throws IOException {
        res.setStatus(status);
        if (status == 401) {
            res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"chamados\"");
        }
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"momento\":\"" + LocalDateTime.now() + "\",\"status\":" + status
                + ",\"erro\":\"" + mensagem + "\",\"detalhes\":[]}");
    }
}