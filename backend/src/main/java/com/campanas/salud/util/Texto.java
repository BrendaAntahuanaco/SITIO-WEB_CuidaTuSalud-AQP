package com.campanas.salud.util;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/** Funciones pequeñas que usan varios controladores. */
public final class Texto {
    private Texto() { }

    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** Fecha de hoy en Arequipa (hora de Perú), sin depender de la zona del servidor. */
    public static LocalDate hoy() { return LocalDate.now(LIMA); }

    /** Minúsculas y sin tildes, para buscar "sarampion" y encontrar "Sarampión". */
    public static String normalizar(String s) {
        if (s == null) return "";
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim();
    }

    public static boolean vacio(String s) { return s == null || s.isBlank(); }

    /** Texto limpio o null si estaba vacío. */
    public static String limpio(String s) { return vacio(s) ? null : s.trim(); }
}
