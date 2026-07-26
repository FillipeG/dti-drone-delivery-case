package com.dtidigital.simulador.service;

import com.dtidigital.simulador.dto.ZonaExclusaoDTO;
import com.dtidigital.simulador.exception.ResourceNotFoundException;
import com.dtidigital.simulador.model.ZonaExclusao;
import com.dtidigital.simulador.repository.ZonaExclusaoRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ZonaExclusaoService {

    private final ZonaExclusaoRepository zonaExclusaoRepository;
    private final PedidoService pedidoService;

    // PedidoService ja depende de ZonaExclusaoService (pra checar bloqueio na
    // hora de alocar); @Lazy quebra o ciclo, resolvendo o bean sob demanda
    public ZonaExclusaoService(ZonaExclusaoRepository zonaExclusaoRepository, @Lazy PedidoService pedidoService) {
        this.zonaExclusaoRepository = zonaExclusaoRepository;
        this.pedidoService = pedidoService;
    }

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
            throw new ResourceNotFoundException("Zona de exclusão não encontrada com o ID: " + id);
        }
        zonaExclusaoRepository.deleteById(id);
        pedidoService.processarFila();
    }

    public Optional<ZonaExclusao> verificarBloqueio(double origemX, double origemY,
                                                      double destinoX, double destinoY) {
        return zonaExclusaoRepository.findAll().stream()
                .filter(zona -> segmentoIntersectaCirculo(
                        origemX, origemY, destinoX, destinoY,
                        zona.getCoordenadaX(), zona.getCoordenadaY(), zona.getRaioKm()))
                .findFirst();
    }

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