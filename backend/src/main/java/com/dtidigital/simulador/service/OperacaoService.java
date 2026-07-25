package com.dtidigital.simulador.service;

import com.dtidigital.simulador.dto.DroneStatusDTO;
import com.dtidigital.simulador.dto.ParadaDTO;
import com.dtidigital.simulador.dto.RotaViagemDTO;
import com.dtidigital.simulador.exception.ResourceNotFoundException;
import com.dtidigital.simulador.model.Drone;
import com.dtidigital.simulador.model.Pedido;
import com.dtidigital.simulador.model.StatusDrone;
import com.dtidigital.simulador.model.StatusPedido;
import com.dtidigital.simulador.repository.DroneRepository;
import com.dtidigital.simulador.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.dtidigital.simulador.config.ParametrosSimulacao.*;

@Service
@RequiredArgsConstructor
public class OperacaoService {

    private final DroneRepository droneRepository;
    private final PedidoRepository pedidoRepository;


    public List<DroneStatusDTO> listarStatusDosDrones() {
        return droneRepository.findAll().stream()
                .map(this::montarStatus)
                .toList();
    }

    public DroneStatusDTO obterStatusDoDrone(String id) {
        Drone drone = droneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Drone não encontrado com o ID: " + id));
        return montarStatus(drone);
    }

    private DroneStatusDTO montarStatus(Drone drone) {
        List<Pedido> rota = rotaDoDrone(drone);

        double bateria = drone.getBateriaAtual() == null ? 0.0 : drone.getBateriaAtual();
        double autonomia = drone.getAutonomiaMaximaKm() == null ? 0.0 : drone.getAutonomiaMaximaKm();
        double percentual = autonomia == 0.0 ? 0.0 : arredondar(bateria / autonomia * 100.0);

        List<Pedido> aBordo = rota.stream()
                .filter(p -> p.getStatus() == StatusPedido.EM_TRANSPORTE)
                .toList();

        double pesoTransportado = aBordo.stream()
                .filter(p -> p.getPeso() != null)
                .mapToDouble(Pedido::getPeso)
                .sum();

        return new DroneStatusDTO(
                drone.getId(),
                drone.getStatus(),
                drone.getCapacidadeMaximaPeso(),
                drone.getAutonomiaMaximaKm(),
                arredondar(bateria),
                percentual,
                drone.getViagemAtualId(),
                drone.getParadaAtual(),
                rota.size(),
                aBordo.size(),
                arredondar(pesoTransportado),
                drone.getTempoRestanteEtapaMinutos(),
                tempoRestanteViagem(drone, rota)
        );
    }

    private double tempoRestanteViagem(Drone drone, List<Pedido> rota) {
        if (drone.getStatus() == null || drone.getStatus() == StatusDrone.IDLE || rota.isEmpty()) {
            return 0.0;
        }

        double total = drone.getTempoRestanteEtapaMinutos() == null
                ? 0.0
                : drone.getTempoRestanteEtapaMinutos();

        int paradaAtual = drone.getParadaAtual() == null ? 0 : drone.getParadaAtual();

        switch (drone.getStatus()) {
            case CARREGANDO -> {
                total += minutosParaPercorrer(distanciaTotalDaRota(rota));
                total += TEMPO_ENTREGA_MINUTOS * rota.size();
            }
            case EM_VOO -> {
                total += TEMPO_ENTREGA_MINUTOS;
                total += tempoDasParadasSeguintes(rota, paradaAtual);
            }
            case ENTREGANDO -> total += tempoDasParadasSeguintes(rota, paradaAtual);
            default -> {
            }
        }

        return arredondar(total);
    }

    private double tempoDasParadasSeguintes(List<Pedido> rota, int indice) {
        double total = 0.0;

        for (int i = indice + 1; i < rota.size(); i++) {
            total += minutosParaPercorrer(distanciaDoTrecho(rota, i));
            total += TEMPO_ENTREGA_MINUTOS;
        }

        Pedido ultima = rota.get(rota.size() - 1);
        total += minutosParaPercorrer(distanciaDaBase(ultima.getCoordenadaX(), ultima.getCoordenadaY()));

        return total;
    }


