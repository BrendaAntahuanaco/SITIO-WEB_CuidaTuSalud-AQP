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
                              long cupos, long reservadas, long atendidas, long canceladas) { }
    /** Por distrito, "reservadas" ya incluye a las atendidas (así lo espera la pantalla). */
    public record FilaDistrito(String distrito, long cupos, long reservadas, long atendidas) { }
    public record Resumen(long ciudadanos, long totalReservas, long reservadas, long atendidas,
                          double cobertura, List<FilaCampana> porCampana, List<FilaDistrito> porDistrito) { }

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
        List<Reserva> todas = reservas.findAll();
        long reservadas = todas.stream().filter(r -> "RESERVADA".equals(r.getEstado())).count();
        long atendidas = todas.stream().filter(r -> "ATENDIDA".equals(r.getEstado())).count();
        long cuposOfrecidos = turnos.findAll().stream().mapToLong(Turno::getCuposTotal).sum();
        double cobertura = cuposOfrecidos == 0 ? 0 : Math.round(atendidas * 1000.0 / cuposOfrecidos) / 10.0;
        return new Resumen(usuarios.countByRol("CIUDADANO"), todas.size(), reservadas, atendidas,
                cobertura, filasPorCampana(), filasPorDistrito());
    }

    private List<FilaCampana> filasPorCampana() {
        Map<Long, Long> cupos = new HashMap<>();
        for (Turno t : turnos.findAll()) cupos.merge(t.getCampana().getId(), (long) t.getCuposTotal(), Long::sum);
        Map<Long, long[]> conteo = new HashMap<>(); // [reservadas, atendidas, canceladas]
        for (Reserva r : reservas.findAll()) {
            long[] c = conteo.computeIfAbsent(r.getTurno().getCampana().getId(), k -> new long[3]);
            switch (r.getEstado()) {
                case "RESERVADA" -> c[0]++;
                case "ATENDIDA" -> c[1]++;
                case "CANCELADA" -> c[2]++;
                default -> { }
            }
        }
        List<FilaCampana> filas = new ArrayList<>();
        for (Campana c : campanas.findAll()) {
            long[] n = conteo.getOrDefault(c.getId(), new long[3]);
            String distrito = c.getEstablecimiento() == null ? "—" : c.getEstablecimiento().getDistrito();
            filas.add(new FilaCampana(c.getId(), c.getVacuna(), distrito, c.getEstado(),
                    cupos.getOrDefault(c.getId(), 0L), n[0], n[1], n[2]));
        }
        return filas;
    }

    private List<FilaDistrito> filasPorDistrito() {
        Map<String, long[]> mapa = new TreeMap<>(); // [cupos, reservadas+atendidas, atendidas]
        for (Turno t : turnos.findAll()) {
            String d = distritoDe(t.getCampana());
            mapa.computeIfAbsent(d, k -> new long[3])[0] += t.getCuposTotal();
        }
        for (Reserva r : reservas.findAll()) {
            long[] n = mapa.computeIfAbsent(distritoDe(r.getTurno().getCampana()), k -> new long[3]);
            if ("RESERVADA".equals(r.getEstado()) || "ATENDIDA".equals(r.getEstado())) n[1]++;
            if ("ATENDIDA".equals(r.getEstado())) n[2]++;
        }
        List<FilaDistrito> filas = new ArrayList<>();
        mapa.forEach((d, n) -> filas.add(new FilaDistrito(d, n[0], n[1], n[2])));
        return filas;
    }

    private String distritoDe(Campana c) {
        return c.getEstablecimiento() == null ? "—" : c.getEstablecimiento().getDistrito();
    }

    /** Descarga los datos en CSV, que se abre directamente en Excel (RF-21). */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar() {
        StringBuilder csv = new StringBuilder("\uFEFFCampaña,Distrito,Estado,Cupos,Reservadas,Atendidas,Canceladas\r\n");
        for (FilaCampana f : filasPorCampana()) {
            csv.append(celda(f.vacuna())).append(',').append(celda(f.distrito())).append(',')
               .append(celda(f.estado())).append(',').append(f.cupos()).append(',')
               .append(f.reservadas()).append(',').append(f.atendidas()).append(',')
               .append(f.canceladas()).append("\r\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-campanas.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Protege el CSV: comillas dobles y, si empieza con = + - @, se neutraliza para que Excel no lo ejecute. */
    private String celda(String valor) {
        String s = valor == null ? "" : valor;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** Últimas 50 acciones del panel de administración. */
    @GetMapping("/acciones")
    public List<RegistroAccion> acciones() { return acciones.findTop50ByOrderByFechaHoraDesc(); }
}
