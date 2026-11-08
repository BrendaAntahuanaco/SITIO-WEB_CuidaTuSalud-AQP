package com.campanas.salud.repository;

import com.campanas.salud.model.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TurnoRepository extends JpaRepository<Turno, Long> {
    List<Turno> findByCampanaId(Long campanaId);

    /** Descuenta un cupo en una sola operación atómica: dos personas no pueden tomar el último cupo. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Turno t set t.cuposDisponibles = t.cuposDisponibles - 1 "
         + "where t.id = :id and t.cuposDisponibles > 0")
    int descontarCupo(@Param("id") Long id);

    /** Devuelve un cupo sin superar nunca el cupo total. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Turno t set t.cuposDisponibles = t.cuposDisponibles + 1 "
         + "where t.id = :id and t.cuposDisponibles < t.cuposTotal")
    int devolverCupo(@Param("id") Long id);
}
