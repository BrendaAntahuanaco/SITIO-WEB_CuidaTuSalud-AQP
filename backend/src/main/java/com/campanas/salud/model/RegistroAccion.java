package com.campanas.salud.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Bitácora de las acciones hechas en el panel de administración. */
@Entity
public class RegistroAccion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long usuarioId;
    @Column(length = 200) private String usuarioNombre;
    @Column(length = 200) private String accion;
    @Column(length = 200) private String entidadAfectada;
    private LocalDateTime fechaHora = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long u) { this.usuarioId = u; }
    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String n) { this.usuarioNombre = n; }
    public String getAccion() { return accion; }
    public void setAccion(String a) { this.accion = a; }
    public String getEntidadAfectada() { return entidadAfectada; }
    public void setEntidadAfectada(String e) { this.entidadAfectada = e; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime f) { this.fechaHora = f; }
}
