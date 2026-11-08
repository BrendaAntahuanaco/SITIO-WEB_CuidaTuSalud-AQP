package com.campanas.salud.seguridad;

import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Control de acceso por rol (RF-05). Se ejecuta antes de cada petición a /api/**:
 *  - Pública: registro, login y la consulta de campañas, turnos, avisos y establecimientos.
 *  - Ciudadano: sus propias reservas.
 *  - Administrador: crear/editar/borrar datos, usuarios, asistencia y reportes.
 * Así la seguridad no depende de lo que muestre React: el servidor siempre decide.
 */
@Component
public class SeguridadInterceptor implements HandlerInterceptor {
    public static final String USUARIO = "usuarioActual";

    private final TokenService tokens;
    private final UsuarioRepository usuarios;

    public SeguridadInterceptor(TokenService tokens, UsuarioRepository usuarios) {
        this.tokens = tokens;
        this.usuarios = usuarios;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String metodo = req.getMethod();
        String ruta = req.getRequestURI();
        if ("OPTIONS".equals(metodo) || esPublica(metodo, ruta)) return true;

        String cabecera = req.getHeader("Authorization");
        Long id = (cabecera != null && cabecera.startsWith("Bearer "))
                ? tokens.validar(cabecera.substring(7)) : null;
        Usuario u = id == null ? null : usuarios.findById(id).orElse(null);
        if (u == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar");
        if (u.estaDeBaja())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu cuenta fue dada de baja");
        if (soloAdmin(metodo, ruta) && !"ADMIN".equals(u.getRol()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta acción es solo para administradores");

        req.setAttribute(USUARIO, u);
        return true;
    }

    private boolean esPublica(String m, String p) {
        if (p.startsWith("/api/auth/")) return true;
        if (!"GET".equals(m)) return false;
        return p.equals("/api/campanas") || p.startsWith("/api/campanas/")
                || p.equals("/api/turnos") || p.equals("/api/avisos") || p.equals("/api/establecimientos");
    }

    private boolean soloAdmin(String m, String p) {
        if (p.startsWith("/api/reportes") || p.startsWith("/api/usuarios")) return true;
        if (p.equals("/api/reservas/buscar") || p.endsWith("/atender")) return true;
        boolean datosPublicos = p.startsWith("/api/campanas") || p.startsWith("/api/turnos")
                || p.startsWith("/api/establecimientos") || p.startsWith("/api/avisos");
        return datosPublicos && !"GET".equals(m);
    }
}
