package com.campanas.salud.repository;

import com.campanas.salud.model.Turno;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TurnoRepository extends JpaRepository<Turno, Long> {
    List<Turno> findByCampanaIdOrderByFechaAscHoraInicioAsc(Long campanaId);

    /** Lee el turno bloqueando su fila hasta terminar la transacción (para editar el aforo sin perder cupos). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turno t where t.id = :id")
    Optional<Turno> buscarConBloqueo(@Param("id") Long id);

    /** Cupos libres por campaña desde una fecha: filas [idCampana, suma]. */
    @Query("select t.campana.id, coalesce(sum(t.cuposDisponibles), 0) from Turno t "
         + "where t.fecha >= :desde group by t.campana.id")
    List<Object[]> cuposLibresDesde(@Param("desde") LocalDate desde);

    /** Descuenta un cupo de forma atómica: si no quedan, no cambia nada y devuelve 0 (RF-13). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Turno t set t.cuposDisponibles = t.cuposDisponibles - 1 "
         + "where t.id = :id and t.cuposDisponibles > 0")
    int descontarCupo(@Param("id") Long id);

    /** Devuelve un cupo sin pasar del aforo total (RF-14). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Turno t set t.cuposDisponibles = t.cuposDisponibles + 1 "
         + "where t.id = :id and t.cuposDisponibles < t.cuposTotal")
    int devolverCupo(@Param("id") Long id);
}
