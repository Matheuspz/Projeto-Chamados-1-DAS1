package br.com.sistemas.chamados.security;

import br.com.sistemas.chamados.exception.ErroResposta;
import br.com.sistemas.chamados.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService userDetailsService(UsuarioRepository repo) {
        return email -> repo.findByEmail(email).map(UsuarioAutenticado::de)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.authenticationEntryPoint((req, res, ex) ->
                        escrever(res, mapper, 401, "Não autenticado")))
                .exceptionHandling(e -> e.accessDeniedHandler((req, res, ex) ->
                        escrever(res, mapper, 403, "Acesso negado")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/clientes").permitAll()
                        .requestMatchers(HttpMethod.GET, "/clientes").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/clientes/**").hasRole("ADMIN")
                        .requestMatchers("/clientes/*").hasAnyRole("ADMIN", "CLIENTE") // service confere o dono
                        .requestMatchers("/tecnicos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/categorias/**").authenticated()
                        .requestMatchers("/categorias/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/chamados/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/chamados/*/atribuir").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/chamados/*/status").hasAnyRole("ADMIN", "TECNICO")
                        .requestMatchers(HttpMethod.GET, "/comentarios").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/comentarios/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/comentarios/*").hasRole("ADMIN")
                        .anyRequest().authenticated());
        return http.build();
    }

    private void escrever(HttpServletResponse res, ObjectMapper m, int status, String msg) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        m.writeValue(res.getOutputStream(),
                new ErroResposta(LocalDateTime.now(), status, msg, List.of()));
    }
}