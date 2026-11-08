package com.campanas.salud.util;

import com.campanas.salud.model.RegistroAccion;
import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.RegistroAccionRepository;
import org.springframework.stereotype.Service;

/** Guarda quién hizo qué en el panel de administración. */
@Service
public class BitacoraService {
    private final RegistroAccionRepository repo;

    public BitacoraService(RegistroAccionRepository repo) { this.repo = repo; }

    public void registrar(Usuario admin, String accion, String entidad) {
        RegistroAccion r = new RegistroAccion();
        r.setUsuarioId(admin.getId());
        r.setUsuarioNombre(admin.getNombres() + " " + admin.getApellidos());
        r.setAccion(accion);
        r.setEntidadAfectada(entidad != null && entidad.length() > 200 ? entidad.substring(0, 200) : entidad);
        repo.save(r);
    }
}
