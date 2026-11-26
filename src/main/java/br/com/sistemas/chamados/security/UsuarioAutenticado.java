package br.com.sistemas.chamados.security;

import br.com.sistemas.chamados.entity.Usuario;
import br.com.sistemas.chamados.enums.Papel;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record UsuarioAutenticado(
        Long id,
        String nome,
        String email,
        String senhaHash,
        Papel papel,
        Long clienteId,
        Long tecnicoId)

        implements UserDetails {

    public static UsuarioAutenticado de(Usuario u) {
        return new UsuarioAutenticado(u.getId(), u.getNome(), u.getEmail(), u.getSenhaHash(),
                u.getPapel(), u.getClienteId(), u.getTecnicoId());
    }

    public static UsuarioAutenticado atual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado u)) {
            throw new AccessDeniedException("Não autenticado");
        }
        return u;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + papel));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /** Evita que o hash da senha apareça em logs. */
    @Override
    public String toString() {
        return "UsuarioAutenticado[id=" + id + ", papel=" + papel + "]";
    }
}
