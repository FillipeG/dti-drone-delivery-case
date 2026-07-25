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
import java.util.stream.Stream;

import static com.dtidigital.simulador.config.ParametrosSimulacao.*;

@Service
@RequiredArgsConstructor
public class PedidoService {

    // ordem de prioridade decrescente: uma prioridade so pode consumir capacidade
    // depois que a prioridade anterior ja escolheu sua melhor combinacao
    private static final List<Prioridade> PRIORIDADES_DECRESCENTE =
            List.of(Prioridade.ALTA, Prioridade.MEDIA, Prioridade.BAIXA);

    // knapsack trabalha em centigramas (2 casas decimais) pra poder usar indices inteiros
    private static final int ESCALA_PESO = 100;

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

    // busca, por nivel de prioridade, a combinacao de pedidos que mais aproveita
    // a capacidade do drone; a autonomia entra depois, cortando pedidos de menor
    // prioridade caso a rota resultante nao caiba na bateria
    private List<Pedido> montarManifestoParaDrone(Drone drone, List<Pedido> candidatos) {
        List<Pedido> manifesto = new ArrayList<>();
        double capacidadeRestante = drone.getCapacidadeMaximaPeso();

        for (Prioridade prioridade : PRIORIDADES_DECRESCENTE) {
            if (capacidadeRestante <= 0) {
                break;
            }

            List<Pedido> candidatosDoNivel = candidatos.stream()
                    .filter(p -> p.getPrioridade() == prioridade)
                    .toList();

            if (candidatosDoNivel.isEmpty()) {
                continue;
            }

            List<Pedido> selecionados = selecionarCombinacaoDeMaiorPeso(candidatosDoNivel, capacidadeRestante);
            manifesto.addAll(selecionados);
            capacidadeRestante -= selecionados.stream().mapToDouble(Pedido::getPeso).sum();
        }

        return respeitarAutonomia(drone, manifesto);
    }

    // knapsack 0/1: dentre os candidatos do mesmo nivel de prioridade (ja ordenados
    // por tempo de chegada e distancia), escolhe o subconjunto que carrega o maior
    // peso possivel sem estourar a capacidade disponivel
    private List<Pedido> selecionarCombinacaoDeMaiorPeso(List<Pedido> candidatosDoNivel, double capacidadeDisponivel) {
        int capacidade = (int) Math.round(capacidadeDisponivel * ESCALA_PESO);
        if (capacidade <= 0) {
            return List.of();
        }

        int n = candidatosDoNivel.size();
        int[] pesos = new int[n];
        for (int i = 0; i < n; i++) {
            pesos[i] = (int) Math.round(candidatosDoNivel.get(i).getPeso() * ESCALA_PESO);
        }

        int[] dp = new int[capacidade + 1];
        boolean[][] usado = new boolean[n][capacidade + 1];

        for (int i = 0; i < n; i++) {
            int peso = pesos[i];
            if (peso <= 0 || peso > capacidade) {
                continue;
            }
            for (int w = capacidade; w >= peso; w--) {
                int valorComItem = dp[w - peso] + peso;
                if (valorComItem > dp[w]) {
                    dp[w] = valorComItem;
                    usado[i][w] = true;
                }
            }
        }

        List<Pedido> selecionados = new ArrayList<>();
        int w = capacidade;
        for (int i = n - 1; i >= 0; i--) {
            if (usado[i][w]) {
                selecionados.add(candidatosDoNivel.get(i));
                w -= pesos[i];
            }
        }

        Collections.reverse(selecionados);
        return selecionados;
    }

    // remove pedidos de menor prioridade ate a rota caber na autonomia do drone
    private List<Pedido> respeitarAutonomia(Drone drone, List<Pedido> manifesto) {
        List<Pedido> ajustado = new ArrayList<>(manifesto);

        while (!ajustado.isEmpty() && calcularDistanciaRota(ajustado) > drone.getBateriaAtual()) {
            ajustado.remove(escolherPedidoParaRemover(ajustado));
        }

        return ajustado;
    }

    private Pedido escolherPedidoParaRemover(List<Pedido> manifesto) {
        Prioridade menorPrioridade = manifesto.stream()
                .map(Pedido::getPrioridade)
                .min(Comparator.naturalOrder())
                .orElseThrow();

        List<Pedido> candidatosRemocao = manifesto.stream()
                .filter(p -> p.getPrioridade() == menorPrioridade)
                .toList();

        return candidatosRemocao.stream()
                .max(Comparator.comparingDouble(p -> reducaoDeDistanciaAoRemover(manifesto, p)))
                .orElseThrow();
    }

    private double reducaoDeDistanciaAoRemover(List<Pedido> manifesto, Pedido pedido) {
        List<Pedido> semPedido = new ArrayList<>(manifesto);
        semPedido.remove(pedido);
        return calcularDistanciaRota(manifesto) - calcularDistanciaRota(semPedido);
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
        dashboard.put("droneMaisEficiente", determinarDroneMaisEficiente(todosPedidos));
        dashboard.put("mapaEntregas", construirMapaAscii(todosPedidos));

        return dashboard;
    }

    private String determinarDroneMaisEficiente(List<Pedido> pedidos) {
        Map<String, Long> entregasPorDrone = pedidos.stream()
                .filter(p -> p.getStatus() == StatusPedido.ENTREGUE && p.getDroneAlocado() != null)
                .collect(Collectors.groupingBy(p -> p.getDroneAlocado().getId(), Collectors.counting()));

        return entregasPorDrone.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String construirMapaAscii(List<Pedido> pedidos) {
        int tamanho = 21;
        int centro = tamanho / 2;

        double maiorCoordenada = pedidos.stream()
                .flatMap(p -> Stream.of(Math.abs(p.getCoordenadaX()), Math.abs(p.getCoordenadaY())))
                .max(Double::compareTo)
                .orElse(1.0);
        double escala = maiorCoordenada == 0.0 ? 1.0 : (centro - 1) / maiorCoordenada;

        char[][] grade = new char[tamanho][tamanho];
        for (char[] linha : grade) {
            Arrays.fill(linha, '.');
        }
        grade[centro][centro] = 'B';

        for (Pedido pedido : pedidos) {
            int coluna = centro + (int) Math.round(pedido.getCoordenadaX() * escala);
            int linha = centro - (int) Math.round(pedido.getCoordenadaY() * escala);
            if (linha < 0 || linha >= tamanho || coluna < 0 || coluna >= tamanho) {
                continue;
            }

            char marcador = switch (pedido.getStatus()) {
                case ENTREGUE -> 'E';
                case EM_TRANSPORTE -> 'T';
                case CANCELADO -> 'X';
                default -> 'P';
            };
            grade[linha][coluna] = marcador;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Legenda: B=base  P=pendente  T=em transporte  E=entregue  X=cancelado\n");
        for (char[] linha : grade) {
            sb.append(new String(linha)).append('\n');
        }
        return sb.toString();
    }
}
