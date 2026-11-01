package com.campanas.salud.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

/** Horario con cupos de una campaña. */
@Entity
public class Turno {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne private Campana campana;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private int cuposTotal;
    private int cuposDisponibles;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Campana getCampana() { return campana; }
    public void setCampana(Campana campana) { this.campana = campana; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime h) { this.horaInicio = h; }
    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime h) { this.horaFin = h; }
    public int getCuposTotal() { return cuposTotal; }
    public void setCuposTotal(int c) { this.cuposTotal = c; }
    public int getCuposDisponibles() { return cuposDisponibles; }
    public void setCuposDisponibles(int c) { this.cuposDisponibles = c; }
}
