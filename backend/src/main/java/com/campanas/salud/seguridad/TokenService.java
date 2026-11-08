package com.campanas.salud.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Token de sesión firmado (HMAC-SHA256) con el id del usuario y su vencimiento (RF-04).
 * Formato: base64url("id.vence") + "." + base64url(firma). No hace falta ninguna librería extra.
 * Si no se define TOKEN_SECRET, se genera una clave al iniciar: al reiniciar el servidor
 * las sesiones antiguas dejan de valer y la página pide ingresar de nuevo.
 */
@Component
public class TokenService {
    private static final long DURACION_MS = 8L * 60 * 60 * 1000; // 8 horas
    private final byte[] clave;

    public TokenService(@Value("${app.token.secret:}") String secreto) {
        if (secreto == null || secreto.length() < 16) {
            clave = new byte[32];
            new SecureRandom().nextBytes(clave);
        } else {
            clave = secreto.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String crear(Long usuarioId) {
        String contenido = usuarioId + "." + (System.currentTimeMillis() + DURACION_MS);
        return b64(contenido.getBytes(StandardCharsets.UTF_8)) + "." + b64(firmar(contenido));
    }

    /** Devuelve el id del usuario si el token es auténtico y no venció. */
    public Optional<Long> validar(String token) {
        try {
            if (token == null) return Optional.empty();
            String[] partes = token.split("\\.");
            if (partes.length != 2) return Optional.empty();
            String contenido = new String(Base64.getUrlDecoder().decode(partes[0]), StandardCharsets.UTF_8);
            byte[] firma = Base64.getUrlDecoder().decode(partes[1]);
            if (!MessageDigest.isEqual(firma, firmar(contenido))) return Optional.empty();
            String[] datos = contenido.split("\\.");
            if (datos.length != 2 || Long.parseLong(datos[1]) < System.currentTimeMillis()) return Optional.empty();
            return Optional.of(Long.parseLong(datos[0]));
        } catch (RuntimeException e) {
            return Optional.empty(); // token mal formado
        }
    }

    private byte[] firmar(String contenido) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }

    private static String b64(byte[] datos) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(datos);
    }
}
