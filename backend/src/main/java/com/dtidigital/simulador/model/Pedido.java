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

public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotNull(message = "A coordenada X é obrigatória")
    private Double coordenadaX;

    @NotNull(message = "A coordenada Y é obrigatória")
    private Double coordenadaY;

    @NotNull(message = "O peso é obrigatório")
    @Positive(message = "O peso deve ser maior que zero")
    private Double peso;

    @NotNull(message = "A prioridade é obrigatória")
    @Enumerated(EnumType.STRING)
    private Prioridade prioridade;

    @Enumerated(EnumType.STRING)
    private StatusPedido status = StatusPedido.PENDENTE;

    @ManyToOne
    @JoinColumn(name = "drone_id")
    private Drone droneAlocado;

    private Double tempoEstimadoMinutos;

    private String motivoBloqueio;

}