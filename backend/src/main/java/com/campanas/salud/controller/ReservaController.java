package com.campanas.salud.controller;

import com.campanas.salud.model.Reserva;
import com.campanas.salud.model.Turno;
import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.ReservaRepository;
import com.campanas.salud.repository.TurnoRepository;
import com.campanas.salud.seguridad.AutenticacionInterceptor;
import com.campanas.salud.util.BitacoraService;
import com.campanas.salud.util.Texto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

/** Reservas con control de cupos y control de asistencia (RF-12, RF-13, RF-14, RF-15). */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    /** El usuario NO viaja aquí: sale del token, para que nadie reserve a nombre de otra cuenta. */
    public record ReservaRequest(Long turnoId, String beneficiarioNombre, String beneficiarioDni) { }

    private final TurnoRepository turnos;
    private final ReservaRepository reservas;
    private final BitacoraService bitacora;

    public ReservaController(TurnoRepository turnos, ReservaRepository reservas, BitacoraService bitacora) {
        this.turnos = turnos;
        this.reservas = reservas;
        this.bitacora = bitacora;
    }

    private ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }

    /** Reserva un turno y descuenta un cupo. Si no quedan, responde 409 (RF-13). */
    @Transactional
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Reserva reservar(@RequestBody ReservaRequest r, HttpServletRequest req) {
        Usuario usuario = AutenticacionInterceptor.actual(req);
        if (r.turnoId() == null) throw error(HttpStatus.BAD_REQUEST, "Elige un horario");
        Turno t = turnos.findById(r.turnoId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "El turno no existe"));

        if (Texto.vacio(r.beneficiarioNombre()) || r.beneficiarioNombre().trim().length() > 150)
            throw error(HttpStatus.BAD_REQUEST, "Escribe el nombre de quien se vacunará");
        if (r.beneficiarioDni() == null || !r.beneficiarioDni().matches("\\d{8}"))
            throw error(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos");
        if ("CERRADA".equals(t.getCampana().getEstado()))
            throw error(HttpStatus.CONFLICT, "Esta campaña ya está cerrada");
        if (t.getFecha().isBefore(Texto.hoy()))
            throw error(HttpStatus.CONFLICT, "Ese turno ya pasó. Elige otro horario.");
        if (reservas.existsByTurnoCampanaIdAndBeneficiarioDniAndEstado(t.getCampana().getId(), r.beneficiarioDni(), "RESERVADA"))
            throw error(HttpStatus.CONFLICT, "Ya hay una reserva activa para ese DNI en esta campaña.");

        // Descuento atómico: dos personas a la vez no pueden quedarse con el último cupo
        if (turnos.descontarCupo(t.getId()) == 0)
            throw error(HttpStatus.CONFLICT, "No quedan cupos en este turno");

        Reserva reserva = new Reserva();
        reserva.setUsuarioId(usuario.getId());
        reserva.setTurno(turnos.findById(t.getId()).orElseThrow()); // turno ya con el cupo descontado
        reserva.setBeneficiarioNombre(r.beneficiarioNombre().trim());
        reserva.setBeneficiarioDni(r.beneficiarioDni());
        return reservas.save(reserva);
    }

    /** Historial de reservas del ciudadano que inició sesión: GET /api/reservas */
    @GetMapping
    public List<Reserva> misReservas(HttpServletRequest req) {
        return reservas.findByUsuarioIdOrderByFechaReservaDesc(AutenticacionInterceptor.actual(req).getId());
    }

    /** Cancela una reserva y devuelve el cupo (RF-14). Solo su dueño o un administrador. */
    @Transactional
    @DeleteMapping("/{id}")
    public Reserva cancelar(@PathVariable Long id, HttpServletRequest req) {
        Usuario usuario = AutenticacionInterceptor.actual(req);
        Reserva r = reservas.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "La reserva no existe"));
        boolean esDueno = usuario.getId().equals(r.getUsuarioId());
        if (!esDueno && !"ADMIN".equals(usuario.getRol()))
            throw error(HttpStatus.NOT_FOUND, "La reserva no existe"); // no se revela que existe
        if (!"RESERVADA".equals(r.getEstado()))
            throw error(HttpStatus.BAD_REQUEST, "Solo se pueden cancelar reservas activas");
        r.setEstado("CANCELADA");
        turnos.devolverCupo(r.getTurno().getId());
        return reservas.save(r);
    }

    /** Administrador: busca las reservas de un paciente por DNI. */
    @GetMapping("/buscar")
    public List<Reserva> buscar(@RequestParam String dni) {
        if (!dni.matches("\\d{8}")) throw error(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos");
        return reservas.findByBeneficiarioDniOrderByFechaReservaDesc(dni);
    }

    /** Administrador: marca que el paciente fue atendido (alimenta el historial y los reportes). */
    @Transactional
    @PutMapping("/{id}/atender")
    public Reserva atender(@PathVariable Long id, HttpServletRequest req) {
        Reserva r = reservas.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "La reserva no existe"));
        if (!"RESERVADA".equals(r.getEstado()))
            throw error(HttpStatus.BAD_REQUEST, "Solo se puede atender una reserva activa");
        r.setEstado("ATENDIDA");
        r.setFechaAtencion(LocalDateTime.now());
        Reserva guardada = reservas.save(r);
        bitacora.registrar(AutenticacionInterceptor.actual(req), "Registró asistencia", "Reserva AQP-" + String.format("%06d", id));
        return guardada;
    }
}
