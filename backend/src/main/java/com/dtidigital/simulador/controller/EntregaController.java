package com.dtidigital.simulador.controller;

import com.dtidigital.simulador.dto.RotaViagemDTO;
import com.dtidigital.simulador.service.OperacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/entregas")
@RequiredArgsConstructor
public class EntregaController {

    private final OperacaoService operacaoService;

    @GetMapping("/rota")
    public ResponseEntity<List<RotaViagemDTO>> rotas(
            @RequestParam(required = false) String droneId,
            @RequestParam(defaultValue = "false") boolean apenasAtivas) {
        return ResponseEntity.ok(operacaoService.listarRotas(droneId, apenasAtivas));
    }

    @GetMapping("/rota/{viagemId}")
    public ResponseEntity<RotaViagemDTO> rotaPorViagem(@PathVariable String viagemId) {
        return ResponseEntity.ok(operacaoService.obterRota(viagemId));
    }
}