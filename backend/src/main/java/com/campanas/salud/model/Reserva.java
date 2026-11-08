package com.campanas.salud.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Turno reservado por un ciudadano para sí mismo o para un menor a su cargo. */
@Entity
public class Reserva {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long usuarioId;
    @ManyToOne private Turno turno;
    @Column(length = 150) private String beneficiarioNombre;
    @Column(length = 8) private String beneficiarioDni;
    @Column(length = 20) private String estado = "RESERVADA"; // RESERVADA, CANCELADA o ATENDIDA
    private LocalDateTime fechaReserva = LocalDateTime.now();
    private LocalDateTime fechaAtencion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public Turno getTurno() { return turno; }
    public void setTurno(Turno turno) { this.turno = turno; }
    public String getBeneficiarioNombre() { return beneficiarioNombre; }
    public void setBeneficiarioNombre(String n) { this.beneficiarioNombre = n; }
    public String getBeneficiarioDni() { return beneficiarioDni; }
    public void setBeneficiarioDni(String d) { this.beneficiarioDni = d; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getFechaReserva() { return fechaReserva; }
    public void setFechaReserva(LocalDateTime f) { this.fechaReserva = f; }
    public LocalDateTime getFechaAtencion() { return fechaAtencion; }
    public void setFechaAtencion(LocalDateTime f) { this.fechaAtencion = f; }
}
