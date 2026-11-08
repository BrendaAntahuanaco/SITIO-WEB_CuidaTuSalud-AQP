package com.campanas.salud.controller;

import com.campanas.salud.model.Aviso;
import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.AvisoRepository;
import com.campanas.salud.seguridad.AutenticacionInterceptor;
import com.campanas.salud.util.BitacoraService;
import com.campanas.salud.util.Texto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** Noticias y avisos de la página principal (RF-02). Cada aviso debe indicar su fuente. */
@RestController
@RequestMapping("/api/avisos")
public class AvisoController {
    public record AvisoRequest(String titulo, String contenido, String fuente) { }

    private final AvisoRepository repo;
    private final BitacoraService bitacora;

    public AvisoController(AvisoRepository repo, BitacoraService bitacora) {
        this.repo = repo;
        this.bitacora = bitacora;
    }

    @GetMapping
    public List<Aviso> listar() { return repo.findAllByOrderByFechaPublicacionDesc(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Aviso publicar(@RequestBody AvisoRequest r, HttpServletRequest req) {
        if (Texto.vacio(r.titulo()) || Texto.vacio(r.contenido()) || Texto.vacio(r.fuente()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Título, contenido y fuente son obligatorios");
        if (r.titulo().length() > 150 || r.contenido().length() > 1000 || r.fuente().length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alguno de los textos es demasiado largo");
        Usuario admin = AutenticacionInterceptor.actual(req);
        Aviso a = new Aviso();
        a.setUsuarioAdminId(admin.getId());
        a.setTitulo(r.titulo().trim());
        a.setContenido(r.contenido().trim());
        a.setFuente(r.fuente().trim());
        Aviso guardado = repo.save(a);
        bitacora.registrar(admin, "Publicó un aviso", guardado.getTitulo());
        return guardado;
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id, HttpServletRequest req) {
        Aviso a = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aviso no encontrado"));
        repo.delete(a);
        bitacora.registrar(AutenticacionInterceptor.actual(req), "Eliminó un aviso", a.getTitulo());
    }
}
