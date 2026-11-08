package com.campanas.salud.controller;

import com.campanas.salud.model.Reserva;
import com.campanas.salud.model.Turno;
import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.ReservaRepository;
import com.campanas.salud.repository.TurnoRepository;
import com.campanas.salud.seguridad.Bitacora;
import com.campanas.salud.seguridad.SeguridadInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Reservas con control de cupos, cancelación y asistencia (RF-12, RF-13, RF-14, RF-15). */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    public record ReservaRequest(Long turnoId, String beneficiarioNombre, String beneficiarioDni) { }

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final String NOMBRE = "^[\\p{L}][\\p{L} .'-]{2,79}$";

    private final TurnoRepository turnos;
    private final ReservaRepository reservas;
    private final Bitacora bitacora;

    public ReservaController(TurnoRepository turnos, ReservaRepository reservas, Bitacora bitacora) {
        this.turnos = turnos;
        this.reservas = reservas;
        this.bitacora = bitacora;
    }

    private static ResponseStatusException error(HttpStatus e, String m) { return new ResponseStatusException(e, m); }
    private Usuario actual(HttpServletRequest req) { return (Usuario) req.getAttribute(SeguridadInterceptor.USUARIO); }

    /**
     * Reserva un turno y descuenta un cupo. El usuario sale del token, no del cuerpo de la petición,
     * así nadie puede reservar a nombre de otra cuenta. Si no quedan cupos responde 409 (RF-13).
     */
    @Transactional
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Reserva reservar(@RequestBody ReservaRequest r, HttpServletRequest req) {
        if (r.turnoId() == null) throw error(HttpStatus.BAD_REQUEST, "Elige un turno");
        Turno t = turnos.findById(r.turnoId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "El turno no existe"));
        if (r.beneficiarioNombre() == null || !r.beneficiarioNombre().trim().matches(NOMBRE))
            throw error(HttpStatus.BAD_REQUEST, "Escribe el nombre completo de quien se vacunará (solo letras)");
        if (r.beneficiarioDni() == null || !r.beneficiarioDni().matches("\\d{8}"))
            throw error(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos");
        if ("CERRADA".equals(t.getCampana().getEstado()))
            throw error(HttpStatus.CONFLICT, "Esta campaña ya está cerrada");
        if (t.getFecha().isBefore(LocalDate.now(LIMA)))
            throw error(HttpStatus.CONFLICT, "Ese turno ya pasó");
        if (reservas.existsByTurnoCampanaIdAndBeneficiarioDniAndEstado(
                t.getCampana().getId(), r.beneficiarioDni(), "RESERVADA"))
            throw error(HttpStatus.CONFLICT, "Ese DNI ya tiene un turno reservado en esta campaña");

        // Descuento atómico: si dos personas piden el último cupo, solo una lo consigue
        if (turnos.descontarCupo(t.getId()) == 0)
            throw error(HttpStatus.CONFLICT, "No quedan cupos en este turno");

        Reserva reserva = new Reserva();
        reserva.setUsuarioId(actual(req).getId());
        reserva.setTurno(turnos.findById(t.getId()).orElseThrow());
        reserva.setBeneficiarioNombre(r.beneficiarioNombre().trim());
        reserva.setBeneficiarioDni(r.beneficiarioDni());
        return reservas.save(reserva);
    }

    /** Historial de reservas del ciudadano que inició sesión: GET /api/reservas */
    @GetMapping
    public List<Reserva> mias(HttpServletRequest req) {
        return reservas.findByUsuarioId(actual(req).getId()).stream()
                .sorted(java.util.Comparator.comparing(Reserva::getFechaReserva).reversed())
                .toList();
    }

    /** El personal de salud busca a un paciente por DNI (solo administrador). */
    @GetMapping("/buscar")
    public List<Reserva> buscarPorDni(@RequestParam String dni) {
        if (!dni.matches("\\d{8}")) throw error(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos");
        return reservas.findByBeneficiarioDni(dni);
    }

    /** Cancela una reserva y devuelve el cupo (RF-14). Solo su dueño o un administrador. */
    @Transactional
    @DeleteMapping("/{id}")
    public Reserva cancelar(@PathVariable Long id, HttpServletRequest req) {
        Usuario u = actual(req);
        Reserva r = reservas.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "La reserva no existe"));
        if (!u.getId().equals(r.getUsuarioId()) && !"ADMIN".equals(u.getRol()))
            throw error(HttpStatus.FORBIDDEN, "Esa reserva no es tuya");
        if (!"RESERVADA".equals(r.getEstado()))
            throw error(HttpStatus.BAD_REQUEST, "Solo se cancelan reservas activas");
        r.setEstado("CANCELADA");
        reservas.save(r);
        turnos.devolverCupo(r.getTurno().getId());
        return reservas.findById(id).orElseThrow();
    }

    /** Marca que el paciente asistió: alimenta el historial de participación y los reportes. */
    @Transactional
    @PutMapping("/{id}/atender")
    public Reserva atender(@PathVariable Long id, HttpServletRequest req) {
        Reserva r = reservas.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "La reserva no existe"));
        if (!"RESERVADA".equals(r.getEstado()))
            throw error(HttpStatus.BAD_REQUEST, "Solo se atienden reservas activas");
        r.setEstado("ATENDIDA");
        r.setFechaAtencion(LocalDateTime.now(LIMA));
        bitacora.registrar(actual(req), "Marcó asistencia del DNI " + r.getBeneficiarioDni(), "Reserva");
        return reservas.save(r);
    }
}
