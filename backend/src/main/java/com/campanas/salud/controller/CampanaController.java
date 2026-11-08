package com.campanas.salud.controller;

import com.campanas.salud.model.Campana;
import com.campanas.salud.model.Turno;
import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.CampanaRepository;
import com.campanas.salud.repository.EstablecimientoRepository;
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
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** API REST de campañas: listar con filtros, crear, editar, cerrar y eliminar (RF-09, RF-10, RF-11, RF-16). */
@RestController
@RequestMapping("/api/campanas")
public class CampanaController {
    private static final Set<String> ESTADOS = Set.of("ACTIVA", "PROGRAMADA", "CERRADA");

    private final CampanaRepository repo;
    private final EstablecimientoRepository establecimientos;
    private final TurnoRepository turnos;
    private final ReservaRepository reservas;
    private final Bitacora bitacora;

    public CampanaController(CampanaRepository repo, EstablecimientoRepository establecimientos,
                             TurnoRepository turnos, ReservaRepository reservas, Bitacora bitacora) {
        this.repo = repo;
        this.establecimientos = establecimientos;
        this.turnos = turnos;
        this.reservas = reservas;
        this.bitacora = bitacora;
    }

    private static ResponseStatusException error(HttpStatus e, String m) { return new ResponseStatusException(e, m); }

    /**
     * Lista campañas. Filtros opcionales (RF-10):
     * /api/campanas?vacuna=sarampion&distrito=miraflores&estado=ACTIVA&fecha=2026-11-20
     */
    @GetMapping
    public List<Campana> listar(@RequestParam(defaultValue = "") String vacuna,
                                @RequestParam(defaultValue = "") String distrito,
                                @RequestParam(defaultValue = "") String estado,
                                @RequestParam(defaultValue = "") String fecha) {
        LocalDate dia;
        try { dia = fecha.isBlank() ? null : LocalDate.parse(fecha); }
        catch (DateTimeParseException e) { throw error(HttpStatus.BAD_REQUEST, "La fecha debe tener el formato AAAA-MM-DD"); }

        return repo.findByVacunaContainingIgnoreCaseAndEstablecimientoDistritoContainingIgnoreCase(
                        vacuna.trim(), distrito.trim()).stream()
                .filter(c -> estado.isBlank() || estado.equalsIgnoreCase(c.getEstado()))
                .filter(c -> dia == null || (c.getFechaInicio() != null && c.getFechaFin() != null
                        && !dia.isBefore(c.getFechaInicio()) && !dia.isAfter(c.getFechaFin())))
                .toList();
    }

    /** Cupos libres de cada campaña: { "1": 40, "2": 0 } para mostrar «quedan N cupos» (RF-01, RF-09). */
    @GetMapping("/cupos")
    public Map<Long, Integer> cupos() {
        Map<Long, Integer> mapa = new HashMap<>();
        for (Turno t : turnos.findAll())
            mapa.merge(t.getCampana().getId(), t.getCuposDisponibles(), Integer::sum);
        return mapa;
    }

    /** Detalle de una campaña (RF-11). */
    @GetMapping("/{id}")
    public Campana obtener(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Campana crear(@RequestBody Campana c, HttpServletRequest req) {
        c.setId(null);
        validar(c);
        Campana guardada = repo.save(c);
        bitacora.registrar(admin(req), "Creó la campaña «" + c.getVacuna() + "»", "Campaña");
        return guardada;
    }

    @PutMapping("/{id}")
    public Campana editar(@PathVariable Long id, @RequestBody Campana c, HttpServletRequest req) {
        if (!repo.existsById(id)) throw error(HttpStatus.NOT_FOUND, "Campaña no encontrada");
        c.setId(id);
        validar(c);
        Campana guardada = repo.save(c);
        bitacora.registrar(admin(req), "Editó la campaña «" + c.getVacuna() + "» (" + c.getEstado() + ")", "Campaña");
        return guardada;
    }

    /** Elimina la campaña y sus turnos, salvo que ya tenga reservas: en ese caso hay que cerrarla. */
    @Transactional
    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id, HttpServletRequest req) {
        Campana c = repo.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
        if (reservas.existsByTurnoCampanaId(id))
            throw error(HttpStatus.CONFLICT, "La campaña ya tiene reservas. Ciérrala en lugar de eliminarla.");
        turnos.deleteAll(turnos.findByCampanaId(id));
        repo.delete(c);
        bitacora.registrar(admin(req), "Eliminó la campaña «" + c.getVacuna() + "»", "Campaña");
    }

    private Usuario admin(HttpServletRequest req) { return (Usuario) req.getAttribute(SeguridadInterceptor.USUARIO); }

    /** Validación en el servidor (RF-08/RF-16): no se confía en lo que valide el navegador. */
    private void validar(Campana c) {
        if (c.getVacuna() == null || c.getVacuna().isBlank() || c.getVacuna().length() > 100)
            throw error(HttpStatus.BAD_REQUEST, "Escribe el nombre de la vacuna (máximo 100 caracteres)");
        if (c.getFechaInicio() == null || c.getFechaFin() == null)
            throw error(HttpStatus.BAD_REQUEST, "Indica la fecha de inicio y la de fin");
        if (c.getFechaFin().isBefore(c.getFechaInicio()))
            throw error(HttpStatus.BAD_REQUEST, "La fecha de fin no puede ser anterior a la de inicio");
        if (c.getEstado() == null || c.getEstado().isBlank()) c.setEstado("ACTIVA");
        if (!ESTADOS.contains(c.getEstado()))
            throw error(HttpStatus.BAD_REQUEST, "El estado debe ser ACTIVA, PROGRAMADA o CERRADA");
        if (c.getDescripcion() != null && c.getDescripcion().length() > 1000
                || c.getRequisitos() != null && c.getRequisitos().length() > 1000
                || c.getFuente() != null && c.getFuente().length() > 200)
            throw error(HttpStatus.BAD_REQUEST, "Alguno de los textos es demasiado largo");
        if (c.getEstablecimiento() == null || c.getEstablecimiento().getId() == null)
            throw error(HttpStatus.BAD_REQUEST, "Elige el establecimiento de salud");
        c.setEstablecimiento(establecimientos.findById(c.getEstablecimiento().getId()).orElseThrow(() ->
                error(HttpStatus.BAD_REQUEST, "El establecimiento no existe")));
    }
}

