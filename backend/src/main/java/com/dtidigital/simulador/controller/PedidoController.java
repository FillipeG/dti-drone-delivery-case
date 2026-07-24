package com.dtidigital.simulador.controller;

import com.dtidigital.simulador.dto.PedidoDTO;
import com.dtidigital.simulador.model.Pedido;
import com.dtidigital.simulador.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor

public class PedidoController {

    private final PedidoService pedidoService;

    @PostMapping
    public ResponseEntity<Pedido> criarPedido(@RequestBody @Valid PedidoDTO dto){
        Pedido novoPedido = pedidoService.criarPedido(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(novoPedido);
    }

    @GetMapping
    public ResponseEntity<List<Pedido>> listarTodos(){
        return ResponseEntity.ok(pedidoService.listarTodos());
    }

    @PutMapping("/{id}/concluir")
    public ResponseEntity<Pedido> concluirEntrega(@PathVariable String id) {
        return ResponseEntity.ok(pedidoService.concluirEntrega(id));
    }

    @PostMapping("/processar-fila")
    public ResponseEntity<Void> processarFila() {
        pedidoService.processarFila();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> obterDashboard() {
        return ResponseEntity.ok(pedidoService.obterDashboard());
    }
    
}
