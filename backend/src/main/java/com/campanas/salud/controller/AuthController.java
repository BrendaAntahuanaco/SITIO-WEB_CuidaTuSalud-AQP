package com.campanas.salud.controller;

import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.UsuarioRepository;
import com.campanas.salud.seguridad.TokenService;
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

    private final UsuarioRepository repo;
    private final TokenService tokens;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UsuarioRepository repo, TokenService tokens) {
        this.repo = repo;
        this.tokens = tokens;
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
    public SesionResponse registro(@RequestBody RegistroRequest r) {
        // Validación de datos en el servidor (RF-08)
        if (r.nombres() == null || r.nombres().isBlank() || r.apellidos() == null || r.apellidos().isBlank())
            throw error(HttpStatus.BAD_REQUEST, "Nombres y apellidos son obligatorios");
        if (r.nombres().length() > 100 || r.apellidos().length() > 100)
            throw error(HttpStatus.BAD_REQUEST, "Nombres y apellidos son demasiado largos");
        if (r.dni() == null || !r.dni().matches("\\d{8}"))
            throw error(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos");
        if (r.correo() == null || r.correo().length() > 150 || !r.correo().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw error(HttpStatus.BAD_REQUEST, "Correo no válido");
        if (r.contrasena() == null || r.contrasena().length() < 6 || r.contrasena().length() > 72)
            throw error(HttpStatus.BAD_REQUEST, "La contraseña debe tener entre 6 y 72 caracteres");
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
        return sesion(u);
    }

    @PostMapping("/login")
    public SesionResponse login(@RequestBody LoginRequest r) {
        String correo = r.correo() == null ? "" : r.correo().trim().toLowerCase();
        String clave = r.contrasena() == null ? "" : r.contrasena();
        Usuario u = repo.findByCorreo(correo)
                .filter(x -> encoder.matches(clave, x.getContrasenaHash()))
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos"));
        if (!"ACTIVO".equals(u.getEstado()))
            throw error(HttpStatus.FORBIDDEN, "Esta cuenta fue dada de baja. Comunícate con el administrador.");
        return sesion(u);
    }
}
