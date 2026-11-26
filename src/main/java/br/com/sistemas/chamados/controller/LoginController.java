package br.com.sistemas.chamados.controller;

import br.com.sistemas.chamados.enums.Papel;
import br.com.sistemas.chamados.security.UsuarioAutenticado;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** Destino neutro: cada papel é enviado para a sua página inicial. */
    @GetMapping("/inicio")
    public String inicio() {
        return UsuarioAutenticado.atual().papel() == Papel.CLIENTE
                ? "redirect:/meu/chamados" : "redirect:/";
    }

    @GetMapping("/acesso-negado")
    public String acessoNegado() {
        return "acesso-negado";
    }
}
