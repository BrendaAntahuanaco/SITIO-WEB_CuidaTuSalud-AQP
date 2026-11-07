// Lógica de la página. Habla con el backend Spring Boot mediante la API REST.
// Como la página la sirve el mismo Spring Boot, basta una ruta relativa.
const API = "/api";

// Sesión del usuario (se guarda en el navegador para no perderla al recargar)
let sesion = JSON.parse(localStorage.getItem("sesion") || "null");

/** Llama al backend y devuelve el JSON. Si hay error, lanza el mensaje del servidor. */
async function llamar(ruta, opciones) {
    const resp = await fetch(API + ruta, Object.assign({ headers: { "Content-Type": "application/json" } }, opciones));
    const texto = await resp.text();
    let datos = null;
    try { datos = texto ? JSON.parse(texto) : null; } catch (e) { /* respuesta sin JSON */ }
    if (!resp.ok) throw new Error((datos && datos.message) || "Ocurrió un error");
    return datos;
}

/** Protección contra XSS: convierte el texto en texto seguro antes de mostrarlo en la página. */
function esc(texto) {
    const d = document.createElement("div");
    d.textContent = texto == null ? "" : texto;
    return d.innerHTML;
}

function valor(id) { return document.getElementById(id).value; }

/** Muestra un aviso verde (tipo "ok") o rojo (tipo "error") unos segundos. */
function mensaje(texto, tipo) {
    const caja = document.getElementById("mensajes");
    caja.innerHTML = '<div class="' + tipo + '">' + esc(texto) + "</div>";
    setTimeout(function () { caja.innerHTML = ""; }, 6000);
}

/* ---------- Campañas (RF-09, RF-10, RF-11) ---------- */

async function mostrarCampanas() {
    document.getElementById("filtros").classList.remove("oculto");
    const filtros = new URLSearchParams({ vacuna: valor("fVacuna"), distrito: valor("fDistrito") });
    const cont = document.getElementById("contenido");
    try {
        const lista = await llamar("/campanas?" + filtros);
        if (lista.length === 0) {
            cont.innerHTML = "<p>No hay campañas que coincidan con la búsqueda.</p>";
            return;
        }
        cont.innerHTML = lista.map(tarjetaCampana).join("");
    } catch (e) {
        cont.innerHTML = '<div class="error">No se pudo conectar con el servidor.</div>';
    }
}

function tarjetaCampana(c) {
    const lugar = c.establecimiento ? esc(c.establecimiento.nombre) + " (" + esc(c.establecimiento.distrito) + ")" : "";
    return "<article>" +
        "<h3>" + esc(c.vacuna) + "</h3>" +
        "<p><b>Lugar:</b> " + lugar + "</p>" +
        "<p><b>Fechas:</b> del " + esc(c.fechaInicio) + " al " + esc(c.fechaFin) + "</p>" +
        (c.descripcion ? "<p>" + esc(c.descripcion) + "</p>" : "") +
        (c.requisitos ? "<p><b>Requisitos:</b> " + esc(c.requisitos) + "</p>" : "") +
        (c.fuente ? '<p class="fuente">Fuente: ' + esc(c.fuente) + "</p>" : "") +
        '<div id="turnos' + c.id + '"></div>' +
        '<footer><button onclick="verTurnos(' + c.id + ')">Ver turnos y reservar</button></footer>' +
        "</article>";
}

/* ---------- Turnos y reservas (RF-12, RF-13, RF-14) ---------- */

async function verTurnos(idCampana) {
    const caja = document.getElementById("turnos" + idCampana);
    if (caja.innerHTML !== "") { caja.innerHTML = ""; return; } // segundo clic: oculta
    try {
        const turnos = await llamar("/turnos?campanaId=" + idCampana);
        if (turnos.length === 0) {
            caja.innerHTML = "<p>Esta campaña aún no tiene turnos.</p>";
            return;
        }
        caja.innerHTML = turnos.map(function (t) {
            const sinCupos = t.cuposDisponibles <= 0;
            return '<div class="turno"><span>' + esc(t.fecha) + " · " + esc(String(t.horaInicio).slice(0, 5)) +
                " a " + esc(String(t.horaFin).slice(0, 5)) + " · <b>" + t.cuposDisponibles + "</b> cupos</span> " +
                "<button " + (sinCupos ? "disabled" : "") + ' onclick="reservar(' + t.id + ", " + idCampana + ')">' +
                (sinCupos ? "Sin cupos" : "Reservar") + "</button></div>";
        }).join("");
    } catch (e) {
        mensaje(e.message, "error");
    }
}

