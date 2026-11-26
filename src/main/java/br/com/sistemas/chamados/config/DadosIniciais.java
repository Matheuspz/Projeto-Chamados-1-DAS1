package br.com.sistemas.chamados.config;

import br.com.sistemas.chamados.entity.Cliente;
import br.com.sistemas.chamados.entity.Tecnico;
import br.com.sistemas.chamados.entity.Usuario;
import br.com.sistemas.chamados.enums.Papel;
import br.com.sistemas.chamados.enums.Perfil;
import br.com.sistemas.chamados.repository.ClienteRepository;
import br.com.sistemas.chamados.repository.TecnicoRepository;
import br.com.sistemas.chamados.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria usuários de DEMONSTRAÇÃO quando o banco está vazio (H2 em memória).
 * Desligue com app.seed.enabled=false em qualquer ambiente real.
 */
@Component
public class DadosIniciais implements CommandLineRunner {

    private final UsuarioRepository usuarios;
    private final ClienteRepository clientes;
    private final TecnicoRepository tecnicos;
    private final PasswordEncoder encoder;
    private final boolean habilitado;
    private final String senha;

    public DadosIniciais(UsuarioRepository usuarios, ClienteRepository clientes,
                         TecnicoRepository tecnicos, PasswordEncoder encoder,
                         @Value("${app.seed.enabled:true}") boolean habilitado,
                         @Value("${app.seed.senha:Senha@123}") String senha) {
        this.usuarios = usuarios;
        this.clientes = clientes;
        this.tecnicos = tecnicos;
        this.encoder = encoder;
        this.habilitado = habilitado;
        this.senha = senha;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!habilitado || usuarios.count() > 0) {
            return;
        }
        String hash = encoder.encode(senha);

        Tecnico admin = tecnicos.save(new Tecnico("Administrador", "admin@chamados.local", Perfil.ADMIN));
        usuarios.save(new Usuario(admin.getNome(), admin.getEmail(), hash, Papel.ADMIN, null, admin.getId()));

        Tecnico tecnico = tecnicos.save(new Tecnico("Técnico Demo", "tecnico@chamados.local", Perfil.TECNICO));
        usuarios.save(new Usuario(tecnico.getNome(), tecnico.getEmail(), hash, Papel.TECNICO, null, tecnico.getId()));

        for (int i = 1; i <= 2; i++) {
            Cliente c = clientes.save(new Cliente("Cliente " + i, "cliente" + i + "@chamados.local", null));
            usuarios.save(new Usuario(c.getNome(), c.getEmail(), hash, Papel.CLIENTE, c.getId(), null));
        }
    }
}
