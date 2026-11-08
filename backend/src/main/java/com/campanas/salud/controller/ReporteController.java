package com.campanas.salud.controller;

import com.campanas.salud.model.Campana;
import com.campanas.salud.model.RegistroAccion;
import com.campanas.salud.model.Reserva;
import com.campanas.salud.model.Turno;
import com.campanas.salud.repository.CampanaRepository;
import com.campanas.salud.repository.RegistroAccionRepository;
import com.campanas.salud.repository.ReservaRepository;
import com.campanas.salud.repository.TurnoRepository;
import com.campanas.salud.repository.UsuarioRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reportes y estadísticas para el administrador (RF-19, RF-20, RF-21). */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    public record FilaCampana(Long campanaId, String vacuna, String distrito, String estado,
                              int cupos, long reservadas, long atendidas, long canceladas) { }
    public record FilaDistrito(String distrito, int cupos, long reservadas, long atendidas) { }
    public record Resumen(long ciudadanos, long totalReservas, long reservadas, long atendidas,
                          long canceladas, int cuposOfrecidos, long cuposOcupados, double cobertura,
                          List<FilaCampana> porCampana, List<FilaDistrito> porDistrito) { }

    private final CampanaRepository campanas;
    private final TurnoRepository turnos;
    private final ReservaRepository reservas;
    private final UsuarioRepository usuarios;
    private final RegistroAccionRepository acciones;

    public ReporteController(CampanaRepository campanas, TurnoRepository turnos, ReservaRepository reservas,
                             UsuarioRepository usuarios, RegistroAccionRepository acciones) {
        this.campanas = campanas;
        this.turnos = turnos;
        this.reservas = reservas;
        this.usuarios = usuarios;
        this.acciones = acciones;
    }

    @GetMapping("/resumen")
    public Resumen resumen() {
        List<Campana> todas = campanas.findAll();
        List<Turno> losTurnos = turnos.findAll();
        List<Reserva> lasReservas = reservas.findAll();

        Map<Long, Integer> cuposPorCampana = new HashMap<>();
        for (Turno t : losTurnos) cuposPorCampana.merge(t.getCampana().getId(), t.getCuposTotal(), Integer::sum);

        Map<Long, long[]> cuenta = new HashMap<>(); // [reservadas, atendidas, canceladas]
        for (Reserva r : lasReservas) {
            long[] c = cuenta.computeIfAbsent(r.getTurno().getCampana().getId(), k -> new long[3]);
            switch (r.getEstado()) {
                case "RESERVADA" -> c[0]++;
                case "ATENDIDA" -> c[1]++;
                default -> c[2]++;
            }
        }

        List<FilaCampana> filas = new ArrayList<>();
        Map<String, long[]> distritos = new TreeMap<>(); // [cupos, reservadas, atendidas]
        for (Campana c : todas) {
            long[] n = cuenta.getOrDefault(c.getId(), new long[3]);
            int cupos = cuposPorCampana.getOrDefault(c.getId(), 0);
            String distrito = c.getEstablecimiento() == null ? "—" : c.getEstablecimiento().getDistrito();
            filas.add(new FilaCampana(c.getId(), c.getVacuna(), distrito, c.getEstado(), cupos, n[0], n[1], n[2]));
            long[] d = distritos.computeIfAbsent(distrito, k -> new long[3]);
            d[0] += cupos;
            d[1] += n[0] + n[1]; // reservas que ocupan cupo
            d[2] += n[1];
        }
        List<FilaDistrito> porDistrito = new ArrayList<>();
        distritos.forEach((d, n) -> porDistrito.add(new FilaDistrito(d, (int) n[0], n[1], n[2])));

        long reservadas = filas.stream().mapToLong(FilaCampana::reservadas).sum();
        long atendidas = filas.stream().mapToLong(FilaCampana::atendidas).sum();
        long canceladas = filas.stream().mapToLong(FilaCampana::canceladas).sum();
        int cupos = filas.stream().mapToInt(FilaCampana::cupos).sum();
        // Cobertura estimada = personas atendidas ÷ cupos ofrecidos
        double cobertura = cupos == 0 ? 0 : Math.round(atendidas * 1000.0 / cupos) / 10.0;
        long ciudadanos = usuarios.findAll().stream().filter(u -> "CIUDADANO".equals(u.getRol())).count();

        return new Resumen(ciudadanos, lasReservas.size(), reservadas, atendidas, canceladas,
                cupos, reservadas + atendidas, cobertura, filas, porDistrito);
    }

    /** Descarga en CSV, que Excel abre directamente (RF-21). El PDF se obtiene imprimiendo la pantalla. */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar() {
        StringBuilder sb = new StringBuilder("﻿Campaña,Distrito,Estado,Cupos ofrecidos,Reservadas,Atendidas,Canceladas\r\n");
        for (FilaCampana f : resumen().porCampana())
            sb.append(String.join(",", celda(f.vacuna()), celda(f.distrito()), celda(f.estado()),
                    String.valueOf(f.cupos()), String.valueOf(f.reservadas()),
                    String.valueOf(f.atendidas()), String.valueOf(f.canceladas()))).append("\r\n");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-campanas.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Bitácora: últimas 50 acciones del panel de administración. */
    @GetMapping("/acciones")
    public List<RegistroAccion> acciones() { return acciones.findTop50ByOrderByFechaHoraDesc(); }

    /** Escapa comillas y neutraliza fórmulas (=, +, -, @) para que Excel no ejecute texto malicioso. */
    private static String celda(String valor) {
        String v = valor == null ? "" : valor;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
