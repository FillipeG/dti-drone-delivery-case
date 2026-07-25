package com.dtidigital.simulador.dto;

import com.dtidigital.simulador.model.StatusPedido;

public record FeedbackEntregaDTO(
        String pedidoId,
        StatusPedido status,
        String mensagem,
        Double distanciaRestanteKm,
        Double tempoRestanteMinutos
) {
}
