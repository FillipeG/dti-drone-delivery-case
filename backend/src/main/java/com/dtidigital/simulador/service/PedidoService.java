package com.dtidigital.simulador.service;

import com.dtidigital.simulador.dto.PedidoDTO;
import com.dtidigital.simulador.model.Drone;
import com.dtidigital.simulador.model.Pedido;
import com.dtidigital.simulador.model.StatusDrone;
import com.dtidigital.simulador.model.StatusPedido;
import com.dtidigital.simulador.repository.DroneRepository;
import com.dtidigital.simulador.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final DroneRepository droneRepository;

    // Coordenadas da base de operações
    private static final double BASE_X = 0.0;
    private static final double BASE_Y = 0.0;

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

        processarDespacho(pedidoSalvo);

        return pedidoSalvo;
    }

    public List<Pedido> listarTodos() {
        return pedidoRepository.findAll();
    }

    // Formula de distancia euclidiana
    public double calcularDistanciaDaBase(Double destinoX, Double destinoY) {
        double deltaX = destinoX - BASE_X;
        double deltaY = destinoY - BASE_Y;

        return Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2));
    }

    // algoritmo de despacho
    public void processarDespacho(Pedido pedido) {
        double distanciaIda = calcularDistanciaDaBase(pedido.getCoordenadaX(), pedido.getCoordenadaY());
        double distanciaIdaEVolta = distanciaIda * 2;

        List<Drone> dronesDisponiveis = droneRepository.findByStatus(StatusDrone.IDLE);

        // filtra os drones aptos:
        Optional<Drone> melhorDrone = dronesDisponiveis.stream()
            .filter(drone -> drone.getCapacidadeMaximaPeso() >= pedido.getPeso())
            .filter(drone -> drone.getBateriaAtual() >= distanciaIdaEVolta)
            .min(Comparator.comparingDouble(Drone::getCapacidadeMaximaPeso));

        if (melhorDrone.isPresent()) {
            Drone drone = melhorDrone.get();

            drone.setStatus(StatusDrone.EM_VOO);
            drone.setBateriaAtual(drone.getBateriaAtual() - distanciaIdaEVolta);
            droneRepository.save(drone);

            pedido.setDroneAlocado(drone);
            pedido.setStatus(StatusPedido.EM_TRANSPORTE);
            pedidoRepository.save(pedido);
        }
    }
}