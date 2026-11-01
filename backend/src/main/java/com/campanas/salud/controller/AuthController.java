package com.campanas.salud.controller;

import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Registro e inicio de sesión (RF-04, RF-07, RF-08). */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    public record RegistroRequest(String nombres, String apellidos, String dni,
                                  String correo, String contrasena) { }
    public record LoginRequest(String correo, String contrasena) { }
    /** Lo que recibe React: con el rol decide qué pantallas mostrar (RF-05). */
    public record SesionResponse(Long id, String nombres, String rol) { }

    private final UsuarioRepository repo;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UsuarioRepository repo) { this.repo = repo; }

    private ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public SesionResponse registro(@RequestBody RegistroRequest r) {
        // Validación de datos (RF-08)
        if (r.nombres() == null || r.nombres().isBlank() || r.apellidos() == null || r.apellidos().isBlank())
            throw error(HttpStatus.BAD_REQUEST, "Nombres y apellidos son obligatorios");
        if (r.dni() == null || !r.dni().matches("\\d{8}"))
            throw error(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos");
        if (r.correo() == null || !r.correo().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw error(HttpStatus.BAD_REQUEST, "Correo no válido");
        if (r.contrasena() == null || r.contrasena().length() < 6)
            throw error(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 6 caracteres");

        String correo = r.correo().trim().toLowerCase();
        if (repo.existsByCorreo(correo)) throw error(HttpStatus.CONFLICT, "Ese correo ya está registrado");
        if (repo.existsByDni(r.dni())) throw error(HttpStatus.CONFLICT, "Ese DNI ya está registrado");

        Usuario u = new Usuario();
        u.setNombres(r.nombres());
        u.setApellidos(r.apellidos());
        u.setDni(r.dni());
        u.setCorreo(correo);
        u.setContrasenaHash(encoder.encode(r.contrasena()));
        repo.save(u);
        return new SesionResponse(u.getId(), u.getNombres(), u.getRol());
    }

    @PostMapping("/login")
    public SesionResponse login(@RequestBody LoginRequest r) {
        Usuario u = repo.findByCorreo(r.correo() == null ? "" : r.correo().trim().toLowerCase())
                .filter(x -> encoder.matches(r.contrasena() == null ? "" : r.contrasena(), x.getContrasenaHash()))
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos"));
        return new SesionResponse(u.getId(), u.getNombres(), u.getRol());
    }
}
