package com.campanas.salud;

import com.campanas.salud.model.*;
import com.campanas.salud.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Datos de prueba para el prototipo. Solo se cargan cuando las tablas están vacías.
 * Administrador inicial: admin@cuidatusalud.pe / Admin2026!  (cámbiala al desplegar).
 */
@Component
public class DatosIniciales implements CommandLineRunner {
    private final UsuarioRepository usuarios;
    private final EstablecimientoRepository establecimientos;
    private final CampanaRepository campanas;
    private final TurnoRepository turnos;
    private final AvisoRepository avisos;

    public DatosIniciales(UsuarioRepository usuarios, EstablecimientoRepository establecimientos,
                          CampanaRepository campanas, TurnoRepository turnos, AvisoRepository avisos) {
        this.usuarios = usuarios;
        this.establecimientos = establecimientos;
        this.campanas = campanas;
        this.turnos = turnos;
        this.avisos = avisos;
    }

    @Override
    public void run(String... args) {
        if (!usuarios.existsByCorreo("admin@cuidatusalud.pe")) {
            Usuario a = new Usuario();
            a.setNombres("Administrador");
            a.setApellidos("Cuida Tu Salud");
            a.setDni("00000000");
            a.setCorreo("admin@cuidatusalud.pe");
            a.setContrasenaHash(new BCryptPasswordEncoder().encode("Admin2026!"));
            a.setRol("ADMIN");
            if (!usuarios.existsByDni("00000000")) usuarios.save(a);
        }
        if (establecimientos.count() > 0) return;

        Establecimiento miraflores = est("Centro de Salud Miraflores", "Miraflores", "Distrito de Miraflores, Arequipa");
        Establecimiento colorado = est("Centro de Salud Cerro Colorado", "Cerro Colorado", "Distrito de Cerro Colorado, Arequipa");
        Establecimiento asa = est("Centro de Salud Alto Selva Alegre", "Alto Selva Alegre", "Distrito de Alto Selva Alegre, Arequipa");
        Establecimiento paucarpata = est("Centro de Salud Paucarpata", "Paucarpata", "Distrito de Paucarpata, Arequipa");

        LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
        String fuente = "Dato de prueba del prototipo";
        Campana sarampion = camp("Sarampión (refuerzo)", "ACTIVA", miraflores, hoy.minusDays(3), hoy.plusDays(30),
                "Dosis de refuerzo para niñas y niños de 1 a 6 años.",
                "Llevar DNI del niño o niña y carné de vacunación. Un padre, madre o apoderado debe acompañar.", fuente);
        Campana vph = camp("VPH", "ACTIVA", colorado, hoy, hoy.plusDays(21),
                "Vacunación contra el virus del papiloma humano para niñas, niños y adolescentes de 9 a 18 años.",
                "Llevar DNI y carné de vacunación. Menores de edad: acompañados por padre, madre o apoderado.", fuente);
        Campana influenza = camp("Influenza", "ACTIVA", asa, hoy.plusDays(2), hoy.plusDays(40),
                "Vacuna estacional para adultos mayores y personas con enfermedades crónicas.",
                "Llevar DNI. Si tomas medicamentos de forma permanente, trae tu receta.", fuente);
        camp("Tamizaje de anemia", "PROGRAMADA", paucarpata, hoy.plusDays(15), hoy.plusDays(45),
                "Descarte de anemia en niñas y niños menores de 5 años.",
                "Llevar DNI del niño o niña y carné de crecimiento y desarrollo.", fuente);

        for (int d = 1; d <= 3; d++) {
            turno(sarampion, hoy.plusDays(d), 8, 11, 30);
            turno(sarampion, hoy.plusDays(d), 14, 17, 30);
            turno(vph, hoy.plusDays(d + 1), 9, 12, 25);
            turno(influenza, hoy.plusDays(d + 2), 8, 12, 40);
        }

        aviso("Barrido de vacunación contra el sarampión en Arequipa",
                "La Gerencia Regional de Salud proyecta vacunar a 250 mil personas contra el sarampión. "
              + "Revisa las campañas disponibles y reserva tu turno para evitar colas.",
                "El Búho (10 de julio de 2026)");
        aviso("Lleva tu DNI y tu carné de vacunación",
                "Para ser atendido necesitas tu DNI y, si lo tienes, tu carné de vacunación. "
              + "Los menores de edad deben ir acompañados por su padre, madre o apoderado.",
                "Plataforma Cuida Tu Salud AQP");
    }

    private Establecimiento est(String nombre, String distrito, String direccion) {
        Establecimiento e = new Establecimiento();
        e.setNombre(nombre);
        e.setDistrito(distrito);
        e.setDireccion(direccion);
        return establecimientos.save(e);
    }

    private Campana camp(String vacuna, String estado, Establecimiento e, LocalDate ini, LocalDate fin,
                         String descripcion, String requisitos, String fuente) {
        Campana c = new Campana();
        c.setVacuna(vacuna);
        c.setEstado(estado);
        c.setEstablecimiento(e);
        c.setFechaInicio(ini);
        c.setFechaFin(fin);
        c.setDescripcion(descripcion);
        c.setRequisitos(requisitos);
        c.setFuente(fuente);
        return campanas.save(c);
    }

    private void turno(Campana c, LocalDate fecha, int desde, int hasta, int cupos) {
        Turno t = new Turno();
        t.setCampana(c);
        t.setFecha(fecha);
        t.setHoraInicio(LocalTime.of(desde, 0));
        t.setHoraFin(LocalTime.of(hasta, 0));
        t.setCuposTotal(cupos);
        t.setCuposDisponibles(cupos);
        turnos.save(t);
    }

    private void aviso(String titulo, String contenido, String fuente) {
        Aviso a = new Aviso();
        a.setTitulo(titulo);
        a.setContenido(contenido);
        a.setFuente(fuente);
        avisos.save(a);
    }
}
