package com.campanas.salud.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Token de sesión firmado con HMAC-SHA256 (RF-04). Formato: base64(id|expira).firma
 * No necesita librerías extra. El rol NO va en el token: se lee siempre de la base de datos,
 * así un cambio de rol o una baja de cuenta se aplican de inmediato.
 */
@Component
public class TokenService {
    private static final long HORAS_DE_VIDA = 8;
    private final byte[] secreto;

    public TokenService(@Value("${app.token.secret}") String secreto) {
        this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
    }

    public String crear(Long usuarioId) {
        long expira = Instant.now().plusSeconds(HORAS_DE_VIDA * 3600).getEpochSecond();
        String datos = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((usuarioId + "|" + expira).getBytes(StandardCharsets.UTF_8));
        return datos + "." + firmar(datos);
    }

    /** Devuelve el id del usuario si el token es válido y no venció; si no, null. */
    public Long validar(String token) {
        try {
            if (token == null) return null;
            int punto = token.indexOf('.');
            if (punto < 1) return null;
            String datos = token.substring(0, punto);
            byte[] esperada = firmar(datos).getBytes(StandardCharsets.UTF_8);
            byte[] recibida = token.substring(punto + 1).getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(esperada, recibida)) return null;
            String[] partes = new String(Base64.getUrlDecoder().decode(datos), StandardCharsets.UTF_8).split("\\|");
            if (Long.parseLong(partes[1]) < Instant.now().getEpochSecond()) return null;
            return Long.parseLong(partes[0]);
        } catch (Exception e) {
            return null;
        }
    }

    private String firmar(String datos) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(datos.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
