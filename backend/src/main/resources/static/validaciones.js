
(function (global) {
  "use strict";

  const Validar = {
    /** Texto obligatorio: ni vacío ni solo espacios  (valor == null || valor.length == 0 || /^\s+$/.test(valor)) */
    obligatorio(valor, nombre) {
      if (valor == null || valor.length === 0 || /^\s+$/.test(valor)) return "Escribe " + (nombre || "este dato") + ".";
      return "";
    },

    /** Solo letras (con tildes, ñ), espacios, guion y apóstrofe. Para nombres y apellidos. */
    nombre(valor, etiqueta) {
      const e = Validar.obligatorio(valor, etiqueta);
      if (e) return e;
      if (valor.trim().length < 2) return "Escribe al menos 2 letras.";
      if (!/^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ][A-Za-zÁÉÍÓÚÜÑáéíóúüñ' -]*$/.test(valor.trim())) return "Usa solo letras y espacios.";
      return "";
    },

    /** Valor numérico con isNaN(): comprueba decimales y signos por nosotros. */
    numerico(valor, nombre) {
      if (Validar.obligatorio(valor)) return "Escribe " + (nombre || "un número") + ".";
      if (isNaN(valor)) return "Escribe solo números.";
      return "";
    },

    /** DNI peruano: exactamente 8 dígitos, como en /^\d{9}$/ de teléfonos pero con 8. */
    dni(valor) {
      if (Validar.obligatorio(valor)) return "Escribe tu DNI.";
      if (!/^\d{8}$/.test(valor)) return "El DNI tiene 8 dígitos (sin espacios ni letras).";
      return "";
    },

    /** Correo con expresión regular (la de la diapositiva "Validar una dirección de email"). */
    email(valor) {
      if (Validar.obligatorio(valor)) return "Escribe tu correo electrónico.";
      if (!/^\w+([-+.']\w+)*@\w+([-.]\w+)*\.\w+([-.]\w+)*$/.test(valor.trim())) return "Escribe un correo válido, por ejemplo nombre@correo.com.";
      return "";
    },

    /** Teléfono peruano: celular de 9 dígitos que empieza en 9, con prefijo +51 opcional. Es opcional si se indica. */
    telefono(valor, opcional) {
      if (opcional && (valor == null || valor.trim() === "")) return "";
      if (Validar.obligatorio(valor)) return "Escribe tu número de celular.";
      const limpio = valor.replace(/[\s-]/g, "");
      if (!/^(\+51)?9\d{8}$/.test(limpio)) return "Escribe un celular de 9 dígitos que empiece con 9 (ej. 987654321).";
      return "";
    },

    /** Contraseña: mínimo 6 caracteres (misma regla que el servidor). */
    contrasena(valor) {
      if (valor == null || valor.length === 0) return "Escribe una contraseña.";
      if (valor.length < 6) return "Debe tener al menos 6 caracteres.";
      return "";
    },

    /** Fuerza de la contraseña de 0 a 4, para mostrar una barrita de ayuda. */
    fuerza(valor) {
      let p = 0;
      if (!valor) return 0;
      if (valor.length >= 6) p++;
      if (valor.length >= 10) p++;
      if (/[a-z]/.test(valor) && /[A-Z]/.test(valor)) p++;
      if (/\d/.test(valor) && /[^A-Za-z0-9]/.test(valor)) p++;
      return p;
    },

    /** Lista desplegable: el índice 0 es "- Selecciona un valor -" (selectedIndex == 0 es inválido). */
    seleccion(valor, nombre) {
      if (valor == null || valor === "") return "Elige " + (nombre || "una opción") + ".";
      return "";
    },

    /** Checkbox: debe estar marcado (elemento.checked). */
    marcado(marcado, mensaje) {
      return marcado ? "" : mensaje || "Debes marcar esta casilla para continuar.";
    },

    /** Radiobutton: se comprueba que haya uno elegido dentro del grupo. */
    grupo(valorElegido, mensaje) {
      return valorElegido ? "" : mensaje || "Elige una opción.";
    },

    /** Fecha real. OJO: en new Date(anio, mes, dia) el mes va de 0 (enero) a 11 (diciembre).
        Se usa el texto AAAA-MM-DD del <input type="date"> y se comprueba que el día exista (30 de febrero no existe). */
    fecha(texto) {
      if (Validar.obligatorio(texto)) return "Elige una fecha.";
      const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(texto);
      if (!m) return "La fecha no es válida.";
      const [anio, mes, dia] = [Number(m[1]), Number(m[2]), Number(m[3])];
      const f = new Date(anio, mes - 1, dia); // mes - 1 porque JavaScript cuenta los meses desde 0
      if (isNaN(f) || f.getFullYear() !== anio || f.getMonth() !== mes - 1 || f.getDate() !== dia) return "Esa fecha no existe.";
      return "";
    },

    /** Longitud máxima de un textarea. */
    maximo(valor, max) {
      return valor && valor.length > max ? "Máximo " + max + " caracteres." : "";
    },

    /** Mínimo de caracteres no vacíos. */
    minimo(valor, min, nombre) {
      const e = Validar.obligatorio(valor, nombre);
      if (e) return e;
      return valor.trim().length < min ? "Escribe al menos " + min + " caracteres." : "";
    },

    /** Recibe { campo: mensaje }, y devuelve el primer campo con error (o null) para ponerle el foco con focus(). */
    primerError(errores) {
      for (const k of Object.keys(errores)) if (errores[k]) return k;
      return null;
    },
  };

  global.Validar = Validar;
})(window);
