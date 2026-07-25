package com.dtidigital.simulador.controller;

import com.dtidigital.simulador.service.SimulacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/simulacao")
@RequiredArgsConstructor
public class SimulacaoController {

    private final SimulacaoService simulacaoService;

    //avança o relógio da simulação 
    @PostMapping("/avancar")
    public ResponseEntity<Map<String, Object>> avancar(
            @RequestParam(defaultValue = "1") double minutos) {
        return ResponseEntity.ok(simulacaoService.avancarTempo(minutos));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(simulacaoService.estadoAtual());
    }

    //liga/desliga o avanço automático do tempo. 
    @PostMapping("/automatica")
    public ResponseEntity<Map<String, Object>> automatica(@RequestParam boolean ativo) {
        simulacaoService.definirAutomatica(ativo);
        return ResponseEntity.ok(simulacaoService.estadoAtual());
    }
}