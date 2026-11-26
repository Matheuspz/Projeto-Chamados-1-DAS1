package br.com.sistemas.chamados;

import br.com.sistemas.chamados.entity.Categoria;
import br.com.sistemas.chamados.entity.Chamado;
import br.com.sistemas.chamados.entity.Cliente;
import br.com.sistemas.chamados.enums.Prioridade;
import br.com.sistemas.chamados.repository.CategoriaRepository;
import br.com.sistemas.chamados.repository.ChamadoRepository;
import br.com.sistemas.chamados.repository.ClienteRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
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

    MockMvc mvc;
    Long cliente1, cliente2, chamadoDoCliente1, chamadoDoCliente2;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Cliente c1 = clientePorEmail("cliente1@chamados.local");
        Cliente c2 = clientePorEmail("cliente2@chamados.local");
        cliente1 = c1.getId();
        cliente2 = c2.getId();

        Categoria categoria = categorias.findAll().stream().findFirst()
                .orElseGet(() -> categorias.save(new Categoria("Teste", "Categoria de teste")));

        chamadoDoCliente1 = chamados.save(
                new Chamado("Chamado do cliente 1", "descrição", Prioridade.MEDIA, c1, categoria)).getId();
        chamadoDoCliente2 = chamados.save(
                new Chamado("Chamado do cliente 2", "descrição", Prioridade.MEDIA, c2, categoria)).getId();
    }

    private Cliente clientePorEmail(String email) {
        return clientes.findAll().stream()
                .filter(c -> c.getEmail().equals(email))
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
    void clienteListaApenasOsProprios() throws Exception {
        // mesmo pedindo o clienteId de outro, o service força o filtro pelo token
        mvc.perform(get("/chamados?clienteId=" + cliente2)
                        .with(httpBasic("cliente1@chamados.local", SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.clienteId != " + cliente1 + ")]").isEmpty());
    }

    // ---- Cenário 2: usuário sem permissão ----

    @Test
    void clienteNaoVeChamadoDeOutroCliente() throws Exception {
        mvc.perform(get("/chamados/" + chamadoDoCliente2)
                        .with(httpBasic("cliente1@chamados.local", SENHA)))
                .andExpect(status().isNotFound());   // 404: não revela que existe
    }

    @Test
    void clienteNaoExcluiChamado() throws Exception {
        mvc.perform(delete("/chamados/" + chamadoDoCliente1)
                        .with(httpBasic("cliente1@chamados.local", SENHA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteNaoExcluiCliente() throws Exception {
        mvc.perform(delete("/clientes/" + cliente2)
                        .with(httpBasic("cliente1@chamados.local", SENHA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteNaoListaChamadosDeOutroCliente() throws Exception {
        mvc.perform(get("/clientes/" + cliente2 + "/chamados")
                        .with(httpBasic("cliente1@chamados.local", SENHA)))
                .andExpect(status().isNotFound());
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
        mvc.perform(get("/chamados").with(httpBasic("cliente1@chamados.local", "errada123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void painelSemLoginRedirecionaParaLogin() throws Exception {
        mvc.perform(get("/app/chamados"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
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
