package com.dtidigital.simulador.service;

import com.dtidigital.simulador.dto.PedidoDTO;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private DroneRepository droneRepository;

    @Mock
    private ZonaExclusaoService zonaExclusaoService;

    @InjectMocks
    private PedidoService pedidoService;

    private Drone criarDrone(String id, double capacidade, double autonomia, double bateria) {
        Drone drone = new Drone();
        drone.setId(id);
        drone.setCapacidadeMaximaPeso(capacidade);
        drone.setAutonomiaMaximaKm(autonomia);
        drone.setBateriaAtual(bateria);
        drone.setStatus(StatusDrone.IDLE);
        return drone;
    }

    private Pedido criarPedidoPendente(String id, double x, double y, double peso, Prioridade prioridade) {
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setCoordenadaX(x);
        pedido.setCoordenadaY(y);
        pedido.setPeso(peso);
        pedido.setPrioridade(prioridade);
        pedido.setStatus(StatusPedido.PENDENTE);
        return pedido;
    }

    @BeforeEach
    void setUp() {
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(droneRepository.save(any(Drone.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(zonaExclusaoService.verificarBloqueio(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());
    }


    @Test
    void deveCalcularDistanciaEuclidianaDaBase() {
        double distancia = pedidoService.calcularDistanciaDaBase(3.0, 4.0);
        assertThat(distancia).isEqualTo(5.0);
    }


    @Test
    void deveCriarPedidoJaAlocandoDroneDisponivel() {
        PedidoDTO dto = new PedidoDTO();
        dto.setCoordenadaX(3.0);
        dto.setCoordenadaY(4.0);
        dto.setPeso(2.0);
        dto.setPrioridade(Prioridade.ALTA);

        Drone drone = criarDrone("d1", 5.0, 20.0, 20.0);
        AtomicReference<Pedido> salvo = new AtomicReference<>();

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("p1");
            }
            salvo.set(p);
            return p;
        });
        when(pedidoRepository.findAll())
                .thenAnswer(inv -> salvo.get() == null ? List.of() : List.of(salvo.get()));
        when(pedidoRepository.findById("p1")).thenAnswer(inv -> Optional.ofNullable(salvo.get()));
        when(droneRepository.findByStatus(StatusDrone.IDLE)).thenReturn(List.of(drone));

        Pedido resultado = pedidoService.criarPedido(dto);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedido.EM_TRANSPORTE);
        assertThat(resultado.getDroneAlocado()).isEqualTo(drone);
        assertThat(resultado.getTempoEstimadoMinutos()).isEqualTo(27.0);
        assertThat(resultado.getDistanciaRotaKm()).isEqualTo(10.0);
        assertThat(resultado.getViagemId()).isNotNull();
    }


    @Test
    void deveRejeitarPedidoQuePesoExcedeCapacidadeDeTodosOsDrones() {
        PedidoDTO dto = new PedidoDTO();
        dto.setCoordenadaX(1.0);
        dto.setCoordenadaY(1.0);
        dto.setPeso(50.0);
        dto.setPrioridade(Prioridade.ALTA);

        when(droneRepository.findAll()).thenReturn(List.of(criarDrone("d1", 5.0, 20.0, 20.0)));

        assertThatThrownBy(() -> pedidoService.criarPedido(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("excede a capacidade");

        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void deveAceitarPedidoQuandoNaoHaDronesCadastradosAinda() {
        PedidoDTO dto = new PedidoDTO();
        dto.setCoordenadaX(1.0);
        dto.setCoordenadaY(1.0);
        dto.setPeso(999.0);
        dto.setPrioridade(Prioridade.ALTA);

        when(droneRepository.findAll()).thenReturn(List.of());
        when(droneRepository.findByStatus(StatusDrone.IDLE)).thenReturn(List.of());
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("p1");
            }
            return p;
        });
        when(pedidoRepository.findAll()).thenReturn(List.of());
        when(pedidoRepository.findById("p1")).thenReturn(Optional.empty());

        Pedido resultado = pedidoService.criarPedido(dto);

        assertThat(resultado.getStatus()).isEqualTo(StatusPedido.PENDENTE);
    }

    @Test
    void deveAlocarDroneCompativelParaPedidoPendente() {
        Pedido pedido = criarPedidoPendente("p1", 3.0, 4.0, 2.0, Prioridade.MEDIA);
        Drone drone = criarDrone("d1", 5.0, 20.0, 20.0);

        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));
        when(droneRepository.findByStatus(StatusDrone.IDLE)).thenReturn(List.of(drone));

        pedidoService.processarFila();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.EM_TRANSPORTE);
        assertThat(pedido.getDroneAlocado()).isEqualTo(drone);
        assertThat(pedido.getMotivoBloqueio()).isNull();
        assertThat(drone.getStatus()).isEqualTo(StatusDrone.CARREGANDO);
        assertThat(drone.getViagemAtualId()).isEqualTo(pedido.getViagemId());
        assertThat(drone.getTempoRestanteEtapaMinutos()).isEqualTo(5.0);
        assertThat(drone.getBateriaAtual()).isEqualTo(10.0);
    }

    @Test
    void naoDeveAlocarDroneQuandoPesoExcedeCapacidade() {
        Pedido pedido = criarPedidoPendente("p1", 1.0, 0.0, 8.0, Prioridade.ALTA);
        Drone drone = criarDrone("d1", 5.0, 20.0, 20.0);

        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));
        when(droneRepository.findByStatus(StatusDrone.IDLE)).thenReturn(List.of(drone));

        pedidoService.processarFila();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.PENDENTE);
        assertThat(pedido.getDroneAlocado()).isNull();
        assertThat(drone.getStatus()).isEqualTo(StatusDrone.IDLE);
    }

    @Test
    void naoDeveAlocarDroneQuandoBateriaInsuficiente() {
        Pedido pedido = criarPedidoPendente("p1", 50.0, 0.0, 2.0, Prioridade.ALTA);
        Drone drone = criarDrone("d1", 5.0, 20.0, 20.0);

        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));
        when(droneRepository.findByStatus(StatusDrone.IDLE)).thenReturn(List.of(drone));

        pedidoService.processarFila();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.PENDENTE);
        assertThat(pedido.getDroneAlocado()).isNull();
    }

    @Test
    void deveManterPedidoNaFilaQuandoNaoHaDroneDisponivel() {
        Pedido pedido = criarPedidoPendente("p1", 1.0, 0.0, 1.0, Prioridade.ALTA);

        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));
        when(droneRepository.findByStatus(StatusDrone.IDLE)).thenReturn(List.of());

        pedidoService.processarFila();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.PENDENTE);
        assertThat(pedido.getDroneAlocado()).isNull();
    }


    @Test
    void deveConsolidarVariosPedidosNaMesmaViagemQuandoCabemNoDrone() {
        Pedido p1 = criarPedidoPendente("p1", 1.0, 0.0, 2.0, Prioridade.MEDIA);
        Pedido p2 = criarPedidoPendente("p2", 2.0, 0.0, 2.0, Prioridade.MEDIA);
        Drone drone = criarDrone("d1", 5.0, 20.0, 20.0);

        when(pedidoRepository.findAll()).thenReturn(List.of(p1, p2));
        when(droneRepository.findByStatus(StatusDrone.IDLE)).thenReturn(List.of(drone));

        pedidoService.processarFila();

        assertThat(p1.getStatus()).isEqualTo(StatusPedido.EM_TRANSPORTE);
        assertThat(p2.getStatus()).isEqualTo(StatusPedido.EM_TRANSPORTE);
        assertThat(p1.getViagemId()).isNotNull().isEqualTo(p2.getViagemId());
        assertThat(p1.getOrdemNaRota()).isEqualTo(0);
        assertThat(p2.getOrdemNaRota()).isEqualTo(1);
        // rota base -> (1,0) -> (2,0) -> base = 4 km
        assertThat(drone.getBateriaAtual()).isEqualTo(16.0);
        assertThat(p1.getDistanciaRotaKm()).isEqualTo(4.0);
    }

    @Test
    void naoDeveExcederCapacidadeDoDroneAoConsolidarViagem() {
        Pedido p1 = criarPedidoPendente("p1", 1.0, 0.0, 4.0, Prioridade.ALTA);
        Pedido p2 = criarPedidoPendente("p2", 2.0, 0.0, 4.0, Prioridade.ALTA);
        Drone drone = criarDrone("d1", 5.0, 20.0, 20.0);

        when(pedidoRepository.findAll()).thenReturn(List.of(p1, p2));
        when(droneRepository.findByStatus(StatusDrone.IDLE)).thenReturn(List.of(drone));

        pedidoService.processarFila();

        assertThat(p1.getStatus()).isEqualTo(StatusPedido.EM_TRANSPORTE);
        assertThat(p2.getStatus()).isEqualTo(StatusPedido.PENDENTE);
    }

    @Test
    void deveDistribuirPedidosEntreVariosDronesQuandoNaoCabemNaMesmaViagem() {
        Pedido p1 = criarPedidoPendente("p1", 1.0, 0.0, 4.0, Prioridade.ALTA);
        Pedido p2 = criarPedidoPendente("p2", 2.0, 0.0, 4.0, Prioridade.ALTA);
        Pedido p3 = criarPedidoPendente("p3", 3.0, 0.0, 4.0, Prioridade.ALTA);
        Drone d1 = criarDrone("d1", 5.0, 20.0, 20.0);
        Drone d2 = criarDrone("d2", 5.0, 20.0, 20.0);

        when(pedidoRepository.findAll()).thenReturn(List.of(p1, p2, p3));
        when(droneRepository.findByStatus(StatusDrone.IDLE)).thenReturn(List.of(d1, d2));

        pedidoService.processarFila();

        assertThat(p1.getDroneAlocado()).isEqualTo(d1);
        assertThat(p2.getDroneAlocado()).isEqualTo(d2);
        assertThat(p3.getStatus()).isEqualTo(StatusPedido.PENDENTE);
        assertThat(p1.getViagemId()).isNotEqualTo(p2.getViagemId());
    }


    @Test
    void deveAtenderPedidoDeMaiorPrioridadePrimeiroQuandoNaoCabemNaMesmaViagem() {
        Pedido pedidoBaixa = criarPedidoPendente("p1", 1.0, 0.0, 4.0, Prioridade.BAIXA);
        Pedido pedidoAlta = criarPedidoPendente("p2", 2.0, 0.0, 4.0, Prioridade.ALTA);
        Drone drone = criarDrone("d1", 5.0, 20.0, 20.0);

        when(pedidoRepository.findAll()).thenReturn(List.of(pedidoBaixa, pedidoAlta));
        when(droneRepository.findByStatus(StatusDrone.IDLE)).thenReturn(List.of(drone));

        pedidoService.processarFila();

        assertThat(pedidoAlta.getStatus()).isEqualTo(StatusPedido.EM_TRANSPORTE);
        assertThat(pedidoBaixa.getStatus()).isEqualTo(StatusPedido.PENDENTE);
    }

    @Test
    void deveDesempatarPorTempoDeChegadaQuandoPrioridadeEDistanciaSaoIguais() {
        // mesma prioridade e mesma distância da base (3-4-5): só o mais antigo cabe no drone
        Pedido chegouDepois = criarPedidoPendente("p1", 3.0, 4.0, 4.0, Prioridade.MEDIA);
        chegouDepois.setCriadoEm(Instant.now());

        Pedido chegouPrimeiro = criarPedidoPendente("p2", 4.0, 3.0, 4.0, Prioridade.MEDIA);
        chegouPrimeiro.setCriadoEm(Instant.now().minus(1, ChronoUnit.MINUTES));

        Drone drone = criarDrone("d1", 5.0, 20.0, 20.0);

        when(pedidoRepository.findAll()).thenReturn(List.of(chegouDepois, chegouPrimeiro));
        when(droneRepository.findByStatus(StatusDrone.IDLE)).thenReturn(List.of(drone));

        pedidoService.processarFila();

        assertThat(chegouPrimeiro.getStatus()).isEqualTo(StatusPedido.EM_TRANSPORTE);
        assertThat(chegouDepois.getStatus()).isEqualTo(StatusPedido.PENDENTE);
    }


    @Test
    void deveBloquearPedidoCujaRotaCruzaZonaDeExclusao() {
        Pedido pedido = criarPedidoPendente("p1", 6.0, 6.0, 2.0, Prioridade.ALTA);
        Drone drone = criarDrone("d1", 5.0, 20.0, 20.0);
        ZonaExclusao zona = new ZonaExclusao("z1", "Aeroporto", 5.0, 5.0, 3.0);

        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));
        when(droneRepository.findByStatus(StatusDrone.IDLE)).thenReturn(List.of(drone));
        when(zonaExclusaoService.verificarBloqueio(0.0, 0.0, 6.0, 6.0)).thenReturn(Optional.of(zona));

        pedidoService.processarFila();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.PENDENTE);
        assertThat(pedido.getMotivoBloqueio()).contains("Aeroporto");
        assertThat(pedido.getDroneAlocado()).isNull();
        assertThat(drone.getStatus()).isEqualTo(StatusDrone.IDLE);
        assertThat(drone.getBateriaAtual()).isEqualTo(20.0);
    }


    @Test
    void deveLiberarDroneEMarcarPedidoComoEntregueAoConcluir() {
        Drone drone = criarDrone("d1", 5.0, 20.0, 10.0);
        drone.setStatus(StatusDrone.EM_VOO);

        Pedido pedido = criarPedidoPendente("p1", 3.0, 4.0, 2.0, Prioridade.MEDIA);
        pedido.setStatus(StatusPedido.EM_TRANSPORTE);
        pedido.setDroneAlocado(drone);
        pedido.setViagemId("v1");

        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));
        when(pedidoRepository.findAll()).thenReturn(List.of(pedido));

        Pedido concluido = pedidoService.concluirEntrega("p1");

        assertThat(concluido.getStatus()).isEqualTo(StatusPedido.ENTREGUE);
        assertThat(drone.getStatus()).isEqualTo(StatusDrone.IDLE);
        assertThat(drone.getBateriaAtual()).isEqualTo(drone.getAutonomiaMaximaKm());
    }

    @Test
    void deveRecarregarBateriaAutomaticamenteAoEncerrarViagem() {
        Drone drone = criarDrone("d1", 5.0, 20.0, 3.5);
        drone.setStatus(StatusDrone.RETORNANDO);
        drone.setViagemAtualId("v1");
        drone.setParadaAtual(2);

        pedidoService.encerrarViagem(drone);

        assertThat(drone.getStatus()).isEqualTo(StatusDrone.IDLE);
        assertThat(drone.getViagemAtualId()).isNull();
        assertThat(drone.getParadaAtual()).isNull();
        assertThat(drone.getBateriaAtual()).isEqualTo(20.0);
    }

    @Test
    void naoDeveLiberarDroneEnquantoHouverPedidosDaMesmaViagemEmTransporte() {
        Drone drone = criarDrone("d1", 10.0, 20.0, 10.0);
        drone.setStatus(StatusDrone.EM_VOO);

        Pedido p1 = criarPedidoPendente("p1", 1.0, 0.0, 2.0, Prioridade.MEDIA);
        p1.setStatus(StatusPedido.EM_TRANSPORTE);
        p1.setDroneAlocado(drone);
        p1.setViagemId("v1");

        Pedido p2 = criarPedidoPendente("p2", 2.0, 0.0, 2.0, Prioridade.MEDIA);
        p2.setStatus(StatusPedido.EM_TRANSPORTE);
        p2.setDroneAlocado(drone);
        p2.setViagemId("v1");

        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(p1));
        when(pedidoRepository.findAll()).thenReturn(List.of(p1, p2));

        pedidoService.concluirEntrega("p1");

        assertThat(p1.getStatus()).isEqualTo(StatusPedido.ENTREGUE);
        assertThat(p2.getStatus()).isEqualTo(StatusPedido.EM_TRANSPORTE);
        assertThat(drone.getStatus()).isEqualTo(StatusDrone.EM_VOO);
    }

    @Test
    void deveLancarExcecaoAoConcluirPedidoInexistente() {
        when(pedidoRepository.findById("id-invalido")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pedidoService.concluirEntrega("id-invalido"))
                .isInstanceOf(ResourceNotFoundException.class);
    }


    @Test
    void deveCalcularDashboardCorretamente() {
        Drone drone = criarDrone("d1", 5.0, 20.0, 20.0);

        Pedido entregue = criarPedidoPendente("p1", 3.0, 4.0, 1.0, Prioridade.ALTA);
        entregue.setStatus(StatusPedido.ENTREGUE);
        entregue.setTempoEstimadoMinutos(20.0);
        entregue.setViagemId("v1");
        entregue.setDroneAlocado(drone);

        Pedido pendente = criarPedidoPendente("p2", 1.0, 1.0, 1.0, Prioridade.BAIXA);

        when(pedidoRepository.findAll()).thenReturn(List.of(entregue, pendente));
        when(droneRepository.findAll()).thenReturn(List.of(drone));

        Map<String, Object> dashboard = pedidoService.obterDashboard();

        assertThat(dashboard.get("totalPedidos")).isEqualTo(2);
        assertThat(dashboard.get("entregasRealizadas")).isEqualTo(1L);
        assertThat(dashboard.get("pedidosNaFila")).isEqualTo(1L);
        assertThat(dashboard.get("tempoMedioMinutos")).isEqualTo(20.0);
        assertThat(dashboard.get("totalDrones")).isEqualTo(1);
        assertThat(dashboard.get("totalViagens")).isEqualTo(1L);
        assertThat(dashboard.get("droneMaisEficiente")).isEqualTo("d1");
        assertThat((String) dashboard.get("mapaEntregas")).contains("B").contains("Legenda");
    }

    @Test
    void deveProcessarGrandeVolumeDePedidosSemErrosRespeitandoRegras() {
        int quantidadeDrones = 50;
        int quantidadePedidos = 300;
        double capacidadePorDrone = 5.0;
        double pesoPorPedido = 1.0;

        List<Drone> drones = new ArrayList<>();
        for (int i = 0; i < quantidadeDrones; i++) {
            drones.add(criarDrone("d" + i, capacidadePorDrone, 100.0, 100.0));
        }

        List<Pedido> pedidos = new ArrayList<>();
        Prioridade[] prioridades = Prioridade.values();
        for (int i = 0; i < quantidadePedidos; i++) {
            double x = (i % 20) - 10;
            double y = ((i / 20) % 20) - 10;
            Pedido pedido = criarPedidoPendente("p" + i, x, y, pesoPorPedido, prioridades[i % prioridades.length]);
            pedidos.add(pedido);
        }

        when(pedidoRepository.findAll()).thenReturn(pedidos);
        when(droneRepository.findByStatus(StatusDrone.IDLE)).thenReturn(drones);

        long inicio = System.nanoTime();
        pedidoService.processarFila();
        long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;

        long alocados = pedidos.stream().filter(p -> p.getStatus() == StatusPedido.EM_TRANSPORTE).count();
        long pendentes = pedidos.stream().filter(p -> p.getStatus() == StatusPedido.PENDENTE).count();

        assertThat(alocados + pendentes).isEqualTo(quantidadePedidos);
        assertThat(alocados).isEqualTo((long) (quantidadeDrones * capacidadePorDrone / pesoPorPedido));
        assertThat(duracaoMs).isLessThan(5_000);

        Map<String, Long> pesoPorViagem = pedidos.stream()
                .filter(p -> p.getViagemId() != null)
                .collect(Collectors.groupingBy(Pedido::getViagemId, Collectors.summingLong(p -> (long) p.getPeso().doubleValue())));
        assertThat(pesoPorViagem.values()).allMatch(peso -> peso <= capacidadePorDrone);
    }
}
