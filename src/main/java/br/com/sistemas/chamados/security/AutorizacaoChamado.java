package br.com.sistemas.chamados.security;

import br.com.sistemas.chamados.entity.Chamado;
import br.com.sistemas.chamados.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Component;

@Component
public class AutorizacaoChamado {
    public void verificar(Chamado c) {
        UsuarioAutenticado u = UsuarioAutenticado.atual();
        boolean ok = switch (u.papel()) {
            case ADMIN   -> true;
            case CLIENTE -> c.getCliente().getId().equals(u.clienteId());
            case TECNICO -> c.getTecnico() != null && c.getTecnico().getId().equals(u.tecnicoId());
        };
        if (!ok) throw new RecursoNaoEncontradoException("Chamado " + c.getId() + " não encontrado");
    }
}
