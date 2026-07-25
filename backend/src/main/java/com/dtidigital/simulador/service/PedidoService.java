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
        return calcularDistancia(BASE_X, BASE_Y, destinoX, destinoY);
    }

    private double calcularDistancia(double x1, double y1, double x2, double y2) {
        double deltaX = x2 - x1;
        double deltaY = y2 - y1;
        return Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2));
    }

    public void processarFila() {

        List<Pedido> pedidosPendentes = pedidoRepository.findAll().stream()
                .filter(p -> p.getStatus() == StatusPedido.PENDENTE)
                .collect(Collectors.toList());

        pedidosPendentes.sort(
                Comparator.comparing(Pedido::getPrioridade).reversed()
                        .thenComparing(p -> calcularDistanciaDaBase(p.getCoordenadaX(), p.getCoordenadaY()))
        );

        List<Pedido> pedidosDisponiveis = new ArrayList<>();
        for (Pedido pedido : pedidosPendentes) {
            Optional<ZonaExclusao> zonaBloqueando = zonaExclusaoService.verificarBloqueio(
                    BASE_X, BASE_Y, pedido.getCoordenadaX(), pedido.getCoordenadaY());

            if (zonaBloqueando.isPresent()) {
                pedido.setMotivoBloqueio(
                        "Rota bloqueada pela zona de exclusão: " + zonaBloqueando.get().getNome());
                pedidoRepository.save(pedido);
            } else {
                pedidosDisponiveis.add(pedido);
            }
        }

        List<Drone> dronesDisponiveis = new ArrayList<>(droneRepository.findByStatus(StatusDrone.IDLE));

        for (Drone drone : dronesDisponiveis) {
            if (pedidosDisponiveis.isEmpty()) {
                break;
            }

            List<Pedido> manifesto = montarManifestoParaDrone(drone, pedidosDisponiveis);

            if (manifesto.isEmpty()) {
                continue;
            }

            despacharViagem(drone, manifesto);
            pedidosDisponiveis.removeAll(manifesto);
        }
    }


    private List<Pedido> montarManifestoParaDrone(Drone drone, List<Pedido> candidatos) {
        List<Pedido> manifesto = new ArrayList<>();
        double pesoAcumulado = 0.0;

        for (Pedido candidato : candidatos) {
            double novoPeso = pesoAcumulado + candidato.getPeso();
            if (novoPeso > drone.getCapacidadeMaximaPeso()) {
                continue;
            }

            List<Pedido> tentativa = new ArrayList<>(manifesto);
            tentativa.add(candidato);

            double distanciaRota = calcularDistanciaRota(tentativa);
            if (distanciaRota > drone.getBateriaAtual()) {
                continue;
            }

            manifesto.add(candidato);
            pesoAcumulado = novoPeso;
        }

        return manifesto;
    }

    private double calcularDistanciaRota(List<Pedido> pedidos) {
        if (pedidos.isEmpty()) {
            return 0.0;
        }

        List<Pedido> restantes = new ArrayList<>(pedidos);
        double x = BASE_X;
        double y = BASE_Y;
        double distanciaTotal = 0.0;

        while (!restantes.isEmpty()) {
            final double atualX = x;
            final double atualY = y;

            Pedido maisProximo = restantes.stream()
                    .min(Comparator.comparingDouble(
                            p -> calcularDistancia(atualX, atualY, p.getCoordenadaX(), p.getCoordenadaY())))
                    .orElseThrow();

            distanciaTotal += calcularDistancia(atualX, atualY, maisProximo.getCoordenadaX(), maisProximo.getCoordenadaY());
            x = maisProximo.getCoordenadaX();
            y = maisProximo.getCoordenadaY();
            restantes.remove(maisProximo);
        }

        // volta pra base ao final da rota
        distanciaTotal += calcularDistancia(x, y, BASE_X, BASE_Y);

        return distanciaTotal;
    }

    private void despacharViagem(Drone drone, List<Pedido> manifesto) {
        double distanciaRota = calcularDistanciaRota(manifesto);
        double tempoHoras = distanciaRota / VELOCIDADE_DRONE_KM_H;
        double tempoMinutos = Math.round(tempoHoras * 60 * 100.0) / 100.0;

        String viagemId = UUID.randomUUID().toString();

        drone.setStatus(StatusDrone.EM_VOO);
        drone.setBateriaAtual(drone.getBateriaAtual() - distanciaRota);
        droneRepository.save(drone);

        for (Pedido pedido : manifesto) {
            pedido.setDroneAlocado(drone);
            pedido.setStatus(StatusPedido.EM_TRANSPORTE);
            pedido.setTempoEstimadoMinutos(tempoMinutos);
            pedido.setDistanciaRotaKm(Math.round(distanciaRota * 100.0) / 100.0);
            pedido.setViagemId(viagemId);
            pedido.setMotivoBloqueio(null);
            pedidoRepository.save(pedido);
        }
    }

    // concluir entrega
    public Pedido concluirEntrega(String pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado com o ID: " + pedidoId));

        pedido.setStatus(StatusPedido.ENTREGUE);
        Pedido pedidoConcluido = pedidoRepository.save(pedido);

        liberarDroneSeViagemConcluida(pedido);

        processarFila();

        return pedidoConcluido;
    }

    private void liberarDroneSeViagemConcluida(Pedido pedido) {
        if (pedido.getDroneAlocado() == null || pedido.getViagemId() == null) {
            return;
        }

        Drone drone = pedido.getDroneAlocado();

        boolean aindaHaPedidosDaMesmaViagemEmTransporte = pedidoRepository.findAll().stream()
                .anyMatch(p -> pedido.getViagemId().equals(p.getViagemId())
                        && p.getStatus() == StatusPedido.EM_TRANSPORTE);

        if (!aindaHaPedidosDaMesmaViagemEmTransporte) {
            drone.setStatus(StatusDrone.IDLE);
            droneRepository.save(drone);
        }
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

        long totalViagens = todosPedidos.stream()
                .map(Pedido::getViagemId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("totalPedidos", todosPedidos.size());
        dashboard.put("entregasRealizadas", entregasRealizadas);
        dashboard.put("pedidosNaFila", pedidosPendentes);
        dashboard.put("tempoMedioMinutos", Math.round(tempoMedioMinutos * 100.0) / 100.0);
        dashboard.put("totalDrones", todosDrones.size());
        dashboard.put("totalViagens", totalViagens);

        return dashboard;
    }
}