package br.com.sistemas.chamados.service;

import br.com.sistemas.chamados.dto.TecnicoRequest;
import br.com.sistemas.chamados.dto.TecnicoResponse;
import br.com.sistemas.chamados.entity.Tecnico;
import br.com.sistemas.chamados.entity.Usuario;
import br.com.sistemas.chamados.enums.Papel;
import br.com.sistemas.chamados.enums.Perfil;
import br.com.sistemas.chamados.exception.RecursoNaoEncontradoException;
import br.com.sistemas.chamados.exception.RegraNegocioException;
import br.com.sistemas.chamados.repository.TecnicoRepository;
import br.com.sistemas.chamados.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TecnicoService {

    private final TecnicoRepository repository;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public TecnicoService(TecnicoRepository repository, UsuarioRepository usuarios,
                          PasswordEncoder encoder) {
        this.repository = repository;
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Transactional
    public TecnicoResponse criar(TecnicoRequest dto) {
        if (repository.existsByEmail(dto.email()) || usuarios.existsByEmail(dto.email())) {
            throw new RegraNegocioException("Dados já cadastrados");
        }
        if (dto.senha() == null || dto.senha().isBlank()) {
            throw new RegraNegocioException("Senha é obrigatória (8 a 72 caracteres)");
        }
        Tecnico salvo = repository.save(new Tecnico(dto.nome(), dto.email(), dto.perfil()));
        usuarios.save(new Usuario(salvo.getNome(), salvo.getEmail(), encoder.encode(dto.senha()),
                papelDe(dto.perfil()), null, salvo.getId()));
        return TecnicoResponse.de(salvo);
    }

    @Transactional(readOnly = true)
    public List<TecnicoResponse> listar() {
        return repository.findAll().stream().map(TecnicoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public TecnicoResponse buscar(Long id) {
        return TecnicoResponse.de(buscarEntity(id));
    }

    @Transactional
    public TecnicoResponse atualizar(Long id, TecnicoRequest dto) {
        Tecnico tecnico = buscarEntity(id);
        boolean emailUsadoPorOutro =
                repository.existsByEmailAndIdNot(dto.email(), id)
                        || usuarios.findByEmail(dto.email())
                        .filter(usr -> !id.equals(usr.getTecnicoId())).isPresent();
        if (emailUsadoPorOutro) {
            throw new RegraNegocioException("Dados já cadastrados");
        }
        tecnico.setNome(dto.nome());
        tecnico.setEmail(dto.email());
        tecnico.setPerfil(dto.perfil());

        usuarios.findByTecnicoId(id).ifPresent(usr -> {
            usr.setNome(dto.nome());
            usr.setEmail(dto.email());
            usr.setPapel(papelDe(dto.perfil()));
            if (dto.senha() != null && !dto.senha().isBlank()) {
                usr.setSenhaHash(encoder.encode(dto.senha()));
            }
        });
        return TecnicoResponse.de(tecnico);
    }

    @Transactional
    public void excluir(Long id) {
        Tecnico tecnico = buscarEntity(id);
        usuarios.findByTecnicoId(id).ifPresent(usuarios::delete);
        repository.delete(tecnico);
    }

    public Tecnico buscarEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Tecnico " + id + " não encontrado"));
    }

    private Papel papelDe(Perfil perfil) {
        return perfil == Perfil.ADMIN ? Papel.ADMIN : Papel.TECNICO;
    }
}
