package com.campanas.salud.seguridad;

import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Control de acceso por rol (RF-05). Se ejecuta antes de cada petición a /api/**:
 *  - PUBLICO: consultar campañas, turnos, establecimientos y avisos; registrarse e ingresar.
 *  - SESION:  reservar, ver y cancelar las propias reservas.
 *  - ADMIN:   crear, editar y eliminar datos, usuarios, asistencia y reportes.
 *            Es el nivel por defecto: una ruta nueva queda protegida hasta que se la declare pública o de sesión.
 * El usuario sale del token, nunca de datos que envíe el navegador.
 */
@Component
public class AutenticacionInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AutenticacionInterceptor.class);
    private static final String ATRIBUTO = "usuarioActual";

    private enum Nivel { PUBLICO, SESION, ADMIN }

    private final TokenService tokens;
    private final UsuarioRepository usuarios;

    public AutenticacionInterceptor(TokenService tokens, UsuarioRepository usuarios) {
        this.tokens = tokens;
        this.usuarios = usuarios;
    }

    /** Usuario que hizo la petición (solo existe si la ruta pidió sesión o es pública con token). */
    public static Usuario actual(HttpServletRequest request) {
        return (Usuario) request.getAttribute(ATRIBUTO);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        response.setHeader("Cache-Control", "no-store");        // datos personales: no guardar en caché
        response.setHeader("X-Content-Type-Options", "nosniff");
        if ("OPTIONS".equals(request.getMethod())) return true;

        // Si trae un token válido y la cuenta sigue activa, se identifica al usuario
        Usuario usuario = identificar(request.getHeader("Authorization"));
        if (usuario != null) request.setAttribute(ATRIBUTO, usuario);

        Nivel nivel = nivel(request.getMethod(), request.getServletPath());
        if (nivel == Nivel.PUBLICO) return true;
        if (usuario == null) {
            log.warn("401 sin sesión válida: {} {} desde {}", request.getMethod(), request.getServletPath(), request.getRemoteAddr());
            return rechazar(response, 401, "Tu sesión venció o no has ingresado. Ingresa de nuevo.");
        }
        if (nivel == Nivel.ADMIN && !"ADMIN".equals(usuario.getRol())) {
            log.warn("403 acceso de administrador denegado: {} {} usuario {} desde {}",
                    request.getMethod(), request.getServletPath(), usuario.getId(), request.getRemoteAddr());
            return rechazar(response, 403, "Esta acción es solo para administradores.");
        }
        return true;
    }

    private Usuario identificar(String cabecera) {
        if (cabecera == null || !cabecera.startsWith("Bearer ")) return null;
        Optional<Long> id = tokens.validar(cabecera.substring(7).trim());
        // Se consulta la base en cada petición: si cambian el rol o dan de baja la cuenta, se nota al instante
        return id.flatMap(usuarios::findById).filter(u -> "ACTIVO".equals(u.getEstado())).orElse(null);
    }

    private Nivel nivel(String metodo, String ruta) {
        boolean lectura = "GET".equals(metodo) || "HEAD".equals(metodo);
        if (ruta.startsWith("/api/auth/")) return Nivel.PUBLICO;
        if (lectura && (ruta.equals("/api/campanas") || ruta.startsWith("/api/campanas/")
                || ruta.equals("/api/turnos") || ruta.equals("/api/establecimientos")
                || ruta.equals("/api/avisos")))
            return Nivel.PUBLICO;
        // Solo estas rutas piden únicamente sesión: reservar, ver las propias y cancelar una reserva
        if (ruta.equals("/api/reservas") || ruta.matches("/api/reservas/\\d+"))
            return Nivel.SESION;
        // Cualquier otra ruta (usuarios, reportes, escritura de datos, rutas nuevas...) es solo para administradores
        return Nivel.ADMIN;
    }

    private boolean rechazar(HttpServletResponse response, int estado, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(("{\"message\":\"" + mensaje + "\"}").getBytes(StandardCharsets.UTF_8));
        return false;
    }
}
