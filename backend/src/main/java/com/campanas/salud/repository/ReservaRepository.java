package com.campanas.salud.repository;

import com.campanas.salud.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    List<Reserva> findByUsuarioIdOrderByFechaReservaDesc(Long usuarioId);
    List<Reserva> findByBeneficiarioDniOrderByFechaReservaDesc(String dni);
    boolean existsByTurnoId(Long turnoId);
    boolean existsByTurnoCampanaId(Long campanaId);
    /** Evita reservas duplicadas: mismo DNI, misma campaña y reserva todavía activa. */
    boolean existsByTurnoCampanaIdAndBeneficiarioDniAndEstado(Long campanaId, String dni, String estado);
}
