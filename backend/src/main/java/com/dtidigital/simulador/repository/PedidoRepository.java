package com.dtidigital.simulador.repository;

import com.dtidigital.simulador.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, String> {

    List<Pedido> findByViagemIdOrderByOrdemNaRotaAsc(String viagemId);

    List<Pedido> findByViagemIdIsNotNull();

}