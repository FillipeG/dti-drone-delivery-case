package com.dtidigital.simulador.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZonaExclusao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank(message = "O nome da zona é obrigatório")
    private String nome;

    @NotNull(message = "A coordenada X do centro é obrigatória")
    private Double coordenadaX;

    @NotNull(message = "A coordenada Y do centro é obrigatória")
    private Double coordenadaY;

    @NotNull(message = "O raio da zona é obrigatório")
    @Positive(message = "O raio deve ser maior que zero")
    private Double raioKm;

}