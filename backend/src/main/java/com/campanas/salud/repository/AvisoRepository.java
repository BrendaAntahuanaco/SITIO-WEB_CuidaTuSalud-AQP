package com.campanas.salud.repository;

import com.campanas.salud.model.Aviso;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AvisoRepository extends JpaRepository<Aviso, Long> {
    List<Aviso> findAllByOrderByFechaPublicacionDesc();
}
