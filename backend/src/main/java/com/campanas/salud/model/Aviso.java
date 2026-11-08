package com.campanas.salud.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Noticia o aviso de la página principal (RF-02). Siempre indica su fuente. */
@Entity
public class Aviso {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long usuarioAdminId;
    @Column(length = 150) private String titulo;
    @Column(length = 1000) private String contenido;
    @Column(length = 200) private String fuente;
    private LocalDateTime fechaPublicacion = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUsuarioAdminId() { return usuarioAdminId; }
    public void setUsuarioAdminId(Long u) { this.usuarioAdminId = u; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public String getFuente() { return fuente; }
    public void setFuente(String fuente) { this.fuente = fuente; }
    public LocalDateTime getFechaPublicacion() { return fechaPublicacion; }
    public void setFechaPublicacion(LocalDateTime f) { this.fechaPublicacion = f; }
}
