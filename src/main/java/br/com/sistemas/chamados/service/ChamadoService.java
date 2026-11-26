package br.com.sistemas.chamados.service;

import br.com.sistemas.chamados.dto.ChamadoAtribuirRequest;
import br.com.sistemas.chamados.dto.ChamadoRequest;
import br.com.sistemas.chamados.dto.ChamadoResponse;
import br.com.sistemas.chamados.dto.ChamadoStatusRequest;
import br.com.sistemas.chamados.entity.Categoria;
import br.com.sistemas.chamados.entity.Chamado;
import br.com.sistemas.chamados.entity.Cliente;
import br.com.sistemas.chamados.enums.Papel;
import br.com.sistemas.chamados.enums.Prioridade;
import br.com.sistemas.chamados.enums.StatusChamado;
import br.com.sistemas.chamados.exception.RecursoNaoEncontradoException;
import br.com.sistemas.chamados.exception.RegraNegocioException;
import br.com.sistemas.chamados.repository.ChamadoRepository;
import br.com.sistemas.chamados.security.AutorizacaoChamado;
import br.com.sistemas.chamados.security.UsuarioAutenticado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChamadoService {

    private final ChamadoRepository repository;
    private final TecnicoService tecnicoService;
    private final CategoriaService categoriaService;
    private final ClienteService clienteService;
    private final AutorizacaoChamado autorizacao;

    ChamadoService(ChamadoRepository repository, TecnicoService tecnicoService,
                   CategoriaService categoriaService, ClienteService clienteService,
                   AutorizacaoChamado autorizacao) {
        this.repository = repository;
        this.tecnicoService = tecnicoService;
        this.categoriaService = categoriaService;
        this.clienteService = clienteService;
        this.autorizacao = autorizacao;
    }

    @Transactional
    public ChamadoResponse criar(ChamadoRequest dto) {
        UsuarioAutenticado u = UsuarioAutenticado.atual();
        if (u.papel() == Papel.TECNICO) {
            throw new AccessDeniedException("Sem permissão");
        }
        Long clienteId = u.papel() == Papel.CLIENTE ? u.clienteId() : dto.clienteId();
        if (clienteId == null) {
            throw new RegraNegocioException("Cliente é obrigatório");
        }

        Cliente cliente = clienteService.buscarEntity(clienteId);
        Categoria categoria = categoriaService.buscarEntity(dto.categoriaId());

        Chamado salvo = repository.save(new Chamado(
                dto.titulo(),
                dto.descricao(),
                dto.prioridade(),
                cliente,
                categoria
        ));
        return ChamadoResponse.de(salvo);
    }

    @Transactional(readOnly = true)
    public Page<ChamadoResponse> listar(StatusChamado status, Prioridade prioridade, Long clienteId, Pageable pageable) {
        UsuarioAutenticado u = UsuarioAutenticado.atual();
        Long filtroCliente = clienteId;
        Long filtroTecnico = null;
        if (u.papel() == Papel.CLIENTE) {
            filtroCliente = u.clienteId();
        } else if (u.papel() == Papel.TECNICO) {
            filtroTecnico = u.tecnicoId();
        }
        return repository.filtrar(status, prioridade, filtroCliente, filtroTecnico, pageable)
                .map(ChamadoResponse::de);
    }

    @Transactional(readOnly = true)
    public Page<ChamadoResponse> listarPorCliente(Long clienteId, Pageable pageable) {
        UsuarioAutenticado u = UsuarioAutenticado.atual();
        if (u.papel() == Papel.TECNICO) {
            throw new AccessDeniedException("Sem permissão");
        }
        if (u.papel() == Papel.CLIENTE && !clienteId.equals(u.clienteId())) {
            throw new RecursoNaoEncontradoException("Cliente " + clienteId + " não encontrado");
        }
        clienteService.buscarEntity(clienteId);
        return repository.findByCliente_Id(clienteId, pageable)
                .map(ChamadoResponse::de);
    }

    @Transactional(readOnly = true)
    public ChamadoResponse buscar(Long id) {
        return ChamadoResponse.de(buscarEntity(id));
    }

    @Transactional
    public ChamadoResponse atualizar(Long id, ChamadoRequest dto) {
        Chamado chamado = buscarEntity(id);
        UsuarioAutenticado u = UsuarioAutenticado.atual();
        if (u.papel() == Papel.TECNICO) {
            throw new AccessDeniedException("Sem permissão");
        }
        if (u.papel() == Papel.CLIENTE && chamado.getStatus() != StatusChamado.ABERTO) {
            throw new RegraNegocioException("Só é possível editar chamados abertos");
        }
        chamado.setTitulo(dto.titulo());
        chamado.setDescricao(dto.descricao());
        chamado.setPrioridade(dto.prioridade());

        chamado.setCategoria(categoriaService.buscarEntity(dto.categoriaId()));
        return ChamadoResponse.de(chamado);
    }

    @Transactional
    public ChamadoResponse atribuirTecnico(Long id, ChamadoAtribuirRequest dto) {
        if (UsuarioAutenticado.atual().papel() != Papel.ADMIN) {
            throw new AccessDeniedException("Sem permissão");
        }
        Chamado chamado = buscarEntity(id);
        if (chamado.getStatus() == StatusChamado.FECHADO) {
            throw new RegraNegocioException("Não é possível atribuir um Técnico a um chamado Fechado");
        }
        chamado.setTecnico(tecnicoService.buscarEntity(dto.tecnicoId()));
        return ChamadoResponse.de(chamado);
    }

    @Transactional
    public ChamadoResponse alterarStatus(Long id, ChamadoStatusRequest dto) {
        Chamado chamado = buscarEntity(id);
        if (UsuarioAutenticado.atual().papel() == Papel.CLIENTE) {
            throw new AccessDeniedException("Sem permissão");
        }
        if (dto.status().equals(StatusChamado.FECHADO)) {
            throw new RegraNegocioException("Não é permitido alterar o status para Fechado");
        }
        chamado.setStatus(dto.status());
        return ChamadoResponse.de(chamado);
    }

    @Transactional
    public ChamadoResponse fechar(Long id) {
        Chamado chamado = buscarEntity(id);
        if (chamado.getStatus() == StatusChamado.FECHADO) {
            throw new RegraNegocioException("Chamado já está fechado");
        }
        chamado.setDataFechamento(LocalDateTime.now());
        chamado.setStatus(StatusChamado.FECHADO);
        return ChamadoResponse.de(chamado);
    }

    @Transactional
    public void excluir(Long id) {
        if (UsuarioAutenticado.atual().papel() != Papel.ADMIN) {
            throw new AccessDeniedException("Sem permissão");
        }
        repository.delete(buscarEntity(id));
    }

    public Chamado buscarEntity(Long id) {
        Chamado chamado = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Chamado " + id + " não encontrado"));
        autorizacao.verificar(chamado);
        return chamado;
    }

}
