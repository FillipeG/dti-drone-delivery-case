package com.dtidigital.simulador.service;

import com.dtidigital.simulador.dto.DroneStatusDTO;
import com.dtidigital.simulador.dto.RotaViagemDTO;
import com.dtidigital.simulador.exception.ResourceNotFoundException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperacaoServiceTest {

    @Mock
    private DroneRepository droneRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @InjectMocks
    private OperacaoService operacaoService;

    private Drone drone;

    @BeforeEach
    void setUp() {
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

    private Pedido criarPedido(String id, double x, double y, double peso, int ordem, StatusPedido status) {
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setCoordenadaX(x);
        pedido.setCoordenadaY(y);
        pedido.setPeso(peso);
        pedido.setPrioridade(Prioridade.MEDIA);
        pedido.setStatus(status);
        pedido.setViagemId("v1");
        pedido.setOrdemNaRota(ordem);
        pedido.setDroneAlocado(drone);
        return pedido;
    }


    @Test
    void deveMontarRotaComParadasOrdenadasEDistanciaPorTrecho() {
        Pedido p1 = criarPedido("p1", 1.0, 0.0, 2.0, 0, StatusPedido.EM_TRANSPORTE);
        Pedido p2 = criarPedido("p2", 2.0, 0.0, 3.0, 1, StatusPedido.EM_TRANSPORTE);

        when(pedidoRepository.findByViagemIdIsNotNull()).thenReturn(List.of(p2, p1));

        List<RotaViagemDTO> rotas = operacaoService.listarRotas(null, false);

        assertThat(rotas).hasSize(1);
        RotaViagemDTO rota = rotas.get(0);

        assertThat(rota.viagemId()).isEqualTo("v1");
        assertThat(rota.droneId()).isEqualTo("d1");
        assertThat(rota.totalParadas()).isEqualTo(2);
        assertThat(rota.pesoTotalKg()).isEqualTo(5.0);
        assertThat(rota.paradas()).extracting(parada -> parada.pedidoId())
                .containsExactly("p1", "p2");
        assertThat(rota.paradas()).extracting(parada -> parada.distanciaDoPontoAnteriorKm())
                .containsExactly(1.0, 1.0);
        assertThat(rota.distanciaTotalKm()).isEqualTo(4.0);
        assertThat(rota.distanciaRetornoBaseKm()).isEqualTo(2.0);
        assertThat(rota.concluida()).isFalse();
    }

    @Test
    void deveEstimarOMesmoTempoTotalCalculadoNoDespacho() {
        Pedido p1 = criarPedido("p1", 1.0, 0.0, 2.0, 0, StatusPedido.EM_TRANSPORTE);
        Pedido p2 = criarPedido("p2", 2.0, 0.0, 2.0, 1, StatusPedido.EM_TRANSPORTE);
        when(pedidoRepository.findByViagemIdIsNotNull()).thenReturn(List.of(p1, p2));

        RotaViagemDTO rota = operacaoService.listarRotas(null, false).get(0);

        assertThat(rota.tempoEstimadoMinutos()).isEqualTo(17.0);
        assertThat(rota.paradas()).extracting(parada -> parada.tempoAcumuladoAteEntregaMinutos())
                .containsExactly(9.0, 13.0);
    }

    @Test
    void deveMarcarViagemComoConcluidaQuandoTodosOsPedidosForamEntregues() {
        Pedido p1 = criarPedido("p1", 1.0, 0.0, 2.0, 0, StatusPedido.ENTREGUE);
        Pedido p2 = criarPedido("p2", 2.0, 0.0, 2.0, 1, StatusPedido.ENTREGUE);
        when(pedidoRepository.findByViagemIdIsNotNull()).thenReturn(List.of(p1, p2));

        assertThat(operacaoService.listarRotas(null, false).get(0).concluida()).isTrue();
        assertThat(operacaoService.listarRotas(null, true)).isEmpty();
    }

    @Test
    void deveFiltrarRotasPorDrone() {
        Pedido pedido = criarPedido("p1", 1.0, 0.0, 2.0, 0, StatusPedido.EM_TRANSPORTE);
        when(pedidoRepository.findByViagemIdIsNotNull()).thenReturn(List.of(pedido));

        assertThat(operacaoService.listarRotas("d1", false)).hasSize(1);
        assertThat(operacaoService.listarRotas("outro-drone", false)).isEmpty();
    }

    @Test
    void deveLancarExcecaoAoBuscarViagemInexistente() {
        when(pedidoRepository.findByViagemIdOrderByOrdemNaRotaAsc("nao-existe")).thenReturn(List.of());

        assertThatThrownBy(() -> operacaoService.obterRota("nao-existe"))
                .isInstanceOf(ResourceNotFoundException.class);
    }


    @Test
    void deveProjetarTempoRestanteEmCadaEstadoDaViagem() {
        // uma única parada a 5 km da base: 5 + 10 + 2 + 10 = 27 min de viagem
        Pedido pedido = criarPedido("p1", 3.0, 4.0, 2.0, 0, StatusPedido.EM_TRANSPORTE);
        when(droneRepository.findById("d1")).thenReturn(Optional.of(drone));
        when(pedidoRepository.findByViagemIdOrderByOrdemNaRotaAsc("v1")).thenReturn(List.of(pedido));

        assertThat(operacaoService.obterStatusDoDrone("d1").tempoRestanteViagemMinutos())
                .isEqualTo(27.0);

        drone.setStatus(StatusDrone.EM_VOO);
        drone.setTempoRestanteEtapaMinutos(10.0);
        assertThat(operacaoService.obterStatusDoDrone("d1").tempoRestanteViagemMinutos())
                .isEqualTo(22.0);

        drone.setStatus(StatusDrone.ENTREGANDO);
        drone.setTempoRestanteEtapaMinutos(2.0);
        assertThat(operacaoService.obterStatusDoDrone("d1").tempoRestanteViagemMinutos())
                .isEqualTo(12.0);

        drone.setStatus(StatusDrone.RETORNANDO);
        drone.setTempoRestanteEtapaMinutos(10.0);
        assertThat(operacaoService.obterStatusDoDrone("d1").tempoRestanteViagemMinutos())
                .isEqualTo(10.0);
    }

    @Test
    void deveInformarCargaABordoEBateriaEmPercentual() {
        Pedido entregue = criarPedido("p1", 1.0, 0.0, 2.0, 0, StatusPedido.ENTREGUE);
        Pedido aBordo = criarPedido("p2", 2.0, 0.0, 3.0, 1, StatusPedido.EM_TRANSPORTE);

        drone.setStatus(StatusDrone.EM_VOO);
        drone.setParadaAtual(1);
        drone.setTempoRestanteEtapaMinutos(2.0);

        when(droneRepository.findAll()).thenReturn(List.of(drone));
        when(pedidoRepository.findByViagemIdOrderByOrdemNaRotaAsc("v1"))
                .thenReturn(List.of(entregue, aBordo));

        DroneStatusDTO status = operacaoService.listarStatusDosDrones().get(0);

        assertThat(status.totalParadasDaViagem()).isEqualTo(2);
        assertThat(status.entregasPendentesNaViagem()).isEqualTo(1);
        assertThat(status.pesoTransportadoKg()).isEqualTo(3.0);
        assertThat(status.bateriaPercentual()).isEqualTo(50.0); // 10 de 20 km
        assertThat(status.paradaAtual()).isEqualTo(1);
    }

    @Test
    void deveRetornarStatusNeutroParaDroneOcioso() {
        drone.setStatus(StatusDrone.IDLE);
        drone.setViagemAtualId(null);
        drone.setParadaAtual(null);
        drone.setTempoRestanteEtapaMinutos(0.0);
        drone.setBateriaAtual(20.0);
        when(droneRepository.findAll()).thenReturn(List.of(drone));

        DroneStatusDTO status = operacaoService.listarStatusDosDrones().get(0);

        assertThat(status.status()).isEqualTo(StatusDrone.IDLE);
        assertThat(status.totalParadasDaViagem()).isZero();
        assertThat(status.pesoTransportadoKg()).isZero();
        assertThat(status.tempoRestanteViagemMinutos()).isZero();
        assertThat(status.bateriaPercentual()).isEqualTo(100.0);
    }

    @Test
    void deveLancarExcecaoAoBuscarStatusDeDroneInexistente() {
        when(droneRepository.findById("nao-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> operacaoService.obterStatusDoDrone("nao-existe"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}