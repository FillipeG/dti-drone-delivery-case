package com.dtidigital.simulador.service;

import com.dtidigital.simulador.model.*;
import com.dtidigital.simulador.repository.DroneRepository;
import com.dtidigital.simulador.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulacaoServiceTest {

    @Mock
    private DroneRepository droneRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private PedidoService pedidoService;

    @InjectMocks
    private SimulacaoService simulacaoService;

    private Drone drone;

    @BeforeEach
    void setUp() {
        lenient().when(droneRepository.save(any(Drone.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        lenient().doAnswer(inv -> {
            Drone d = inv.getArgument(0);
            d.setStatus(StatusDrone.IDLE);
            d.setViagemAtualId(null);
            d.setParadaAtual(null);
            d.setTempoRestanteEtapaMinutos(0.0);
            return null;
        }).when(pedidoService).encerrarViagem(any(Drone.class));

        drone = new Drone();
        drone.setId("d1");
        drone.setCapacidadeMaximaPeso(10.0);
        drone.setAutonomiaMaximaKm(20.0);
        drone.setBateriaAtual(10.0);
        drone.setStatus(StatusDrone.CARREGANDO);
        drone.setViagemAtualId("v1");
        drone.setParadaAtual(0);
        drone.setTempoRestanteEtapaMinutos(5.0);
    }

    private Pedido criarPedidoEmTransporte(String id, double x, double y, int ordem) {
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setCoordenadaX(x);
        pedido.setCoordenadaY(y);
        pedido.setPeso(2.0);
        pedido.setPrioridade(Prioridade.MEDIA);
        pedido.setStatus(StatusPedido.EM_TRANSPORTE);
        pedido.setViagemId("v1");
        pedido.setOrdemNaRota(ordem);
        return pedido;
    }

    private void comRota(List<Pedido> rota) {
        when(droneRepository.findAll()).thenReturn(List.of(drone));
        lenient().when(pedidoRepository.findByViagemIdOrderByOrdemNaRotaAsc("v1")).thenReturn(rota);
    }


    @Test
    void devePercorrerTodasAsTransicoesDaViagemNaOrdemCorreta() {
        Pedido pedido = criarPedidoEmTransporte("p1", 3.0, 4.0, 0); 
        comRota(List.of(pedido));

        simulacaoService.avancarTempo(5.0);
        assertThat(drone.getStatus()).isEqualTo(StatusDrone.EM_VOO);
        assertThat(drone.getTempoRestanteEtapaMinutos()).isEqualTo(10.0); 

        simulacaoService.avancarTempo(10.0);
        assertThat(drone.getStatus()).isEqualTo(StatusDrone.ENTREGANDO);
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.EM_TRANSPORTE);

        simulacaoService.avancarTempo(2.0);
        assertThat(drone.getStatus()).isEqualTo(StatusDrone.RETORNANDO);
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.ENTREGUE);

        simulacaoService.avancarTempo(10.0);
        assertThat(drone.getStatus()).isEqualTo(StatusDrone.IDLE);
        assertThat(drone.getViagemAtualId()).isNull();
        assertThat(drone.getParadaAtual()).isNull();
    }

    @Test
    void deveConcluirViagemInteiraEmUmUnicoAvancoDeTempo() {
        Pedido pedido = criarPedidoEmTransporte("p1", 3.0, 4.0, 0);
        comRota(List.of(pedido));

        simulacaoService.avancarTempo(27.0);

        assertThat(drone.getStatus()).isEqualTo(StatusDrone.IDLE);
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.ENTREGUE);
    }

    @Test
    void deveManterEtapaEmAndamentoQuandoOTempoNaoEhSuficiente() {
        comRota(List.of(criarPedidoEmTransporte("p1", 3.0, 4.0, 0)));

        simulacaoService.avancarTempo(2.0);

        assertThat(drone.getStatus()).isEqualTo(StatusDrone.CARREGANDO);
        assertThat(drone.getTempoRestanteEtapaMinutos()).isEqualTo(3.0);
    }

    @Test
    void deveEntregarAsParadasNaOrdemDaRotaAntesDeRetornar() {
        Pedido primeira = criarPedidoEmTransporte("p1", 1.0, 0.0, 0);
        Pedido segunda = criarPedidoEmTransporte("p2", 2.0, 0.0, 1);
        comRota(List.of(primeira, segunda));

        simulacaoService.avancarTempo(9.0);
        assertThat(primeira.getStatus()).isEqualTo(StatusPedido.ENTREGUE);
        assertThat(segunda.getStatus()).isEqualTo(StatusPedido.EM_TRANSPORTE);
        assertThat(drone.getStatus()).isEqualTo(StatusDrone.EM_VOO);
        assertThat(drone.getParadaAtual()).isEqualTo(1);

        simulacaoService.avancarTempo(4.0);
        assertThat(segunda.getStatus()).isEqualTo(StatusPedido.ENTREGUE);
        assertThat(drone.getStatus()).isEqualTo(StatusDrone.RETORNANDO);
        assertThat(drone.getTempoRestanteEtapaMinutos()).isEqualTo(4.0); 

        simulacaoService.avancarTempo(4.0);
        assertThat(drone.getStatus()).isEqualTo(StatusDrone.IDLE);
    }


    @Test
    void deveReprocessarAFilaAposAvancarOTempo() {
        comRota(List.of(criarPedidoEmTransporte("p1", 3.0, 4.0, 0)));

        simulacaoService.avancarTempo(27.0);

        verify(pedidoService).processarFila();
    }

    @Test
    void naoDeveMovimentarDroneOcioso() {
        drone.setStatus(StatusDrone.IDLE);
        drone.setViagemAtualId(null);
        drone.setParadaAtual(null);
        drone.setTempoRestanteEtapaMinutos(0.0);
        when(droneRepository.findAll()).thenReturn(List.of(drone));

        simulacaoService.avancarTempo(60.0);

        assertThat(drone.getStatus()).isEqualTo(StatusDrone.IDLE);
        verify(droneRepository, never()).save(any(Drone.class));
    }

    @Test
    void deveEncerrarViagemQuandoNaoHaPedidosAssociados() {
        when(droneRepository.findAll()).thenReturn(List.of(drone));
        when(pedidoRepository.findByViagemIdOrderByOrdemNaRotaAsc("v1")).thenReturn(List.of());

        simulacaoService.avancarTempo(5.0);

        assertThat(drone.getStatus()).isEqualTo(StatusDrone.IDLE);
    }


    @Test
    void deveAcumularORelogioDaSimulacao() {
        when(droneRepository.findAll()).thenReturn(List.of(drone));
        lenient().when(pedidoRepository.findByViagemIdOrderByOrdemNaRotaAsc("v1"))
                .thenReturn(List.of(criarPedidoEmTransporte("p1", 3.0, 4.0, 0)));

        simulacaoService.avancarTempo(5.0);
        var estado = simulacaoService.avancarTempo(10.0);

        assertThat(estado.get("relogioMinutos")).isEqualTo(15.0);
        assertThat(estado.get("dronesEmOperacao")).isEqualTo(1L);
    }

    @Test
    void deveRejeitarAvancoDeTempoNaoPositivo() {
        assertThatThrownBy(() -> simulacaoService.avancarTempo(0.0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> simulacaoService.avancarTempo(-5.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}