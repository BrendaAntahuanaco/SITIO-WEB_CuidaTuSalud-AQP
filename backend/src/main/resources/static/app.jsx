const { useState, useEffect, useRef, useContext, createContext, useCallback } = React;
const API = "/api";
const Ctx = createContext(null); 
function leerSesion() {
  try { return JSON.parse(localStorage.getItem("sesion")); } catch { return null; }
}
let sesionActual = leerSesion(); 

async function llamar(ruta, opciones = {}) {
  const cabeceras = { "Content-Type": "application/json" };
  if (sesionActual?.token) cabeceras.Authorization = "Bearer " + sesionActual.token;
  let resp;
  try {
    resp = await fetch(API + ruta, { ...opciones, headers: { ...cabeceras, ...opciones.headers } });
  } catch {
    throw new Error("No se pudo conectar con el servidor. Revisa tu internet e inténtalo de nuevo.");
  }
  if (opciones.blob) {
    if (!resp.ok) throw new Error("No se pudo descargar el archivo");
    return resp.blob();
  }
  const texto = await resp.text();
  let datos = null;
  try { datos = texto ? JSON.parse(texto) : null; } catch { /* respuesta sin JSON */ }
  if (!resp.ok) {
    if (resp.status === 401 && sesionActual) window.dispatchEvent(new Event("sesion-vencida"));
    throw new Error(datos?.message || datos?.detail || "Ocurrió un error (" + resp.status + ")");
  }
  return datos;
}

const hoyLima = () => new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" }); 

