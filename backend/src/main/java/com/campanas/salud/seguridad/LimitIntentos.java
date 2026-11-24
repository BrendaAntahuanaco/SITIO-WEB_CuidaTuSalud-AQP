package com.campanas.salud.seguridad;

import org.springframework.stereotype.Component;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita cuántas veces se puede repetir una acción en una ventana de tiempo (por ejemplo,
 * intentos fallidos de ingreso o creación de cuentas desde una misma IP).
 * Es en memoria: sirve para una sola instancia del servidor (como en Render) y se reinicia
 * al reiniciar la aplicación. Frena la fuerza bruta y el registro masivo de cuentas.
 */
@Component
public class LimiteIntentos {
    private static final int MAX_CLAVES = 10_000; // tope de memoria

    private final ConcurrentHashMap<String, Deque<Long>> eventos = new ConcurrentHashMap<>();

    /** true si todavía no se alcanzó el máximo de eventos dentro de la ventana. */
    public boolean permitido(String clave, int maximo, long ventanaMs) {
        Deque<Long> cola = eventos.get(clave);
        if (cola == null) return true;
        synchronized (cola) {
            purgar(cola, ventanaMs);
            return cola.size() < maximo;
        }
    }

    /** Anota un evento (un intento fallido, una cuenta creada...) para esa clave. */
    public void registrar(String clave, long ventanaMs) {
        if (eventos.size() > MAX_CLAVES) limpiarVencidos(ventanaMs);
        Deque<Long> cola = eventos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (cola) {
            purgar(cola, ventanaMs);
            cola.addLast(System.currentTimeMillis());
        }
    }

    /** Borra el historial de esa clave (por ejemplo, tras un ingreso correcto). */
    public void olvidar(String clave) {
        eventos.remove(clave);
    }

    private static void purgar(Deque<Long> cola, long ventanaMs) {
        long limite = System.currentTimeMillis() - ventanaMs;
        while (!cola.isEmpty() && cola.peekFirst() < limite) cola.removeFirst();
    }

    private void limpiarVencidos(long ventanaMs) {
        eventos.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                purgar(e.getValue(), ventanaMs);
                return e.getValue().isEmpty();
            }
        });
    }
}
