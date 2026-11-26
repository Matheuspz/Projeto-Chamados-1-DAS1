package br.com.sistemas.chamados.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClienteRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String nome,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @Size(max = 20, message = "Telefone dever ter no máximo 20 caracteres")
        String telefone,

        @Pattern(regexp = "^(.{8,72})?$", message = "Senha deve ter entre 8 e 72 caracteres")
        String senha
)
{
        @Override
        public String toString() {
            return "ClienteRequest[nome=" + nome + ", email=" + email + "]";
        }
}
