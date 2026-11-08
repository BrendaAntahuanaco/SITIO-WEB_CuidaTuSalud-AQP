package com.campanas.salud.controller;

import com.campanas.salud.model.Turno;
import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.CampanaRepository;
import com.campanas.salud.repository.ReservaRepository;
import com.campanas.salud.repository.TurnoRepository;
import com.campanas.salud.seguridad.Bitacora;
import com.campanas.salud.seguridad.SeguridadInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** Turnos de una campaña y gestión del aforo por horario (RF-12, RF-13, RF-18). */
@RestController
@RequestMapping("/api/turnos")
public class TurnoController {
    private final TurnoRepository turnos;
    private final CampanaRepository campanas;
    private final ReservaRepository reservas;
    private final Bitacora bitacora;

    public TurnoController(TurnoRepository turnos, CampanaRepository campanas,
                           ReservaRepository reservas, Bitacora bitacora) {
        this.turnos = turnos;
        this.campanas = campanas;
        this.reservas = reservas;
        this.bitacora = bitacora;
    }

    private static ResponseStatusException error(HttpStatus e, String m) { return new ResponseStatusException(e, m); }
    private Usuario admin(HttpServletRequest req) { return (Usuario) req.getAttribute(SeguridadInterceptor.USUARIO); }

    /** Turnos de una campaña: GET /api/turnos?campanaId=1 */
    @GetMapping
    public List<Turno> listar(@RequestParam Long campanaId) {
        return turnos.findByCampanaId(campanaId).stream()
                .sorted(java.util.Comparator.comparing(Turno::getFecha).thenComparing(Turno::getHoraInicio))
                .toList();
    }

    /** Crea un turno con su aforo (RF-18): POST /api/turnos?campanaId=1 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Turno crear(@RequestParam Long campanaId, @RequestBody Turno t, HttpServletRequest req) {
        validar(t);
        t.setId(null);
        t.setCampana(campanas.findById(campanaId).orElseThrow(() ->
                error(HttpStatus.NOT_FOUND, "Campaña no encontrada")));
        t.setCuposDisponibles(t.getCuposTotal());
        Turno guardado = turnos.save(t);
        bitacora.registrar(admin(req), "Creó un turno de " + t.getCuposTotal() + " cupos en «"
                + t.getCampana().getVacuna() + "»", "Turno");
        return guardado;
    }

    /** Cambia el horario o el aforo. El aforo nuevo no puede ser menor que los cupos ya reservados. */
    @Transactional
    @PutMapping("/{id}")
    public Turno editar(@PathVariable Long id, @RequestBody Turno nuevo, HttpServletRequest req) {
        Turno t = turnos.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "El turno no existe"));
        validar(nuevo);
        long ocupados = reservas.countByTurnoIdAndEstadoNot(id, "CANCELADA");
        if (nuevo.getCuposTotal() < ocupados)
            throw error(HttpStatus.BAD_REQUEST, "Ya hay " + ocupados + " cupos reservados; el aforo no puede ser menor");
        t.setFecha(nuevo.getFecha());
        t.setHoraInicio(nuevo.getHoraInicio());
        t.setHoraFin(nuevo.getHoraFin());
        t.setCuposTotal(nuevo.getCuposTotal());
        t.setCuposDisponibles(nuevo.getCuposTotal() - (int) ocupados);
        Turno guardado = turnos.save(t);
        bitacora.registrar(admin(req), "Actualizó el aforo de un turno a " + t.getCuposTotal() + " cupos", "Turno");
        return guardado;
    }

    @Transactional
    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id, HttpServletRequest req) {
        if (!turnos.existsById(id)) throw error(HttpStatus.NOT_FOUND, "El turno no existe");
        if (reservas.existsByTurnoId(id))
            throw error(HttpStatus.CONFLICT, "El turno ya tiene reservas; no se puede eliminar");
        turnos.deleteById(id);
        bitacora.registrar(admin(req), "Eliminó un turno", "Turno");
    }

    private void validar(Turno t) {
        if (t.getFecha() == null || t.getHoraInicio() == null || t.getHoraFin() == null)
            throw error(HttpStatus.BAD_REQUEST, "Indica la fecha y las horas del turno");
        if (!t.getHoraFin().isAfter(t.getHoraInicio()))
            throw error(HttpStatus.BAD_REQUEST, "La hora de fin debe ser posterior a la de inicio");
        if (t.getCuposTotal() <= 0 || t.getCuposTotal() > 5000)
            throw error(HttpStatus.BAD_REQUEST, "Los cupos deben estar entre 1 y 5000");
    }
}
