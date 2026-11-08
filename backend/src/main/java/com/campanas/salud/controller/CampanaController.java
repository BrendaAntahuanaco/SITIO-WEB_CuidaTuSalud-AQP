package com.campanas.salud.controller;

import com.campanas.salud.model.Campana;
import com.campanas.salud.model.Establecimiento;
import com.campanas.salud.repository.CampanaRepository;
import com.campanas.salud.repository.EstablecimientoRepository;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/** API REST de campañas: listar con filtros, detalle, crear, editar y eliminar (RF-09, RF-10, RF-11, RF-16). */
@RestController
@RequestMapping("/api/campanas")
public class CampanaController {
    private static final Set<String> ESTADOS = Set.of("ACTIVA", "PROGRAMADA", "CERRADA");

    /** Datos que envía el panel de administración. */
    public record CampanaRequest(String vacuna, String descripcion, String requisitos, String fuente,
                                 LocalDate fechaInicio, LocalDate fechaFin, String estado,
                                 EstablecimientoRef establecimiento) { }
    public record EstablecimientoRef(Long id) { }

    private final CampanaRepository campanas;
    private final EstablecimientoRepository establecimientos;
    private final TurnoRepository turnos;
    private final ReservaRepository reservas;
    private final BitacoraService bitacora;

    public CampanaController(CampanaRepository campanas, EstablecimientoRepository establecimientos,
                             TurnoRepository turnos, ReservaRepository reservas, BitacoraService bitacora) {
        this.campanas = campanas;
        this.establecimientos = establecimientos;
        this.turnos = turnos;
        this.reservas = reservas;
        this.bitacora = bitacora;
    }

