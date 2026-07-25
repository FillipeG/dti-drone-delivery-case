package com.dtidigital.simulador.service;

import com.dtidigital.simulador.model.Drone;
import com.dtidigital.simulador.model.Pedido;
import com.dtidigital.simulador.model.StatusDrone;
import com.dtidigital.simulador.model.StatusPedido;
import com.dtidigital.simulador.repository.DroneRepository;
import com.dtidigital.simulador.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.dtidigital.simulador.config.ParametrosSimulacao.*;

/**
 * Conduz a máquina de estados dos drones ao longo do tempo simulado.
 *
 * Ciclo de uma viagem:
 * IDLE -> CARREGANDO -> EM_VOO -> ENTREGANDO -> (EM_VOO -> ENTREGANDO)* -> RETORNANDO -> IDLE
 *
 * O tempo não corre sozinho: ele avança em blocos, o que mantém a
 * simulação determinística e testável, sem threads nem sleeps.
 */
@Service
@RequiredArgsConstructor
public class SimulacaoService {

    /** Proteção contra etapas de duração zero em sequência. */
    private static final int MAX_TRANSICOES_POR_TICK = 1000;

    private final DroneRepository droneRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;

    @Value("${simulacao.automatica.habilitada:false}")
    private boolean automaticaHabilitada;

    @Value("${simulacao.automatica.minutos-por-tick:1.0}")
    private double minutosPorTick;

    private double relogioMinutos = 0.0;

    @Transactional
    public Map<String, Object> avancarTempo(double minutos) {
        if (minutos <= 0) {
            throw new IllegalArgumentException("O tempo a avançar deve ser maior que zero");
        }

        List<Drone> emOperacao = droneRepository.findAll().stream()
                .filter(drone -> drone.getStatus() != StatusDrone.IDLE)
                .toList();

        for (Drone drone : emOperacao) {
            avancarDrone(drone, minutos);
        }

        relogioMinutos += minutos;

        pedidoService.processarFila();

        return estadoAtual();
    }

    private void avancarDrone(Drone drone, double minutos) {
        double restante = minutos;
        int transicoes = 0;

        while (restante > 0 && drone.getStatus() != StatusDrone.IDLE) {
            if (++transicoes > MAX_TRANSICOES_POR_TICK) {
                break;
            }

            double etapa = drone.getTempoRestanteEtapaMinutos() == null
                    ? 0.0
                    : drone.getTempoRestanteEtapaMinutos();

            if (etapa > restante) {
                drone.setTempoRestanteEtapaMinutos(arredondar(etapa - restante));
                restante = 0;
            } else {
                restante -= etapa;
                drone.setTempoRestanteEtapaMinutos(0.0);
                concluirEtapa(drone);
            }
        }

        droneRepository.save(drone);
    }

    private void concluirEtapa(Drone drone) {
        List<Pedido> rota = rotaDaViagem(drone);

        if (rota.isEmpty()) {
            pedidoService.encerrarViagem(drone);
            return;
        }

        switch (drone.getStatus()) {
            case CARREGANDO -> decolarPara(drone, 0, rota);

            case EM_VOO -> {
                drone.setStatus(StatusDrone.ENTREGANDO);
                drone.setTempoRestanteEtapaMinutos(TEMPO_ENTREGA_MINUTOS);
            }

            case ENTREGANDO -> {
                int indice = drone.getParadaAtual() == null ? 0 : drone.getParadaAtual();

                if (indice < rota.size()) {
                    Pedido entregue = rota.get(indice);
                    entregue.setStatus(StatusPedido.ENTREGUE);
                    pedidoRepository.save(entregue);
                }

                int proximaParada = indice + 1;
                if (proximaParada < rota.size()) {
                    decolarPara(drone, proximaParada, rota);
                } else {
                    iniciarRetorno(drone, rota);
                }
            }

            case RETORNANDO -> pedidoService.encerrarViagem(drone);

            default -> pedidoService.encerrarViagem(drone);
        }
    }

    private void decolarPara(Drone drone, int indiceDestino, List<Pedido> rota) {
        double origemX = BASE_X;
        double origemY = BASE_Y;

        if (indiceDestino > 0) {
            Pedido anterior = rota.get(indiceDestino - 1);
            origemX = anterior.getCoordenadaX();
            origemY = anterior.getCoordenadaY();
        }

        Pedido destino = rota.get(indiceDestino);
        double distanciaKm = distancia(origemX, origemY, destino.getCoordenadaX(), destino.getCoordenadaY());

        drone.setParadaAtual(indiceDestino);
        drone.setStatus(StatusDrone.EM_VOO);
        drone.setTempoRestanteEtapaMinutos(arredondar(minutosParaPercorrer(distanciaKm)));
    }

    private void iniciarRetorno(Drone drone, List<Pedido> rota) {
        Pedido ultimaParada = rota.get(rota.size() - 1);
        double distanciaKm = distancia(
                ultimaParada.getCoordenadaX(), ultimaParada.getCoordenadaY(), BASE_X, BASE_Y);

        drone.setStatus(StatusDrone.RETORNANDO);
        drone.setTempoRestanteEtapaMinutos(arredondar(minutosParaPercorrer(distanciaKm)));
    }

    private List<Pedido> rotaDaViagem(Drone drone) {
        if (drone.getViagemAtualId() == null) {
            return List.of();
        }
        return pedidoRepository.findByViagemIdOrderByOrdemNaRotaAsc(drone.getViagemAtualId());
    }

    private double distancia(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    public Map<String, Object> estadoAtual() {
        List<Drone> drones = droneRepository.findAll();

        List<Map<String, Object>> detalheDrones = drones.stream().map(drone -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", drone.getId());
            item.put("status", drone.getStatus());
            item.put("bateriaRestanteKm", drone.getBateriaAtual());
            item.put("viagemAtualId", drone.getViagemAtualId());
            item.put("paradaAtual", drone.getParadaAtual());
            item.put("tempoRestanteEtapaMinutos", drone.getTempoRestanteEtapaMinutos());
            return item;
        }).toList();

        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("relogioMinutos", arredondar(relogioMinutos));
        estado.put("automatica", automaticaHabilitada);
        estado.put("minutosPorTick", minutosPorTick);
        estado.put("dronesEmOperacao",
                drones.stream().filter(d -> d.getStatus() != StatusDrone.IDLE).count());
        estado.put("drones", detalheDrones);
        return estado;
    }

    public void definirAutomatica(boolean ativo) {
        this.automaticaHabilitada = ativo;
    }

    public boolean isAutomaticaHabilitada() {
        return automaticaHabilitada;
    }

    /**
     * Modo automático: cada segundo de mundo real avança "minutosPorTick" na
     * simulação. Desligado por padrão, para não interferir nos testes.
     */
    @Scheduled(fixedDelayString = "${simulacao.automatica.intervalo-ms:1000}")
    public void tickAutomatico() {
        if (!automaticaHabilitada) {
            return;
        }
        avancarTempo(minutosPorTick);
    }
}