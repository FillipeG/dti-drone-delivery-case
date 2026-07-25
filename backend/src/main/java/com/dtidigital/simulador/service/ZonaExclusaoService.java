package com.dtidigital.simulador.service;

import com.dtidigital.simulador.dto.ZonaExclusaoDTO;
import com.dtidigital.simulador.model.ZonaExclusao;
import com.dtidigital.simulador.repository.ZonaExclusaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ZonaExclusaoService {

    private final ZonaExclusaoRepository zonaExclusaoRepository;

    public ZonaExclusao cadastrarZona(ZonaExclusaoDTO dto) {
        ZonaExclusao zona = new ZonaExclusao();
        zona.setNome(dto.getNome());
        zona.setCoordenadaX(dto.getCoordenadaX());
        zona.setCoordenadaY(dto.getCoordenadaY());
        zona.setRaioKm(dto.getRaioKm());
        return zonaExclusaoRepository.save(zona);
    }

    public List<ZonaExclusao> listarTodas() {
        return zonaExclusaoRepository.findAll();
    }

    public void removerZona(String id) {
        if (!zonaExclusaoRepository.existsById(id)) {
            throw new RuntimeException("Zona de exclusão não encontrada com o ID: " + id);
        }
        zonaExclusaoRepository.deleteById(id);
    }

    /**
     * Verifica se o trajeto em linha reta entre a origem (base) e o destino (pedido)
     * cruza alguma zona de exclusão aérea cadastrada.
     * Retorna a primeira zona encontrada que bloqueia a rota, se houver.
     */
    public Optional<ZonaExclusao> verificarBloqueio(double origemX, double origemY,
                                                      double destinoX, double destinoY) {
        return zonaExclusaoRepository.findAll().stream()
                .filter(zona -> segmentoIntersectaCirculo(
                        origemX, origemY, destinoX, destinoY,
                        zona.getCoordenadaX(), zona.getCoordenadaY(), zona.getRaioKm()))
                .findFirst();
    }

    /**
     * Calcula se o segmento de reta A-B passa a uma distância menor ou igual
     * ao raio do centro de um círculo C (projeção do ponto no segmento).
     */
    private boolean segmentoIntersectaCirculo(double ax, double ay, double bx, double by,
                                                double cx, double cy, double raio) {
        double abX = bx - ax;
        double abY = by - ay;
        double acX = cx - ax;
        double acY = cy - ay;

        double abLenSquared = abX * abX + abY * abY;

        double t = abLenSquared == 0 ? 0 : (acX * abX + acY * abY) / abLenSquared;
        t = Math.max(0, Math.min(1, t));

        double closestX = ax + t * abX;
        double closestY = ay + t * abY;

        double distX = closestX - cx;
        double distY = closestY - cy;
        double distancia = Math.sqrt(distX * distX + distY * distY);

        return distancia <= raio;
    }
}