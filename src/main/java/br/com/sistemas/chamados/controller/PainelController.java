package br.com.sistemas.chamados.controller;

import br.com.sistemas.chamados.dto.*;
import br.com.sistemas.chamados.enums.Perfil;
import br.com.sistemas.chamados.enums.Prioridade;
import br.com.sistemas.chamados.enums.StatusChamado;
import br.com.sistemas.chamados.service.CategoriaService;
import br.com.sistemas.chamados.service.ChamadoService;
import br.com.sistemas.chamados.service.ClienteService;
import br.com.sistemas.chamados.service.TecnicoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class PainelController {

    private static final int TAMANHO_PAGINA = 10;
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ChamadoService chamadoService;
    private final ClienteService clienteService;
    private final TecnicoService tecnicoService;
    private final CategoriaService categoriaService;

    public PainelController(
            ChamadoService chamadoService,
            ClienteService clienteService,
            TecnicoService tecnicoService,
            CategoriaService categoriaService) {
        this.chamadoService = chamadoService;
        this.clienteService = clienteService;
        this.tecnicoService = tecnicoService;
        this.categoriaService = categoriaService;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        Page<ChamadoResponse> recentes = chamadoService.listar(
                null, null, null,
                PageRequest.of(0, 6, Sort.by(Sort.Direction.DESC, "dataCriacao")));

        model.addAttribute("totalChamados", contarChamados(null));
        model.addAttribute("totalAbertos", contarChamados(StatusChamado.ABERTO));
        model.addAttribute("totalEmAndamento", contarChamados(StatusChamado.EM_ANDAMENTO));
        model.addAttribute("totalFechados", contarChamados(StatusChamado.FECHADO));
        model.addAttribute("totalClientes", clienteService.listar().size());
        model.addAttribute("totalTecnicos", tecnicoService.listar().size());
        model.addAttribute("totalCategorias", categoriaService.listar().size());
        model.addAttribute("recentes", recentes.getContent());
        model.addAttribute("active", "dashboard");
        model.addAttribute("breadcrumb", "Visão geral");
        return "dashboard";
    }

    @GetMapping("/app/chamados")
    public String listarChamados(
            @RequestParam(required = false) StatusChamado status,
            @RequestParam(required = false) Prioridade prioridade,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        int pagina = Math.max(0, page);
        Page<ChamadoResponse> chamados = chamadoService.listar(
                status, prioridade, clienteId,
                PageRequest.of(pagina, TAMANHO_PAGINA,
                        Sort.by(Sort.Direction.DESC, "dataCriacao")));

        model.addAttribute("chamados", chamados);
        model.addAttribute("statusSelecionado", status);
        model.addAttribute("prioridadeSelecionada", prioridade);
        model.addAttribute("clienteSelecionado", clienteId);
        model.addAttribute("clientes", clienteService.listar());
        model.addAttribute("statusOptions", StatusChamado.values());
        model.addAttribute("prioridadeOptions", Prioridade.values());
        model.addAttribute("active", "chamados");
        model.addAttribute("breadcrumb", "Chamados");
        return "chamados/lista";
    }

    @GetMapping("/app/chamados/novo")
    public String novoChamado(Model model) {
        prepararFormularioChamado(model, new ChamadoRequest("", "", Prioridade.MEDIA, null, null), false, null);
        return "chamados/form";
    }

    @PostMapping("/app/chamados")
    public String criarChamado(
            @Valid @ModelAttribute("form") ChamadoRequest form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {
        if (result.hasErrors()) {
            prepararFormularioChamado(model, form, false, null);
            return "chamados/form";
        }
        try {
            ChamadoResponse criado = chamadoService.criar(form);
            redirect.addFlashAttribute("success", "Chamado criado com sucesso.");
            return "redirect:/app/chamados/" + criado.id();
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
            return "redirect:/app/chamados";
        }
    }

    @GetMapping("/app/chamados/{id}")
    public String detalheChamado(@PathVariable Long id, Model model) {
        ChamadoResponse chamado = chamadoService.buscar(id);
        model.addAttribute("chamado", chamado);
        model.addAttribute("dataCriacaoFormatada", formatarData(chamado.dataCriacao()));
        model.addAttribute("dataFechamentoFormatada", formatarData(chamado.dataFechamento()));
        model.addAttribute("tecnicos", tecnicoService.listar());
        model.addAttribute("statusOptions", StatusChamado.values());
        model.addAttribute("active", "chamados");
        model.addAttribute("breadcrumb", "Detalhes do chamado");
        return "chamados/detalhe";
    }

    @GetMapping("/app/chamados/{id}/editar")
    public String editarChamado(@PathVariable Long id, Model model) {
        ChamadoResponse chamado = chamadoService.buscar(id);
        ChamadoRequest form = new ChamadoRequest(
                chamado.titulo(), chamado.descricao(), chamado.prioridade(),
                chamado.clienteId(), chamado.categoriaId());
        prepararFormularioChamado(model, form, true, id);
        return "chamados/form";
    }

    @PostMapping("/app/chamados/{id}/editar")
    public String atualizarChamado(
            @PathVariable Long id,
            @Valid @ModelAttribute("form") ChamadoRequest form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {
        if (result.hasErrors()) {
            prepararFormularioChamado(model, form, true, id);
            return "chamados/form";
        }
        try {
            chamadoService.atualizar(id, form);
            redirect.addFlashAttribute("success", "Chamado atualizado com sucesso.");
            return "redirect:/app/chamados/" + id;
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
            return "redirect:/app/chamados/" + id;
        }
    }

    @PostMapping("/app/chamados/{id}/atribuir")
    public String atribuirTecnico(
            @PathVariable Long id,
            @RequestParam Long tecnicoId,
            RedirectAttributes redirect) {
        try {
            chamadoService.atribuirTecnico(id, new ChamadoAtribuirRequest(tecnicoId));
            redirect.addFlashAttribute("success", "Técnico atribuído ao chamado.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
        }
        return "redirect:/app/chamados/" + id;
    }

    @PostMapping("/app/chamados/{id}/status")
    public String alterarStatus(
            @PathVariable Long id,
            @RequestParam StatusChamado status,
            RedirectAttributes redirect) {
        try {
            chamadoService.alterarStatus(id, new ChamadoStatusRequest(status));
            redirect.addFlashAttribute("success", "Status do chamado atualizado.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
        }
        return "redirect:/app/chamados/" + id;
    }

    @PostMapping("/app/chamados/{id}/fechar")
    public String fecharChamado(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            chamadoService.fechar(id);
            redirect.addFlashAttribute("success", "Chamado encerrado.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
        }
        return "redirect:/app/chamados/" + id;
    }

    @PostMapping("/app/chamados/{id}/excluir")
    public String excluirChamado(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            chamadoService.excluir(id);
            redirect.addFlashAttribute("success", "Chamado excluído.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
        }
        return "redirect:/app/chamados";
    }

    @GetMapping("/app/clientes")
    public String listarClientes(Model model) {
        model.addAttribute("clientes", clienteService.listar());
        model.addAttribute("active", "clientes");
        model.addAttribute("breadcrumb", "Clientes");
        return "clientes/lista";
    }

    @GetMapping("/app/clientes/novo")
    public String novoCliente(Model model) {
        prepararFormularioCliente(model, new ClienteRequest("", "", ""), false, null);
        return "clientes/form";
    }

    @PostMapping("/app/clientes")
    public String criarCliente(@Valid @ModelAttribute("form") ClienteRequest form,
                               BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            prepararFormularioCliente(model, form, false, null);
            return "clientes/form";
        }
        try {
            clienteService.criar(form);
            redirect.addFlashAttribute("success", "Cliente cadastrado com sucesso.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
        }
        return "redirect:/app/clientes";
    }

    @GetMapping("/app/clientes/{id}/editar")
    public String editarCliente(@PathVariable Long id, Model model) {
        ClienteResponse cliente = clienteService.buscar(id);
        prepararFormularioCliente(model,
                new ClienteRequest(cliente.nome(), cliente.email(), cliente.telefone()), true, id);
        return "clientes/form";
    }

    @PostMapping("/app/clientes/{id}/editar")
    public String atualizarCliente(@PathVariable Long id,
                                   @Valid @ModelAttribute("form") ClienteRequest form,
                                   BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            prepararFormularioCliente(model, form, true, id);
            return "clientes/form";
        }
        try {
            clienteService.atualizar(id, form);
            redirect.addFlashAttribute("success", "Cliente atualizado com sucesso.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
        }
        return "redirect:/app/clientes";
    }

    @PostMapping("/app/clientes/{id}/excluir")
    public String excluirCliente(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            clienteService.excluir(id);
            redirect.addFlashAttribute("success", "Cliente excluído.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
        }
        return "redirect:/app/clientes";
    }

    @GetMapping("/app/tecnicos")
    public String listarTecnicos(Model model) {
        model.addAttribute("tecnicos", tecnicoService.listar());
        model.addAttribute("active", "tecnicos");
        model.addAttribute("breadcrumb", "Técnicos");
        return "tecnicos/lista";
    }

    @GetMapping("/app/tecnicos/novo")
    public String novoTecnico(Model model) {
        prepararFormularioTecnico(model, new TecnicoRequest("", "", Perfil.TECNICO), false, null);
        return "tecnicos/form";
    }

    @PostMapping("/app/tecnicos")
    public String criarTecnico(@Valid @ModelAttribute("form") TecnicoRequest form,
                               BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            prepararFormularioTecnico(model, form, false, null);
            return "tecnicos/form";
        }
        try {
            tecnicoService.criar(form);
            redirect.addFlashAttribute("success", "Técnico cadastrado com sucesso.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
        }
        return "redirect:/app/tecnicos";
    }

    @GetMapping("/app/tecnicos/{id}/editar")
    public String editarTecnico(@PathVariable Long id, Model model) {
        TecnicoResponse tecnico = tecnicoService.buscar(id);
        prepararFormularioTecnico(model,
                new TecnicoRequest(tecnico.nome(), tecnico.email(), tecnico.perfil()), true, id);
        return "tecnicos/form";
    }

    @PostMapping("/app/tecnicos/{id}/editar")
    public String atualizarTecnico(@PathVariable Long id,
                                   @Valid @ModelAttribute("form") TecnicoRequest form,
                                   BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            prepararFormularioTecnico(model, form, true, id);
            return "tecnicos/form";
        }
        try {
            tecnicoService.atualizar(id, form);
            redirect.addFlashAttribute("success", "Técnico atualizado com sucesso.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
        }
        return "redirect:/app/tecnicos";
    }

    @PostMapping("/app/tecnicos/{id}/excluir")
    public String excluirTecnico(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            tecnicoService.excluir(id);
            redirect.addFlashAttribute("success", "Técnico excluído.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
        }
        return "redirect:/app/tecnicos";
    }

    @GetMapping("/app/categorias")
    public String listarCategorias(Model model) {
        model.addAttribute("categorias", categoriaService.listar());
        model.addAttribute("active", "categorias");
        model.addAttribute("breadcrumb", "Categorias");
        return "categorias/lista";
    }

    @GetMapping("/app/categorias/novo")
    public String novaCategoria(Model model) {
        prepararFormularioCategoria(model, new CategoriaRequest("", ""), false, null);
        return "categorias/form";
    }

    @PostMapping("/app/categorias")
    public String criarCategoria(@Valid @ModelAttribute("form") CategoriaRequest form,
                                 BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            prepararFormularioCategoria(model, form, false, null);
            return "categorias/form";
        }
        try {
            categoriaService.criar(form);
            redirect.addFlashAttribute("success", "Categoria cadastrada com sucesso.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
        }
        return "redirect:/app/categorias";
    }

    @GetMapping("/app/categorias/{id}/editar")
    public String editarCategoria(@PathVariable Long id, Model model) {
        CategoriaResponse categoria = categoriaService.buscar(id);
        prepararFormularioCategoria(model,
                new CategoriaRequest(categoria.titulo(), categoria.descricao()), true, id);
        return "categorias/form";
    }

    @PostMapping("/app/categorias/{id}/editar")
    public String atualizarCategoria(@PathVariable Long id,
                                     @Valid @ModelAttribute("form") CategoriaRequest form,
                                     BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            prepararFormularioCategoria(model, form, true, id);
            return "categorias/form";
        }
        try {
            categoriaService.atualizar(id, form);
            redirect.addFlashAttribute("success", "Categoria atualizada com sucesso.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
        }
        return "redirect:/app/categorias";
    }

    @PostMapping("/app/categorias/{id}/excluir")
    public String excluirCategoria(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            categoriaService.excluir(id);
            redirect.addFlashAttribute("success", "Categoria excluída.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", mensagem(ex));
        }
        return "redirect:/app/categorias";
    }

    private long contarChamados(StatusChamado status) {
        return chamadoService.listar(status, null, null, PageRequest.of(0, 1)).getTotalElements();
    }

    private void prepararFormularioChamado(Model model, ChamadoRequest form, boolean editando, Long id) {
        model.addAttribute("form", form);
        model.addAttribute("editando", editando);
        model.addAttribute("registroId", id);
        model.addAttribute("clientes", clienteService.listar());
        model.addAttribute("categorias", categoriaService.listar());
        model.addAttribute("prioridadeOptions", Prioridade.values());
        model.addAttribute("active", "chamados");
        model.addAttribute("breadcrumb", editando ? "Editar chamado" : "Novo chamado");
    }

    private void prepararFormularioCliente(Model model, ClienteRequest form, boolean editando, Long id) {
        model.addAttribute("form", form);
        model.addAttribute("editando", editando);
        model.addAttribute("registroId", id);
        model.addAttribute("active", "clientes");
        model.addAttribute("breadcrumb", editando ? "Editar cliente" : "Novo cliente");
    }

    private void prepararFormularioTecnico(Model model, TecnicoRequest form, boolean editando, Long id) {
        model.addAttribute("form", form);
        model.addAttribute("editando", editando);
        model.addAttribute("registroId", id);
        model.addAttribute("perfilOptions", Perfil.values());
        model.addAttribute("active", "tecnicos");
        model.addAttribute("breadcrumb", editando ? "Editar técnico" : "Novo técnico");
    }

    private void prepararFormularioCategoria(Model model, CategoriaRequest form, boolean editando, Long id) {
        model.addAttribute("form", form);
        model.addAttribute("editando", editando);
        model.addAttribute("registroId", id);
        model.addAttribute("active", "categorias");
        model.addAttribute("breadcrumb", editando ? "Editar categoria" : "Nova categoria");
    }

    private String formatarData(LocalDateTime data) {
        return data == null ? "—" : data.format(FORMATO_DATA);
    }

    private String mensagem(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Não foi possível concluir a operação." : ex.getMessage();
    }
}