    public List<RotaViagemDTO> listarRotas(String droneId, boolean apenasAtivas) {
        Map<String, List<Pedido>> porViagem = pedidoRepository.findByViagemIdIsNotNull().stream()
                .collect(Collectors.groupingBy(Pedido::getViagemId, LinkedHashMap::new, Collectors.toList()));

        return porViagem.entrySet().stream()
                .map(entrada -> montarRota(entrada.getKey(), entrada.getValue()))
                .filter(rota -> droneId == null || droneId.equals(rota.droneId()))
                .filter(rota -> !apenasAtivas || !rota.concluida())
                .toList();
    }

    public RotaViagemDTO obterRota(String viagemId) {
        List<Pedido> pedidos = pedidoRepository.findByViagemIdOrderByOrdemNaRotaAsc(viagemId);

        if (pedidos.isEmpty()) {
            throw new ResourceNotFoundException("Viagem não encontrada com o ID: " + viagemId);
        }

        return montarRota(viagemId, pedidos);
    }

    private RotaViagemDTO montarRota(String viagemId, List<Pedido> pedidos) {
        List<Pedido> rota = ordenarPelaRota(pedidos);

        List<ParadaDTO> paradas = new ArrayList<>();
        double distanciaAcumulada = 0.0;

        for (int i = 0; i < rota.size(); i++) {
            Pedido pedido = rota.get(i);

            double trecho = distanciaDoTrecho(rota, i);
            distanciaAcumulada += trecho;

            double tempoAcumulado = TEMPO_CARREGAMENTO_MINUTOS
                    + minutosParaPercorrer(distanciaAcumulada)
                    + TEMPO_ENTREGA_MINUTOS * (i + 1);

            paradas.add(new ParadaDTO(
                    i,
                    pedido.getId(),
                    pedido.getCoordenadaX(),
                    pedido.getCoordenadaY(),
                    pedido.getPeso(),
                    pedido.getPrioridade(),
                    pedido.getStatus(),
                    arredondar(trecho),
                    arredondar(tempoAcumulado)
            ));
        }

        Pedido ultima = rota.get(rota.size() - 1);
        double retorno = distanciaDaBase(ultima.getCoordenadaX(), ultima.getCoordenadaY());
        double distanciaTotal = distanciaAcumulada + retorno;

        double pesoTotal = rota.stream()
                .filter(p -> p.getPeso() != null)
                .mapToDouble(Pedido::getPeso)
                .sum();

        double tempoEstimado = TEMPO_CARREGAMENTO_MINUTOS
                + minutosParaPercorrer(distanciaTotal)
                + TEMPO_ENTREGA_MINUTOS * rota.size();

        boolean concluida = rota.stream().allMatch(p -> p.getStatus() == StatusPedido.ENTREGUE);

        Drone drone = rota.stream()
                .map(Pedido::getDroneAlocado)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return new RotaViagemDTO(
                viagemId,
                drone == null ? null : drone.getId(),
                drone == null ? null : drone.getStatus(),
                rota.size(),
                arredondar(pesoTotal),
                arredondar(distanciaTotal),
                arredondar(retorno),
                arredondar(tempoEstimado),
                concluida,
                paradas
        );
    }


    private List<Pedido> rotaDoDrone(Drone drone) {
        if (drone.getViagemAtualId() == null) {
            return List.of();
        }
        return pedidoRepository.findByViagemIdOrderByOrdemNaRotaAsc(drone.getViagemAtualId());
    }

    private List<Pedido> ordenarPelaRota(List<Pedido> pedidos) {
        return pedidos.stream()
                .sorted(Comparator.comparingInt(p -> p.getOrdemNaRota() == null ? 0 : p.getOrdemNaRota()))
                .toList();
    }

    private double distanciaDoTrecho(List<Pedido> rota, int indice) {
        Pedido destino = rota.get(indice);

        if (indice == 0) {
            return distanciaDaBase(destino.getCoordenadaX(), destino.getCoordenadaY());
        }

        Pedido anterior = rota.get(indice - 1);
        return distancia(anterior.getCoordenadaX(), anterior.getCoordenadaY(),
                destino.getCoordenadaX(), destino.getCoordenadaY());
    }

    private double distanciaTotalDaRota(List<Pedido> rota) {
        double total = 0.0;

        for (int i = 0; i < rota.size(); i++) {
            total += distanciaDoTrecho(rota, i);
        }

        Pedido ultima = rota.get(rota.size() - 1);
        total += distanciaDaBase(ultima.getCoordenadaX(), ultima.getCoordenadaY());

        return total;
    }
}