package com.dtidigital.simulador.service;

import com.dtidigital.simulador.exception.ResourceNotFoundException;
import com.dtidigital.simulador.model.Drone;
import com.dtidigital.simulador.model.StatusDrone;
import com.dtidigital.simulador.repository.DroneRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DroneServiceTest {

    @Mock
    private DroneRepository droneRepository;

    @InjectMocks
    private DroneService droneService;

    private Drone drone;

    @BeforeEach
    void setUp() {
        drone = new Drone();
        drone.setId("drone-1");
        drone.setCapacidadeMaximaPeso(10.0);
        drone.setAutonomiaMaximaKm(20.0);
    }

    @Test
    void deveDefinirBateriaCheiaEStatusIdleAoCadastrarDroneSemEssesCampos() {
        when(droneRepository.save(any(Drone.class))).thenAnswer(inv -> inv.getArgument(0));

        Drone salvo = droneService.cadastrarDrone(drone);

        assertThat(salvo.getBateriaAtual()).isEqualTo(20.0);
        assertThat(salvo.getStatus()).isEqualTo(StatusDrone.IDLE);
    }

    @Test
    void naoDeveSobrescreverBateriaEStatusQuandoJaInformados() {
        drone.setBateriaAtual(5.0);
        drone.setStatus(StatusDrone.EM_VOO);
        when(droneRepository.save(any(Drone.class))).thenAnswer(inv -> inv.getArgument(0));

        Drone salvo = droneService.cadastrarDrone(drone);

        assertThat(salvo.getBateriaAtual()).isEqualTo(5.0);
        assertThat(salvo.getStatus()).isEqualTo(StatusDrone.EM_VOO);
    }

    @Test
    void deveListarApenasDronesDisponiveis() {
        Drone idle = new Drone();
        idle.setStatus(StatusDrone.IDLE);
        Drone emVoo = new Drone();
        emVoo.setStatus(StatusDrone.EM_VOO);
        when(droneRepository.findAll()).thenReturn(List.of(idle, emVoo));

        List<Drone> disponiveis = droneService.listarDisponiveis();

        assertThat(disponiveis).containsExactly(idle);
    }

    @Test
    void deveListarApenasDronesEmUso() {
        Drone idle = new Drone();
        idle.setStatus(StatusDrone.IDLE);
        Drone emVoo = new Drone();
        emVoo.setStatus(StatusDrone.EM_VOO);
        when(droneRepository.findAll()).thenReturn(List.of(idle, emVoo));

        List<Drone> emUso = droneService.listarEmUso();

        assertThat(emUso).containsExactly(emVoo);
    }

    @Test
    void deveLancarExcecaoAoAtualizarStatusDeDroneInexistente() {
        when(droneRepository.findById("id-invalido")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> droneService.atualizarStatus("id-invalido", StatusDrone.IDLE))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("id-invalido");
    }

    @Test
    void deveAtualizarStatusDoDrone() {
        when(droneRepository.findById("drone-1")).thenReturn(Optional.of(drone));
        when(droneRepository.save(any(Drone.class))).thenAnswer(inv -> inv.getArgument(0));

        Drone atualizado = droneService.atualizarStatus("drone-1", StatusDrone.ENTREGANDO);

        assertThat(atualizado.getStatus()).isEqualTo(StatusDrone.ENTREGANDO);
    }

    @Test
    void deveRecarregarBateriaERetornarDroneParaIdle() {
        drone.setBateriaAtual(2.0);
        drone.setStatus(StatusDrone.RETORNANDO);
        when(droneRepository.findById("drone-1")).thenReturn(Optional.of(drone));
        when(droneRepository.save(any(Drone.class))).thenAnswer(inv -> inv.getArgument(0));

        Drone recarregado = droneService.recarregarBateria("drone-1");

        assertThat(recarregado.getBateriaAtual()).isEqualTo(20.0);
        assertThat(recarregado.getStatus()).isEqualTo(StatusDrone.IDLE);
    }

    @Test
    void deveLancarExcecaoAoRecarregarDroneInexistente() {
        when(droneRepository.findById("id-invalido")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> droneService.recarregarBateria("id-invalido"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}