package com.campanas.salud.controller;

import com.campanas.salud.model.Establecimiento;
import com.campanas.salud.repository.CampanaRepository;
import com.campanas.salud.repository.EstablecimientoRepository;
import com.campanas.salud.seguridad.AutenticacionInterceptor;
import com.campanas.salud.util.BitacoraService;
import com.campanas.salud.util.Texto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** Centros de salud donde se hacen las campañas (RF-16). Leer es público; escribir es solo del administrador. */
@RestController
@RequestMapping("/api/establecimientos")
public class EstablecimientoController {
    public record EstablecimientoRequest(String nombre, String distrito, String direccion) { }

    private final EstablecimientoRepository repo;
    private final CampanaRepository campanas;
    private final BitacoraService bitacora;

    public EstablecimientoController(EstablecimientoRepository repo, CampanaRepository campanas, BitacoraService bitacora) {
        this.repo = repo;
        this.campanas = campanas;
        this.bitacora = bitacora;
    }

    private ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }

    @GetMapping
    public List<Establecimiento> listar() {
        return repo.findAll().stream()
                .sorted((a, b) -> Texto.normalizar(a.getNombre()).compareTo(Texto.normalizar(b.getNombre())))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Establecimiento crear(@RequestBody EstablecimientoRequest r, HttpServletRequest req) {
        Establecimiento e = new Establecimiento();
        aplicar(e, r);
        Establecimiento guardado = repo.save(e);
        bitacora.registrar(AutenticacionInterceptor.actual(req), "Creó un establecimiento", guardado.getNombre());
        return guardado;
    }

    @PutMapping("/{id}")
    public Establecimiento editar(@PathVariable Long id, @RequestBody EstablecimientoRequest r, HttpServletRequest req) {
        Establecimiento e = repo.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Establecimiento no encontrado"));
        aplicar(e, r);
        Establecimiento guardado = repo.save(e);
        bitacora.registrar(AutenticacionInterceptor.actual(req), "Editó un establecimiento", guardado.getNombre());
        return guardado;
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id, HttpServletRequest req) {
        Establecimiento e = repo.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Establecimiento no encontrado"));
        if (campanas.existsByEstablecimientoId(id))
            throw error(HttpStatus.CONFLICT, "Este establecimiento tiene campañas. Elimina o cambia esas campañas primero.");
        repo.delete(e);
        bitacora.registrar(AutenticacionInterceptor.actual(req), "Eliminó un establecimiento", e.getNombre());
    }

    private void aplicar(Establecimiento e, EstablecimientoRequest r) {
        if (Texto.vacio(r.nombre()) || Texto.vacio(r.distrito()) || Texto.vacio(r.direccion()))
            throw error(HttpStatus.BAD_REQUEST, "Nombre, distrito y dirección son obligatorios");
        if (r.nombre().length() > 150 || r.distrito().length() > 100 || r.direccion().length() > 200)
            throw error(HttpStatus.BAD_REQUEST, "Alguno de los datos es demasiado largo");
        e.setNombre(r.nombre().trim());
        e.setDistrito(r.distrito().trim());
        e.setDireccion(r.direccion().trim());
    }
}
