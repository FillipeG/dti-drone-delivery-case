package com.dtidigital.simulador.dto;

import com.dtidigital.simulador.model.StatusDrone;

import java.util.List;

public record RotaViagemDTO(
        String viagemId,
        String droneId,
        StatusDrone statusDrone,
        int totalParadas,
        Double pesoTotalKg,
        Double distanciaTotalKm,
        Double distanciaRetornoBaseKm,
        Double tempoEstimadoMinutos,
        boolean concluida,
        List<ParadaDTO> paradas
) {
}