async function reservar(idTurno, idCampana) {
    if (!sesion) {
        mensaje("Primero ingresa o crea tu cuenta en el panel de la derecha.", "error");
        return;
    }
    // La reserva puede hacerla un familiar a nombre del beneficiario (sección 2.2 del informe)
    const nombre = prompt("Nombre completo de quien se vacunará:");
    if (!nombre) return;
    const dni = prompt("DNI de quien se vacunará (8 dígitos):");
    if (!dni) return;
    try {
        await llamar("/reservas", {
            method: "POST",
            body: JSON.stringify({ usuarioId: sesion.id, turnoId: idTurno, beneficiarioNombre: nombre, beneficiarioDni: dni })
        });
        mensaje("¡Turno reservado! Puedes verlo en «Mis reservas».", "ok");
        document.getElementById("turnos" + idCampana).innerHTML = "";
        verTurnos(idCampana); // actualiza los cupos
    } catch (e) {
        mensaje(e.message, "error");
    }
}

async function mostrarReservas() {
    document.getElementById("filtros").classList.add("oculto");
    const cont = document.getElementById("contenido");
    try {
        const lista = await llamar("/reservas?usuarioId=" + sesion.id);
        let html = "<h2>Mis reservas</h2>";
        if (lista.length === 0) html += "<p>Aún no tienes reservas.</p>";
        html += lista.map(function (r) {
            const t = r.turno || {};
            return "<article>" +
                "<h3>" + esc(t.campana ? t.campana.vacuna : "") + "</h3>" +
                "<p><b>Para:</b> " + esc(r.beneficiarioNombre) + " (DNI " + esc(r.beneficiarioDni) + ")</p>" +
                "<p><b>Fecha:</b> " + esc(t.fecha) + " · " + esc(String(t.horaInicio).slice(0, 5)) + " a " + esc(String(t.horaFin).slice(0, 5)) + "</p>" +
                "<p><b>Estado:</b> " + esc(r.estado) + "</p>" +
                (r.estado === "RESERVADA" ? '<footer><button class="cancelar" onclick="cancelar(' + r.id + ')">Cancelar reserva</button></footer>' : "") +
                "</article>";
        }).join("");
        cont.innerHTML = html;
    } catch (e) {
        mensaje(e.message, "error");
    }
}

async function cancelar(idReserva) {
    try {
        await llamar("/reservas/" + idReserva, { method: "DELETE" });
        mensaje("Reserva cancelada. El cupo fue devuelto.", "ok");
        mostrarReservas();
    } catch (e) {
        mensaje(e.message, "error");
    }
}

/* ---------- Registro e inicio de sesión (RF-04, RF-06, RF-07, RF-08) ---------- */

document.getElementById("formLogin").addEventListener("submit", async function (e) {
    e.preventDefault();
    try {
        const datos = await llamar("/auth/login", {
            method: "POST",
            body: JSON.stringify({ correo: valor("lCorreo"), contrasena: valor("lClave") })
        });
        guardarSesion(datos);
    } catch (err) {
        mensaje(err.message, "error");
    }
});

document.getElementById("formRegistro").addEventListener("submit", async function (e) {
    e.preventDefault();
    try {
        const datos = await llamar("/auth/registro", {
            method: "POST",
            body: JSON.stringify({
                nombres: valor("rNombres"), apellidos: valor("rApellidos"),
                dni: valor("rDni"), correo: valor("rCorreo"), contrasena: valor("rClave")
            })
        });
        guardarSesion(datos);
    } catch (err) {
        mensaje(err.message, "error");
    }
});

function guardarSesion(datos) {
    sesion = datos;
    localStorage.setItem("sesion", JSON.stringify(datos));
    actualizarPantalla();
    mostrarCampanas();
}

function salir() {
    sesion = null;
    localStorage.removeItem("sesion");
    actualizarPantalla();
    mostrarCampanas();
}

function verRegistro(mostrar) {
    document.getElementById("cajaRegistro").classList.toggle("oculto", !mostrar);
    document.getElementById("cajaLogin").classList.toggle("oculto", mostrar);
}

/** Muestra u oculta las cajas y el menú según haya o no una sesión iniciada (RF-05). */
function actualizarPantalla() {
    const conSesion = sesion !== null;
    document.getElementById("cajaLogin").classList.toggle("oculto", conSesion);
    document.getElementById("cajaRegistro").classList.add("oculto");
    document.getElementById("cajaUsuario").classList.toggle("oculto", !conSesion);
    document.getElementById("menuReservas").classList.toggle("oculto", !conSesion);
    document.getElementById("menuSalir").classList.toggle("oculto", !conSesion);
    if (conSesion) document.getElementById("saludo").textContent = "Hola, " + sesion.nombres;
}

// Al abrir la página
actualizarPantalla();
mostrarCampanas();
