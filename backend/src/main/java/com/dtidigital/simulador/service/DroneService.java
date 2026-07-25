package com.dtidigital.simulador.service;

import com.dtidigital.simulador.exception.ResourceNotFoundException;
import com.dtidigital.simulador.model.Drone;
import com.dtidigital.simulador.model.StatusDrone;
import com.dtidigital.simulador.repository.DroneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor

public class DroneService {

    private final DroneRepository droneRepository;
    private final PedidoService pedidoService;

    public Drone cadastrarDrone(Drone drone){

        if(drone.getBateriaAtual() == null){
            drone.setBateriaAtual(drone.getAutonomiaMaximaKm());
        }

        if (drone.getStatus() == null) {
            drone.setStatus(StatusDrone.IDLE);
        }

        Drone droneSalvo = droneRepository.save(drone);
        pedidoService.processarFila();
        return droneSalvo;
    }

    public List<Drone> listarTodos(){
        return droneRepository.findAll();
    }

    public Optional<Drone> buscarPorId(String id){
        return droneRepository.findById(id);
    }

    public List<Drone> listarDisponiveis(){
        return droneRepository.findAll().stream().filter(d -> d.getStatus() == StatusDrone.IDLE).toList();
    }

    public List<Drone> listarEmUso(){
        return droneRepository.findAll().stream().filter(d -> d.getStatus() != StatusDrone.IDLE).toList();
    }

    public Drone atualizarStatus(String id, StatusDrone novoStatus){

        Drone drone = droneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Drone não encontrado com o ID: " + id));
        drone.setStatus(novoStatus);
        return droneRepository.save(drone);
    }

    public Drone recarregarBateria(String id) {
        Drone drone = droneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Drone não encontrado com o ID: " + id));

        drone.setBateriaAtual(drone.getAutonomiaMaximaKm());
        drone.setStatus(StatusDrone.IDLE);
        Drone droneRecarregado = droneRepository.save(drone);
        pedidoService.processarFila();
        return droneRecarregado;
    }

}