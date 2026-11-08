package com.campanas.salud.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/** Campaña de salud publicada por un administrador. */
@Entity
public class Campana {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String vacuna;
    private String descripcion;
    private String requisitos;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String fuente;

    @ManyToOne
    private Establecimiento establecimiento;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getVacuna() { return vacuna; }
    public void setVacuna(String vacuna) { this.vacuna = vacuna; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getRequisitos() { return requisitos; }
    public void setRequisitos(String requisitos) { this.requisitos = requisitos; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public String getFuente() { return fuente; }
    public void setFuente(String fuente) { this.fuente = fuente; }
    public Establecimiento getEstablecimiento() { return establecimiento; }
    public void setEstablecimiento(Establecimiento e) { this.establecimiento = e; }
}