function fecha(s) {
  if (!s) return "—";
  const [a, m, d] = s.slice(0, 10).split("-").map(Number);
  return new Date(a, m - 1, d).toLocaleDateString("es-PE", { weekday: "short", day: "numeric", month: "short", year: "numeric" });
}
const hora = (s) => (s ? s.slice(0, 5) : "");
function fechaHora(s) {
  if (!s) return "—";
  return new Date(s).toLocaleString("es-PE", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}
const codigoReserva = (id) => "AQP-" + String(id).padStart(6, "0");

const ESTADO_CAMPANA = {
  ACTIVA: ["ok", "Activa"],
  PROGRAMADA: ["info", "Próximamente"],
  CERRADA: ["alerta", "Cerrada"],
};
const ESTADO_RESERVA = {
  RESERVADA: ["info", "Reservada"],
  ATENDIDA: ["ok", "Atendida"],
  CANCELADA: ["error", "Cancelada"],
};
function Insignia({ tabla, estado }) {
  const [clase, texto] = tabla[estado] || ["info", estado];
  return <span className={"insignia " + clase}>{texto}</span>;
}

function useCarga(ruta, inicial = null) {
  const { avisar } = useContext(Ctx);
  const [datos, setDatos] = useState(inicial);
  const [cargando, setCargando] = useState(true);
  const cargar = useCallback(async () => {
    try { setDatos(await llamar(ruta)); }
    catch (e) { avisar(e.message, "error"); }
    finally { setCargando(false); }
  }, [ruta]);
  useEffect(() => { cargar(); }, [cargar]);
  return [datos, cargar, cargando];
}

function useRuta() {
  const leer = () => (window.location.hash.replace(/^#\/?/, "") || "inicio").split("/");
  const [ruta, setRuta] = useState(leer);
  useEffect(() => {
    const f = () => setRuta(leer());
    window.addEventListener("hashchange", f);
    return () => window.removeEventListener("hashchange", f);
  }, []);
  return ruta;
}
const ir = (destino) => { window.location.hash = "#/" + destino; };
function Campo({ id, etiqueta, ayuda, area, error, contador, ...resto }) {
  const descripcion = [error && id + "-err", ayuda && id + "-ayuda"].filter(Boolean).join(" ") || undefined;
  const propiedades = { id, "aria-invalid": error ? "true" : undefined, "aria-describedby": descripcion, className: error ? "con-error" : undefined, ...resto };
  return (
    <>
      <label htmlFor={id}>{etiqueta}</label>
      {area ? <textarea {...propiedades} /> : <input {...propiedades} />}
      {contador !== undefined && <p className="contador" aria-live="off">{contador} caracteres</p>}
      {ayuda && <p className="ayuda" id={id + "-ayuda"}>{ayuda}</p>}
      {error && <p className="error-campo" id={id + "-err"} role="alert">⚠ {error}</p>}
    </>
  );
}
/
function ErrorCampo({ id, error }) {
  return error ? <p className="error-campo" id={id + "-err"} role="alert">⚠ {error}</p> : null;
}

function useFormulario(inicial, reglas, prefijo = "f-") {
  const [valores, setValores] = useState(inicial);
  const [errores, setErrores] = useState({});
  const [tocados, setTocados] = useState({});
  const calcular = (campo, v) => (reglas[campo] ? reglas[campo](v[campo], v) : "");

  const cambiar = (campo, limpiar) => (e) => {
    const bruto = e.target.type === "checkbox" ? e.target.checked : e.target.value;
    const valor = limpiar && typeof bruto === "string" ? limpiar(bruto) : bruto;
    const nuevos = { ...valores, [campo]: valor };
    const esCasilla = e.target.type === "checkbox" || e.target.type === "radio";
    const visitados = esCasilla ? { ...tocados, [campo]: true } : tocados; 
    setValores(nuevos);
    setTocados(visitados);
    setErrores((prev) => {
      const sig = { ...prev };
      for (const k of Object.keys(reglas)) if (visitados[k]) sig[k] = calcular(k, nuevos); 
      return sig;
    });
  };
  const alSalir = (campo) => () => {
    setTocados((t) => ({ ...t, [campo]: true }));
    setErrores((prev) => ({ ...prev, [campo]: calcular(campo, valores) }));
  };

  const campo = (nombre, limpiar) => ({
    id: prefijo + nombre, value: valores[nombre], error: errores[nombre],
    onChange: cambiar(nombre, limpiar), onBlur: alSalir(nombre),
  });

  function validarTodo() {
    const todos = {};
    for (const k of Object.keys(reglas)) todos[k] = calcular(k, valores);
    setErrores(todos);
    setTocados(Object.fromEntries(Object.keys(reglas).map((k) => [k, true])));
    const primero = Validar.primerError(todos);
    if (primero) document.getElementById(prefijo + primero)?.focus();
    return !primero;
  }
  const reiniciar = () => { setValores(inicial); setErrores({}); setTocados({}); };
  return { valores, errores, setValores, cambiar, campo, validarTodo, reiniciar, prefijo };
}

function Cargando({ cargando, vacio, children }) {
  if (cargando) return <p className="vacio" role="status">Cargando…</p>;
  if (vacio) return <p className="vacio">{vacio}</p>;
  return children;
}

function Protegida({ rol, children }) {
  const { sesion } = useContext(Ctx);
  if (!sesion)
    return (
      <div className="tarjeta">
        <h1>Necesitas ingresar</h1>
        <p>Para ver esta pantalla primero inicia sesión.</p>
        <a className="boton grande" href="#/ingresar">Ingresar</a>
      </div>
    );
  if (rol && sesion.rol !== rol)
    return (
      <div className="tarjeta">
        <h1>Acceso restringido</h1>
        <p>Esta pantalla es solo para administradores.</p>
        <a className="boton" href="#/inicio">Volver al inicio</a>
      </div>
    );
  return children;
}
function Inicio() {
  const { sesion, avisar } = useContext(Ctx);
  const [campanas, setCampanas] = useState(null);
  const [cupos, setCupos] = useState({});
  const [avisos, setAvisos] = useState(null);

  useEffect(() => {
    Promise.all([llamar("/campanas?estado=ACTIVA"), llamar("/campanas/cupos"), llamar("/avisos")])
      .then(([c, q, a]) => { setCampanas(c); setCupos(q); setAvisos(a); })
      .catch((e) => { avisar(e.message, "error"); setCampanas([]); setAvisos([]); });
  }, []);

  const hoy = hoyLima();
  const destacadas = (campanas || [])
    .filter((c) => c.fechaFin >= hoy)
    .sort((a, b) => (a.fechaInicio > hoy ? a.fechaInicio : hoy).localeCompare(b.fechaInicio > hoy ? b.fechaInicio : hoy))
    .slice(0, 3);

  return (
    <>
      <section className="portada" aria-labelledby="titulo-portada">
        <h1 id="titulo-portada">Vacúnate sin colas: reserva tu turno</h1>
        <p>
          Consulta las campañas de vacunación de Arequipa, mira fechas, lugares y cupos, y reserva tu turno
          desde el celular o la computadora.
        </p>
        <div className="fila">
          <a className="boton grande" href="#/campanas">Ver campañas</a>
          {!sesion && <a className="boton grande secundario" href="#/registro">Crear cuenta</a>}
          {!sesion && <a className="boton grande secundario" href="#/ingresar">Ingresar</a>}
          {sesion && <a className="boton grande secundario" href="#/mis-reservas">Mis reservas</a>}
        </div>
      </section>

      <h2>¿Cómo funciona?</h2>
      <ol className="pasos">
        <li><b>Elige una campaña</b><br />Filtra por vacuna, distrito o fecha.</li>
        <li><b>Crea tu cuenta</b><br />Solo necesitas tu DNI y un correo.</li>
        <li><b>Reserva tu turno</b><br />Tú o un familiar, a nombre de quien se vacunará.</li>
        <li><b>Imprime tu comprobante</b><br />Llévalo el día de la campaña.</li>
      </ol>

      <h2>Campañas destacadas</h2>
      <Cargando cargando={campanas === null} vacio={destacadas.length === 0 && "Por ahora no hay campañas activas."}>
        <div className="rejilla">
          {destacadas.map((c) => (
            <article className="tarjeta" key={c.id}>
              <h3>{c.vacuna}</h3>
              <p><Insignia tabla={ESTADO_CAMPANA} estado={c.estado} /></p>
              <p><b>Lugar:</b> {c.establecimiento?.nombre} ({c.establecimiento?.distrito})</p>
              <p><b>Fechas:</b> del {fecha(c.fechaInicio)} al {fecha(c.fechaFin)}</p>
              <p><b>Cupos libres:</b> {cupos[c.id] ?? 0}</p>
              <div className="fila"><a className="boton" href={"#/campanas/" + c.id}>Ver y reservar</a></div>
            </article>
          ))}
        </div>
      </Cargando>

      <h2>Noticias y avisos</h2>
      <Cargando cargando={avisos === null} vacio={avisos?.length === 0 && "No hay avisos publicados."}>
        {(avisos || []).map((a) => (
          <article className="tarjeta" key={a.id}>
            <h3>{a.titulo}</h3>
            <p>{a.contenido}</p>
            <p className="fuente">Fuente: {a.fuente} · Publicado el {fechaHora(a.fechaPublicacion)}</p>
          </article>
        ))}
      </Cargando>

      <div className="tarjeta">
        <h2>¿Necesitas ayuda para reservar?</h2>
        <p>
          La reserva puede hacerla un familiar o una persona de confianza a nombre de quien se vacunará.
          Esta página solo informa y organiza los turnos: no da diagnósticos ni recomendaciones médicas.
        </p>
      </div>
    </>
  );
}
function Campanas({ ruta }) {
  const { avisar } = useContext(Ctx);
  const [filtros, setFiltros] = useState({ vacuna: "", distrito: "", fecha: "", estado: "" });
  const [lista, setLista] = useState(null);
  const [cupos, setCupos] = useState({});
  const [estabs] = useCarga("/establecimientos", []);
  const [panel, setPanel] = useState(ruta[1] ? { id: Number(ruta[1]), tipo: "reserva" } : null);

  async function buscar(f = filtros) {
    try {
      const [c, q] = await Promise.all([llamar("/campanas?" + new URLSearchParams(f)), llamar("/campanas/cupos")]);
      const orden = { ACTIVA: 0, PROGRAMADA: 1, CERRADA: 2 };
      setLista(c.sort((a, b) => (orden[a.estado] - orden[b.estado]) || a.fechaInicio.localeCompare(b.fechaInicio)));
      setCupos(q);
    } catch (e) {
      avisar(e.message, "error");
      setLista([]);
    }
  }
  useEffect(() => { buscar(); }, []);

  const cambiar = (campo) => (e) => setFiltros({ ...filtros, [campo]: e.target.value });
  const distritos = [...new Set(estabs.map((e) => e.distrito))].sort();
  const alternar = (id, tipo) => setPanel(panel && panel.id === id && panel.tipo === tipo ? null : { id, tipo });

  return (
    <>
      <h1>Campañas de salud</h1>
      <form className="tarjeta" onSubmit={(e) => { e.preventDefault(); buscar(); }} aria-label="Filtros de búsqueda">
        <div className="dos">
          <div><Campo id="fVacuna" etiqueta="Vacuna" value={filtros.vacuna} onChange={cambiar("vacuna")} placeholder="Ej. Sarampión" /></div>
          <div>
            <label htmlFor="fDistrito">Distrito</label>
            <select id="fDistrito" value={filtros.distrito} onChange={cambiar("distrito")}>
              <option value="">Todos los distritos</option>
              {distritos.map((d) => <option key={d}>{d}</option>)}
            </select>
          </div>
          <div><Campo id="fFecha" etiqueta="Fecha" type="date" value={filtros.fecha} onChange={cambiar("fecha")} /></div>
          <div>
            <label htmlFor="fEstado">Estado</label>
            <select id="fEstado" value={filtros.estado} onChange={cambiar("estado")}>
              <option value="">Todos</option>
              <option value="ACTIVA">Activas</option>
              <option value="PROGRAMADA">Próximamente</option>
              <option value="CERRADA">Cerradas</option>
            </select>
          </div>
        </div>
        <div className="fila" style={{ marginTop: ".8rem" }}>
          <button type="submit">Buscar</button>
          <button type="button" className="secundario"
            onClick={() => { const v = { vacuna: "", distrito: "", fecha: "", estado: "" }; setFiltros(v); buscar(v); }}>
            Limpiar filtros
          </button>
        </div>
      </form>

      <p role="status" className="ayuda">{lista && `${lista.length} campaña(s) encontrada(s)`}</p>
      <Cargando cargando={lista === null} vacio={lista?.length === 0 && "No hay campañas que coincidan con la búsqueda."}>
        {(lista || []).map((c) => (
          <article className="tarjeta" key={c.id} id={"campana-" + c.id}>
            <h2>{c.vacuna} <Insignia tabla={ESTADO_CAMPANA} estado={c.estado} /></h2>
            <p><b>Lugar:</b> {c.establecimiento?.nombre} ({c.establecimiento?.distrito}) · {c.establecimiento?.direccion}{" "}
              <a target="_blank" rel="noopener noreferrer"
                href={"https://www.openstreetmap.org/search?query=" + encodeURIComponent(c.establecimiento?.nombre + ", Arequipa")}>
                Cómo llegar (se abre en otra pestaña)
              </a>
            </p>
            <p><b>Fechas:</b> del {fecha(c.fechaInicio)} al {fecha(c.fechaFin)}</p>
            <p>
              <b>Cupos libres:</b>{" "}
              {(cupos[c.id] ?? 0) > 0 ? <span className="insignia ok">Quedan {cupos[c.id]}</span> : <span className="insignia error">Sin cupos</span>}
            </p>

            {panel?.id === c.id && panel.tipo === "detalle" && (
              <div className="panel-reserva">
                {c.descripcion && <p>{c.descripcion}</p>}
                {c.requisitos && <p><b>Requisitos:</b> {c.requisitos}</p>}
                <p className="fuente">Fuente: {c.fuente || "No indicada"} · Última actualización: {fechaHora(c.fechaActualizacion)}</p>
              </div>
            )}
            {panel?.id === c.id && panel.tipo === "reserva" && <PanelReserva campana={c} alCambiar={() => buscar()} />}

            <div className="fila" style={{ marginTop: ".6rem" }}>
              <button className="secundario" aria-expanded={panel?.id === c.id && panel.tipo === "detalle"} onClick={() => alternar(c.id, "detalle")}>
                {panel?.id === c.id && panel.tipo === "detalle" ? "Ocultar detalle" : "Ver detalle"}
              </button>
              {c.estado !== "CERRADA" && (
                <button aria-expanded={panel?.id === c.id && panel.tipo === "reserva"} onClick={() => alternar(c.id, "reserva")}>
                  {panel?.id === c.id && panel.tipo === "reserva" ? "Cerrar reserva" : "Reservar turno"}
                </button>
              )}
            </div>
          </article>
        ))}
      </Cargando>
    </>
  );
}

function PanelReserva({ campana, alCambiar }) {
  const { sesion, avisar } = useContext(Ctx);
  const [turnos, setTurnos] = useState(null);
  const [turnoId, setTurnoId] = useState(null);
  const [paraMi, setParaMi] = useState(true);
  const [enviando, setEnviando] = useState(false);
  const f = useFormulario({ nombre: "", dni: "" }, {
    nombre: (v) => (paraMi ? "" : Validar.nombre(v, "el nombre de quien se vacunará")),
    dni: (v) => (paraMi ? "" : Validar.dni(v)),
  }, "rb" + campana.id + "-");
  const otro = f.valores;

  async function cargar() {
    try { setTurnos(await llamar("/turnos?campanaId=" + campana.id)); }
    catch (e) { avisar(e.message, "error"); setTurnos([]); }
  }
  useEffect(() => { cargar(); }, []);

  if (!sesion)
    return (
      <div className="panel-reserva">
        <p><b>Para reservar necesitas una cuenta.</b> Es gratis y toma un minuto.</p>
        <div className="fila">
          <a className="boton" href="#/ingresar">Ingresar</a>
          <a className="boton secundario" href="#/registro">Crear cuenta</a>
        </div>
      </div>
    );

  const hoy = hoyLima();
  const disponibles = (turnos || []).filter((t) => t.fecha >= hoy);
  const nombre = paraMi ? `${sesion.nombres} ${sesion.apellidos}` : otro.nombre;
  const dni = paraMi ? sesion.dni : otro.dni;

  async function confirmar(e) {
    e.preventDefault();
    if (!turnoId) { avisar("Elige un horario para continuar.", "error"); return; }
    if (!f.validarTodo()) return;
    setEnviando(true);
    try {
      const r = await llamar("/reservas", {
        method: "POST",
        body: JSON.stringify({ turnoId, beneficiarioNombre: nombre, beneficiarioDni: dni }),
      });
      avisar("¡Turno reservado! Aquí está tu comprobante.", "ok");
      alCambiar();
      ir("comprobante/" + r.id);
    } catch (err) {
      avisar(err.message, "error");
      cargar(); // por si el cupo se agotó mientras elegías
      setTurnoId(null);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className="panel-reserva" onSubmit={confirmar} noValidate aria-label={"Reservar turno de " + campana.vacuna}>
      <Cargando cargando={turnos === null} vacio={disponibles.length === 0 && "Esta campaña aún no tiene turnos disponibles."}>
        <fieldset className="turnos">
          <legend>1. Elige un horario</legend>
          {disponibles.map((t) => {
            const lleno = t.cuposDisponibles <= 0;
            return (
              <label key={t.id} className={"turno" + (turnoId === t.id ? " elegido" : "") + (lleno ? " lleno" : "")} style={{ margin: ".4rem 0" }}>
                <input type="radio" name={"turno" + campana.id} disabled={lleno} checked={turnoId === t.id} onChange={() => setTurnoId(t.id)} />
                <span>{fecha(t.fecha)} · {hora(t.horaInicio)} a {hora(t.horaFin)}</span>
                <b>{lleno ? "Sin cupos" : `Quedan ${t.cuposDisponibles}`}</b>
              </label>
            );
          })}
        </fieldset>

        <fieldset className="turnos">
          <legend>2. ¿Quién se vacunará?</legend>
          <div className="marcar">
            <input id={"paraMi" + campana.id} type="checkbox" checked={paraMi} onChange={(e) => setParaMi(e.target.checked)} />
            <label htmlFor={"paraMi" + campana.id}>Es para mí ({sesion.nombres} {sesion.apellidos}, DNI {sesion.dni})</label>
          </div>
          {!paraMi && (
            <>
              <Campo etiqueta="Nombre completo de quien se vacunará" autoComplete="off"
                ayuda="Si es un menor de edad, escribe su nombre: tú, como padre, madre o apoderado, lo acompañarás." {...f.campo("nombre")} />
              <Campo etiqueta="DNI (8 dígitos)" inputMode="numeric" maxLength={8} autoComplete="off"
                {...f.campo("dni", (t) => t.replace(/\D/g, ""))} />
            </>
          )}
        </fieldset>

        <p className="ayuda">Al reservar aceptas que usemos estos datos solo para organizar la campaña. <a href="#/privacidad" target="_blank" rel="noopener noreferrer">Política de privacidad</a>.</p>
        <button type="submit" className="grande" disabled={!turnoId || enviando}>
          {enviando ? "Reservando…" : "3. Confirmar reserva"}
        </button>
      </Cargando>
    </form>
  );
}
function Ingresar() {
  const { entrar, avisar } = useContext(Ctx);
  const f = useFormulario({ correo: "", contrasena: "" }, {
    correo: (v) => Validar.email(v),
    contrasena: (v) => Validar.obligatorio(v, "tu contraseña"),
  }, "in-");
  const [ver, setVer] = useState(false);
  const [enviando, setEnviando] = useState(false);

  async function enviar(e) {
    e.preventDefault();
    if (!f.validarTodo()) return;
    setEnviando(true);
    try { entrar(await llamar("/auth/login", { method: "POST", body: JSON.stringify(f.valores) })); }
    catch (err) { avisar(err.message, "error"); }
    finally { setEnviando(false); }
  }

  return (
    <div className="tarjeta" style={{ maxWidth: 520 }}>
      <h1>Ingresar</h1>
      <form onSubmit={enviar} noValidate>
        <Campo etiqueta="Correo electrónico" type="email" autoComplete="email" {...f.campo("correo")} />
        <Campo etiqueta="Contraseña" type={ver ? "text" : "password"} autoComplete="current-password" {...f.campo("contrasena")} />
        <div className="marcar">
          <input id="in-ver" type="checkbox" checked={ver} onChange={(e) => setVer(e.target.checked)} />
          <label htmlFor="in-ver">Mostrar contraseña</label>
        </div>
        <button className="grande" type="submit" disabled={enviando}>{enviando ? "Ingresando…" : "Ingresar"}</button>
      </form>
      <p style={{ marginTop: "1rem" }}>¿No tienes cuenta? <a href="#/registro">Crear cuenta</a></p>
    </div>
  );
}

function FuerzaClave({ clave }) {
  const nivel = Validar.fuerza(clave);
  const textos = ["", "Débil", "Aceptable", "Buena", "Muy buena"];
  if (!clave) return null;
  return (
    <div className="fuerza" role="status" aria-label={"Seguridad de la contraseña: " + textos[nivel]}>
      <div className={"barra-fuerza nivel" + nivel}><i /><i /><i /><i /></div>
      <span>{textos[nivel]}</span>
    </div>
  );
}

function Registro() {
  const { entrar, avisar } = useContext(Ctx);
  const f = useFormulario(
    { nombres: "", apellidos: "", dni: "", correo: "", contrasena: "", confirmar: "", aceptaTerminos: false },
    {
      nombres: (v) => Validar.nombre(v, "tus nombres"),
      apellidos: (v) => Validar.nombre(v, "tus apellidos"),
      dni: (v) => Validar.dni(v),
      correo: (v) => Validar.email(v),
      contrasena: (v) => Validar.contrasena(v),
      confirmar: (v, t) => (Validar.obligatorio(v) ? "Repite tu contraseña." : v !== t.contrasena ? "Las contraseñas no coinciden." : ""),
      aceptaTerminos: (v) => Validar.marcado(v, "Para crear tu cuenta debes aceptar la política de privacidad."),
    }, "re-");
  const [ver, setVer] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const soloDigitos = (t) => t.replace(/\D/g, "");

  async function enviar(e) {
    e.preventDefault();
    if (!f.validarTodo()) return;
    const { confirmar, ...datos } = f.valores; 
    setEnviando(true);
    try { entrar(await llamar("/auth/registro", { method: "POST", body: JSON.stringify(datos) })); avisar("¡Cuenta creada! Ya puedes reservar turnos.", "ok"); }
    catch (err) { avisar(err.message, "error"); }
    finally { setEnviando(false); }
  }

  return (
    <div className="tarjeta" style={{ maxWidth: 560 }}>
      <h1>Crear cuenta</h1>
      <p className="ayuda">Pedimos solo lo necesario: tu nombre, DNI y correo. Todos los datos son obligatorios.</p>
      <form onSubmit={enviar} noValidate>
        <Campo etiqueta="Nombres" autoComplete="given-name" {...f.campo("nombres")} />
        <Campo etiqueta="Apellidos" autoComplete="family-name" {...f.campo("apellidos")} />
        <Campo etiqueta="DNI (8 dígitos)" inputMode="numeric" maxLength={8} autoComplete="off" {...f.campo("dni", soloDigitos)} />
        <Campo etiqueta="Correo electrónico" type="email" autoComplete="email" {...f.campo("correo")} />
        <Campo etiqueta="Contraseña (mínimo 6 caracteres)" type={ver ? "text" : "password"} autoComplete="new-password" {...f.campo("contrasena")} />
        <FuerzaClave clave={f.valores.contrasena} />
        <Campo etiqueta="Repite la contraseña" type={ver ? "text" : "password"} autoComplete="new-password" {...f.campo("confirmar")} />
        <div className="marcar">
          <input id="re-ver" type="checkbox" checked={ver} onChange={(e) => setVer(e.target.checked)} />
          <label htmlFor="re-ver">Mostrar contraseñas</label>
        </div>
        <div className="marcar">
          <input id="re-aceptaTerminos" type="checkbox" checked={f.valores.aceptaTerminos} onChange={f.cambiar("aceptaTerminos")}
            aria-invalid={f.errores.aceptaTerminos ? "true" : undefined} aria-describedby={f.errores.aceptaTerminos ? "re-aceptaTerminos-err" : undefined} />
          <label htmlFor="re-aceptaTerminos">
            Acepto la <a href="#/privacidad" target="_blank" rel="noopener noreferrer">política de privacidad y los términos de uso</a> y
            autorizo el uso de mis datos para reservar turnos.
          </label>
        </div>
        <ErrorCampo id="re-aceptaTerminos" error={f.errores.aceptaTerminos} />
        <button className="grande" type="submit" disabled={enviando}>{enviando ? "Creando…" : "Crear mi cuenta"}</button>
      </form>
      <p style={{ marginTop: "1rem" }}>¿Ya tienes cuenta? <a href="#/ingresar">Ingresar</a></p>
    </div>
  );
}
function MisReservas() {
  const { avisar } = useContext(Ctx);
  const [reservas, cargar, cargando] = useCarga("/reservas", []);

  async function cancelar(r) {
    if (!window.confirm("¿Cancelar la reserva de " + r.beneficiarioNombre + "? El cupo quedará libre para otra persona.")) return;
    try {
      await llamar("/reservas/" + r.id, { method: "DELETE" });
      avisar("Reserva cancelada. El cupo fue devuelto.", "ok");
      cargar();
    } catch (e) { avisar(e.message, "error"); }
  }

  const activas = reservas.filter((r) => r.estado === "RESERVADA");
  const atendidas = reservas.filter((r) => r.estado === "ATENDIDA");
  const canceladas = reservas.filter((r) => r.estado === "CANCELADA");

  const Tarjeta = ({ r, acciones }) => (
    <article className="tarjeta">
      <h3>{r.turno?.campana?.vacuna} <Insignia tabla={ESTADO_RESERVA} estado={r.estado} /></h3>
      <p><b>Para:</b> {r.beneficiarioNombre} (DNI {r.beneficiarioDni})</p>
      <p><b>Cuándo:</b> {fecha(r.turno?.fecha)} · {hora(r.turno?.horaInicio)} a {hora(r.turno?.horaFin)}</p>
      <p><b>Dónde:</b> {r.turno?.campana?.establecimiento?.nombre}</p>
      {r.fechaAtencion && <p><b>Atendido el:</b> {fechaHora(r.fechaAtencion)}</p>}
      {acciones && <div className="fila">{acciones}</div>}
    </article>
  );

  return (
    <>
      <h1>Mis reservas</h1>
      <Cargando cargando={cargando} vacio={reservas.length === 0 && "Aún no tienes reservas."}>
        {reservas.length === 0 && <a className="boton" href="#/campanas">Ver campañas</a>}
        {activas.length > 0 && <h2>Próximos turnos</h2>}
        {activas.map((r) => (
          <Tarjeta key={r.id} r={r} acciones={<>
            <a className="boton" href={"#/comprobante/" + r.id}>Ver comprobante</a>
            <button className="peligro" onClick={() => cancelar(r)}>Cancelar reserva</button>
          </>} />
        ))}
        {atendidas.length > 0 && <h2>Historial de participación</h2>}
        {atendidas.map((r) => <Tarjeta key={r.id} r={r} acciones={<a className="boton secundario" href={"#/comprobante/" + r.id}>Ver comprobante</a>} />)}
        {canceladas.length > 0 && <h2>Reservas canceladas</h2>}
        {canceladas.map((r) => <Tarjeta key={r.id} r={r} />)}
      </Cargando>
    </>
  );
}

function Comprobante({ id }) {
  const [reservas, , cargando] = useCarga("/reservas", []);
  const r = reservas.find((x) => String(x.id) === String(id));

  return (
    <>
      <h1 className="no-imprimir">Comprobante de turno</h1>
      <Cargando cargando={cargando} vacio={!r && "No encontramos esa reserva en tu cuenta."}>
        {r && (
          <>
            <section className="comprobante" aria-label="Comprobante">
              <p className="fuente" style={{ margin: 0 }}>Cuida Tu Salud AQP · Comprobante de turno</p>
              <p className="codigo">{codigoReserva(r.id)}</p>
              <p><Insignia tabla={ESTADO_RESERVA} estado={r.estado} /></p>
              <dl>
                <dt>Campaña</dt><dd>{r.turno?.campana?.vacuna}</dd>
                <dt>Beneficiario</dt><dd>{r.beneficiarioNombre}</dd>
                <dt>DNI</dt><dd>{r.beneficiarioDni}</dd>
                <dt>Fecha</dt><dd>{fecha(r.turno?.fecha)}</dd>
                <dt>Horario</dt><dd>{hora(r.turno?.horaInicio)} a {hora(r.turno?.horaFin)}</dd>
                <dt>Lugar</dt><dd>{r.turno?.campana?.establecimiento?.nombre}, {r.turno?.campana?.establecimiento?.direccion}</dd>
                <dt>Reservado el</dt><dd>{fechaHora(r.fechaReserva)}</dd>
              </dl>
              {r.turno?.campana?.requisitos && <p><b>Lleva:</b> {r.turno.campana.requisitos}</p>}
              <p className="fuente">Los menores de edad deben ir acompañados por su padre, madre o apoderado.</p>
            </section>
            <div className="fila no-imprimir" style={{ marginTop: "1rem" }}>
              <button className="grande" onClick={() => window.print()}>Imprimir comprobante</button>
              <a className="boton grande secundario" href="#/mis-reservas">Volver a mis reservas</a>
            </div>
          </>
        )}
      </Cargando>
    </>
  );
}
const CONTACTO = { correo: "contacto@cuidatusalud.pe", celular: "", direccion: "Arequipa, Perú" };
const REDES = [
  { nombre: "Facebook", url: "https://www.facebook.com/", ico: "f" },
  { nombre: "Instagram", url: "https://www.instagram.com/", ico: "i" },
  { nombre: "WhatsApp", url: "https://wa.me/", ico: "w" },
  { nombre: "YouTube", url: "https://www.youtube.com/", ico: "y" },
];
const INTEGRANTES = ["Antahuanaco Ticona, Brenda Adeli", "Ramos Gallegos, Maythee Alexandra", "Roque Sullca, Waldemar Emil"];
const MOTIVOS = ["Duda sobre una campaña", "Ayuda para reservar un turno", "Error en la página", "Sugerencia", "Otro"];

function RedesSociales() {
  return (
    <ul className="redes" aria-label="Redes sociales">
      {REDES.map((r) => (
        <li key={r.nombre}>
          <a href={r.url} target="_blank" rel="noopener noreferrer" aria-label={r.nombre + " (se abre en otra pestaña)"}>
            <span className="ico" aria-hidden="true">{r.ico}</span>{r.nombre}
          </a>
        </li>
      ))}
    </ul>
  );
}

function Nosotros() {
  const { avisar } = useContext(Ctx);
  const f = useFormulario({ nombre: "", correo: "", celular: "", motivo: "", respuesta: "", mensaje: "", acepta: false }, {
    nombre: (v) => Validar.nombre(v, "tu nombre"),
    correo: (v) => Validar.email(v),
    celular: (v) => Validar.telefono(v, true),
    motivo: (v) => Validar.seleccion(v, "el motivo de tu mensaje"),
    respuesta: (v) => Validar.grupo(v, "Elige cómo prefieres que te respondamos."),
    mensaje: (v) => Validar.minimo(v, 10, "tu mensaje") || Validar.maximo(v, 500),
    acepta: (v) => Validar.marcado(v, "Debes aceptar el uso de tus datos para poder responderte."),
  }, "co-");
  const [enviado, setEnviado] = useState(null);
  const { valores: v, errores: er } = f;

  function enviar(e) {
    e.preventDefault();
    if (!f.validarTodo()) return;
    // Este prototipo no guarda mensajes en el servidor: se abre el programa de correo con el texto ya escrito.
    const cuerpo = `${v.mensaje}\n\n— ${v.nombre}\nCorreo: ${v.correo}${v.celular ? "\nCelular: " + v.celular : ""}\nPrefiere respuesta por: ${v.respuesta}`;
    window.location.href = `mailto:${CONTACTO.correo}?subject=${encodeURIComponent(v.motivo)}&body=${encodeURIComponent(cuerpo)}`;
    setEnviado(v.nombre.trim());
    avisar("Mensaje preparado. Se abrió tu programa de correo para enviarlo.", "ok");
    f.reiniciar();
  }

  return (
    <>
      <h1>Nosotros y contacto</h1>
      <div className="tarjeta">
        <h2>¿Qué es Cuida Tu Salud AQP?</h2>
        <p>
          Es una plataforma web para consultar en un solo lugar las campañas de vacunación de Arequipa y reservar turnos con
          control de cupos, para reducir la desinformación, las colas y las aglomeraciones en los establecimientos de salud.
          Es un proyecto del curso de Programación Web.
        </p>
        <h3>Equipo</h3>
        <ul>{INTEGRANTES.map((i) => <li key={i}>{i}</li>)}</ul>
        <h3>Datos de contacto</h3>
        <p><b>Correo:</b> <a href={"mailto:" + CONTACTO.correo}>{CONTACTO.correo}</a>
          {CONTACTO.celular && <><br /><b>Celular:</b> {CONTACTO.celular}</>}
          <br /><b>Ubicación:</b> {CONTACTO.direccion}</p>
        <h3>Síguenos</h3>
        <RedesSociales />
      </div>

      <div className="tarjeta" style={{ maxWidth: 640 }}>
        <h2>Escríbenos</h2>
        {enviado && <p className="mensaje ok" role="status">Gracias, {enviado}. Revisa tu programa de correo para enviar el mensaje.</p>}
        <form onSubmit={enviar} noValidate>
          <Campo etiqueta="Tu nombre" autoComplete="name" {...f.campo("nombre")} />
          <Campo etiqueta="Correo electrónico" type="email" autoComplete="email" {...f.campo("correo")} />
          <Campo etiqueta="Celular (opcional)" type="tel" inputMode="tel" autoComplete="tel" ayuda="9 dígitos, empieza con 9. Ej. 987654321"
            {...f.campo("celular")} />

          <label htmlFor="co-motivo">Motivo</label>
          <select id="co-motivo" value={v.motivo} onChange={f.cambiar("motivo")} onBlur={f.campo("motivo").onBlur} className={er.motivo ? "con-error" : undefined}
            aria-invalid={er.motivo ? "true" : undefined} aria-describedby={er.motivo ? "co-motivo-err" : undefined}>
            <option value="">- Selecciona un motivo -</option>
            {MOTIVOS.map((m) => <option key={m}>{m}</option>)}
          </select>
          <ErrorCampo id="co-motivo" error={er.motivo} />

          <fieldset className="turnos" aria-describedby={er.respuesta ? "co-respuesta-err" : undefined}>
            <legend>¿Cómo prefieres que te respondamos?</legend>
            {["Correo", "WhatsApp", "Llamada"].map((o) => (
              <div className="marcar" key={o}>
                <input id={"co-resp-" + o} type="radio" name="respuesta" value={o} checked={v.respuesta === o} onChange={f.cambiar("respuesta")} />
                <label htmlFor={"co-resp-" + o}>{o}</label>
              </div>
            ))}
          </fieldset>
          <ErrorCampo id="co-respuesta" error={er.respuesta} />

          <Campo area rows={5} maxLength={600} etiqueta="Mensaje" contador={v.mensaje.length + " de 500"} {...f.campo("mensaje")} />

          <div className="marcar">
            <input id="co-acepta" type="checkbox" checked={v.acepta} onChange={f.cambiar("acepta")}
              aria-invalid={er.acepta ? "true" : undefined} aria-describedby={er.acepta ? "co-acepta-err" : undefined} />
            <label htmlFor="co-acepta">Acepto que usen mis datos solo para responderme (<a href="#/privacidad" target="_blank" rel="noopener noreferrer">ver privacidad</a>).</label>
          </div>
          <ErrorCampo id="co-acepta" error={er.acepta} />
          <button className="grande" type="submit">Preparar mensaje</button>
        </form>
      </div>
    </>
  );
}
function Privacidad() {
  return (
    <div className="tarjeta">
      <h1>Privacidad y términos de uso</h1>
      <p className="ayuda">Explicado en palabras sencillas. Proyecto académico de Programación Web (prototipo con datos de prueba).</p>
      <h2>Qué datos pedimos y para qué</h2>
      <p>
        Pedimos tu nombre, DNI y correo para crear tu cuenta, y el nombre y DNI de quien se vacunará para reservar
        un turno. Usamos estos datos solo para organizar las campañas, evitar reservas duplicadas y mostrar
        estadísticas generales. Pedimos únicamente lo necesario.
      </p>
      <h2>Tus derechos</h2>
      <p>
        Según la Ley N.º 29733 (Protección de Datos Personales) puedes pedir acceder a tus datos, corregirlos o
        eliminarlos. Tu contraseña se guarda cifrada y nadie, ni el equipo administrador, puede verla.
      </p>
      <h2>Menores de edad</h2>
      <p>La reserva para un menor la hace su padre, madre o apoderado, y debe acompañarlo el día de la campaña.</p>
      <h2>Información de salud</h2>
      <p>
        Esta plataforma informa y organiza turnos. No emite diagnósticos ni recomendaciones médicas. Cada campaña
        muestra su fuente y la fecha de su última actualización; solo el personal autorizado las publica.
      </p>
      <h2>Uso responsable</h2>
      <p>Reserva solo si vas a asistir y cancela si no puedes ir, para que otra persona use el cupo.</p>
    </div>
  );
}
const PESTANAS = [
  ["campanas", "Campañas"],
  ["establecimientos", "Establecimientos"],
  ["avisos", "Avisos"],
  ["asistencia", "Asistencia"],
  ["usuarios", "Usuarios"],
  ["reportes", "Reportes"],
  ["bitacora", "Bitácora"],
];

function Admin({ ruta }) {
  const tab = ruta[1] || "campanas";
  const Pantalla = { campanas: AdminCampanas, establecimientos: AdminEstablecimientos, avisos: AdminAvisos,
    asistencia: AdminAsistencia, usuarios: AdminUsuarios, reportes: AdminReportes, bitacora: AdminBitacora }[tab] || AdminCampanas;
  return (
    <Protegida rol="ADMIN">
      <h1 className="no-imprimir">Panel de administración</h1>
      <nav aria-label="Secciones del panel">
        <ul className="pestanas">
          {PESTANAS.map(([id, nombre]) => (
            <li key={id}><a href={"#/admin/" + id} aria-current={tab === id ? "page" : undefined}>{nombre}</a></li>
          ))}
        </ul>
      </nav>
      <Pantalla />
    </Protegida>
  );
}

const cuerpoCampana = (c, estado) => ({
  vacuna: c.vacuna, descripcion: c.descripcion, requisitos: c.requisitos, fuente: c.fuente,
  fechaInicio: c.fechaInicio, fechaFin: c.fechaFin, estado: estado || c.estado,
  establecimiento: { id: Number(c.establecimiento?.id ?? c.establecimientoId) },
});

function AdminCampanas() {
  const { avisar } = useContext(Ctx);
  const [campanas, cargar, cargando] = useCarga("/campanas", []);
  const [cupos, cargarCupos] = useCarga("/campanas/cupos", {});
  const [estabs] = useCarga("/establecimientos", []);
  const [form, setForm] = useState(null);     // null = oculto; objeto = crear o editar
  const [turnosDe, setTurnosDe] = useState(null);

  const nueva = () => setForm({ vacuna: "", descripcion: "", requisitos: "", fuente: "", fechaInicio: hoyLima(), fechaFin: hoyLima(), estado: "ACTIVA", establecimientoId: "" });
  const editar = (c) => setForm({ ...c, establecimientoId: c.establecimiento?.id ?? "" });
  const refrescar = () => { cargar(); cargarCupos(); };

  async function guardar(e) {
    e.preventDefault();
    try {
      const cuerpo = cuerpoCampana({ ...form, establecimiento: { id: form.establecimientoId } });
      if (form.id) await llamar("/campanas/" + form.id, { method: "PUT", body: JSON.stringify(cuerpo) });
      else await llamar("/campanas", { method: "POST", body: JSON.stringify(cuerpo) });
      avisar("Campaña guardada.", "ok");
      setForm(null);
      refrescar();
    } catch (err) { avisar(err.message, "error"); }
  }

  async function cerrar(c) {
    if (!window.confirm("¿Cerrar la campaña «" + c.vacuna + "»? Ya no se podrán hacer reservas nuevas.")) return;
    try { await llamar("/campanas/" + c.id, { method: "PUT", body: JSON.stringify(cuerpoCampana(c, "CERRADA")) }); avisar("Campaña cerrada.", "ok"); refrescar(); }
    catch (err) { avisar(err.message, "error"); }
  }
  async function eliminar(c) {
    if (!window.confirm("¿Eliminar la campaña «" + c.vacuna + "» y sus turnos? Esta acción no se puede deshacer.")) return;
    try { await llamar("/campanas/" + c.id, { method: "DELETE" }); avisar("Campaña eliminada.", "ok"); refrescar(); }
    catch (err) { avisar(err.message, "error"); }
  }

  const cambiar = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });

  return (
    <>
      <div className="fila" style={{ justifyContent: "space-between" }}>
        <h2>Gestión de campañas</h2>
        {!form && <button onClick={nueva}>+ Nueva campaña</button>}
      </div>

      {form && (
        <form className="tarjeta" onSubmit={guardar} aria-label="Formulario de campaña">
          <h3>{form.id ? "Editar campaña" : "Nueva campaña"}</h3>
          <div className="dos">
            <div><Campo id="cVacuna" etiqueta="Vacuna o servicio" value={form.vacuna} onChange={cambiar("vacuna")} required maxLength={100} /></div>
            <div>
              <label htmlFor="cEst">Establecimiento</label>
              <select id="cEst" value={form.establecimientoId} onChange={cambiar("establecimientoId")} required>
                <option value="">Elige uno…</option>
                {estabs.map((e) => <option key={e.id} value={e.id}>{e.nombre} ({e.distrito})</option>)}
              </select>
            </div>
            <div><Campo id="cIni" etiqueta="Fecha de inicio" type="date" value={form.fechaInicio} onChange={cambiar("fechaInicio")} required /></div>
            <div><Campo id="cFin" etiqueta="Fecha de fin" type="date" value={form.fechaFin} onChange={cambiar("fechaFin")} required min={form.fechaInicio} /></div>
            <div>
              <label htmlFor="cEstado">Estado</label>
              <select id="cEstado" value={form.estado} onChange={cambiar("estado")}>
                <option value="ACTIVA">Activa</option><option value="PROGRAMADA">Programada</option><option value="CERRADA">Cerrada</option>
              </select>
            </div>
            <div><Campo id="cFuente" etiqueta="Fuente de la información" value={form.fuente || ""} onChange={cambiar("fuente")} maxLength={200} placeholder="Ej. GERESA Arequipa" /></div>
          </div>
          <Campo id="cDesc" etiqueta="Descripción" area value={form.descripcion || ""} onChange={cambiar("descripcion")} maxLength={1000} />
          <Campo id="cReq" etiqueta="Requisitos" area value={form.requisitos || ""} onChange={cambiar("requisitos")} maxLength={1000} />
          <div className="fila" style={{ marginTop: ".8rem" }}>
            <button type="submit">Guardar campaña</button>
            <button type="button" className="secundario" onClick={() => setForm(null)}>Cancelar</button>
          </div>
        </form>
      )}

      <Cargando cargando={cargando} vacio={campanas.length === 0 && "Aún no hay campañas. Crea la primera."}>
        <div className="tabla-scroll">
          <table>
            <thead><tr><th>Vacuna</th><th>Lugar</th><th>Fechas</th><th>Estado</th><th className="num">Cupos libres</th><th>Acciones</th></tr></thead>
            <tbody>
              {campanas.map((c) => (
                <tr key={c.id}>
                  <td><b>{c.vacuna}</b></td>
                  <td>{c.establecimiento?.nombre}</td>
                  <td>{fecha(c.fechaInicio)} – {fecha(c.fechaFin)}</td>
                  <td><Insignia tabla={ESTADO_CAMPANA} estado={c.estado} /></td>
                  <td className="num">{cupos[c.id] ?? 0}</td>
                  <td>
                    <div className="fila">
                      <button className="secundario" onClick={() => editar(c)}>Editar</button>
                      <button className="secundario" onClick={() => setTurnosDe(turnosDe?.id === c.id ? null : c)}>Turnos y cupos</button>
                      {c.estado !== "CERRADA" && <button className="secundario" onClick={() => cerrar(c)}>Cerrar</button>}
                      <button className="peligro" onClick={() => eliminar(c)}>Eliminar</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Cargando>

      {turnosDe && <AdminTurnos campana={turnosDe} alCambiar={refrescar} />}
    </>
  );
}

function AdminTurnos({ campana, alCambiar }) {
  const { avisar } = useContext(Ctx);
  const [turnos, cargar, cargando] = useCarga("/turnos?campanaId=" + campana.id, []);
  const [nuevo, setNuevo] = useState({ fecha: hoyLima(), horaInicio: "08:00", horaFin: "12:00", cuposTotal: 30 });
  const [edicion, setEdicion] = useState({}); // cambios sin guardar por id de turno

  const valor = (t, campo) => (edicion[t.id]?.[campo] ?? (campo.startsWith("hora") ? hora(t[campo]) : t[campo]));
  const tocar = (t, campo, v) => setEdicion({ ...edicion, [t.id]: { ...edicion[t.id], [campo]: v } });
  const cuerpo = (t) => JSON.stringify({ fecha: t.fecha, horaInicio: t.horaInicio, horaFin: t.horaFin, cuposTotal: Number(t.cuposTotal) });

  async function guardar(t) {
    try {
      await llamar("/turnos/" + t.id, { method: "PUT", body: cuerpo({ fecha: valor(t, "fecha"), horaInicio: valor(t, "horaInicio"), horaFin: valor(t, "horaFin"), cuposTotal: valor(t, "cuposTotal") }) });
      avisar("Turno actualizado.", "ok");
      setEdicion({ ...edicion, [t.id]: undefined });
      cargar(); alCambiar();
    } catch (e) { avisar(e.message, "error"); }
  }
  async function eliminar(t) {
    if (!window.confirm("¿Eliminar este turno?")) return;
    try { await llamar("/turnos/" + t.id, { method: "DELETE" }); avisar("Turno eliminado.", "ok"); cargar(); alCambiar(); }
    catch (e) { avisar(e.message, "error"); }
  }
  async function crear(e) {
    e.preventDefault();
    try { await llamar("/turnos?campanaId=" + campana.id, { method: "POST", body: cuerpo(nuevo) }); avisar("Turno creado.", "ok"); cargar(); alCambiar(); }
    catch (err) { avisar(err.message, "error"); }
  }

  return (
    <section className="tarjeta" aria-label={"Turnos de " + campana.vacuna}>
      <h3>Turnos y cupos de «{campana.vacuna}»</h3>
      <Cargando cargando={cargando} vacio={turnos.length === 0 && "Esta campaña aún no tiene turnos."}>
        <div className="tabla-scroll">
          <table>
            <thead><tr><th>Fecha</th><th>Desde</th><th>Hasta</th><th className="num">Aforo</th><th className="num">Libres</th><th>Acciones</th></tr></thead>
            <tbody>
              {turnos.map((t) => (
                <tr key={t.id}>
                  <td><input aria-label="Fecha del turno" type="date" value={valor(t, "fecha")} onChange={(e) => tocar(t, "fecha", e.target.value)} /></td>
                  <td><input aria-label="Hora de inicio" type="time" value={valor(t, "horaInicio")} onChange={(e) => tocar(t, "horaInicio", e.target.value)} /></td>
                  <td><input aria-label="Hora de fin" type="time" value={valor(t, "horaFin")} onChange={(e) => tocar(t, "horaFin", e.target.value)} /></td>
                  <td className="num"><input aria-label="Aforo total" type="number" min="1" max="5000" style={{ width: 90 }} value={valor(t, "cuposTotal")} onChange={(e) => tocar(t, "cuposTotal", e.target.value)} /></td>
                  <td className="num">{t.cuposDisponibles}</td>
                  <td><div className="fila"><button onClick={() => guardar(t)}>Guardar</button><button className="peligro" onClick={() => eliminar(t)}>Eliminar</button></div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Cargando>
      <form onSubmit={crear} aria-label="Nuevo turno">
        <h3>Agregar turno</h3>
        <div className="dos">
          <div><Campo id="tFecha" etiqueta="Fecha" type="date" value={nuevo.fecha} onChange={(e) => setNuevo({ ...nuevo, fecha: e.target.value })} required /></div>
          <div><Campo id="tIni" etiqueta="Desde" type="time" value={nuevo.horaInicio} onChange={(e) => setNuevo({ ...nuevo, horaInicio: e.target.value })} required /></div>
          <div><Campo id="tFin" etiqueta="Hasta" type="time" value={nuevo.horaFin} onChange={(e) => setNuevo({ ...nuevo, horaFin: e.target.value })} required /></div>
          <div><Campo id="tCupos" etiqueta="Cupos (aforo)" type="number" min="1" max="5000" value={nuevo.cuposTotal} onChange={(e) => setNuevo({ ...nuevo, cuposTotal: e.target.value })} required /></div>
        </div>
        <button type="submit" style={{ marginTop: ".8rem" }}>Agregar turno</button>
      </form>
    </section>
  );
}

function AdminEstablecimientos() {
  const { avisar } = useContext(Ctx);
  const [lista, cargar, cargando] = useCarga("/establecimientos", []);
  const vacio = { nombre: "", distrito: "", direccion: "" };
  const [form, setForm] = useState(vacio);

  async function guardar(e) {
    e.preventDefault();
    try {
      const cuerpo = JSON.stringify({ nombre: form.nombre, distrito: form.distrito, direccion: form.direccion });
      if (form.id) await llamar("/establecimientos/" + form.id, { method: "PUT", body: cuerpo });
      else await llamar("/establecimientos", { method: "POST", body: cuerpo });
      avisar("Establecimiento guardado.", "ok");
      setForm(vacio); cargar();
    } catch (err) { avisar(err.message, "error"); }
  }
  async function eliminar(x) {
    if (!window.confirm("¿Eliminar «" + x.nombre + "»?")) return;
    try { await llamar("/establecimientos/" + x.id, { method: "DELETE" }); avisar("Establecimiento eliminado.", "ok"); cargar(); }
    catch (err) { avisar(err.message, "error"); }
  }
  const cambiar = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });

  return (
    <>
      <h2>Establecimientos de salud</h2>
      <form className="tarjeta" onSubmit={guardar} aria-label="Formulario de establecimiento">
        <h3>{form.id ? "Editar establecimiento" : "Nuevo establecimiento"}</h3>
        <div className="dos">
          <div><Campo id="eNom" etiqueta="Nombre" value={form.nombre} onChange={cambiar("nombre")} required maxLength={150} /></div>
          <div><Campo id="eDis" etiqueta="Distrito" value={form.distrito} onChange={cambiar("distrito")} required maxLength={100} /></div>
          <div><Campo id="eDir" etiqueta="Dirección" value={form.direccion} onChange={cambiar("direccion")} required maxLength={200} /></div>
        </div>
        <div className="fila" style={{ marginTop: ".8rem" }}>
          <button type="submit">Guardar</button>
          {form.id && <button type="button" className="secundario" onClick={() => setForm(vacio)}>Cancelar edición</button>}
        </div>
      </form>
      <Cargando cargando={cargando} vacio={lista.length === 0 && "No hay establecimientos."}>
        <div className="tabla-scroll">
          <table>
            <thead><tr><th>Nombre</th><th>Distrito</th><th>Dirección</th><th>Acciones</th></tr></thead>
            <tbody>
              {lista.map((x) => (
                <tr key={x.id}>
                  <td><b>{x.nombre}</b></td><td>{x.distrito}</td><td>{x.direccion}</td>
                  <td><div className="fila"><button className="secundario" onClick={() => setForm(x)}>Editar</button><button className="peligro" onClick={() => eliminar(x)}>Eliminar</button></div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Cargando>
    </>
  );
}

function AdminAvisos() {
  const { avisar } = useContext(Ctx);
  const [lista, cargar, cargando] = useCarga("/avisos", []);
  const [form, setForm] = useState({ titulo: "", contenido: "", fuente: "" });
  const cambiar = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });

  async function publicar(e) {
    e.preventDefault();
    try { await llamar("/avisos", { method: "POST", body: JSON.stringify(form) }); avisar("Aviso publicado.", "ok"); setForm({ titulo: "", contenido: "", fuente: "" }); cargar(); }
    catch (err) { avisar(err.message, "error"); }
  }
  async function eliminar(a) {
    if (!window.confirm("¿Eliminar el aviso «" + a.titulo + "»?")) return;
    try { await llamar("/avisos/" + a.id, { method: "DELETE" }); avisar("Aviso eliminado.", "ok"); cargar(); }
    catch (err) { avisar(err.message, "error"); }
  }

  return (
    <>
      <h2>Noticias y avisos</h2>
      <form className="tarjeta" onSubmit={publicar} aria-label="Nuevo aviso">
        <Campo id="aTit" etiqueta="Título" value={form.titulo} onChange={cambiar("titulo")} required maxLength={150} />
        <Campo id="aCon" etiqueta="Contenido" area value={form.contenido} onChange={cambiar("contenido")} required maxLength={1000} />
        <Campo id="aFue" etiqueta="Fuente" value={form.fuente} onChange={cambiar("fuente")} required maxLength={200}
          ayuda="Toda noticia debe indicar de dónde viene la información (GERESA, MINSA, medio de prensa…)." />
        <button type="submit" style={{ marginTop: ".8rem" }}>Publicar aviso</button>
      </form>
      <Cargando cargando={cargando} vacio={lista.length === 0 && "No hay avisos publicados."}>
        {lista.map((a) => (
          <article className="tarjeta" key={a.id}>
            <h3>{a.titulo}</h3>
            <p>{a.contenido}</p>
            <p className="fuente">Fuente: {a.fuente} · {fechaHora(a.fechaPublicacion)}</p>
            <button className="peligro" onClick={() => eliminar(a)}>Eliminar</button>
          </article>
        ))}
      </Cargando>
    </>
  );
}

function AdminUsuarios() {
  const { sesion, avisar } = useContext(Ctx);
  const [lista, cargar, cargando] = useCarga("/usuarios", []);

  async function cambiar(u, cambio) {
    try { await llamar("/usuarios/" + u.id, { method: "PUT", body: JSON.stringify(cambio) }); avisar("Cuenta actualizada.", "ok"); cargar(); }
    catch (err) { avisar(err.message, "error"); }
  }

  return (
    <>
      <h2>Usuarios</h2>
      <Cargando cargando={cargando} vacio={lista.length === 0 && "No hay usuarios."}>
        <div className="tabla-scroll">
          <table>
            <thead><tr><th>Nombre</th><th>DNI</th><th>Correo</th><th>Rol</th><th>Estado</th><th>Registro</th></tr></thead>
            <tbody>
              {lista.map((u) => {
                const yo = u.id === sesion.id;
                return (
                  <tr key={u.id}>
                    <td><b>{u.nombres} {u.apellidos}</b>{yo && " (tú)"}</td>
                    <td>{u.dni}</td>
                    <td>{u.correo}</td>
                    <td>
                      <select aria-label={"Rol de " + u.nombres} value={u.rol} disabled={yo} style={{ minWidth: 150 }}
                        onChange={(e) => cambiar(u, { rol: e.target.value })}>
                        <option value="CIUDADANO">Ciudadano</option><option value="ADMIN">Administrador</option>
                      </select>
                    </td>
                    <td>
                      {u.estado === "BAJA" ? <span className="insignia error">De baja</span> : <span className="insignia ok">Activo</span>}{" "}
                      {!yo && (
                        <button className={u.estado === "BAJA" ? "secundario" : "peligro"}
                          onClick={() => cambiar(u, { estado: u.estado === "BAJA" ? "ACTIVO" : "BAJA" })}>
                          {u.estado === "BAJA" ? "Reactivar" : "Dar de baja"}
                        </button>
                      )}
                    </td>
                    <td>{fechaHora(u.fechaRegistro)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Cargando>
    </>
  );
}

function AdminAsistencia() {
  const { avisar } = useContext(Ctx);
  const [dni, setDni] = useState("");
  const [buscado, setBuscado] = useState(null);
  const [resultados, setResultados] = useState([]);

  async function buscar(e) {
    e?.preventDefault();
    try { setResultados(await llamar("/reservas/buscar?dni=" + dni)); setBuscado(dni); }
    catch (err) { avisar(err.message, "error"); }
  }
  async function atender(r) {
    try { await llamar("/reservas/" + r.id + "/atender", { method: "PUT" }); avisar("Asistencia registrada.", "ok"); buscar(); }
    catch (err) { avisar(err.message, "error"); }
  }

  return (
    <>
      <h2>Control de asistencia</h2>
      <form className="tarjeta" onSubmit={buscar} aria-label="Buscar paciente por DNI">
        <Campo id="aDni" etiqueta="DNI del paciente" value={dni} inputMode="numeric" pattern="\d{8}" maxLength={8} required
          title="8 dígitos" onChange={(e) => setDni(e.target.value.replace(/\D/g, ""))} />
        <button type="submit" style={{ marginTop: ".8rem" }}>Buscar reservas</button>
      </form>
      {buscado && resultados.length === 0 && <p className="vacio">No hay reservas para el DNI {buscado}.</p>}
      {resultados.map((r) => (
        <article className="tarjeta" key={r.id}>
          <h3>{r.beneficiarioNombre} <Insignia tabla={ESTADO_RESERVA} estado={r.estado} /></h3>
          <p><b>Código:</b> {codigoReserva(r.id)} · <b>Campaña:</b> {r.turno?.campana?.vacuna}</p>
          <p><b>Turno:</b> {fecha(r.turno?.fecha)} · {hora(r.turno?.horaInicio)} a {hora(r.turno?.horaFin)}</p>
          {r.estado === "RESERVADA" && <button onClick={() => atender(r)}>Marcar como atendido</button>}
        </article>
      ))}
    </>
  );
}

function AdminReportes() {
  const { avisar } = useContext(Ctx);
  const [r, , cargando] = useCarga("/reportes/resumen", null);

  async function descargar() {
    try {
      const blob = await llamar("/reportes/exportar", { blob: true });
      const enlace = document.createElement("a");
      enlace.href = URL.createObjectURL(blob);
      enlace.download = "reporte-campanas.csv";
      enlace.click();
      URL.revokeObjectURL(enlace.href);
    } catch (e) { avisar(e.message, "error"); }
  }

  const Barras = ({ filas, nombre, detalle }) => (
    <ul className="barras">
      {filas.map((f, i) => {
        const total = f.cupos || 0;
        const pct = (n) => (total ? Math.min(100, (n / total) * 100) : 0);
        // por distrito, "reservadas" ya incluye a las atendidas; por campaña hay que sumarlas
        const usados = f.canceladas === undefined ? f.reservadas : f.reservadas + f.atendidas;
        return (
          <li key={i}>
            <div className="nombre"><span>{nombre(f)}</span><span>{usados} de {total} cupos · {f.atendidas} atendidas</span></div>
            <div className="pista" role="img" aria-label={`${nombre(f)}: ${usados} cupos ocupados de ${total}, ${f.atendidas} atendidas`}>
              <i className="reservas" style={{ width: pct(usados) + "%" }} />
              <i className="atendidas" style={{ width: pct(f.atendidas) + "%" }} />
            </div>
          </li>
        );
      })}
    </ul>
  );

  return (
    <>
      <div className="fila no-imprimir" style={{ justifyContent: "space-between" }}>
        <h2>Reportes y estadísticas</h2>
        <div className="fila">
          <button onClick={descargar}>Descargar Excel (CSV)</button>
          <button className="secundario" onClick={() => window.print()}>Imprimir / guardar PDF</button>
        </div>
      </div>
      <h2 className="solo-impresion">Cuida Tu Salud AQP · Reporte de campañas ({fecha(hoyLima())})</h2>
      <Cargando cargando={cargando} vacio={!r && "No se pudo cargar el reporte."}>
        {r && (
          <>
            <div className="cifras">
              <div className="cifra"><b>{r.ciudadanos}</b><span>Ciudadanos inscritos</span></div>
              <div className="cifra"><b>{r.totalReservas}</b><span>Reservas hechas</span></div>
              <div className="cifra"><b>{r.reservadas}</b><span>Turnos reservados</span></div>
              <div className="cifra"><b>{r.atendidas}</b><span>Personas atendidas</span></div>
              <div className="cifra"><b>{r.cobertura}%</b><span>Cobertura estimada (atendidas ÷ cupos ofrecidos)</span></div>
            </div>

            <div className="tarjeta">
              <h3>Asistentes por campaña</h3>
              <div className="leyenda"><span>Cupos ocupados</span><span>Atendidos</span></div>
              {r.porCampana.length === 0 ? <p>Sin datos.</p> : <Barras filas={r.porCampana} nombre={(f) => `${f.vacuna} (${f.distrito})`} />}
            </div>
            <div className="tarjeta">
              <h3>Asistentes por distrito</h3>
              {r.porDistrito.length === 0 ? <p>Sin datos.</p> : <Barras filas={r.porDistrito} nombre={(f) => f.distrito} />}
            </div>

            <div className="tabla-scroll">
              <table>
                <thead><tr><th>Campaña</th><th>Distrito</th><th>Estado</th><th className="num">Cupos</th><th className="num">Reservadas</th><th className="num">Atendidas</th><th className="num">Canceladas</th></tr></thead>
                <tbody>
                  {r.porCampana.map((f) => (
                    <tr key={f.campanaId}>
                      <td><b>{f.vacuna}</b></td><td>{f.distrito}</td><td><Insignia tabla={ESTADO_CAMPANA} estado={f.estado} /></td>
                      <td className="num">{f.cupos}</td><td className="num">{f.reservadas}</td><td className="num">{f.atendidas}</td><td className="num">{f.canceladas}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </Cargando>
    </>
  );
}

function AdminBitacora() {
  const [lista, , cargando] = useCarga("/reportes/acciones", []);
  return (
    <>
      <h2>Bitácora de acciones</h2>
      <p className="ayuda">Últimas 50 acciones hechas en el panel de administración.</p>
      <Cargando cargando={cargando} vacio={lista.length === 0 && "Aún no hay acciones registradas."}>
        <div className="tabla-scroll">
          <table>
            <thead><tr><th>Cuándo</th><th>Quién</th><th>Qué</th><th>Sobre</th></tr></thead>
            <tbody>
              {lista.map((a) => (
                <tr key={a.id}><td>{fechaHora(a.fechaHora)}</td><td>{a.usuarioNombre}</td><td>{a.accion}</td><td>{a.entidadAfectada}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      </Cargando>
    </>
  );
}

function Logo() {
  return (
    <svg viewBox="0 0 64 64" aria-hidden="true" focusable="false">
      <rect width="64" height="64" rx="14" fill="#fff" />
      <path d="M4 58 L26 24 L34 34 L40 27 L60 58 Z" fill="#9fd3cb" />
      <path d="M27 8h10v11h11v10H37v11H27V29H16V19h11z" fill="#0f6b63" />
    </svg>
  );
}
function Bienvenida({ sesion }) {
  const [visible, setVisible] = useState(() => {
    try { return !sessionStorage.getItem("bienvenida"); } catch { return true; }
  });
  const cerrar = useCallback(() => {
    setVisible(false);
    try { sessionStorage.setItem("bienvenida", "1"); } catch { /* sin almacenamiento */ }
  }, []);

  useEffect(() => {
    if (!visible) return;
    const alTeclear = (e) => { if (e.key === "Escape") cerrar(); }; 
    document.addEventListener("keydown", alTeclear);
    return () => document.removeEventListener("keydown", alTeclear);
  }, [visible]);

  if (!visible) return null;
  const h = Number(new Date().toLocaleString("en-US", { hour: "numeric", hour12: false, timeZone: "America/Lima" })) % 24;
  const saludo = h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches";
  return (
    <section className="bienvenida" role="status" aria-label="Mensaje de bienvenida">
      <p>
        <b>{saludo}{sesion ? ", " + sesion.nombres : ""}. Bienvenido(a) a Cuida Tu Salud AQP.</b><br />
        Aquí puedes ver las campañas de vacunación de Arequipa y reservar tu turno sin hacer cola.
        Si necesitas ayuda, pide a un familiar que te acompañe a reservar.
      </p>
      <button className="secundario" onClick={cerrar}>Entendido</button>
    </section>
  );
}

function leerPref() {
  try { return { escala: 1, contraste: false, ...JSON.parse(localStorage.getItem("pref")) }; }
  catch { return { escala: 1, contraste: false }; }
}

function App() {
  const ruta = useRuta();
  const [sesion, setSesion] = useState(leerSesion);
  const [mensaje, setMensaje] = useState(null); 
  const [pref, setPref] = useState(leerPref);
  const temporizador = useRef(null);
  const principal = useRef(null);

  useEffect(() => {
    document.documentElement.style.setProperty("--escala", pref.escala);
    document.documentElement.dataset.contraste = pref.contraste ? "alto" : "normal";
    try { localStorage.setItem("pref", JSON.stringify(pref)); } catch { /* sin almacenamiento */ }
  }, [pref]);

  useEffect(() => {
    window.scrollTo(0, 0);
    principal.current?.focus({ preventScroll: true });
  }, [ruta.join("/")]);

  const avisar = useCallback((texto, tipo) => {
    clearTimeout(temporizador.current);
    setMensaje({ texto, tipo });
    temporizador.current = setTimeout(() => setMensaje(null), 9000);
  }, []);

  function entrar(datos) {
    sesionActual = datos;
    setSesion(datos);
    try { localStorage.setItem("sesion", JSON.stringify(datos)); } catch { /* sin almacenamiento */ }
    ir(datos.rol === "ADMIN" ? "admin/campanas" : "campanas");
  }
  const salir = useCallback((aviso) => {
    sesionActual = null;
    setSesion(null);
    try { localStorage.removeItem("sesion"); } catch { /* sin almacenamiento */ }
    ir("inicio");
    if (aviso) avisar(aviso, "error");
  }, []);

  useEffect(() => {
    const f = () => salir("Tu sesión venció. Ingresa de nuevo.");
    window.addEventListener("sesion-vencida", f);
    return () => window.removeEventListener("sesion-vencida", f);
  }, []);

  const escalas = [1, 1.15, 1.3, 1.5];
  const cambiarEscala = (paso) => {
    const i = Math.max(0, Math.min(escalas.length - 1, escalas.indexOf(pref.escala) + paso));
    setPref({ ...pref, escala: escalas[i] });
  };

  const seccion = ruta[0];
  const enlace = (destino, texto, actual) => (
    <li><a href={"#/" + destino} aria-current={actual ? "page" : undefined}>{texto}</a></li>
  );

  let pantalla;
  switch (seccion) {
    case "campanas": pantalla = <Campanas key={ruta[1] || "todas"} ruta={ruta} />; break;
    case "ingresar": pantalla = <Ingresar />; break;
    case "registro": pantalla = <Registro />; break;
    case "mis-reservas": pantalla = <Protegida><MisReservas /></Protegida>; break;
    case "comprobante": pantalla = <Protegida><Comprobante id={ruta[1]} /></Protegida>; break;
    case "admin": pantalla = <Admin ruta={ruta} />; break;
    case "privacidad": pantalla = <Privacidad />; break;
    case "nosotros": pantalla = <Nosotros />; break;
    case "inicio": pantalla = <Inicio />; break;
    default:
      pantalla = <div className="tarjeta"><h1>Página no encontrada</h1><a className="boton" href="#/inicio">Ir al inicio</a></div>;
  }

  return (
    <Ctx.Provider value={{ sesion, avisar, entrar, salir }}>
      <div id="agrupar">
        <a className="saltar" href="#contenido" onClick={(e) => { e.preventDefault(); principal.current?.focus(); }}>Saltar al contenido</a>

        <header id="cabecera">
          <div className="barra">
            <a className="marca" href="#/inicio" aria-label="Cuida Tu Salud AQP, ir al inicio">
              <Logo />
              <span><b>Cuida Tu Salud AQP</b><small>Campañas de vacunación en Arequipa</small></span>
            </a>
            <div className="ajustes" role="group" aria-label="Ajustes de lectura">
              <button onClick={() => cambiarEscala(-1)} disabled={pref.escala === escalas[0]} aria-label="Reducir tamaño de letra">A−</button>
              <button onClick={() => cambiarEscala(1)} disabled={pref.escala === escalas[escalas.length - 1]} aria-label="Aumentar tamaño de letra">A+</button>
              <button onClick={() => setPref({ ...pref, contraste: !pref.contraste })} aria-pressed={pref.contraste}>Alto contraste</button>
            </div>
          </div>
        </header>

        <nav id="menu" aria-label="Menú principal">
          <ul>
            {enlace("inicio", "Inicio", seccion === "inicio")}
            {enlace("campanas", "Campañas", seccion === "campanas")}
            {enlace("nosotros", "Nosotros y contacto", seccion === "nosotros")}
            {sesion && sesion.rol !== "ADMIN" && enlace("mis-reservas", "Mis reservas", seccion === "mis-reservas" || seccion === "comprobante")}
            {sesion?.rol === "ADMIN" && enlace("admin/campanas", "Administración", seccion === "admin")}
            {!sesion && <li className="derecha"><a href="#/ingresar" aria-current={seccion === "ingresar" ? "page" : undefined}>Ingresar</a></li>}
            {!sesion && enlace("registro", "Crear cuenta", seccion === "registro")}
            {sesion && (
              <li className="derecha">
                <a href="#/inicio" onClick={(e) => { e.preventDefault(); salir(); }}>Salir ({sesion.nombres})</a>
              </li>
            )}
          </ul>
        </nav>

        <main id="contenido" ref={principal} tabIndex={-1}>
          <div id="avisos-vivos" aria-live="polite">
            {mensaje && (
              <div className={"mensaje " + mensaje.tipo} role={mensaje.tipo === "error" ? "alert" : "status"}>
                <span>{mensaje.texto}</span>
                <button onClick={() => setMensaje(null)} aria-label="Cerrar aviso">Cerrar</button>
              </div>
            )}
          </div>
          <Bienvenida sesion={sesion} />
          {pantalla}
        </main>

        <footer id="pie">
          <div>
            Proyecto de Programación Web · Arequipa 2026 ·{" "}
            <a href="#/privacidad">Privacidad y términos</a> · <a href="#/nosotros">Nosotros y contacto</a>
            <RedesSociales />
            Prototipo con datos de prueba. Esta página informa y organiza turnos; no da diagnósticos médicos.
          </div>
        </footer>
      </div>
    </Ctx.Provider>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
