package com.campanas.salud.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/** Cuenta de ciudadano o administrador. La contraseña se guarda solo como hash. */
@Entity
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombres;
    private String apellidos;
    @Column(unique = true) private String dni;
    @Column(unique = true) private String correo;
    @JsonIgnore private String contrasenaHash; // nunca se envía en las respuestas
    private String rol = "CIUDADANO";          // CIUDADANO o ADMIN

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getContrasenaHash() { return contrasenaHash; }
    public void setContrasenaHash(String h) { this.contrasenaHash = h; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
}
