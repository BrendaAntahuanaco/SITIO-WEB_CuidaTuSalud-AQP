package com.campanas.salud.repository;

import com.campanas.salud.model.Campana;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampanaRepository extends JpaRepository<Campana, Long> {
    boolean existsByEstablecimientoId(Long establecimientoId);
}
