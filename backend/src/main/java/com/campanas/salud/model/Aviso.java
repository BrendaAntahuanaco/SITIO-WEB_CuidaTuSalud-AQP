package com.campanas.salud.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Noticia o aviso de salud que se muestra en la página principal (RF-02). */
@Entity
public class Aviso {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 150) private String titulo;
    @Column(length = 1000) private String contenido;
    @Column(length = 200) private String fuente;
    private LocalDateTime fechaPublicacion = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String t) { this.titulo = t; }
    public String getContenido() { return contenido; }
    public void setContenido(String c) { this.contenido = c; }
    public String getFuente() { return fuente; }
    public void setFuente(String f) { this.fuente = f; }
    public LocalDateTime getFechaPublicacion() { return fechaPublicacion; }
    public void setFechaPublicacion(LocalDateTime f) { this.fechaPublicacion = f; }
}
