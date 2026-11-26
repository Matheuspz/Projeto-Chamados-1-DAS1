package br.com.sistemas.chamados.controller;

import br.com.sistemas.chamados.dto.CategoriaResponse;
import br.com.sistemas.chamados.dto.ChamadoRequest;
import br.com.sistemas.chamados.dto.ChamadoResponse;
import br.com.sistemas.chamados.enums.Prioridade;
import br.com.sistemas.chamados.enums.StatusChamado;
import br.com.sistemas.chamados.exception.RecursoNaoEncontradoException;
import br.com.sistemas.chamados.exception.RegraNegocioException;
import br.com.sistemas.chamados.service.CategoriaService;
import br.com.sistemas.chamados.service.ChamadoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Portal do cliente (/meu/**, só papel CLIENTE). Reaproveita o ChamadoService, que já
 * restringe tudo ao usuário logado: o dono é o do login e chamado alheio responde 404.
 */
@Controller
@RequestMapping("/meu/chamados")
public class PortalClienteController {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ChamadoService chamadoService;
    private final CategoriaService categoriaService;

    public PortalClienteController(ChamadoService chamadoService, CategoriaService categoriaService) {
        this.chamadoService = chamadoService;
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public String listar(@RequestParam(name = "page", defaultValue = "0") int page, Model model) {
        Page<ChamadoResponse> chamados = chamadoService.listar(null, null, null,
                PageRequest.of(Math.max(0, page), 10, Sort.by(Sort.Direction.DESC, "dataCriacao")));
        model.addAttribute("chamados", chamados);
        return "portal/lista";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        preparar(model, new ChamadoRequest("", "", Prioridade.MEDIA, null, null), false, null);
        return "portal/form";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("form") ChamadoRequest form, BindingResult result,
                        Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            preparar(model, form, false, null);
            return "portal/form";
        }
        try {
            ChamadoResponse criado = chamadoService.criar(form); // dono = usuário logado
            redirect.addFlashAttribute("success", "Chamado aberto com sucesso.");
            return "redirect:/meu/chamados/" + criado.id();
        } catch (RegraNegocioException | RecursoNaoEncontradoException ex) {
            preparar(model, form, false, null);
            model.addAttribute("error", ex.getMessage());
            return "portal/form";
        }
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable("id") Long id, Model model, RedirectAttributes redirect) {
        try {
            ChamadoResponse chamado = chamadoService.buscar(id);
            model.addAttribute("chamado", chamado);
            model.addAttribute("criadoEm", formatar(chamado.dataCriacao()));
            model.addAttribute("podeEditar", chamado.status() == StatusChamado.ABERTO);
            return "portal/detalhe";
        } catch (RecursoNaoEncontradoException ex) {
            redirect.addFlashAttribute("error", "Chamado não encontrado.");
            return "redirect:/meu/chamados";
        }
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable("id") Long id, Model model, RedirectAttributes redirect) {
        try {
            ChamadoResponse c = chamadoService.buscar(id);
            if (c.status() != StatusChamado.ABERTO) {
                redirect.addFlashAttribute("error", "Só é possível editar chamados abertos.");
                return "redirect:/meu/chamados/" + id;
            }
            preparar(model, new ChamadoRequest(c.titulo(), c.descricao(), c.prioridade(), null, c.categoriaId()),
                    true, id);
            return "portal/form";
        } catch (RecursoNaoEncontradoException ex) {
            redirect.addFlashAttribute("error", "Chamado não encontrado.");
            return "redirect:/meu/chamados";
        }
    }

    @PostMapping("/{id}/editar")
    public String atualizar(@PathVariable("id") Long id, @Valid @ModelAttribute("form") ChamadoRequest form,
                            BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            preparar(model, form, true, id);
            return "portal/form";
        }
        try {
            chamadoService.atualizar(id, form);
            redirect.addFlashAttribute("success", "Chamado atualizado com sucesso.");
        } catch (RegraNegocioException | RecursoNaoEncontradoException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/meu/chamados/" + id;
    }

    private void preparar(Model model, ChamadoRequest form, boolean editando, Long id) {
        List<CategoriaResponse> categorias = categoriaService.listar();
        model.addAttribute("form", form);
        model.addAttribute("editando", editando);
        model.addAttribute("registroId", id);
        model.addAttribute("categorias", categorias);
        model.addAttribute("prioridadeOptions", Prioridade.values());
    }

    private String formatar(LocalDateTime data) {
        return data == null ? "—" : data.format(FORMATO);
    }
}
