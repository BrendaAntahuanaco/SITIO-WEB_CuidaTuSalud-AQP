// Aplicación React de Cuida Tu Salud AQP.
// React y ReactDOM se cargan desde index.html, por eso aquí no hay "import".
// React escapa automáticamente el texto que muestra, lo que protege contra XSS.
const { useState, useEffect } = React;

// El backend (Spring Boot) sirve esta página, así que basta una ruta relativa.
const API = "/api";

/** Llama al backend. Si el servidor responde con error, lanza el mensaje que envió. */
async function llamar(ruta, opciones = {}) {
  const resp = await fetch(API + ruta, {
    headers: { "Content-Type": "application/json" },
    ...opciones,
  });
  const texto = await resp.text();
  let datos = null;
  try { datos = texto ? JSON.parse(texto) : null; } catch { /* respuesta sin JSON */ }
  if (!resp.ok) throw new Error(datos?.message || "Ocurrió un error");
  return datos;
}

/** Panel lateral: inicio de sesión, registro o saludo (RF-04, RF-07, RF-08). */
function Acceso({ sesion, alIngresar, avisar }) {
  const [registrando, setRegistrando] = useState(false);
  const [datos, setDatos] = useState({ nombres: "", apellidos: "", dni: "", correo: "", contrasena: "" });

  if (sesion) {
    return (
      <div>
        <h2>Hola, {sesion.nombres}</h2>
        <p>Ya puedes reservar turnos en cualquier campaña.</p>
      </div>
    );
  }

  const cambiar = (campo) => (e) => setDatos({ ...datos, [campo]: e.target.value });

  async function enviar(e) {
    e.preventDefault();
    try {
      const ruta = registrando ? "/auth/registro" : "/auth/login";
      const cuerpo = registrando ? datos : { correo: datos.correo, contrasena: datos.contrasena };
      alIngresar(await llamar(ruta, { method: "POST", body: JSON.stringify(cuerpo) }));
    } catch (err) {
      avisar(err.message, "error");
    }
  }

  return (
    <div>
      {/* Dos botones para elegir entre iniciar sesión y crear cuenta */}
      <button className={registrando ? "secundario" : ""} onClick={() => setRegistrando(false)}>Ya tengo cuenta</button>{" "}
      <button className={registrando ? "" : "secundario"} onClick={() => setRegistrando(true)}>Crear cuenta</button>

      <h2 style={{ marginTop: 15 }}>{registrando ? "Crear cuenta" : "Ingresar"}</h2>
      <form onSubmit={enviar}>
        {registrando && (
          <>
            <label htmlFor="nombres">Nombres</label>
            <input id="nombres" value={datos.nombres} onChange={cambiar("nombres")} required />
            <label htmlFor="apellidos">Apellidos</label>
            <input id="apellidos" value={datos.apellidos} onChange={cambiar("apellidos")} required />
            <label htmlFor="dni">DNI (8 dígitos)</label>
            <input id="dni" value={datos.dni} onChange={cambiar("dni")} maxLength={8} required />
          </>
        )}
        <label htmlFor="correo">Correo</label>
        <input id="correo" type="email" value={datos.correo} onChange={cambiar("correo")} required />
        <label htmlFor="contrasena">Contraseña (mínimo 6)</label>
        <input id="contrasena" type="password" value={datos.contrasena} onChange={cambiar("contrasena")} required />
        <button type="submit">{registrando ? "Registrarme" : "Entrar"}</button>
      </form>
    </div>
  );
}

