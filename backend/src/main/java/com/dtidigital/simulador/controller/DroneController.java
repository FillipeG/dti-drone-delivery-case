package com.dtidigital.simulador.controller;

import com.dtidigital.simulador.dto.DroneStatusDTO;
import com.dtidigital.simulador.model.Drone;
import com.dtidigital.simulador.service.DroneService;
import com.dtidigital.simulador.service.OperacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drones")
@RequiredArgsConstructor

public class DroneController {

    private final DroneService droneService;
    private final OperacaoService operacaoService;

    @PostMapping
    public ResponseEntity<Drone> cadastrar(@RequestBody @Valid Drone drone){
        Drone novoDrone = droneService.cadastrarDrone(drone);
        return ResponseEntity.status(HttpStatus.CREATED).body(novoDrone);
    }

    @GetMapping
    public ResponseEntity<List<Drone>> listarTodos(){
        return ResponseEntity.ok(droneService.listarTodos());
    }

    @GetMapping("/status")
    public ResponseEntity<List<DroneStatusDTO>> status(){
        return ResponseEntity.ok(operacaoService.listarStatusDosDrones());
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<DroneStatusDTO> statusPorId(@PathVariable String id){
        return ResponseEntity.ok(operacaoService.obterStatusDoDrone(id));
    }

    @GetMapping("/disponiveis")
    public ResponseEntity<List<Drone>> listarDisponiveis(){
        return ResponseEntity.ok(droneService.listarDisponiveis());
    }

    @GetMapping("/em-uso")
    public ResponseEntity<List<Drone>> listarEmUso(){
        return ResponseEntity.ok(droneService.listarEmUso());
    }

    @PutMapping("/{id}/recarregar")
    public ResponseEntity<Drone> recarregar(@PathVariable String id) {
        return ResponseEntity.ok(droneService.recarregarBateria(id));
    }

}