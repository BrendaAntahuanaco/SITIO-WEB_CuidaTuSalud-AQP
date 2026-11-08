package com.campanas.salud.controller;

import com.campanas.salud.model.Establecimiento;
import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.CampanaRepository;
import com.campanas.salud.repository.EstablecimientoRepository;
import com.campanas.salud.seguridad.Bitacora;
import com.campanas.salud.seguridad.SeguridadInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** CRUD de establecimientos de salud. Consultar es público; crear, editar y borrar es de administrador. */
@RestController
@RequestMapping("/api/establecimientos")
public class EstablecimientoController {
    private final EstablecimientoRepository repo;
    private final CampanaRepository campanas;
    private final Bitacora bitacora;

    public EstablecimientoController(EstablecimientoRepository repo, CampanaRepository campanas, Bitacora bitacora) {
        this.repo = repo;
        this.campanas = campanas;
        this.bitacora = bitacora;
    }

    private static ResponseStatusException error(HttpStatus e, String m) { return new ResponseStatusException(e, m); }
    private Usuario admin(HttpServletRequest req) { return (Usuario) req.getAttribute(SeguridadInterceptor.USUARIO); }

    @GetMapping
    public List<Establecimiento> listar() {
        return repo.findAll().stream()
                .sorted(java.util.Comparator.comparing(Establecimiento::getNombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Establecimiento crear(@RequestBody Establecimiento e, HttpServletRequest req) {
        validar(e);
        e.setId(null);
        Establecimiento g = repo.save(e);
        bitacora.registrar(admin(req), "Creó el establecimiento «" + e.getNombre() + "»", "Establecimiento");
        return g;
    }

    @PutMapping("/{id}")
    public Establecimiento editar(@PathVariable Long id, @RequestBody Establecimiento e, HttpServletRequest req) {
        if (!repo.existsById(id)) throw error(HttpStatus.NOT_FOUND, "Establecimiento no encontrado");
        validar(e);
        e.setId(id);
        Establecimiento g = repo.save(e);
        bitacora.registrar(admin(req), "Editó el establecimiento «" + e.getNombre() + "»", "Establecimiento");
        return g;
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id, HttpServletRequest req) {
        Establecimiento e = repo.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Establecimiento no encontrado"));
        if (campanas.existsByEstablecimientoId(id))
            throw error(HttpStatus.CONFLICT, "Tiene campañas asociadas; elimínalas o cámbialas de establecimiento primero");
        repo.delete(e);
        bitacora.registrar(admin(req), "Eliminó el establecimiento «" + e.getNombre() + "»", "Establecimiento");
    }

    private void validar(Establecimiento e) {
        if (e.getNombre() == null || e.getNombre().isBlank() || e.getNombre().length() > 150
                || e.getDistrito() == null || e.getDistrito().isBlank() || e.getDistrito().length() > 100
                || e.getDireccion() == null || e.getDireccion().isBlank() || e.getDireccion().length() > 200)
            throw error(HttpStatus.BAD_REQUEST, "Completa nombre, distrito y dirección (sin pasarte de largo)");
    }
}
