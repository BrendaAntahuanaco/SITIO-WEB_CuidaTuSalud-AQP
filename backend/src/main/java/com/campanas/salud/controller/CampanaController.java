package com.campanas.salud.controller;

import com.campanas.salud.model.Campana;
import com.campanas.salud.repository.CampanaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** API REST de campañas: listar con filtros, detalle, crear, editar y eliminar (RF-09, RF-10, RF-11, RF-16). */
@RestController
@RequestMapping("/api/campanas")
public class CampanaController {
    private final CampanaRepository repo;

    public CampanaController(CampanaRepository repo) { this.repo = repo; }

    /** Lista campañas. Filtros opcionales: /api/campanas?vacuna=sarampion&distrito=miraflores */
    @GetMapping
    public List<Campana> listar(@RequestParam(defaultValue = "") String vacuna,
                                @RequestParam(defaultValue = "") String distrito) {
        return repo.findByVacunaContainingIgnoreCaseAndEstablecimientoDistritoContainingIgnoreCase(
                vacuna, distrito);
    }

    /** Detalle de una campaña (RF-11). */
    @GetMapping("/{id}")
    public Campana obtener(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
    }

    @PostMapping
    public Campana crear(@RequestBody Campana c) {
        c.setId(null);
        return repo.save(c);
    }

    @PutMapping("/{id}")
    public Campana editar(@PathVariable Long id, @RequestBody Campana c) {
        obtener(id); // lanza 404 si no existe
        c.setId(id);
        return repo.save(c);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        obtener(id);
        repo.deleteById(id);
    }
}
