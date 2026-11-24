package com.campanas.salud.controller;

import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.UsuarioRepository;
import com.campanas.salud.seguridad.LimiteIntentos;
import com.campanas.salud.seguridad.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Registro e inicio de sesión (RF-04, RF-07, RF-08). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegistroRequest(String nombres, String apellidos, String dni, String correo,
                                  String contrasena, Boolean aceptaTerminos) { }
    public record LoginRequest(String correo, String contrasena) { }
    /** Lo que recibe React: con el rol decide qué pantallas mostrar (RF-05) y el token va en cada petición. */
    public record SesionResponse(Long id, String nombres, String apellidos, String dni, String rol, String token) { }

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final int MAX_FALLOS_LOGIN = 5;                 // intentos fallidos por correo + IP
    private static final long VENTANA_LOGIN_MS = 10 * 60 * 1000L;  // en 10 minutos
    private static final long VENTANA_REGISTRO_MS = 60 * 60 * 1000L; // en 1 hora
    private static final int MIN_CONTRASENA = 8;

    private final UsuarioRepository repo;
    private final TokenService tokens;
    private final LimiteIntentos limite;
    private final int maxRegistrosIp; // cuentas nuevas por IP y hora (app.limite.registros-por-hora)
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UsuarioRepository repo, TokenService tokens, LimiteIntentos limite,
                          @Value("${app.limite.registros-por-hora:20}") int maxRegistrosIp) {
        this.repo = repo;
        this.tokens = tokens;
        this.limite = limite;
        this.maxRegistrosIp = maxRegistrosIp;
    }

    private ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }

    private SesionResponse sesion(Usuario u) {
        return new SesionResponse(u.getId(), u.getNombres(), u.getApellidos(), u.getDni(), u.getRol(),
                tokens.crear(u.getId()));
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public SesionResponse registro(@RequestBody RegistroRequest r, HttpServletRequest req) {
        String ip = req.getRemoteAddr();
        if (!limite.permitido("registro:" + ip, maxRegistrosIp, VENTANA_REGISTRO_MS)) {
            log.warn("Registro bloqueado por exceso de cuentas nuevas desde {}", ip);
            throw error(HttpStatus.TOO_MANY_REQUESTS, "Se crearon demasiadas cuentas desde tu conexión. Inténtalo más tarde.");
        }
        // Validación de datos en el servidor (RF-08)
        if (r.nombres() == null || r.nombres().isBlank() || r.apellidos() == null || r.apellidos().isBlank())
            throw error(HttpStatus.BAD_REQUEST, "Nombres y apellidos son obligatorios");
        if (r.nombres().length() > 100 || r.apellidos().length() > 100)
            throw error(HttpStatus.BAD_REQUEST, "Nombres y apellidos son demasiado largos");
        if (r.dni() == null || !r.dni().matches("\\d{8}"))
            throw error(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos");
        if (r.correo() == null || r.correo().length() > 150 || !r.correo().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw error(HttpStatus.BAD_REQUEST, "Correo no válido");
        if (r.contrasena() == null || r.contrasena().length() < MIN_CONTRASENA || r.contrasena().length() > 72)
            throw error(HttpStatus.BAD_REQUEST, "La contraseña debe tener entre " + MIN_CONTRASENA + " y 72 caracteres");
        if (!Boolean.TRUE.equals(r.aceptaTerminos()))
            throw error(HttpStatus.BAD_REQUEST, "Debes aceptar la política de privacidad para crear tu cuenta");

        String correo = r.correo().trim().toLowerCase();
        if (repo.existsByCorreo(correo)) throw error(HttpStatus.CONFLICT, "Ese correo ya está registrado");
        if (repo.existsByDni(r.dni())) throw error(HttpStatus.CONFLICT, "Ese DNI ya está registrado");

        Usuario u = new Usuario();
        u.setNombres(r.nombres().trim());
        u.setApellidos(r.apellidos().trim());
        u.setDni(r.dni());
        u.setCorreo(correo);
        u.setContrasenaHash(encoder.encode(r.contrasena())); // nunca se guarda en texto plano
        // El rol siempre es CIUDADANO: quien se registra no puede elegir ser administrador
        repo.save(u);
        limite.registrar("registro:" + ip, VENTANA_REGISTRO_MS);
        return sesion(u);
    }

    @PostMapping("/login")
    public SesionResponse login(@RequestBody LoginRequest r, HttpServletRequest req) {
        String correo = r.correo() == null ? "" : r.correo().trim().toLowerCase();
        String clave = r.contrasena() == null ? "" : r.contrasena();
        String ip = req.getRemoteAddr();
        String claveLimite = "login:" + ip + ":" + correo;
        if (!limite.permitido(claveLimite, MAX_FALLOS_LOGIN, VENTANA_LOGIN_MS)) {
            log.warn("Ingreso bloqueado por demasiados intentos fallidos: {} desde {}", correo, ip);
            throw error(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos fallidos. Espera 10 minutos e inténtalo de nuevo.");
        }
        Usuario u = repo.findByCorreo(correo)
                .filter(x -> encoder.matches(clave, x.getContrasenaHash()))
                .orElse(null);
        if (u == null) {
            limite.registrar(claveLimite, VENTANA_LOGIN_MS);
            log.warn("Ingreso fallido: {} desde {}", correo, ip);
            throw error(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
        }
        if (!"ACTIVO".equals(u.getEstado()))
            throw error(HttpStatus.FORBIDDEN, "Esta cuenta fue dada de baja. Comunícate con el administrador.");
        limite.olvidar(claveLimite);
        return sesion(u);
    }
}
