package br.com.sistemas.chamados.service;

import br.com.sistemas.chamados.dto.ComentarioRequest;
import br.com.sistemas.chamados.dto.ComentarioResponse;
import br.com.sistemas.chamados.entity.Chamado;
import br.com.sistemas.chamados.entity.Comentario;
import br.com.sistemas.chamados.enums.Papel;
import br.com.sistemas.chamados.exception.RecursoNaoEncontradoException;
import br.com.sistemas.chamados.repository.ComentarioRepository;
import br.com.sistemas.chamados.security.UsuarioAutenticado;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ComentarioService {

    private final ComentarioRepository repository;
    private final ChamadoService chamadoService;

    public ComentarioService(ComentarioRepository repository, ChamadoService chamadoService) {
        this.repository = repository;
        this.chamadoService = chamadoService;
    }

    @Transactional
    public ComentarioResponse criar(ComentarioRequest dto) {
        Chamado chamado = chamadoService.buscarEntity(dto.chamadoId());

        String autor = UsuarioAutenticado.atual().nome();

        Comentario salvo = repository.save(new Comentario(dto.texto(), autor, chamado));
        return ComentarioResponse.de(salvo);
    }

    @Transactional(readOnly = true)
    public List<ComentarioResponse> listar() {
        exigirAdmin();
        return repository.findAll().stream().map(ComentarioResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public ComentarioResponse buscar(Long id) {
        Comentario comentario = buscarEntity(id);
        chamadoService.buscarEntity(comentario.getChamado().getId());
        return ComentarioResponse.de(comentario);
    }

    @Transactional
    public ComentarioResponse atualizar(Long id, ComentarioRequest dto) {
        exigirAdmin();
        Comentario comentario = buscarEntity(id);
        comentario.setTexto(dto.texto());
        return ComentarioResponse.de(comentario);
    }

    @Transactional
    public void excluir(Long id) {
        exigirAdmin();
        repository.delete(buscarEntity(id));
    }

    public Comentario buscarEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Comentario " + id + " não encontrado"));
    }

    private void exigirAdmin() {
        if (UsuarioAutenticado.atual().papel() != Papel.ADMIN) {
            throw new AccessDeniedException("Sem permissão");
        }
    }
}
