package com.campanas.salud;

import com.campanas.salud.model.Campana;
import com.campanas.salud.model.Establecimiento;
import com.campanas.salud.model.Turno;
import com.campanas.salud.repository.CampanaRepository;
import com.campanas.salud.repository.EstablecimientoRepository;
import com.campanas.salud.repository.TurnoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Datos de ejemplo para probar el prototipo. NO son información oficial.
 * Solo se cargan cuando no hay establecimientos en la base de datos.
 */
@Component
public class DatosIniciales implements CommandLineRunner {
    private final EstablecimientoRepository establecimientos;
    private final CampanaRepository campanas;
    private final TurnoRepository turnos;

    public DatosIniciales(EstablecimientoRepository establecimientos, CampanaRepository campanas,
                          TurnoRepository turnos) {
        this.establecimientos = establecimientos;
        this.campanas = campanas;
        this.turnos = turnos;
    }

    @Override
    public void run(String... args) {
        if (establecimientos.count() > 0) return;

        Establecimiento miraflores = establecimiento("Centro de Salud Miraflores", "Miraflores");
        Establecimiento melgar = establecimiento("Centro de Salud Mariano Melgar", "Mariano Melgar");

        LocalDate hoy = LocalDate.now();
        Campana sarampion = campana("Sarampión", "Vacunación para niños de 1 a 6 años",
                "DNI y carné de vacunación", hoy, hoy.plusDays(30), miraflores);
        Campana influenza = campana("Influenza", "Vacunación para adultos mayores y gestantes",
                "DNI", hoy, hoy.plusDays(20), melgar);

        turno(sarampion, hoy.plusDays(1), LocalTime.of(9, 0), LocalTime.of(12, 0), 20);
        turno(sarampion, hoy.plusDays(2), LocalTime.of(14, 0), LocalTime.of(17, 0), 15);
        turno(influenza, hoy.plusDays(1), LocalTime.of(8, 0), LocalTime.of(11, 0), 25);
    }

    private Establecimiento establecimiento(String nombre, String distrito) {
        Establecimiento e = new Establecimiento();
        e.setNombre(nombre);
        e.setDistrito(distrito);
        e.setDireccion("Distrito de " + distrito + ", Arequipa");
        return establecimientos.save(e);
    }

    private Campana campana(String vacuna, String descripcion, String requisitos,
                            LocalDate inicio, LocalDate fin, Establecimiento lugar) {
        Campana c = new Campana();
        c.setVacuna(vacuna);
        c.setDescripcion(descripcion);
        c.setRequisitos(requisitos);
        c.setFechaInicio(inicio);
        c.setFechaFin(fin);
        c.setFuente("Datos de prueba");
        c.setEstablecimiento(lugar);
        return campanas.save(c);
    }

    private void turno(Campana campana, LocalDate fecha, LocalTime desde, LocalTime hasta, int cupos) {
        Turno t = new Turno();
        t.setCampana(campana);
        t.setFecha(fecha);
        t.setHoraInicio(desde);
        t.setHoraFin(hasta);
        t.setCuposTotal(cupos);
        t.setCuposDisponibles(cupos);
        turnos.save(t);
    }
}