/** Listado de campañas con filtros (RF-09, RF-10, RF-11). */
function Campanas({ sesion, avisar }) {
  const [vacuna, setVacuna] = useState("");
  const [distrito, setDistrito] = useState("");
  const [lista, setLista] = useState([]);
  const [abierta, setAbierta] = useState(null); // id de la campaña con turnos desplegados

  async function buscar() {
    try {
      const filtros = new URLSearchParams({ vacuna, distrito });
      setLista(await llamar("/campanas?" + filtros));
    } catch {
      avisar("No se pudo conectar con el servidor.", "error");
    }
  }

  // Al abrir la pantalla se cargan todas las campañas
  useEffect(() => { buscar(); }, []);

  return (
    <>
      <div id="filtros">
        <label htmlFor="fVacuna">Vacuna</label>
        <input id="fVacuna" value={vacuna} onChange={(e) => setVacuna(e.target.value)} placeholder="Ej. Sarampión" />
        <label htmlFor="fDistrito">Distrito</label>
        <input id="fDistrito" value={distrito} onChange={(e) => setDistrito(e.target.value)} placeholder="Ej. Miraflores" />
        <button onClick={buscar}>Buscar</button>
      </div>

      {lista.length === 0 && <p>No hay campañas que coincidan con la búsqueda.</p>}

      {lista.map((c) => (
        <article key={c.id}>
          <h3>{c.vacuna}</h3>
          <p><b>Lugar:</b> {c.establecimiento?.nombre} ({c.establecimiento?.distrito})</p>
          <p><b>Fechas:</b> del {c.fechaInicio} al {c.fechaFin}</p>
          {c.descripcion && <p>{c.descripcion}</p>}
          {c.requisitos && <p><b>Requisitos:</b> {c.requisitos}</p>}
          {c.fuente && <p className="fuente">Fuente: {c.fuente}</p>}
          {abierta === c.id && <Turnos campana={c} sesion={sesion} avisar={avisar} />}
          <footer>
            <button onClick={() => setAbierta(abierta === c.id ? null : c.id)}>
              {abierta === c.id ? "Ocultar turnos" : "Ver turnos y reservar"}
            </button>
          </footer>
        </article>
      ))}
    </>
  );
}

/** Turnos de una campaña y reserva con control de cupos (RF-12, RF-13). */
function Turnos({ campana, sesion, avisar }) {
  const [turnos, setTurnos] = useState([]);
  const [nombre, setNombre] = useState("");
  const [dni, setDni] = useState("");

  async function cargar() {
    try { setTurnos(await llamar("/turnos?campanaId=" + campana.id)); }
    catch (e) { avisar(e.message, "error"); }
  }

  useEffect(() => { cargar(); }, []);

  async function reservar(idTurno) {
    if (!sesion) {
      avisar("Primero ingresa o crea tu cuenta en el panel de la derecha.", "error");
      return;
    }
    try {
      await llamar("/reservas", {
        method: "POST",
        body: JSON.stringify({
          usuarioId: sesion.id,
          turnoId: idTurno,
          beneficiarioNombre: nombre,
          beneficiarioDni: dni,
        }),
      });
      avisar("¡Turno reservado! Puedes verlo en «Mis reservas».", "ok");
      cargar(); // actualiza los cupos
    } catch (e) {
      avisar(e.message, "error");
    }
  }

  if (turnos.length === 0) return <p>Esta campaña aún no tiene turnos.</p>;

  return (
    <div>
      <p className="fuente">La reserva puede hacerla un familiar a nombre del beneficiario.</p>
      <label htmlFor={"nombre" + campana.id}>Nombre de quien se vacunará</label>
      <input id={"nombre" + campana.id} value={nombre} onChange={(e) => setNombre(e.target.value)} />
      <label htmlFor={"dni" + campana.id}>DNI (8 dígitos)</label>
      <input id={"dni" + campana.id} value={dni} onChange={(e) => setDni(e.target.value)} maxLength={8} />

      {turnos.map((t) => (
        <div className="turno" key={t.id}>
          <span>
            {t.fecha} · {t.horaInicio?.slice(0, 5)} a {t.horaFin?.slice(0, 5)} · <b>{t.cuposDisponibles}</b> cupos
          </span>{" "}
          <button disabled={t.cuposDisponibles <= 0} onClick={() => reservar(t.id)}>
            {t.cuposDisponibles <= 0 ? "Sin cupos" : "Reservar"}
          </button>
        </div>
      ))}
    </div>
  );
}

