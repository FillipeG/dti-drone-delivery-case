package com.dtidigital.simulador.service;

import com.dtidigital.simulador.dto.PedidoDTO;
import com.dtidigital.simulador.exception.ResourceNotFoundException;
import com.dtidigital.simulador.model.*;
import com.dtidigital.simulador.repository.DroneRepository;
import com.dtidigital.simulador.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final DroneRepository droneRepository;
    private final ZonaExclusaoService zonaExclusaoService;

    private static final double BASE_X = 0.0;
    private static final double BASE_Y = 0.0;
    private static final double VELOCIDADE_DRONE_KM_H = 30.0;

    public Pedido criarPedido(PedidoDTO dto) {
        Pedido pedido = new Pedido();
        pedido.setCoordenadaX(dto.getCoordenadaX());
        pedido.setCoordenadaY(dto.getCoordenadaY());
        pedido.setPeso(dto.getPeso());
        pedido.setPrioridade(dto.getPrioridade());

        if (pedido.getStatus() == null) {
            pedido.setStatus(StatusPedido.PENDENTE);
        }

        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        processarFila();

        return pedidoRepository.findById(pedidoSalvo.getId()).orElse(pedidoSalvo);
    }

    public List<Pedido> listarTodos() {
        return pedidoRepository.findAll();
    }

    public double calcularDistanciaDaBase(Double destinoX, Double destinoY) {
        double deltaX = destinoX - BASE_X;
        double deltaY = destinoY - BASE_Y;
        return Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2));
    }

    // fila de prioridade e processamento lote
    public void processarFila() {

        List<Pedido> pedidosPendentes = pedidoRepository.findAll().stream()
                .filter(p -> p.getStatus() == StatusPedido.PENDENTE)
                .collect(Collectors.toList());

        pedidosPendentes.sort(Comparator.comparing(Pedido::getPrioridade).reversed());

        for (Pedido pedido : pedidosPendentes) {

            Optional<ZonaExclusao> zonaBloqueando = zonaExclusaoService.verificarBloqueio(
                    BASE_X, BASE_Y, pedido.getCoordenadaX(), pedido.getCoordenadaY());

            if (zonaBloqueando.isPresent()) {
                pedido.setMotivoBloqueio(
                        "Rota bloqueada pela zona de exclusão: " + zonaBloqueando.get().getNome());
                pedidoRepository.save(pedido);
                continue;
            }

            double distanciaIda = calcularDistanciaDaBase(pedido.getCoordenadaX(), pedido.getCoordenadaY());
            double distanciaIdaEVolta = distanciaIda * 2;

            List<Drone> dronesDisponiveis = droneRepository.findByStatus(StatusDrone.IDLE);

            Optional<Drone> melhorDrone = dronesDisponiveis.stream()
                    .filter(d -> d.getCapacidadeMaximaPeso() >= pedido.getPeso())
                    .filter(d -> d.getBateriaAtual() >= distanciaIdaEVolta)
                    .min(Comparator.comparingDouble(Drone::getCapacidadeMaximaPeso));

            if (melhorDrone.isPresent()) {
                Drone drone = melhorDrone.get();

                double tempoHoras = distanciaIdaEVolta / VELOCIDADE_DRONE_KM_H;
                double tempoMinutos = Math.round(tempoHoras * 60 * 100.0) / 100.0;

                drone.setStatus(StatusDrone.EM_VOO);
                drone.setBateriaAtual(drone.getBateriaAtual() - distanciaIdaEVolta);
                droneRepository.save(drone);

                pedido.setDroneAlocado(drone);
                pedido.setStatus(StatusPedido.EM_TRANSPORTE);
                pedido.setTempoEstimadoMinutos(tempoMinutos);
                pedido.setMotivoBloqueio(null);
                pedidoRepository.save(pedido);
            }
        }
    }

    // concluir entrega
    public Pedido concluirEntrega(String pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado com o ID: " + pedidoId));

        if (pedido.getDroneAlocado() != null) {
            Drone drone = pedido.getDroneAlocado();
            drone.setStatus(StatusDrone.IDLE);
            droneRepository.save(drone);
        }

        pedido.setStatus(StatusPedido.ENTREGUE);
        Pedido pedidoConcluido = pedidoRepository.save(pedido);

        processarFila();

        return pedidoConcluido;
    }

    // dashboard
    public Map<String, Object> obterDashboard() {
        List<Pedido> todosPedidos = pedidoRepository.findAll();
        List<Drone> todosDrones = droneRepository.findAll();

        long entregasRealizadas = todosPedidos.stream()
                .filter(p -> p.getStatus() == StatusPedido.ENTREGUE)
                .count();

        long pedidosPendentes = todosPedidos.stream()
                .filter(p -> p.getStatus() == StatusPedido.PENDENTE)
                .count();

        double tempoMedioMinutos = todosPedidos.stream()
                .filter(p -> p.getTempoEstimadoMinutos() != null)
                .mapToDouble(Pedido::getTempoEstimadoMinutos)
                .average()
                .orElse(0.0);

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("totalPedidos", todosPedidos.size());
        dashboard.put("entregasRealizadas", entregasRealizadas);
        dashboard.put("pedidosNaFila", pedidosPendentes);
        dashboard.put("tempoMedioMinutos", Math.round(tempoMedioMinutos * 100.0) / 100.0);
        dashboard.put("totalDrones", todosDrones.size());

        return dashboard;
    }
}