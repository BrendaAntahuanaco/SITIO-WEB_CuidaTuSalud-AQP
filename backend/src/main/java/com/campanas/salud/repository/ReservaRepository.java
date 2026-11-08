package com.campanas.salud.repository;

import com.campanas.salud.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    List<Reserva> findByUsuarioId(Long usuarioId);
    List<Reserva> findByBeneficiarioDni(String dni);

    /** Evita que el mismo beneficiario tenga dos reservas activas en la misma campaña. */
    boolean existsByTurnoCampanaIdAndBeneficiarioDniAndEstado(Long campanaId, String dni, String estado);

    boolean existsByTurnoCampanaId(Long campanaId);
    boolean existsByTurnoId(Long turnoId);

    /** Reservas que ocupan cupo (todas menos las canceladas). */
    long countByTurnoIdAndEstadoNot(Long turnoId, String estado);
}
