package com.campanas.salud.seguridad;

import com.campanas.salud.model.RegistroAccion;
import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.RegistroAccionRepository;
import org.springframework.stereotype.Component;

/** Guarda quién hizo qué en el panel de administración. */
@Component
public class Bitacora {
    private final RegistroAccionRepository repo;

    public Bitacora(RegistroAccionRepository repo) { this.repo = repo; }

    public void registrar(Usuario u, String accion, String entidad) {
        RegistroAccion r = new RegistroAccion();
        r.setUsuarioId(u.getId());
        r.setUsuarioNombre(u.getNombres() + " " + u.getApellidos());
        r.setAccion(accion.length() > 200 ? accion.substring(0, 200) : accion);
        r.setEntidadAfectada(entidad);
        repo.save(r);
    }
}
