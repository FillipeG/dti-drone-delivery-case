package com.dtidigital.simulador.service;

import com.dtidigital.simulador.config.ParametrosSimulacao;
import com.dtidigital.simulador.dto.PedidoDTO;
import com.dtidigital.simulador.exception.ResourceNotFoundException;
import com.dtidigital.simulador.model.*;
import com.dtidigital.simulador.repository.DroneRepository;
import com.dtidigital.simulador.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.dtidigital.simulador.config.ParametrosSimulacao.*;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final DroneRepository droneRepository;
    private final ZonaExclusaoService zonaExclusaoService;

    public Pedido criarPedido(PedidoDTO dto) {
        validarCapacidadeCompativel(dto.getPeso());

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

    private void validarCapacidadeCompativel(double peso) {
        List<Drone> drones = droneRepository.findAll();
        if (drones.isEmpty()) {
            return;
        }

        double maiorCapacidade = drones.stream()
                .mapToDouble(Drone::getCapacidadeMaximaPeso)
                .max()
                .orElse(0.0);

        if (peso > maiorCapacidade) {
            throw new IllegalArgumentException(
                    "Pacote de " + peso + " kg excede a capacidade máxima de todos os drones cadastrados ("
                            + maiorCapacidade + " kg)");
        }
    }

    public List<Pedido> listarTodos() {
        return pedidoRepository.findAll();
    }

    public double calcularDistanciaDaBase(Double destinoX, Double destinoY) {
        return calcularDistancia(BASE_X, BASE_Y, destinoX, destinoY);
    }

 private double calcularDistancia(double x1, double y1, double x2, double y2) {
    return distancia(x1, y1, x2, y2);
}

    public void processarFila() {

        List<Pedido> pedidosPendentes = pedidoRepository.findAll().stream()
                .filter(p -> p.getStatus() == StatusPedido.PENDENTE)
                .collect(Collectors.toList());

        // fila por prioridade e, em caso de empate, por ordem de chegada (FIFO)
        pedidosPendentes.sort(
                Comparator.comparing(Pedido::getPrioridade).reversed()
                        .thenComparing(Pedido::getCriadoEm)
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

    public List<Pedido> ordenarRota(List<Pedido> pedidos) {
        List<Pedido> restantes = new ArrayList<>(pedidos);
        List<Pedido> rota = new ArrayList<>();

        double x = BASE_X;
        double y = BASE_Y;

        while (!restantes.isEmpty()) {
            final double atualX = x;
            final double atualY = y;

            Pedido maisProximo = restantes.stream()
                    .min(Comparator.comparingDouble(
                            p -> calcularDistancia(atualX, atualY, p.getCoordenadaX(), p.getCoordenadaY())))
                    .orElseThrow();

            rota.add(maisProximo);
            restantes.remove(maisProximo);
            x = maisProximo.getCoordenadaX();
            y = maisProximo.getCoordenadaY();
        }

        return rota;
    }

    public double distanciaDaRotaOrdenada(List<Pedido> rota) {
        if (rota.isEmpty()) {
            return 0.0;
        }

        double x = BASE_X;
        double y = BASE_Y;
        double distanciaTotal = 0.0;

        for (Pedido pedido : rota) {
            distanciaTotal += calcularDistancia(x, y, pedido.getCoordenadaX(), pedido.getCoordenadaY());
            x = pedido.getCoordenadaX();
            y = pedido.getCoordenadaY();
        }

        // volta pra base ao final da rota
        distanciaTotal += calcularDistancia(x, y, BASE_X, BASE_Y);

        return distanciaTotal;
    }

    private double calcularDistanciaRota(List<Pedido> pedidos) {
        return distanciaDaRotaOrdenada(ordenarRota(pedidos));
    }

    private void despacharViagem(Drone drone, List<Pedido> manifesto) {
        List<Pedido> rota = ordenarRota(manifesto);

        double distanciaRota = distanciaDaRotaOrdenada(rota);
        double tempoTotalMinutos = arredondar(
                TEMPO_CARREGAMENTO_MINUTOS
                        + minutosParaPercorrer(distanciaRota)
                        + TEMPO_ENTREGA_MINUTOS * rota.size());

        String viagemId = UUID.randomUUID().toString();

        drone.setStatus(StatusDrone.CARREGANDO);
        drone.setViagemAtualId(viagemId);
        drone.setParadaAtual(0);
        drone.setTempoRestanteEtapaMinutos(TEMPO_CARREGAMENTO_MINUTOS);
        drone.setBateriaAtual(arredondar(drone.getBateriaAtual() - distanciaRota));
        droneRepository.save(drone);

        for (int i = 0; i < rota.size(); i++) {
            Pedido pedido = rota.get(i);
            pedido.setDroneAlocado(drone);
            pedido.setStatus(StatusPedido.EM_TRANSPORTE);
            pedido.setTempoEstimadoMinutos(tempoTotalMinutos);
            pedido.setDistanciaRotaKm(arredondar(distanciaRota));
            pedido.setViagemId(viagemId);
            pedido.setOrdemNaRota(i);
            pedido.setMotivoBloqueio(null);
            pedidoRepository.save(pedido);
        }
    }

    // conclusão manual
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
            encerrarViagem(drone);
            droneRepository.save(drone);
        }
    }

    // drone com recarga automática: ao voltar pra base, já reabastece a bateria
    public void encerrarViagem(Drone drone) {
        drone.setStatus(StatusDrone.IDLE);
        drone.setViagemAtualId(null);
        drone.setParadaAtual(null);
        drone.setTempoRestanteEtapaMinutos(0.0);
        drone.setBateriaAtual(drone.getAutonomiaMaximaKm());
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
        dashboard.put("tempoMedioMinutos", ParametrosSimulacao.arredondar(tempoMedioMinutos));
        dashboard.put("totalDrones", todosDrones.size());
        dashboard.put("totalViagens", totalViagens);

        return dashboard;
    }
}
