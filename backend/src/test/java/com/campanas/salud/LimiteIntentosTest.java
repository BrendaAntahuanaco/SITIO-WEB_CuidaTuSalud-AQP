package com.campanas.salud;

import com.campanas.salud.seguridad.LimiteIntentos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prueba del límite de intentos (no necesita base de datos). */
class LimiteIntentosTest {
    private static final long VENTANA = 60_000;

    @Test
    void bloqueaDespuesDelMaximoDeIntentos() {
        LimiteIntentos limite = new LimiteIntentos();
        for (int i = 0; i < 5; i++) {
            assertTrue(limite.permitido("login:x", 5, VENTANA));
            limite.registrar("login:x", VENTANA);
        }
        assertFalse(limite.permitido("login:x", 5, VENTANA), "el 6.º intento debe bloquearse");
        assertTrue(limite.permitido("login:otro", 5, VENTANA), "otra clave no se ve afectada");
    }

    @Test
    void olvidarReiniciaElContador() {
        LimiteIntentos limite = new LimiteIntentos();
        for (int i = 0; i < 5; i++) limite.registrar("login:y", VENTANA);
        limite.olvidar("login:y");
        assertTrue(limite.permitido("login:y", 5, VENTANA));
    }

    @Test
    void losIntentosVencenConElTiempo() throws InterruptedException {
        LimiteIntentos limite = new LimiteIntentos();
        limite.registrar("login:z", 50);
        assertFalse(limite.permitido("login:z", 1, 50));
        Thread.sleep(80);
        assertTrue(limite.permitido("login:z", 1, 50));
    }
}
