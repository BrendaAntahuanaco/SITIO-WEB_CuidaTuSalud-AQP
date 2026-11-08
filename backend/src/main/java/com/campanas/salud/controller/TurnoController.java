package com.campanas.salud.controller;

import com.campanas.salud.model.Campana;
import com.campanas.salud.model.Turno;
import com.campanas.salud.repository.CampanaRepository;
import com.campanas.salud.repository.ReservaRepository;
import com.campanas.salud.repository.TurnoRepository;
import com.campanas.salud.seguridad.AutenticacionInterceptor;
import com.campanas.salud.util.BitacoraService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Turnos y aforo de cada campaña (RF-13, RF-18). Leer es público; crear, editar y borrar es del administrador. */
@RestController
@RequestMapping("/api/turnos")
public class TurnoController {
    private static final int AFORO_MAXIMO = 5000;

    public record TurnoRequest(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, Integer cuposTotal) { }

    private final TurnoRepository turnos;
    private final CampanaRepository campanas;
    private final ReservaRepository reservas;
    private final BitacoraService bitacora;

    public TurnoController(TurnoRepository turnos, CampanaRepository campanas, ReservaRepository reservas,
                           BitacoraService bitacora) {
        this.turnos = turnos;
        this.campanas = campanas;
        this.reservas = reservas;
        this.bitacora = bitacora;
    }

    private ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }

    /** Turnos de una campaña: GET /api/turnos?campanaId=1 */
    @GetMapping
    public List<Turno> listar(@RequestParam Long campanaId) {
        return turnos.findByCampanaIdOrderByFechaAscHoraInicioAsc(campanaId);
    }

    /** Crea un turno con su aforo (RF-18): POST /api/turnos?campanaId=1 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Turno crear(@RequestParam Long campanaId, @RequestBody TurnoRequest r, HttpServletRequest req) {
        Campana c = campanas.findById(campanaId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
        validar(r);
        Turno t = new Turno();
        t.setCampana(c);
        t.setFecha(r.fecha());
        t.setHoraInicio(r.horaInicio());
        t.setHoraFin(r.horaFin());
        t.setCuposTotal(r.cuposTotal());
        t.setCuposDisponibles(r.cuposTotal());
        Turno guardado = turnos.save(t);
        bitacora.registrar(AutenticacionInterceptor.actual(req), "Creó un turno", c.getVacuna() + " " + r.fecha());
        return guardado;
    }

    /** Cambia fecha, horario o aforo. Los cupos ya reservados se respetan. */
    @PutMapping("/{id}")
    public Turno editar(@PathVariable Long id, @RequestBody TurnoRequest r, HttpServletRequest req) {
        Turno t = turnos.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Turno no encontrado"));
        validar(r);
        int ocupados = t.getCuposTotal() - t.getCuposDisponibles();
        if (r.cuposTotal() < ocupados)
            throw error(HttpStatus.CONFLICT, "Ya hay " + ocupados + " cupos reservados: el aforo no puede ser menor.");
        t.setFecha(r.fecha());
        t.setHoraInicio(r.horaInicio());
        t.setHoraFin(r.horaFin());
        t.setCuposTotal(r.cuposTotal());
        t.setCuposDisponibles(r.cuposTotal() - ocupados);
        Turno guardado = turnos.save(t);
        bitacora.registrar(AutenticacionInterceptor.actual(req), "Editó un turno", t.getCampana().getVacuna() + " " + r.fecha());
        return guardado;
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id, HttpServletRequest req) {
        Turno t = turnos.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Turno no encontrado"));
        if (reservas.existsByTurnoId(id))
            throw error(HttpStatus.CONFLICT, "Este turno ya tiene reservas y no se puede eliminar. Baja el aforo si hace falta.");
        turnos.delete(t);
        bitacora.registrar(AutenticacionInterceptor.actual(req), "Eliminó un turno", t.getCampana().getVacuna() + " " + t.getFecha());
    }

    private void validar(TurnoRequest r) {
        if (r.fecha() == null || r.horaInicio() == null || r.horaFin() == null)
            throw error(HttpStatus.BAD_REQUEST, "Indica la fecha y las horas del turno");
        if (!r.horaFin().isAfter(r.horaInicio()))
            throw error(HttpStatus.BAD_REQUEST, "La hora de fin debe ser posterior a la de inicio");
        if (r.cuposTotal() == null || r.cuposTotal() <= 0 || r.cuposTotal() > AFORO_MAXIMO)
            throw error(HttpStatus.BAD_REQUEST, "Los cupos deben estar entre 1 y " + AFORO_MAXIMO);
    }
}
