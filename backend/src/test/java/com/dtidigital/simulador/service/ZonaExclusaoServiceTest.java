package com.dtidigital.simulador.service;

import com.dtidigital.simulador.dto.ZonaExclusaoDTO;
import com.dtidigital.simulador.exception.ResourceNotFoundException;
import com.dtidigital.simulador.model.ZonaExclusao;
import com.dtidigital.simulador.repository.ZonaExclusaoRepository;
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
class ZonaExclusaoServiceTest {

    @Mock
    private ZonaExclusaoRepository zonaExclusaoRepository;

    @InjectMocks
    private ZonaExclusaoService zonaExclusaoService;

    private ZonaExclusaoDTO criarDTO(String nome, double x, double y, double raio) {
        ZonaExclusaoDTO dto = new ZonaExclusaoDTO();
        dto.setNome(nome);
        dto.setCoordenadaX(x);
        dto.setCoordenadaY(y);
        dto.setRaioKm(raio);
        return dto;
    }

    @Test
    void deveCadastrarZonaComOsDadosDoDTO() {
        ZonaExclusaoDTO dto = criarDTO("Aeroporto", 5.0, 5.0, 3.0);
        when(zonaExclusaoRepository.save(any(ZonaExclusao.class))).thenAnswer(inv -> inv.getArgument(0));

        ZonaExclusao salva = zonaExclusaoService.cadastrarZona(dto);

        assertThat(salva.getNome()).isEqualTo("Aeroporto");
        assertThat(salva.getCoordenadaX()).isEqualTo(5.0);
        assertThat(salva.getCoordenadaY()).isEqualTo(5.0);
        assertThat(salva.getRaioKm()).isEqualTo(3.0);
    }

    @Test
    void deveLancarExcecaoAoRemoverZonaInexistente() {
        when(zonaExclusaoRepository.existsById("id-invalido")).thenReturn(false);

        assertThatThrownBy(() -> zonaExclusaoService.removerZona("id-invalido"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(zonaExclusaoRepository, never()).deleteById(any());
    }

    @Test
    void deveRemoverZonaExistente() {
        when(zonaExclusaoRepository.existsById("z1")).thenReturn(true);

        zonaExclusaoService.removerZona("z1");

        verify(zonaExclusaoRepository).deleteById("z1");
    }

    @Test
    void deveDetectarRotaQuePassaDentroDaZona() {
        ZonaExclusao zona = new ZonaExclusao("z1", "Aeroporto", 5.0, 5.0, 3.0);
        when(zonaExclusaoRepository.findAll()).thenReturn(List.of(zona));

        Optional<ZonaExclusao> resultado = zonaExclusaoService.verificarBloqueio(0.0, 0.0, 10.0, 10.0);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getNome()).isEqualTo("Aeroporto");
    }

    @Test
    void naoDeveDetectarBloqueioQuandoRotaNaoCruzaAZona() {
        ZonaExclusao zona = new ZonaExclusao("z1", "Aeroporto", 5.0, 5.0, 1.0);
        when(zonaExclusaoRepository.findAll()).thenReturn(List.of(zona));

        Optional<ZonaExclusao> resultado = zonaExclusaoService.verificarBloqueio(0.0, 0.0, 20.0, 0.0);

        assertThat(resultado).isEmpty();
    }

    @Test
    void deveConsiderarBloqueadoQuandoRotaTocaExatamenteNaBordaDaZona() {
        ZonaExclusao zona = new ZonaExclusao("z1", "Zona", 10.0, 0.0, 2.0);
        when(zonaExclusaoRepository.findAll()).thenReturn(List.of(zona));

        Optional<ZonaExclusao> resultado = zonaExclusaoService.verificarBloqueio(0.0, 0.0, 8.0, 0.0);

        assertThat(resultado).isPresent();
    }

    @Test
    void naoDeveBloquearQuandoNaoHaZonasCadastradas() {
        when(zonaExclusaoRepository.findAll()).thenReturn(List.of());

        Optional<ZonaExclusao> resultado = zonaExclusaoService.verificarBloqueio(0.0, 0.0, 10.0, 10.0);

        assertThat(resultado).isEmpty();
    }
}