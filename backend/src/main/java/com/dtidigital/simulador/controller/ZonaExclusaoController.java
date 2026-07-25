package com.dtidigital.simulador.controller;

import com.dtidigital.simulador.dto.ZonaExclusaoDTO;
import com.dtidigital.simulador.model.ZonaExclusao;
import com.dtidigital.simulador.service.ZonaExclusaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/zonas-exclusao")
@RequiredArgsConstructor
public class ZonaExclusaoController {

    private final ZonaExclusaoService zonaExclusaoService;

    @PostMapping
    public ResponseEntity<ZonaExclusao> cadastrar(@RequestBody @Valid ZonaExclusaoDTO dto) {
        ZonaExclusao novaZona = zonaExclusaoService.cadastrarZona(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(novaZona);
    }

    @GetMapping
    public ResponseEntity<List<ZonaExclusao>> listarTodas() {
        return ResponseEntity.ok(zonaExclusaoService.listarTodas());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable String id) {
        zonaExclusaoService.removerZona(id);
        return ResponseEntity.noContent().build();
    }
}