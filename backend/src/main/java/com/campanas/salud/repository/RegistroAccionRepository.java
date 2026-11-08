package com.campanas.salud.repository;

import com.campanas.salud.model.RegistroAccion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RegistroAccionRepository extends JpaRepository<RegistroAccion, Long> {
    List<RegistroAccion> findTop50ByOrderByFechaHoraDesc();
}
