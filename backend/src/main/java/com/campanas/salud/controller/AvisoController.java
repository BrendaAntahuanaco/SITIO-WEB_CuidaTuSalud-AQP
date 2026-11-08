package com.campanas.salud.controller;

import com.campanas.salud.model.Aviso;
import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.AvisoRepository;
import com.campanas.salud.seguridad.Bitacora;
import com.campanas.salud.seguridad.SeguridadInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

/** Noticias y avisos de la página principal (RF-02). Solo el personal autorizado publica. */
@RestController
@RequestMapping("/api/avisos")
public class AvisoController {
    private final AvisoRepository repo;
    private final Bitacora bitacora;

    public AvisoController(AvisoRepository repo, Bitacora bitacora) {
        this.repo = repo;
        this.bitacora = bitacora;
    }

    private static ResponseStatusException error(HttpStatus e, String m) { return new ResponseStatusException(e, m); }

    @GetMapping
    public List<Aviso> listar() { return repo.findAllByOrderByFechaPublicacionDesc(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Aviso publicar(@RequestBody Aviso a, HttpServletRequest req) {
        if (a.getTitulo() == null || a.getTitulo().isBlank() || a.getTitulo().length() > 150
                || a.getContenido() == null || a.getContenido().isBlank() || a.getContenido().length() > 1000
                || a.getFuente() == null || a.getFuente().isBlank() || a.getFuente().length() > 200)
            throw error(HttpStatus.BAD_REQUEST, "Completa título, contenido y fuente del aviso");
        a.setId(null);
        a.setFechaPublicacion(LocalDateTime.now());
        Aviso g = repo.save(a);
        bitacora.registrar((Usuario) req.getAttribute(SeguridadInterceptor.USUARIO),
                "Publicó el aviso «" + a.getTitulo() + "»", "Aviso");
        return g;
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id, HttpServletRequest req) {
        Aviso a = repo.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Aviso no encontrado"));
        repo.delete(a);
        bitacora.registrar((Usuario) req.getAttribute(SeguridadInterceptor.USUARIO),
                "Eliminó el aviso «" + a.getTitulo() + "»", "Aviso");
    }
}