    private ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }

    /**
     * Lista campañas. Todos los filtros son opcionales y se ignoran si vienen vacíos:
     * /api/campanas?vacuna=sarampion&distrito=miraflores&fecha=2026-11-10&estado=ACTIVA
     */
    @GetMapping
    public List<Campana> listar(@RequestParam(required = false) String vacuna,
                                @RequestParam(required = false) String distrito,
                                @RequestParam(required = false) String fecha,
                                @RequestParam(required = false) String estado) {
        String v = Texto.normalizar(vacuna);
        String d = Texto.normalizar(distrito);
        LocalDate dia = null;
        if (!Texto.vacio(fecha)) {
            try { dia = LocalDate.parse(fecha.trim()); }
            catch (DateTimeParseException e) { throw error(HttpStatus.BAD_REQUEST, "La fecha del filtro no es válida"); }
        }
        String est = Texto.vacio(estado) ? "" : estado.trim().toUpperCase();
        if (!est.isEmpty() && !ESTADOS.contains(est)) throw error(HttpStatus.BAD_REQUEST, "El estado del filtro no es válido");

        final LocalDate diaFiltro = dia;
        return campanas.findAll().stream()
                .filter(c -> v.isEmpty() || Texto.normalizar(c.getVacuna()).contains(v))
                .filter(c -> d.isEmpty() || (c.getEstablecimiento() != null
                        && Texto.normalizar(c.getEstablecimiento().getDistrito()).contains(d)))
                .filter(c -> diaFiltro == null || (c.getFechaInicio() != null && c.getFechaFin() != null
                        && !diaFiltro.isBefore(c.getFechaInicio()) && !diaFiltro.isAfter(c.getFechaFin())))
                .filter(c -> est.isEmpty() || est.equals(c.getEstado()))
                .sorted(Comparator.comparing(Campana::getFechaInicio, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Cupos libres por campaña (solo turnos de hoy en adelante): {"1": 35, "2": 25}. */
    @GetMapping("/cupos")
    public Map<Long, Long> cupos() {
        Map<Long, Long> mapa = new HashMap<>();
        for (Object[] fila : turnos.cuposLibresDesde(Texto.hoy()))
            mapa.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        return mapa;
    }

    /** Detalle de una campaña (RF-11). Solo acepta ids numéricos, así "cupos" nunca se confunde con un id. */
    @GetMapping("/{id:\\d+}")
    public Campana obtener(@PathVariable Long id) {
        return campanas.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Campana crear(@RequestBody CampanaRequest r, HttpServletRequest req) {
        Campana c = new Campana();
        aplicar(c, r);
        Campana guardada = campanas.save(c);
        bitacora.registrar(AutenticacionInterceptor.actual(req), "Creó una campaña", guardada.getVacuna());
        return guardada;
    }

    @PutMapping("/{id:\\d+}")
    public Campana editar(@PathVariable Long id, @RequestBody CampanaRequest r, HttpServletRequest req) {
        Campana c = obtener(id);
        aplicar(c, r);
        Campana guardada = campanas.save(c);
        String accion = "CERRADA".equals(guardada.getEstado()) ? "Cerró o editó una campaña" : "Editó una campaña";
        bitacora.registrar(AutenticacionInterceptor.actual(req), accion, guardada.getVacuna());
        return guardada;
    }

    @Transactional
    @DeleteMapping("/{id:\\d+}")
    public void eliminar(@PathVariable Long id, HttpServletRequest req) {
        Campana c = obtener(id);
        if (reservas.existsByTurnoCampanaId(id))
            throw error(HttpStatus.CONFLICT,
                    "La campaña ya tiene reservas. Ciérrala en lugar de eliminarla para conservar el historial.");
        turnos.deleteAll(turnos.findByCampanaIdOrderByFechaAscHoraInicioAsc(id));
        campanas.delete(c);
        bitacora.registrar(AutenticacionInterceptor.actual(req), "Eliminó una campaña", c.getVacuna());
    }

    /** Valida los datos del formulario y los copia a la campaña. */
    private void aplicar(Campana c, CampanaRequest r) {
        if (Texto.vacio(r.vacuna())) throw error(HttpStatus.BAD_REQUEST, "Escribe la vacuna o servicio");
        if (r.vacuna().trim().length() > 100) throw error(HttpStatus.BAD_REQUEST, "El nombre de la vacuna es demasiado largo");
        if (r.fechaInicio() == null || r.fechaFin() == null)
            throw error(HttpStatus.BAD_REQUEST, "Indica la fecha de inicio y la de fin");
        if (r.fechaFin().isBefore(r.fechaInicio()))
            throw error(HttpStatus.BAD_REQUEST, "La fecha de fin no puede ser anterior a la de inicio");
        String estado = Texto.vacio(r.estado()) ? "ACTIVA" : r.estado().trim().toUpperCase();
        if (!ESTADOS.contains(estado)) throw error(HttpStatus.BAD_REQUEST, "El estado debe ser ACTIVA, PROGRAMADA o CERRADA");
        if (r.establecimiento() == null || r.establecimiento().id() == null)
            throw error(HttpStatus.BAD_REQUEST, "Elige un establecimiento");
        Establecimiento lugar = establecimientos.findById(r.establecimiento().id())
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "El establecimiento elegido no existe"));
        if (r.descripcion() != null && r.descripcion().length() > 1000
                || r.requisitos() != null && r.requisitos().length() > 1000)
            throw error(HttpStatus.BAD_REQUEST, "La descripción y los requisitos admiten hasta 1000 caracteres");
        if (r.fuente() != null && r.fuente().length() > 200)
            throw error(HttpStatus.BAD_REQUEST, "La fuente admite hasta 200 caracteres");

        c.setVacuna(r.vacuna().trim());
        c.setDescripcion(Texto.limpio(r.descripcion()));
        c.setRequisitos(Texto.limpio(r.requisitos()));
        c.setFuente(Texto.limpio(r.fuente()));
        c.setFechaInicio(r.fechaInicio());
        c.setFechaFin(r.fechaFin());
        c.setEstado(estado);
        c.setEstablecimiento(lugar);
    }
}
