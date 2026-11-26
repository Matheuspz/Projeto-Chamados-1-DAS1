package br.com.sistemas.chamados.repository;

import br.com.sistemas.chamados.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Usuario> findByClienteId(Long clienteId);

    Optional<Usuario> findByTecnicoId(Long tecnicoId);
}
