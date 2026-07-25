package com.dtidigital.simulador.repository;

import com.dtidigital.simulador.model.ZonaExclusao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ZonaExclusaoRepository extends JpaRepository<ZonaExclusao, String> {
}