package com.dtidigital.simulador.service;

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

    public Drone cadastrarDrone(Drone drone){

        if(drone.getBateriaAtual() == null){
            drone.setBateriaAtual(drone.getAutonomiaMaximaKm());
        }
        return droneRepository.save(drone);
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
        
        Drone drone = droneRepository.findById(id).orElseThrow(() -> new RuntimeException("Drone não encontrado com o ID: " + id));
        drone.setStatus(novoStatus);
        return droneRepository.save(drone);
    }
    
}
