package com.dtidigital.simulador.dto;

import com.dtidigital.simulador.model.Prioridade;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PedidoDTO {

    @NotNull(message = "A coordenada X é obrigatória")
    private Double coordenadaX;

    @NotNull(message = "A coordenada Y é obrigatória")
    private Double coordenadaY;

    @NotNull(message = "O peso é obrigatório")
    @Positive(message = "O peso deve ser positivo")
    private Double peso;

    @NotNull(message = "A prioridade é obrigatória")
    private Prioridade prioridade;
    
}