/** Historial de reservas del ciudadano y cancelación (RF-14). */
function Reservas({ sesion, avisar }) {
  const [reservas, setReservas] = useState([]);

  async function cargar() {
    try { setReservas(await llamar("/reservas?usuarioId=" + sesion.id)); }
    catch (e) { avisar(e.message, "error"); }
  }

  useEffect(() => { cargar(); }, []);

  async function cancelar(id) {
    try {
      await llamar("/reservas/" + id, { method: "DELETE" });
      avisar("Reserva cancelada. El cupo fue devuelto.", "ok");
      cargar();
    } catch (e) {
      avisar(e.message, "error");
    }
  }

  return (
    <>
      <h2>Mis reservas</h2>
      {reservas.length === 0 && <p>Aún no tienes reservas.</p>}
      {reservas.map((r) => (
        <article key={r.id}>
          <h3>{r.turno?.campana?.vacuna}</h3>
          <p><b>Para:</b> {r.beneficiarioNombre} (DNI {r.beneficiarioDni})</p>
          <p><b>Fecha:</b> {r.turno?.fecha} · {r.turno?.horaInicio?.slice(0, 5)} a {r.turno?.horaFin?.slice(0, 5)}</p>
          <p><b>Estado:</b> {r.estado}</p>
          {r.estado === "RESERVADA" && (
            <footer>
              <button className="cancelar" onClick={() => cancelar(r.id)}>Cancelar reserva</button>
            </footer>
          )}
        </article>
      ))}
    </>
  );
}

/** Componente principal: estructura de la guía (#agrupar > header, nav, section, aside, footer). */
function App() {
  const [vista, setVista] = useState("campanas"); // "campanas" o "reservas"
  const [mensaje, setMensaje] = useState(null);   // { texto, tipo }
  const [sesion, setSesion] = useState(() => {
    try { return JSON.parse(localStorage.getItem("sesion")); } catch { return null; }
  });

  /** Muestra un aviso verde ("ok") o rojo ("error") por unos segundos. */
  function avisar(texto, tipo) {
    setMensaje({ texto, tipo });
    setTimeout(() => setMensaje(null), 6000);
  }

  function iniciarSesion(datos) {
    setSesion(datos);
    localStorage.setItem("sesion", JSON.stringify(datos));
    setVista("campanas");
  }

  function salir() {
    setSesion(null);
    localStorage.removeItem("sesion");
    setVista("campanas");
  }

  /** Cambia de pantalla desde un enlace del menú. */
  function ir(e, destino) {
    e.preventDefault();
    setVista(destino);
  }

  return (
    <div id="agrupar">
      <header id="cabecera">
        <h1>Cuida Tu Salud AQP</h1>
        <h2>Campañas de vacunación en Arequipa</h2>
      </header>

      <nav id="menu">
        <ul>
          <li><a href="#" onClick={(e) => ir(e, "campanas")}>Campañas</a></li>
          {sesion && <li><a href="#" onClick={(e) => ir(e, "reservas")}>Mis reservas</a></li>}
          {sesion && <li><a href="#" onClick={(e) => { e.preventDefault(); salir(); }}>Salir</a></li>}
        </ul>
      </nav>

      <section id="seccion">
        {mensaje && <div className={mensaje.tipo}>{mensaje.texto}</div>}
        {vista === "campanas" && <Campanas sesion={sesion} avisar={avisar} />}
        {vista === "reservas" && sesion && <Reservas sesion={sesion} avisar={avisar} />}
      </section>

      <aside id="columna">
        <Acceso sesion={sesion} alIngresar={iniciarSesion} avisar={avisar} />
      </aside>

      <footer id="pie">Proyecto de Programación Web · Arequipa 2026</footer>
    </div>
  );
}

// Dibuja la aplicación dentro del div "root" de index.html
ReactDOM.createRoot(document.getElementById("root")).render(<App />);
