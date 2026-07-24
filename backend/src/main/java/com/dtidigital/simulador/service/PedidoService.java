package com.dtidigital.simulador.service;

import com.dtidigital.simulador.dto.PedidoDTO;
import com.dtidigital.simulador.model.Pedido;
import com.dtidigital.simulador.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor

public class PedidoService {

    private final PedidoRepository pedidoRepository;

    //coordenadas da base de operações
    private static final double BASE_X = 0.0;
    private static final double BASE_Y = 0.0;

    public Pedido criarPedido(PedidoDTO dto){

        Pedido pedido = new Pedido();
        pedido.setCoordenadaX(dto.getCoordenadaX());
        pedido.setCoordenadaY(dto.getCoordenadaY());
        pedido.setPeso(dto.getPeso());
        pedido.setPrioridade(dto.getPrioridade());

        return pedidoRepository.save(pedido);
    }

    public List<Pedido> listarTodos(){
        return pedidoRepository.findAll();
    }


    //formula de distancia euclidiana
    public double calcularDistanciaDaBase(Double destinoX, Double destinoY){
        double deltaX = destinoX - BASE_X;
        double deltaY = destinoY - BASE_Y;

        return Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2));
    }
    
}
