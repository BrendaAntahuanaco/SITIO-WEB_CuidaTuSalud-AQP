
(function (global) {
  "use strict";

  const Validar = {

    obligatorio(valor, nombre) {
      if (valor == null || valor.length === 0 || /^\s+$/.test(valor)) return "Escribe " + (nombre || "este dato") + ".";
      return "";
    },

    nombre(valor, etiqueta) {
      const e = Validar.obligatorio(valor, etiqueta);
      if (e) return e;
      if (valor.trim().length < 2) return "Escribe al menos 2 letras.";
      if (!/^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ][A-Za-zÁÉÍÓÚÜÑáéíóúüñ' -]*$/.test(valor.trim())) return "Usa solo letras y espacios.";
      return "";
    },

    numerico(valor, nombre) {
      if (Validar.obligatorio(valor)) return "Escribe " + (nombre || "un número") + ".";
      if (isNaN(valor)) return "Escribe solo números.";
      return "";
    },

    dni(valor) {
      if (Validar.obligatorio(valor)) return "Escribe tu DNI.";
      if (!/^\d{8}$/.test(valor)) return "El DNI tiene 8 dígitos (sin espacios ni letras).";
      return "";
    },
    
    email(valor) {
      if (Validar.obligatorio(valor)) return "Escribe tu correo electrónico.";
      if (!/^\w+([-+.']\w+)*@\w+([-.]\w+)*\.\w+([-.]\w+)*$/.test(valor.trim())) return "Escribe un correo válido, por ejemplo nombre@correo.com.";
      return "";
    },

    telefono(valor, opcional) {
      if (opcional && (valor == null || valor.trim() === "")) return "";
      if (Validar.obligatorio(valor)) return "Escribe tu número de celular.";
      const limpio = valor.replace(/[\s-]/g, "");
      if (!/^(\+51)?9\d{8}$/.test(limpio)) return "Escribe un celular de 9 dígitos que empiece con 9 (ej. 987654321).";
      return "";
    },

    contrasena(valor) {
      if (valor == null || valor.length === 0) return "Escribe una contraseña.";
      if (valor.length < 6) return "Debe tener al menos 6 caracteres.";
      return "";
    },

    fuerza(valor) {
      let p = 0;
      if (!valor) return 0;
      if (valor.length >= 6) p++;
      if (valor.length >= 10) p++;
      if (/[a-z]/.test(valor) && /[A-Z]/.test(valor)) p++;
      if (/\d/.test(valor) && /[^A-Za-z0-9]/.test(valor)) p++;
      return p;
    },

    seleccion(valor, nombre) {
      if (valor == null || valor === "") return "Elige " + (nombre || "una opción") + ".";
      return "";
    },

    marcado(marcado, mensaje) {
      return marcado ? "" : mensaje || "Debes marcar esta casilla para continuar.";
    },

    grupo(valorElegido, mensaje) {
      return valorElegido ? "" : mensaje || "Elige una opción.";
    },

    fecha(texto) {
      if (Validar.obligatorio(texto)) return "Elige una fecha.";
      const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(texto);
      if (!m) return "La fecha no es válida.";
      const [anio, mes, dia] = [Number(m[1]), Number(m[2]), Number(m[3])];
      const f = new Date(anio, mes - 1, dia); 
      if (isNaN(f) || f.getFullYear() !== anio || f.getMonth() !== mes - 1 || f.getDate() !== dia) return "Esa fecha no existe.";
      return "";
    },

    maximo(valor, max) {
      return valor && valor.length > max ? "Máximo " + max + " caracteres." : "";
    },

    minimo(valor, min, nombre) {
      const e = Validar.obligatorio(valor, nombre);
      if (e) return e;
      return valor.trim().length < min ? "Escribe al menos " + min + " caracteres." : "";
    },

    primerError(errores) {
      for (const k of Object.keys(errores)) if (errores[k]) return k;
      return null;
    },
  };

  global.Validar = Validar;
})(window);
