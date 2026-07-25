package com.dtidigital.simulador.dto;

import com.dtidigital.simulador.model.StatusDrone;

public record DroneStatusDTO(
        String id,
        StatusDrone status,
        Double capacidadeMaximaPesoKg,
        Double autonomiaMaximaKm,
        Double bateriaAtualKm,
        Double bateriaPercentual,
        String viagemAtualId,
        Integer paradaAtual,
        Integer totalParadasDaViagem,
        Integer entregasPendentesNaViagem,
        Double pesoTransportadoKg,
        Double tempoRestanteEtapaMinutos,
        Double tempoRestanteViagemMinutos
) {
}