package com.dtidigital.simulador.dto;

import com.dtidigital.simulador.model.Prioridade;
import com.dtidigital.simulador.model.StatusPedido;

public record ParadaDTO(
        int ordem,
        String pedidoId,
        Double coordenadaX,
        Double coordenadaY,
        Double pesoKg,
        Prioridade prioridade,
        StatusPedido status,
        Double distanciaDoPontoAnteriorKm,
        Double tempoAcumuladoAteEntregaMinutos
) {
}