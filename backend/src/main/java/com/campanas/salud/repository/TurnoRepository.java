package com.campanas.salud.repository;

import com.campanas.salud.model.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TurnoRepository extends JpaRepository<Turno, Long> {
    List<Turno> findByCampanaId(Long campanaId);
}
