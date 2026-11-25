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

    ChamadoService(ChamadoRepository repository, TecnicoService tecnicoService, CategoriaService categoriaService, ClienteService clienteService, AutorizacaoChamado autorizacaoChamado) {
        this.repository = repository;
        this.tecnicoService = tecnicoService;
        this.categoriaService = categoriaService;
        this.clienteService = clienteService;
        this.autorizacao = autorizacaoChamado;
    }

    @Transactional
    public ChamadoResponse criar(ChamadoRequest dto) {

        UsuarioAutenticado usuario = UsuarioAutenticado.atual();
        Long cid = usuario.papel() == Papel.CLIENTE ? usuario.id() : dto.clienteId();

        if (cid == null) {
            throw new RegraNegocioException("clienteId é obrigatório para administradores");
        }

        Cliente cliente = clienteService.buscarEntity(cid);
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
        return repository.filtrar(status, prioridade, clienteId, pageable)
                .map(ChamadoResponse::de);
    }
    @Transactional(readOnly = true)
    public Page<ChamadoResponse> listarPorCliente(Long clienteId, Pageable pageable) {
        clienteService.buscarEntity(clienteId);
        return repository.findByCliente_Id(clienteId, pageable)
                .map(ChamadoResponse::de);
    }

    @Transactional(readOnly = true)
    public ChamadoResponse buscar(Long id) {
        Chamado chamado = buscarEntity(id);
        autorizacao.verificar(chamado);
        return ChamadoResponse.de(chamado);
    }

    @Transactional
    public ChamadoResponse atualizar(Long id, ChamadoRequest dto) {
        if (repository.existsByStatus(StatusChamado.FECHADO)) {
            throw new RegraNegocioException("Não é possível editar um chamado Fechado");
        }
        Chamado chamado = buscarEntity(id);
        autorizacao.verificar(chamado);
        chamado.setTitulo(dto.titulo());
        chamado.setDescricao(dto.descricao());
        chamado.setPrioridade(dto.prioridade());
        chamado.setCliente(clienteService.buscarEntity(dto.clienteId()));
        chamado.setCategoria(categoriaService.buscarEntity(dto.categoriaId()));
        return ChamadoResponse.de(chamado);
    }

    @Transactional
    public ChamadoResponse atribuirTecnico(Long id, ChamadoAtribuirRequest dto) {
        if (repository.existsByStatus(StatusChamado.FECHADO)) {
            throw new RegraNegocioException("Não é possível atribuir um Técnico a um chamado Fechado");
        }
        Chamado chamado = buscarEntity(id);
        autorizacao.verificar(chamado);
        chamado.setTecnico(tecnicoService.buscarEntity(dto.tecnicoId()));
        return ChamadoResponse.de(chamado);
    }

    @Transactional
    public ChamadoResponse alterarStatus(Long id, ChamadoStatusRequest dto) {
        if (dto.status().equals(StatusChamado.FECHADO)) {
            throw new RegraNegocioException("Não é permitido alterar o status para Fechado");
        }
        Chamado chamado = buscarEntity(id);
        autorizacao.verificar(chamado);
        chamado.setStatus(dto.status());
        return ChamadoResponse.de(chamado);
    }

    @Transactional
    public ChamadoResponse fechar(Long id) {
        Chamado chamado = buscarEntity(id);
        autorizacao.verificar(chamado);
        chamado.setDataFechamento(LocalDateTime.now());
        chamado.setStatus(StatusChamado.FECHADO);
        return ChamadoResponse.de(chamado);
    }

    @Transactional
    public void excluir(Long id) {
        Chamado chamado = buscarEntity(id);
        autorizacao.verificar(chamado);
        repository.delete(chamado);
    }

    public Chamado buscarEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Chamado " + id + " não encontrado"));
    }
}
