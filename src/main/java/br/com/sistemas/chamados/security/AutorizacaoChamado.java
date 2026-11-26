package br.com.sistemas.chamados.security;

import br.com.sistemas.chamados.entity.Chamado;
import br.com.sistemas.chamados.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Component;

@Component
public class AutorizacaoChamado {
    public void verificar(Chamado chamado) {
        UsuarioAutenticado u = UsuarioAutenticado.atual();
        boolean permitido = switch (u.papel()) {
            case ADMIN -> true;
            case CLIENTE -> u.clienteId() != null
                    && u.clienteId().equals(chamado.getCliente().getId());
            case TECNICO -> u.tecnicoId() != null
                    && chamado.getTecnico() != null
                    && u.tecnicoId().equals(chamado.getTecnico().getId());
        };
        if (!permitido) {
            throw new RecursoNaoEncontradoException("Chamado " + chamado.getId() + " não encontrado");
        }
    }
}
