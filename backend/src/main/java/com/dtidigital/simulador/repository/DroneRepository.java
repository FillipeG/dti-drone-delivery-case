package com.dtidigital.simulador.repository;

import com.dtidigital.simulador.model.Drone;
import com.dtidigital.simulador.model.StatusDrone;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DroneRepository extends JpaRepository<Drone, String> {
    List<Drone> findByStatus(StatusDrone status);
    
}
