package com.campanas.salud;

import com.campanas.salud.model.Aviso;
import com.campanas.salud.model.Campana;
import com.campanas.salud.model.Establecimiento;
import com.campanas.salud.model.Turno;
import com.campanas.salud.model.Usuario;
import com.campanas.salud.repository.AvisoRepository;
import com.campanas.salud.repository.CampanaRepository;
import com.campanas.salud.repository.EstablecimientoRepository;
import com.campanas.salud.repository.TurnoRepository;
import com.campanas.salud.repository.UsuarioRepository;
import com.campanas.salud.util.Texto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Al iniciar crea lo mínimo para poder probar el prototipo:
 *  1. La cuenta de administrador (si todavía no existe ninguna).
 *  2. Datos de ejemplo (solo si no hay establecimientos). NO son información oficial.
 */
@Component
public class DatosIniciales implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);

    private final EstablecimientoRepository establecimientos;
    private final CampanaRepository campanas;
    private final TurnoRepository turnos;
    private final UsuarioRepository usuarios;
    private final AvisoRepository avisos;
    private final String adminCorreo;
    private final String adminClave;

    public DatosIniciales(EstablecimientoRepository establecimientos, CampanaRepository campanas,
                          TurnoRepository turnos, UsuarioRepository usuarios, AvisoRepository avisos,
                          @Value("${app.admin.correo:admin@cuidatusalud.pe}") String adminCorreo,
                          @Value("${app.admin.password:}") String adminClave) {
        this.establecimientos = establecimientos;
        this.campanas = campanas;
        this.turnos = turnos;
        this.usuarios = usuarios;
        this.avisos = avisos;
        this.adminCorreo = adminCorreo;
        this.adminClave = adminClave;
    }

    @Override
    public void run(String... args) {
        crearAdministrador();
        if (establecimientos.count() > 0) return;

        Establecimiento miraflores = establecimiento("Centro de Salud Miraflores", "Miraflores");
        Establecimiento melgar = establecimiento("Centro de Salud Mariano Melgar", "Mariano Melgar");

        LocalDate hoy = Texto.hoy();
        Campana sarampion = campana("Sarampión", "Vacunación para niños de 1 a 6 años",
                "DNI y carné de vacunación", hoy, hoy.plusDays(30), "ACTIVA", miraflores);
        Campana influenza = campana("Influenza", "Vacunación para adultos mayores y gestantes",
                "DNI", hoy, hoy.plusDays(20), "ACTIVA", melgar);
        Campana vph = campana("VPH", "Vacunación para niñas y niños de 9 a 18 años",
                "DNI y consentimiento del apoderado", hoy.plusDays(10), hoy.plusDays(40), "PROGRAMADA", miraflores);

        turno(sarampion, hoy.plusDays(1), LocalTime.of(9, 0), LocalTime.of(12, 0), 20);
        turno(sarampion, hoy.plusDays(2), LocalTime.of(14, 0), LocalTime.of(17, 0), 15);
        turno(influenza, hoy.plusDays(1), LocalTime.of(8, 0), LocalTime.of(11, 0), 25);
        turno(vph, hoy.plusDays(11), LocalTime.of(9, 0), LocalTime.of(12, 0), 20);

        Aviso a = new Aviso();
        a.setTitulo("Prototipo con datos de prueba");
        a.setContenido("Las campañas y avisos de esta página son de ejemplo y no son información oficial de GERESA ni del MINSA.");
        a.setFuente("Equipo del proyecto");
        avisos.save(a);
    }

    /** Crea la primera cuenta de administrador. Nadie puede hacerse administrador desde el registro. */
    private void crearAdministrador() {
        if (usuarios.existsByRol("ADMIN")) return;
        String clave = adminClave == null || adminClave.length() < 8 ? generarClave() : adminClave;
        Usuario u = new Usuario();
        u.setNombres("Administrador");
        u.setApellidos("del sistema");
        u.setDni("00000000");
        u.setCorreo(adminCorreo.trim().toLowerCase());
        u.setContrasenaHash(new BCryptPasswordEncoder().encode(clave));
        u.setRol("ADMIN");
        usuarios.save(u);
        if (adminClave == null || adminClave.length() < 8) {
            log.warn("""

                    ==========================================================
                     CUENTA DE ADMINISTRADOR CREADA (se muestra una sola vez)
                       Correo:     {}
                       Contraseña: {}
                    ==========================================================""", u.getCorreo(), clave);
        } else {
            log.info("Cuenta de administrador creada: {}", u.getCorreo());
        }
    }

    private String generarClave() {
        String letras = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom azar = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(letras.charAt(azar.nextInt(letras.length())));
        return sb.toString();
    }

    private Establecimiento establecimiento(String nombre, String distrito) {
        Establecimiento e = new Establecimiento();
        e.setNombre(nombre);
        e.setDistrito(distrito);
        e.setDireccion("Distrito de " + distrito + ", Arequipa");
        return establecimientos.save(e);
    }

    private Campana campana(String vacuna, String descripcion, String requisitos,
                            LocalDate inicio, LocalDate fin, String estado, Establecimiento lugar) {
        Campana c = new Campana();
        c.setVacuna(vacuna);
        c.setDescripcion(descripcion);
        c.setRequisitos(requisitos);
        c.setFechaInicio(inicio);
        c.setFechaFin(fin);
        c.setFuente("Datos de prueba");
        c.setEstado(estado);
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
