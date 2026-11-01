package com.campanas.salud.controller;

import com.campanas.salud.model.Reserva;
import com.campanas.salud.model.Turno;
import com.campanas.salud.repository.CampanaRepository;
import com.campanas.salud.repository.ReservaRepository;
import com.campanas.salud.repository.TurnoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** Turnos y reservas con control de cupos (RF-12, RF-13, RF-14, RF-18). */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class ReservaController {

    public record ReservaRequest(Long usuarioId, Long turnoId,
                                 String beneficiarioNombre, String beneficiarioDni) { }

    private final TurnoRepository turnos;
    private final ReservaRepository reservas;
    private final CampanaRepository campanas;

    public ReservaController(TurnoRepository turnos, ReservaRepository reservas, CampanaRepository campanas) {
        this.turnos = turnos;
        this.reservas = reservas;
        this.campanas = campanas;
    }

    /** Turnos de una campaña: GET /api/turnos?campanaId=1 */
    @GetMapping("/turnos")
    public List<Turno> turnos(@RequestParam Long campanaId) {
        return turnos.findByCampanaId(campanaId);
    }

    /** Crea un turno con su aforo (RF-18): POST /api/turnos?campanaId=1 */
    @PostMapping("/turnos")
    @ResponseStatus(HttpStatus.CREATED)
    public Turno crearTurno(@RequestParam Long campanaId, @RequestBody Turno t) {
        if (t.getCuposTotal() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los cupos deben ser mayores a 0");
        t.setId(null);
        t.setCampana(campanas.findById(campanaId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaña no encontrada")));
        t.setCuposDisponibles(t.getCuposTotal());
        return turnos.save(t);
    }

    /** Reserva un turno y descuenta un cupo. Si no quedan, responde 409 (RF-13). */
    @Transactional
    @PostMapping("/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    public Reserva reservar(@RequestBody ReservaRequest r) {
        Turno t = turnos.findById(r.turnoId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "El turno no existe"));
        if (r.beneficiarioDni() == null || !r.beneficiarioDni().matches("\\d{8}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos");
        if (t.getCuposDisponibles() <= 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No quedan cupos en este turno");

        t.setCuposDisponibles(t.getCuposDisponibles() - 1);
        turnos.save(t);

        Reserva reserva = new Reserva();
        reserva.setUsuarioId(r.usuarioId());
        reserva.setTurno(t);
        reserva.setBeneficiarioNombre(r.beneficiarioNombre());
        reserva.setBeneficiarioDni(r.beneficiarioDni());
        return reservas.save(reserva);
    }

    /** Historial de reservas de un ciudadano: GET /api/reservas?usuarioId=1 */
    @GetMapping("/reservas")
    public List<Reserva> delUsuario(@RequestParam Long usuarioId) {
        return reservas.findByUsuarioId(usuarioId);
    }

    /** Cancela una reserva y devuelve el cupo (RF-14). */
    @Transactional
    @DeleteMapping("/reservas/{id}")
    public Reserva cancelar(@PathVariable Long id) {
        Reserva r = reservas.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "La reserva no existe"));
        if (!"RESERVADA".equals(r.getEstado()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se cancelan reservas activas");
        r.setEstado("CANCELADA");
        Turno t = r.getTurno();
        t.setCuposDisponibles(t.getCuposDisponibles() + 1);
        turnos.save(t);
        return reservas.save(r);
    }
}
