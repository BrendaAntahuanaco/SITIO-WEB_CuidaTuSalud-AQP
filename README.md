# Cuida Tu Salud AQP

Aplicación web para difundir las campañas de vacunación de Arequipa y reservar turnos con control de cupos. Proyecto del curso Programación Web (Universidad Continental, 2026) con impacto social: reducir la desinformación sobre fechas y lugares de vacunación y evitar aglomeraciones mediante turnos.

> **Aviso:** es un prototipo. Las campañas, avisos y establecimientos que trae de ejemplo son datos de prueba y no son información oficial de GERESA ni del MINSA. La plataforma informa y organiza turnos; no da diagnósticos ni recomendaciones médicas.

## Contenido

1. [Qué hace](#1-qué-hace)
2. [Tecnologías y licencias](#2-tecnologías-y-licencias)
3. [Requisitos](#3-requisitos)
4. [Instalación y ejecución](#4-instalación-y-ejecución)
5. [Configuración](#5-configuración)
6. [Estructura del proyecto](#6-estructura-del-proyecto)
7. [Arquitectura MVC](#7-arquitectura-mvc)
8. [API REST](#8-api-rest)
9. [Seguridad](#9-seguridad)
10. [Pruebas](#10-pruebas)
11. [Solución de problemas](#11-solución-de-problemas)
12. [Limitaciones y mejoras futuras](#12-limitaciones-y-mejoras-futuras)
13. [Equipo, trabajo en Git y propiedad intelectual](#13-equipo-trabajo-en-git-y-propiedad-intelectual)

Documentación técnica detallada: `docs/arquitectura/DOCUMENTACION_TECNICA.md`.

---

## 1. Qué hace

Tiene dos roles. El ciudadano consulta y reserva; el administrador (personal de salud) publica y controla.

| Rol | Puede |
|---|---|
| Visitante (sin cuenta) | Ver campañas, filtrarlas, ver sus turnos y cupos, leer avisos, crear cuenta e ingresar |
| Ciudadano | Todo lo anterior + reservar turnos (para sí o para un menor a su cargo), ver su historial, cancelar reservas y imprimir el comprobante |
| Administrador | CRUD de campañas, turnos con aforo, establecimientos y avisos; gestión de usuarios (rol y baja); control de asistencia por DNI; reportes con exportación; bitácora de acciones |

Funcionalidades por código de requisito (el mismo código que usa el informe, Tabla 7):

| Código | Funcionalidad | Dónde está |
|---|---|---|
| RF-01 a RF-03 | Inicio: campañas destacadas, avisos, accesos rápidos | Inicio (`app.jsx`), `AvisoController` |
| RF-04 a RF-06 | Ingreso con token, control por rol, cierre de sesión | `AuthController`, `TokenService`, `AutenticacionInterceptor` |
| RF-07, RF-08 | Registro y validación de datos (DNI, correo, duplicados) | `AuthController.registro` |
| RF-09 a RF-11 | Listado, filtros (vacuna, distrito, fecha, estado) y detalle | `CampanaController.listar` / `obtener` |
| RF-12 a RF-14 | Reserva, control de cupos y cancelación que devuelve el cupo | `ReservaController`, `TurnoRepository` |
| RF-15 | Comprobante imprimible | Comprobante (`app.jsx`) |
| RF-16 | Gestión de campañas y establecimientos | `CampanaController`, `EstablecimientoController` |
| RF-17 | Gestión de usuarios: rol y baja | `UsuarioController` |
| RF-18 | Aforo por turno | `TurnoController` |
| RF-19 a RF-21 | Reportes, indicadores y exportación (CSV para Excel, y "Imprimir / guardar PDF") | `ReporteController`, `AdminReportes` |

Además: accesibilidad para adultos mayores (letra ajustable, alto contraste, botones grandes, navegación con teclado), política de privacidad en lenguaje sencillo (Ley N.º 29733) y bitácora de las acciones del administrador.

---

## 2. Tecnologías y licencias

| Capa | Tecnología | Versión | Licencia |
|---|---|---|---|
| Backend | Spring Boot (Spring MVC, Spring Data JPA) | 4.1.2 | Apache 2.0 |
| Backend | Hibernate ORM (lo trae Spring Data JPA) | la que fija Spring Boot | Apache 2.0 (versión 6.3 o posterior) |
| Backend | spring-security-crypto (solo BCrypt) | la que fija Spring Boot | Apache 2.0 |
| Backend | Java (OpenJDK) | 17 | GPL v2 con Classpath Exception |
| Backend | Maven (con Maven Wrapper) | 3.10.0 (lo descarga `mvnw`) | Apache 2.0 |
| Base de datos | MySQL Community Server | 8.0 | GPL v2 |
| Base de datos | MySQL Connector/J (driver) | la que fija Spring Boot | GPL v2 con excepción FOSS |
| Frontend | React y ReactDOM | 18.3.1 | MIT |
| Frontend | Babel Standalone (traduce JSX en el navegador) | 7.26.4 | MIT |
| Control de versiones | Git y GitHub | — | GPL v2 (Git) |

- React, ReactDOM y Babel se cargan desde `unpkg.com` con versión fija (ver `index.html`); no hace falta Node.js.
- No se usan fuentes, íconos ni imágenes de terceros: la tipografía es la del sistema y el logo es un SVG propio.
- Si se agrega otra librería, hay que revisar su licencia y anotarla en esta tabla.

---

## 3. Requisitos

- **JDK 17** (no basta el JRE; `javac -version` debe responder). Recomendado: Eclipse Temurin 17.
- **MySQL 8** en `localhost:3306`.
- Conexión a internet la primera vez (Maven descarga dependencias) y cada vez que se abre la página (React se carga desde `unpkg.com`).
- Un navegador actual (Chrome, Edge, Firefox).

No es necesario instalar Maven: el proyecto incluye `mvnw` / `mvnw.cmd`.

---

## 4. Instalación y ejecución

### 4.1 Obtener el código

```bash
git clone https://github.com/<usuario>/SITIO-WEB_CuidaTuSalud-AQP.git
cd SITIO-WEB_CuidaTuSalud-AQP/backend
```

### 4.2 Base de datos

No hay que crear nada a mano: la base `campanas_salud` y todas sus tablas se crean solas al iniciar (`createDatabaseIfNotExist=true` y `spring.jpa.hibernate.ddl-auto=update`). Solo asegúrate de que MySQL esté encendido y de conocer la contraseña del usuario.

Por defecto la aplicación usa el usuario `root` y la contraseña `123456`. Si la tuya es distinta, defínela antes de ejecutar:

```bat
set DB_USER=root
set DB_PASSWORD=tu_clave_de_mysql
```

### 4.3 Ejecutar (Windows, CMD, dentro de la carpeta `backend`)

```bat
mvnw spring-boot:run
```

En Linux o macOS: `./mvnw spring-boot:run`.

Cuando aparezca `Tomcat started on port 8080` y `Started SaludApplication`, abre:

**http://localhost:8080**

Abre siempre la página desde esa dirección. Si abres `index.html` con doble clic o con Live Server, la página no puede comunicarse con el servidor y no guarda nada.

Para apagar el servidor: `Ctrl + C` en la ventana donde corre.

### 4.4 Cuenta de administrador

El registro público solo crea ciudadanos. La primera cuenta de administrador se crea sola al iniciar, si todavía no existe ninguno:

- Correo: `admin@cuidatusalud.pe` (cambiable, ver sección 5).
- Contraseña: si no defines una, el sistema genera una al azar y la muestra **una sola vez** en la consola (bloque "CUENTA DE ADMINISTRADOR CREADA"). Cópiala en ese momento.

Para fijar tu propia contraseña (mínimo 8 caracteres) antes del primer inicio:

```bat
set APP_ADMIN_PASSWORD=UnaClaveSegura123
```

### 4.5 Datos de ejemplo

Si no hay establecimientos, al iniciar se crean 2 establecimientos, 3 campañas (Sarampión, Influenza, VPH), 4 turnos y 1 aviso que aclara que son datos de prueba. Borra o edita esos datos desde el panel de administración.

### 4.6 Generar un `.jar` (opcional)

```bat
mvnw clean package
java -jar target\salud-0.0.1-SNAPSHOT.jar
```

---

## 5. Configuración

Archivo: `backend/src/main/resources/application.properties`. Los valores sensibles se pueden cambiar con variables de entorno, sin tocar el código.

| Qué | Propiedad | Variable de entorno | Valor por defecto |
|---|---|---|---|
| Usuario de MySQL | `spring.datasource.username` | `DB_USER` | `root` |
| Contraseña de MySQL | `spring.datasource.password` | `DB_PASSWORD` | `123456` (solo para desarrollo) |
| Correo del administrador inicial | `app.admin.correo` | `APP_ADMIN_CORREO` | `admin@cuidatusalud.pe` |
| Contraseña del administrador inicial | `app.admin.password` | `APP_ADMIN_PASSWORD` | aleatoria, se muestra una vez en consola |
| Clave para firmar tokens (mín. 16 caracteres) | `app.token.secret` | `APP_TOKEN_SECRET` | aleatoria en cada inicio |

Si no se define `APP_TOKEN_SECRET`, al reiniciar el servidor todas las sesiones dejan de valer y hay que ingresar de nuevo.

---

## 6. Estructura del proyecto

```
SITIO-WEB_CuidaTuSalud-AQP/
├── README.md
├── backend/                         ← aplicación completa (Spring Boot + página React)
│   ├── pom.xml                      ← dependencias y versión de Java
│   ├── mvnw, mvnw.cmd, .mvn/        ← Maven Wrapper (no hay que instalar Maven)
│   └── src/
│       ├── main/java/com/campanas/salud/
│       │   ├── SaludApplication.java        ← punto de entrada
│       │   ├── DatosIniciales.java          ← administrador inicial y datos de ejemplo
│       │   ├── model/                       ← MODELO: entidades JPA (tablas)
│       │   ├── repository/                  ← MODELO: acceso a datos (Spring Data JPA)
│       │   ├── controller/                  ← CONTROLADOR: API REST y manejo de errores
│       │   ├── seguridad/                   ← token, interceptor de roles y configuración web
│       │   └── util/                        ← bitácora y funciones de texto
│       ├── main/resources/
│       │   ├── application.properties       ← configuración
│       │   └── static/                      ← VISTA: index.html, app.jsx (React), estilos.css
│       └── test/java/.../SaludApplicationTests.java
├── frontend/                        ← reservado (la vista se sirve desde backend/static)
└── docs/
    ├── arquitectura/                ← DOCUMENTACION_TECNICA.md (aquí también va el diagrama de arquitectura)
    ├── modelo-er/                   ← diagrama entidad-relación
    ├── mockups/                     ← mockups y wireframes
    └── actas/                       ← actas de las reuniones semanales
```

---

## 7. Arquitectura MVC

Arquitectura cliente-servidor de tres capas (presentación, lógica de negocio y datos) comunicadas por una API REST.

| MVC | Capa | En este proyecto |
|---|---|---|
| Vista | Presentación | `static/index.html`, `app.jsx` (componentes React) y `estilos.css`, servidos por el mismo Spring Boot |
| Controlador | Lógica de negocio | Clases de `controller/` (reciben la petición, validan, aplican reglas) y el interceptor de `seguridad/` (roles) |
| Modelo | Datos | Entidades de `model/` y repositorios de `repository/`, guardados en MySQL |

**Flujo de una petición:** el navegador (React) llama a `/api/...` → el interceptor identifica al usuario por el token y comprueba el rol → el controlador valida y aplica las reglas → el repositorio consulta o guarda en MySQL → el controlador responde con JSON → React muestra el resultado. Todo error sale como `{"message": "..."}` en español.

---

## 8. API REST

Todas las rutas empiezan con `/api`. Niveles de acceso: **Público**, **Sesión** (cualquier usuario con token válido) y **Admin**. Las respuestas de error tienen el formato `{"message": "texto en español"}`.

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `POST /auth/registro` | Público | Crea una cuenta de ciudadano y devuelve la sesión con token |
| `POST /auth/login` | Público | Ingresa con correo y contraseña |
| `GET /campanas?vacuna=&distrito=&fecha=&estado=` | Público | Lista con filtros opcionales |
| `GET /campanas/{id}` | Público | Detalle de una campaña |
| `GET /campanas/cupos` | Público | Cupos libres por campaña (turnos de hoy en adelante) |
| `POST /campanas`, `PUT /campanas/{id}`, `DELETE /campanas/{id}` | Admin | Crear, editar/cerrar y eliminar |
| `GET /turnos?campanaId=` | Público | Turnos de una campaña |
| `POST /turnos?campanaId=`, `PUT /turnos/{id}`, `DELETE /turnos/{id}` | Admin | Gestionar turnos y aforo |
| `GET /establecimientos` | Público | Lista de centros de salud |
| `POST /establecimientos`, `PUT /establecimientos/{id}`, `DELETE /establecimientos/{id}` | Admin | Gestionar establecimientos |
| `GET /avisos` | Público | Avisos de la página de inicio |
| `POST /avisos`, `DELETE /avisos/{id}` | Admin | Publicar y eliminar avisos |
| `POST /reservas` | Sesión | Reservar un turno (descuenta un cupo) |
| `GET /reservas` | Sesión | Historial de reservas de quien ingresó |
| `DELETE /reservas/{id}` | Sesión | Cancelar una reserva propia (devuelve el cupo); el admin puede cancelar cualquiera |
| `GET /reservas/buscar?dni=` | Admin | Buscar reservas por DNI del beneficiario |
| `PUT /reservas/{id}/atender` | Admin | Marcar asistencia |
| `GET /usuarios`, `PUT /usuarios/{id}` | Admin | Listar; cambiar rol o dar de baja |
| `GET /reportes/resumen`, `/reportes/exportar`, `/reportes/acciones` | Admin | Indicadores, CSV y bitácora |

El token se envía en la cabecera `Authorization: Bearer <token>`.

---

## 9. Seguridad

| Amenaza | Medida aplicada | Dónde |
|---|---|---|
| Contraseñas expuestas | Se guardan solo como hash BCrypt; el hash nunca se envía al navegador (`@JsonIgnore`) | `AuthController`, `Usuario` |
| Acceso sin permiso | Control por rol en el servidor para cada ruta `/api/**` (Público / Sesión / Admin). Se consulta la base en cada petición, así que un cambio de rol o una baja se aplica al instante | `AutenticacionInterceptor` |
| Suplantación de sesión | Token firmado con HMAC-SHA256, comparación en tiempo constante y vencimiento de 8 horas | `TokenService` |
| Elevación de privilegios | El registro siempre crea CIUDADANO; el rol solo lo cambia un administrador, y ese administrador no puede modificar su propia cuenta | `AuthController`, `UsuarioController` |
| Reservar a nombre de otro | El usuario sale del token, no de datos enviados por el navegador; una reserva ajena responde "no existe" | `ReservaController` |
| Inyección SQL | Solo se usan consultas de Spring Data JPA y JPQL con parámetros con nombre (`@Param`). No hay SQL armado con texto ni consultas nativas | `repository/` |
| XSS | React escapa todo el texto que dibuja y no se usa `dangerouslySetInnerHTML`; además `X-Content-Type-Options: nosniff` | `app.jsx`, `AutenticacionInterceptor` |
| Datos inválidos | Validación en el servidor de formato (DNI de 8 dígitos, correo), largo máximo, fechas, aforo y estados permitidos | todos los controladores |
| Inyección en Excel (CSV) | Si una celda empieza con `=` `+` `-` `@`, se neutraliza con `'` | `ReporteController.celda` |
| Cupos sobrevendidos | Descuento de cupo atómico en una sola sentencia SQL; dos personas no pueden quedarse con el último | `TurnoRepository.descontarCupo` |
| Reservas duplicadas | No se permite dos reservas activas del mismo DNI en una campaña | `ReservaController.reservar` |
| Fuga de información | Los errores inesperados se registran solo en la consola; el usuario ve un mensaje genérico. Las respuestas de la API no se guardan en caché | `ManejadorErrores`, interceptor |
| Trazabilidad | Cada acción del administrador queda en la bitácora | `BitacoraService` |

Para publicar la aplicación en internet hay que ponerla detrás de **HTTPS** (por ejemplo, con un proxy inverso) y definir `DB_PASSWORD`, `APP_ADMIN_PASSWORD` y `APP_TOKEN_SECRET`.

---

## 10. Pruebas

- **Prueba automática:** `SaludApplicationTests.contextLoads` comprueba que la aplicación arranca. Necesita MySQL encendido. Se ejecuta con `mvnw test`.
- **Pruebas manuales de seguridad y funcionamiento:** la lista con entradas maliciosas, el resultado esperado y una columna para anotar el resultado está en la sección 10 de `docs/arquitectura/DOCUMENTACION_TECNICA.md`.

---

## 11. Solución de problemas

| Síntoma | Causa y solución |
|---|---|
| `"mvnw" no se reconoce como un comando` | Estás en una carpeta que no es `backend`, o faltan `mvnw.cmd` y la carpeta `.mvn`. Entra a `backend` y revisa que existan |
| `No compiler is provided... JRE rather than a JDK` | Tienes Java sin compilador. Instala el JDK 17 y verifica con `javac -version` |
| `PKIX path building failed` al descargar dependencias | Un antivirus o la red intercepta el HTTPS. Ejecuta `set MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT` y luego `mvnw spring-boot:run`, o prueba con otra red |
| `Access denied for user 'root'` | La contraseña de MySQL no es `123456`. Usa `set DB_PASSWORD=tu_clave` |
| `Port 8080 was already in use` | Hay otro servidor abierto. Ciérralo con `Ctrl + C` o cierra su ventana |
| `Can't create database ...; database exists` (en MySQL Workbench) | No es un error real: la base ya existe. Usa `USE campanas_salud;` |
| La página sale en blanco | No cargaron las librerías desde `unpkg.com` (sin internet o bloqueadas). Revisa la conexión; en el navegador abre F12 → Console |
| El registro no guarda | Abre la página desde `http://localhost:8080`, no desde el archivo. Lee el mensaje rojo que muestra la página |
| `cannot find symbol` al compilar | Hay archivos de versiones distintas mezclados. Reemplaza la carpeta `src` completa por la del repositorio |

---

## 12. Limitaciones y mejoras futuras

Limitaciones del prototipo, dichas con claridad:

- Los datos de campañas son de ejemplo; para uso real los debe cargar y verificar personal autorizado.
- No hay recordatorios por correo, WhatsApp o SMS (los entrevistados los pidieron).
- No se valida el DNI contra RENIEC ni se autocompleta con él.
- No hay mapa de establecimientos.
- La exportación es CSV (abre en Excel); el PDF se obtiene con "Imprimir / guardar PDF" del navegador.
- Sin límite de intentos de ingreso (podría agregarse bloqueo temporal).
- El token se guarda en `localStorage`: es cómodo, pero un XSS lo expondría; por eso se evita todo HTML sin escapar.
- La página depende de `unpkg.com`; para uso sin internet habría que incluir las librerías localmente.
- Tablas creadas con `ddl-auto=update`: para producción conviene migraciones (Flyway o Liquibase).

---

## 13. Equipo, trabajo en Git y propiedad intelectual

**Curso:** Programación Web — **Docente:** Victor Alfonzo Cornejo Arismendi — Arequipa, 2026.

| Integrante | Responsabilidad principal (ver cronograma del informe) |
|---|---|
| Antahuanaco Ticona, Brenda Adeli | Investigación, descripción e información de campañas |
| Ramos Gallegos, Maythee Alexandra | Pruebas del prototipo |
| Roque Sullca, Waldemar Emil | Diseño, arquitectura y reserva de turnos |

**Flujo de trabajo:** cada integrante hace commits con su propia cuenta; los cambios se revisan entre integrantes antes de pasar a `main`. Las actas de las reuniones semanales se guardan en `docs/actas/`.

**Propiedad intelectual:**

- Las fuentes se citan en el informe en formato APA 7.
- Los textos, el logo y los mockups son de elaboración propia del equipo; no se copiaron de otros sitios.
- Todo el software de terceros es de código abierto y está listado en la sección 2.
- Licencia del código del proyecto: pendiente de acordar por el equipo; al elegirla, se agrega el archivo `LICENSE` y se indica aquí.
- Declaración de originalidad: el código y los textos fueron elaborados por los integrantes del equipo; los recursos externos están citados. Cada integrante responde por lo que escribe y programa, y su aporte queda en el historial de commits.
