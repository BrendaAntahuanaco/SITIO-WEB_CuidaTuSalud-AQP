package com.campanas.salud.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/**
 * Convierte cualquier error de la API en {"message": "..."} en español.
 * Así la página siempre muestra el motivo real (y no solo "Ocurrió un error (400)"),
 * y el usuario nunca ve detalles internos del servidor.
 */
@RestControllerAdvice(basePackages = "com.campanas.salud.controller")
public class ManejadorErrores {
    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    private ResponseEntity<Map<String, String>> responder(HttpStatusCode estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("message", mensaje));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> estado(ResponseStatusException e) {
        String mensaje = e.getReason() != null ? e.getReason() : "No se pudo completar la solicitud";
        return responder(e.getStatusCode(), mensaje);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> tipoIncorrecto(MethodArgumentTypeMismatchException e) {
        return responder(HttpStatus.BAD_REQUEST, "El valor de «" + e.getName() + "» no es válido.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> faltaParametro(MissingServletRequestParameterException e) {
        return responder(HttpStatus.BAD_REQUEST, "Falta el dato «" + e.getParameterName() + "».");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> cuerpoIncorrecto(HttpMessageNotReadableException e) {
        return responder(HttpStatus.BAD_REQUEST, "Los datos enviados no tienen el formato correcto (revisa fechas y horas).");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integridad(DataIntegrityViolationException e) {
        log.warn("Violación de integridad: {}", e.getMostSpecificCause().getMessage());
        return responder(HttpStatus.CONFLICT, "Ese dato ya existe o está siendo usado por otro registro.");
    }

    /** Resto de errores: los de Spring con estado propio (404, 405, 415...) y los inesperados (500). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> otros(Exception e) {
        if (e instanceof ErrorResponse er) {
            HttpStatusCode estado = er.getStatusCode();
            String mensaje = switch (estado.value()) {
                case 404 -> "No se encontró lo que buscas.";
                case 405 -> "Esa acción no está permitida aquí.";
                case 415 -> "El formato de los datos enviados no es válido.";
                default -> "No se pudo completar la solicitud.";
            };
            return responder(estado, mensaje);
        }
        log.error("Error inesperado", e); // el detalle queda solo en la consola del servidor
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error en el servidor. Inténtalo de nuevo en un momento.");
    }
}
