package br.com.sistemas.chamados.controller;

import br.com.sistemas.chamados.enums.Papel;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import br.com.sistemas.chamados.security.UsuarioAutenticado;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Disponibiliza "ehAdmin" para os templates do painel (usado para esconder o menu de Cadastros). */
@ControllerAdvice(assignableTypes = PainelController.class)
public class PainelAdvice {

    @ModelAttribute("ehAdmin")
    public boolean ehAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof UsuarioAutenticado u
                && u.papel() == Papel.ADMIN;
    }
}
