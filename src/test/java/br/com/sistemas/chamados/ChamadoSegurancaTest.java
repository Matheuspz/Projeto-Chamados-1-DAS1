package br.com.sistemas.chamados;

import br.com.sistemas.chamados.entity.Categoria;
import br.com.sistemas.chamados.entity.Chamado;
import br.com.sistemas.chamados.entity.Cliente;
import br.com.sistemas.chamados.entity.Tecnico;
import br.com.sistemas.chamados.enums.Prioridade;
import br.com.sistemas.chamados.repository.CategoriaRepository;
import br.com.sistemas.chamados.repository.ChamadoRepository;
import br.com.sistemas.chamados.repository.ClienteRepository;
import br.com.sistemas.chamados.repository.TecnicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Evidências do item 5.1: acesso permitido, usuário sem permissão e recurso inexistente.
 * Usa os usuários criados por DadosIniciais (senha de desenvolvimento Senha@123).
 */
@SpringBootTest
class ChamadoSegurancaTest {

    private static final String SENHA = "Senha@123";

    @Autowired WebApplicationContext context;
    @Autowired ChamadoRepository chamados;
    @Autowired ClienteRepository clientes;
    @Autowired CategoriaRepository categorias;
    @Autowired TecnicoRepository tecnicos;

    MockMvc mvc;
    Long cliente1, cliente2;
    Long chamadoDoCliente1;          // sem técnico
    Long chamadoAtribuidoAoTecnico;  // com técnico atribuído

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Cliente c1 = clientePorEmail("cliente1@chamados.local");
        Cliente c2 = clientePorEmail("cliente2@chamados.local");
        cliente1 = c1.getId();
        cliente2 = c2.getId();

        Tecnico tecnico = tecnicoPorEmail("tecnico@chamados.local");

        Categoria categoria = categorias.findAll().stream().findFirst()
                .orElseGet(() -> categorias.save(new Categoria("Teste", "Categoria de teste")));

        // Chamado SEM técnico (usado nos testes de negação)
        chamadoDoCliente1 = chamados.save(
                new Chamado("Chamado do cliente 1", "descrição", Prioridade.MEDIA, c1, categoria)).getId();

        // Chamado COM técnico atribuído (usado no teste positivo do técnico)
        Chamado chamadoComTecnico = new Chamado("Chamado atribuído", "descrição", Prioridade.MEDIA, c2, categoria);
        chamadoComTecnico.setTecnico(tecnico);
        chamadoAtribuidoAoTecnico = chamados.save(chamadoComTecnico).getId();
    }

    private Cliente clientePorEmail(String email) {
        return clientes.findAll().stream()
                .filter(c -> c.getEmail().equals(email))
                .findFirst().orElseThrow();
    }

    private Tecnico tecnicoPorEmail(String email) {
        return tecnicos.findAll().stream()
                .filter(t -> t.getEmail().equals(email))
                .findFirst().orElseThrow();
    }

    // ---- Cenário 1: acesso permitido ----

    @Test
    void clienteConsultaOProprioChamado() throws Exception {
        mvc.perform(get("/chamados/" + chamadoDoCliente1)
                        .with(httpBasic("cliente1@chamados.local", SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(chamadoDoCliente1));
    }

    @Test
    void tecnicoAcessaChamadoAtribuido() throws Exception {
        mvc.perform(get("/chamados/" + chamadoAtribuidoAoTecnico)
                        .with(httpBasic("tecnico@chamados.local", SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(chamadoAtribuidoAoTecnico));
    }

    @Test
    void adminAcessaQualquerChamado() throws Exception {
        mvc.perform(get("/chamados/" + chamadoDoCliente1)
                        .with(httpBasic("admin@chamados.local", SENHA)))
                .andExpect(status().isOk());
    }

    // ---- Cenário 2: usuário sem permissão ----

    @Test
    void clienteNaoVeChamadoDeOutroCliente() throws Exception {
        mvc.perform(get("/chamados/" + chamadoAtribuidoAoTecnico)
                        .with(httpBasic("cliente1@chamados.local", SENHA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void tecnicoNaoAcessaChamadoNaoAtribuido() throws Exception {
        mvc.perform(get("/chamados/" + chamadoDoCliente1)
                        .with(httpBasic("tecnico@chamados.local", SENHA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void clienteNaoExcluiChamado() throws Exception {
        mvc.perform(delete("/chamados/" + chamadoDoCliente1)
                        .with(httpBasic("cliente1@chamados.local", SENHA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteNaoPodeFecharChamado() throws Exception {
        mvc.perform(put("/chamados/fechar/" + chamadoDoCliente1)
                        .with(httpBasic("cliente1@chamados.local", SENHA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteNaoListaTodosClientes() throws Exception {
        mvc.perform(get("/clientes")
                        .with(httpBasic("cliente1@chamados.local", SENHA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void tecnicoNaoListaChamadosPorCliente() throws Exception {
        mvc.perform(get("/clientes/" + cliente1 + "/chamados")
                        .with(httpBasic("tecnico@chamados.local", SENHA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void semCredenciaisRecebe401() throws Exception {
        mvc.perform(get("/chamados"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void senhaErradaRecebe401() throws Exception {
        mvc.perform(get("/chamados")
                        .with(httpBasic("cliente1@chamados.local", "errada123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void painelSemLoginRedirecionaParaLogin() throws Exception {
        mvc.perform(get("/app/chamados"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ---- Cenário 3: recurso inexistente ----

    @Test
    void adminRecebe404ParaChamadoInexistente() throws Exception {
        mvc.perform(get("/chamados/99999")
                        .with(httpBasic("admin@chamados.local", SENHA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}