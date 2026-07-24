package com.dtidigital.simulador.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor

public class Drone {

    @Id
    private String id = UUID.randomUUID().toString();

    @NotNull(message = "A capacidade de peso é obrigatória")
    @Positive(message = "A capacidade deve ser positiva")
    private Double capacidadeMaximaPeso;

    @NotNull(message = "A autonomia deve ser informada")
    @Positive(message = "A autonomia deve ser positiva")
    private Double autonomiaMaximaKm;

    private Double bateriaAtual;

    @Enumerated(EnumType.STRING)
    private StatusDrone status = StatusDrone.IDLE;
    
}
