package com.campanas.salud.controller;

import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.UsuarioRepository;
import com.campanas.salud.seguridad.AutenticacionInterceptor;
import com.campanas.salud.util.BitacoraService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Set;

/** Gestión de usuarios (RF-17): consultar, cambiar rol y dar de baja o reactivar cuentas. Solo administradores. */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {
    public record CambioUsuario(String rol, String estado) { }

    private final UsuarioRepository repo;
    private final BitacoraService bitacora;

    public UsuarioController(UsuarioRepository repo, BitacoraService bitacora) {
        this.repo = repo;
        this.bitacora = bitacora;
    }

    @GetMapping
    public List<Usuario> listar() { return repo.findAllByOrderByIdAsc(); }

    @PutMapping("/{id}")
    public Usuario cambiar(@PathVariable Long id, @RequestBody CambioUsuario c, HttpServletRequest req) {
        Usuario admin = AutenticacionInterceptor.actual(req);
        Usuario u = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (u.getId().equals(admin.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes cambiar tu propio rol ni darte de baja.");
        if (c.rol() == null && c.estado() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se indicó ningún cambio");
        if (c.rol() != null) {
            if (!Set.of("CIUDADANO", "ADMIN").contains(c.rol()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol no válido");
            u.setRol(c.rol());
        }
        if (c.estado() != null) {
            if (!Set.of("ACTIVO", "BAJA").contains(c.estado()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado no válido");
            u.setEstado(c.estado());
        }
        Usuario guardado = repo.save(u);
        bitacora.registrar(admin, "Cambió una cuenta (rol " + guardado.getRol() + ", " + guardado.getEstado() + ")", guardado.getCorreo());
        return guardado;
    }
}
