package br.com.sistemas.chamados.security;

import br.com.sistemas.chamados.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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

/**
 * Duas cadeias de segurança: API (HTTP Basic, stateless) e painel web (login por formulário).
 * Senhas com BCrypt e autorização por papel (RBAC) por rota.
 * A regra "é o dono do recurso?" fica nos services (AutorizacaoChamado etc.).
 * ATENÇÃO: o Spring usa a PRIMEIRA regra que combina; regras específicas vêm antes das genéricas.
 */
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

    /**
     * API REST: stateless, HTTP Basic, respostas JSON (401/403).
     * Só vale para os caminhos da API; o painel web usa a cadeia abaixo.
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/clientes/**", "/chamados/**", "/tecnicos/**",
                             "/categorias/**", "/comentarios/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> basic.authenticationEntryPoint(
                    (req, res, ex) -> erro(res, 401, "Não autenticado")))
            .exceptionHandling(e -> e.accessDeniedHandler(
                    (req, res, ex) -> erro(res, 403, "Acesso negado")))
            .authorizeHttpRequests(a -> a
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

                .anyRequest().authenticated());
        return http.build();
    }

    /**
     * Painel web (Thymeleaf): login por formulário, sessão, logout e CSRF ligado.
     * Diferente do Basic, o formulário permite sair e entrar de novo com outra conta.
     */
    @Bean
    @Order(2)
    SecurityFilterChain painelChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/login", "/error", "/css/**", "/js/**").permitAll()
                .requestMatchers("/acesso-negado").authenticated()
                .requestMatchers("/meu/**").hasRole("CLIENTE")
                .requestMatchers("/").hasAnyRole("ADMIN", "TECNICO")
                .requestMatchers("/app/chamados/**").hasAnyRole("ADMIN", "TECNICO")
                .requestMatchers("/app/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/inicio")
                .failureUrl("/login?erro"))
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?saiu")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            .exceptionHandling(e -> e.accessDeniedPage("/acesso-negado"));
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
