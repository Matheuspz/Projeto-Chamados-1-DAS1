package br.com.sistemas.chamados.service;

import br.com.sistemas.chamados.dto.ClienteRequest;
import br.com.sistemas.chamados.dto.ClienteResponse;
import br.com.sistemas.chamados.entity.Cliente;
import br.com.sistemas.chamados.entity.Usuario;
import br.com.sistemas.chamados.enums.Papel;
import br.com.sistemas.chamados.exception.RecursoNaoEncontradoException;
import br.com.sistemas.chamados.exception.RegraNegocioException;
import br.com.sistemas.chamados.repository.ClienteRepository;
import br.com.sistemas.chamados.repository.UsuarioRepository;
import br.com.sistemas.chamados.security.UsuarioAutenticado;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClienteService {

    private final ClienteRepository repository;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public ClienteService(ClienteRepository clienteRepository, UsuarioRepository usuarios,
                          PasswordEncoder encoder) {
        this.repository = clienteRepository;
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Transactional
    public ClienteResponse criar(ClienteRequest dto) {
        if (repository.existsByEmail(dto.email()) || usuarios.existsByEmail(dto.email())) {
            throw new RegraNegocioException("Dados já cadastrados");
        }
        if (dto.senha() == null || dto.senha().isBlank()) {
            throw new RegraNegocioException("Senha é obrigatória (8 a 72 caracteres)");
        }
        Cliente salvo = repository.save(new Cliente(dto.nome(), dto.email(), dto.telefone()));
        usuarios.save(new Usuario(salvo.getNome(), salvo.getEmail(), encoder.encode(dto.senha()),
                Papel.CLIENTE, salvo.getId(), null));
        return ClienteResponse.de(salvo);
    }

    @Transactional(readOnly = true)
    public List<ClienteResponse> listar() {
        return repository.findAll().stream().map(ClienteResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public ClienteResponse buscar(Long id) {
        exigirProprioOuAdmin(id);
        return ClienteResponse.de(buscarEntity(id));
    }

    @Transactional
    public ClienteResponse atualizar(Long id, ClienteRequest dto) {
        exigirProprioOuAdmin(id);
        Cliente cliente = buscarEntity(id);
        boolean emailUsadoPorOutro =
                repository.existsByEmailAndIdNot(dto.email(), id)
                        || usuarios.findByEmail(dto.email())
                        .filter(usr -> !id.equals(usr.getClienteId())).isPresent();
        if (emailUsadoPorOutro) {
            throw new RegraNegocioException("Dados já cadastrados");
        }
        cliente.setNome(dto.nome());
        cliente.setEmail(dto.email());
        cliente.setTelefone(dto.telefone());

        usuarios.findByClienteId(id).ifPresent(usr -> {
            usr.setNome(dto.nome());
            usr.setEmail(dto.email());
            if (dto.senha() != null && !dto.senha().isBlank()) {
                usr.setSenhaHash(encoder.encode(dto.senha()));
            }
        });
        return ClienteResponse.de(cliente);
    }

    @Transactional
    public void excluir(Long id) {
        Cliente cliente = buscarEntity(id);
        usuarios.findByClienteId(id).ifPresent(usuarios::delete);
        repository.delete(cliente);
    }

    public Cliente buscarEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Cliente " + id + " não encontrado"));
    }

    private void exigirProprioOuAdmin(Long id) {
        UsuarioAutenticado u = UsuarioAutenticado.atual();
        if (u.papel() == Papel.TECNICO) {
            throw new AccessDeniedException("Sem permissão");
        }
        if (u.papel() == Papel.CLIENTE && !id.equals(u.clienteId())) {
            throw new RecursoNaoEncontradoException("Cliente " + id + " não encontrado");
        }
    }
}
