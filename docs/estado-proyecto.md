# Estado del proyecto

Último checkpoint: [`2026-10-21-03.md`](checkpoints/2026-10-21-03.md).
Fase activa: **F3 — ambas unidades (tara/activación y telemetría)
implementadas, revisadas y aprobadas; la fase sigue abierta** (faltan
TP14 opcional, y TP15 en adelante, que corresponden a F4). **F0, F1 y
F2 están cerradas.** El bloque de F1 (infraestructura local, arranque
con `mysqldev`, autenticación HTTP) quedó publicado y respaldado en el
commit `cf64c61` de `feature/setup-inicial`, árbol de trabajo limpio al
momento del cierre. La guía de reproducción sigue en
[`desarrollo-local.md`](desarrollo-local.md).

**F2 cerrada.** Primera y única unidad vertical de F2 (recepción de
orden): implementada, verificada, documentada (OpenAPI) y con una
colección Postman reproducible y ejecutada. Trabajo en la rama
`feature/f2-recepcion-ordenes`. Ver sección "F2 — primera unidad
vertical" más abajo, "Cierre de F2" al final de esta sección, y los
checkpoints 06 y 2026-10-21-01 para el detalle completo, límites y
evidencia. **F3, primera unidad (tara y activación, TP08-TP09):
implementada, revisada y aprobada, publicada en la rama
`feature/f3-tara-activacion`** — ver sección "F3 — primera unidad" más
abajo y el checkpoint 2026-10-21-02 para el hash y el estado del
respaldo remoto. **F3, segunda unidad (telemetría, TP10-TP13):
implementada, revisada y aprobada, publicada en la misma rama** — ver
sección "F3 — segunda unidad" más abajo y el checkpoint 2026-10-21-03.
**F3 sigue abierta como fase:** no se declara cerrada; quedan
pendientes dentro de su alcance TP14 (opcional, no adelantado) y
TP15–TP19 (cierre de carga, pesaje final, conciliación — corresponden a
F4, todavía no iniciada).

## Repositorios

| Repo | Carpeta | Rama activa | HEAD | Remoto `origin` | Estado |
| --- | --- | --- | --- | --- | --- |
| Backend (copia de trabajo) | `iw3-backend-sanitized` | `feature/f2-recepcion-ordenes` | ver checkpoint | `git@github.com:lantun069-glitch/iw3-backend-2026.git` | `feature/setup-inicial` y `feature/f2-recepcion-ordenes` publicadas (la segunda, creada desde el HEAD de F1, con esta entrada); `main`/`develop` sin publicar, todavía con placeholders del saneamiento |
| Backend (respaldo original) | `iw3-backend` | `feature/setup-inicial` | `b7e705d` | — | Sin tocar, historial original del profesor intacto; no se trabaja sobre esta carpeta |
| Frontend | `iw3-frontend` | `main` | `34382d2` | `git@github.com:lantun069-glitch/iw3-frontend-2026.git` | Publicada; contiene solo `.gitignore`. Sin scaffold de framework |

`main` y `develop` de la copia saneada **no están listas para ejecutarse**:
tienen los cuatro valores de credenciales reemplazados por placeholders de
saneamiento, no por las variables de entorno reales. Esa externalización
solo existe hoy en `feature/setup-inicial`. No describir esas dos ramas
como un estado desplegable.

## Inventario del código heredado (base docente, commit
`b7e705de737e5b1039719807bb413ec89279e7d1`)

### Build y perfiles
- Maven en `iw3/pom.xml` (no en la raíz Git). Parent `spring-boot-starter-parent`
  4.1.1, `java.version=21`, `springdoc-openapi-starter-webmvc-ui` 3.1.1,
  `java-jwt` 4.6.1 (Auth0).
- Perfiles de **ejecución**: `mysqldev`, `mysqlprod`, `cli1`, `cli2` — cada
  uno solo fija `spring.profiles.active`; la lógica real de escaneo está en
  `config/profile/*ScanConfig.java` (ver abajo).
- Perfiles de **empaquetado**: `war-build` (packaging `war`, Tomcat
  `provided`) y `jar-build` (packaging `jar`, Tomcat `compile`).

**Perfiles Maven y Spring: corrección comprobada durante F1.** Los perfiles
Maven `mysqldev`/`mysqlprod`/`cli1`/`cli2` fijan una propiedad de build,
`spring.profiles.active`. En este proyecto, según el diagnóstico de F1, el POM
no la reenvía al proceso de `spring-boot:run`. Evaluar esa propiedad con Maven
no demuestra qué perfil usa Spring en ejecución. El arranque probado necesita
`export SPRING_PROFILES_ACTIVE=mysqldev`, además de
`./mvnw -Dbuild=war -Pmysqldev spring-boot:run`.
Spring selecciona entonces los beans con `@Profile` y las propiedades del perfil.
El empaquetado (`war-build`/`jar-build`) es una decisión separada.

**Hallazgo verificado de forma empírica (no solo leído del POM), con
`./mvnw help:active-profiles` y `./mvnw help:evaluate`:**
- `war-build` tiene `<activeByDefault>true</activeByDefault>` además de
  activarse con `-Dbuild=war`. Un build sin ningún flag de empaquetado ya
  empaqueta WAR por defecto: `./mvnw help:active-profiles` (sin flags)
  muestra `war-build` como único perfil activo.
- Pero esa activación por defecto se cancela en cuanto se activa
  *cualquier otro* perfil del mismo POM por `-P`, sin importar si ese
  perfil tiene algo que ver con el empaquetado. Confirmado: `./mvnw
  help:active-profiles -Pmysqldev` (sin `-Dbuild`) **falla** con `Unknown
  packaging: ${packaging.type}`, porque al activar `mysqldev`
  explícitamente, `war-build` deja de estar activo por defecto y ningún
  otro perfil define `packaging.type`. Esto rompe cualquier goal,
  incluido `spring-boot:run` (probado: mismo error).
- Con `-Dbuild=war -Pmysqldev` quedan activos los dos perfiles a la vez
  (`help:active-profiles` lista `mysqldev` y `war-build`) y
  `packaging.type` resuelve a `war` (`help:evaluate
  -Dexpression=packaging.type` → `war`); `spring.profiles.active`
  resuelve a `mysqldev` (`help:evaluate
  -Dexpression=spring.profiles.active` → `mysqldev`).
- **Regla práctica para los comandos de F1:** siempre combinar un perfil
  de ejecución con `-Dbuild=war` o `-Dbuild=jar` explícito y definir también
  el perfil Spring en runtime. Nunca activar
  `-Pmysqldev`/`-Pmysqlprod`/`-Pcli1`/`-Pcli2` solo, sin `-Dbuild=...`.

- No activar `mysqldev` y `mysqlprod` juntos: cada `*ScanConfig` excluye
  del escaneo de entidades/repositorios JPA al paquete de integración del
  otro cliente (ver abajo), así que conviven dentro del mismo arranque
  produciría un dominio de persistencia inconsistente o incompleto.

### Escaneo de entidades/integraciones (`config/profile/`)
- `MysqldevScanConfig` (`@Profile("mysqldev")`): entidades de
  `ar.edu.iua.iw3.model`, `auth` e `integration.cli1.model`; excluye
  `integration.cli2.*` del component scan de repositorios JPA.
- `MysqlprodScanConfig` (`@Profile("mysqlprod")`): mismo patrón pero con
  `cli2` en vez de `cli1`.
- `Cli1ScanConfig` / `Cli2ScanConfig` (`@Profile("cli1")` /
  `@Profile("cli2")`): existen además como perfiles independientes, con la
  misma lógica de inclusión/exclusión. Confirmado por lectura directa de
  los 4 archivos: `mysqldev` escanea el modelo de `cli1`, `mysqlprod`
  escanea el de `cli2`, cada uno excluyendo al otro.

### Seguridad y autenticación (`auth/`, `security/`)
- `SecurityConfiguration`: CORS abierto (`allowedOrigins("*")`), CSRF
  deshabilitado, sesión `STATELESS`, rutas públicas (login efectivo `POST /api/v1/login`,
  Swagger/OpenAPI, `/ui/**`, `/demo/**`), resto autenticado. **Modificado
  por nosotros**: inyecta `jwt.secret` por `@Value` y lo valida al arrancar
  (`validateJwtSecret`, `@PostConstruct`) — ver `docs/decisiones.md`.
- `JWTAuthorizationFilter`: filtro HTTP Basic/JWT, lee el token de header o
  query param. **Modificado por nosotros**: recibe `jwtSecret` por
  constructor en vez de leer una constante fija.
- `AuthRestController`: login (`POST /api/v1/login`, parámetros de formulario
  `username` y `password`) devuelve JWT firmado. El proyecto también expone
  `/demo/encodepass` para generar un hash BCrypt de prueba. **Modificado
  por nosotros**: firma con `jwtSecret` inyectado en vez de
  `AuthConstants.SECRET`.
- `AuthConstants`: quedan `EXPIRATION_TIME`, `AUTH_HEADER_NAME`,
  `AUTH_PARAM_NAME`, `TOKEN_PREFIX`. **Se eliminó** `SECRET` (quedó sin
  uso tras el cambio anterior).
- `User`/`Role`: entidades JPA reales (`users`, `roles`, `userroles` M:N),
  con `UserDetails` de Spring Security implementado — reutilizable
  directamente para TP27 (roles) en F6, no hace falta un modelo nuevo.
- `CustomAuthenticationManager`, `UserBusiness`/`IUserBusiness`,
  `UserEvent`/`UserEventListener` (eventos de login, `events/`): sin
  modificar, reutilizables.

### Persistencia, negocio y controladores de ejemplo
- `model/Category.java`, `model/Product.java` +
  `model/persistence/{CategoryRespository,ProductRepository}.java` +
  `business/{Category,Product}Business.java` + interfaces +
  `controllers/ProductRestController.java`: es el **dominio de ejemplo del
  profesor** (categorías/productos), no el dominio de órdenes de carga de
  gas. Sirve como plantilla de las 4 capas
  (controlador→negocio→repositorio→entidad) a replicar para nuestro
  dominio en F2, pero no se reutiliza como entidad de negocio real.
- `business/execption/{BusinessException,FoundException,NotFoundException}.java`:
  excepciones de negocio reutilizables tal cual.
- `util/{JsonUtiles,StandartResponse,IStandartResponseBusiness,StandartResponseBusiness}.java`:
  helpers de respuesta estándar y JSON — reutilizar, no reimplementar.
- `util/EmailBusiness.java`: envío simple vía `JavaMailSender`. En F1 se
  reemplazó el remitente fijo por `mail.from`, con default al usuario SMTP.
  El proveedor y el remitente autorizado para correo real siguen pendientes.
- `controllers/BaseRestController.java`: solo expone
  `getUserLogged()` (usuario autenticado desde el `SecurityContext`). **No
  construye respuestas estándar** — confirmado por lectura directa de la
  clase; eso lo hacen las clases de `util/`.
- `controllers/Constants.java`: define los paths base (`/api/v1/...`).
  Nuestros endpoints de dominio deberán agregar sus propias constantes
  acá o en un archivo equivalente, no reemplazar las existentes.

### Integración con sistemas externos (`integration/cli1`, `integration/cli2`)
- `cli1`: ejemplo de integración simple (`ProductCli1` + deserializador
  Jackson custom + negocio + repositorio + controlador).
- `cli2`: ejemplo más elaborado (`ProductCli2`, `ComponentCli2`,
  serializadores Jackson custom, vista "slim"). Usado también por
  `Scheduler` (ver abajo) y por `Iw3Application.run()` como demo
  comentada.
- Estos dos paquetes son la plantilla de "cómo integrar un sistema
  externo" que el profesor dejó de ejemplo; nuestro dominio (SAP/TMS
  simulados) deberá seguir el mismo patrón de capas, en paquetes propios
  (no meter el dominio de gas dentro de `cli1`/`cli2`).

### Scheduling, eventos y websocket
- `schedules/Scheduler.java`: conserva dos jobs de ejemplo y `expired()`,
  que consulta productos de `cli2`. En F1 se eliminó el destinatario docente
  por defecto y se agregó una salida temprana si
  `jobs.expired-notification.enabled=false` (default). Esto deshabilita solo
  ese job de correo, no todos los jobs ni todos los caminos de envío.
- `events/AsyncSpringEventsConfig.java`: habilita `@EnableAsync` para los
  eventos de usuario.
- `websocket/`: ejemplo de WebSocket + gráfico de datos en tiempo real
  (`DataGraphScheduler`, `WsController`, UI estática en
  `static/ui/demo.html`). No implica que debamos usar WebSocket para
  nuestro circuito; sigue siendo una decisión a justificar con su propia
  razón técnica cuando se decida el mecanismo de refresco de la UI (F7).

### Documentación OpenAPI
- `config/OpenApiConfig.java`: define `SecurityScheme` Bearer JWT y la
  info básica de la API (título, contacto `magm@iua.edu.ar`). Reutilizable
  tal cual el esquema de seguridad; el contacto/título probablemente haya
  que actualizarlos a los del equipo antes de F5 (cosmético, no
  bloqueante).

## Qué modificamos nosotros hasta ahora (resumen, detalle completo en
`docs/decisiones.md`)

- Historial saneado (4 credenciales heredadas reemplazadas en todos los
  commits de una copia separada del repositorio).
- `.gitignore`: exclusión de archivos de asistente local en **ambos**
  repos (BE y FE); exclusión de `.env`/`.env.*` reales solo en **BE**
  (`iw3-backend-sanitized`). El `.gitignore` de `iw3-frontend` todavía no
  tiene esa segunda regla — no hay variables de entorno propias del
  frontend todavía, así que no se agregó por anticipado.
- `.env.example` (solo en BE) documentando `DB_USERNAME`, `DB_PASSWORD`, `SMTP_USER`,
  `SMTP_PASS`, `JWT_SECRET`.
- Externalización de esas 5 variables en `application.properties` /
  `application-mysqlprod.properties`, sin defaults.
- `SecurityConfiguration`, `JWTAuthorizationFilter`, `AuthRestController`,
  `AuthConstants`: cambios mínimos para que la clave JWT venga de
  configuración en vez de una constante fija (detalle arriba).
- `JwtSecurityVerificationTest.java`: prueba unitaria nueva (firma/
  verificación JWT + validación de arranque).
- F1: `compose.local.yaml` con MySQL 8.0.43, volumen persistente y puerto
  `127.0.0.1:3306:3306`; `application-mysqldev.properties` apunta a ese puerto.
- F1: remitente y destinatario parametrizados, SMTP host/puerto configurables,
  y job heredado de correo deshabilitado por defecto.
- La URL base de `application.properties` conserva `33306`: el arranque local
  documentado requiere `mysqldev`, cuya URL usa `3306`. No se verificó el
  arranque sin perfil; no describir ambos valores como iguales.
- El resto del inventario corresponde a la base docente salvo las
  modificaciones expresamente indicadas.

## Verificado vs. pendiente (resumen; detalle y evidencia en
`docs/requisitos.md`)

- **Verificado:** compilación de la copia ya saneada y con la
  externalización de credenciales aplicada (`./mvnw clean compile` con
  JDK 21 portátil; no se compiló por separado el commit original del
  profesor sin modificar); prueba unitaria aislada de firma/verificación
  JWT y de `validateJwtSecret()` con valores nulo/vacío/en blanco vía
  reflexión (6/6, sin contexto de Spring); exclusiones de `.gitignore`
  verificadas con `git check-ignore`; saneamiento de historial (0
  ocurrencias de los 4 valores en las 3 ramas); push de
  `feature/setup-inicial` (backend) y `main` (frontend, solo
  `.gitignore`); perfiles Maven/empaquetado verificados con
  `help:active-profiles`/`help:evaluate` (ver arriba).
- **Verificado en F1, según registros del equipo:** MySQL 8.0.43 y base
  `iw3_2026`; arranque Spring con `mysqldev`, JDBC en `localhost:3306` y HTTP
  en `8080`; seis tablas creadas; OpenAPI 200 y Swagger 302; login correcto
  200, acceso con JWT 200, contraseña incorrecta 401, token alterado 401 y
  acceso sin token 401. El último caso se realizó antes, sin repetirse.
- **Alcance de esta revisión:** lectura del ZIP de F1 y contraste con los
  resultados comunicados. No se ejecutaron aquí Maven, MySQL ni HTTP; el ZIP
  no incluye el POM, los controladores de autenticación ni los logs completos.
- **Pendiente:** arranque real sin `JWT_SECRET`; pruebas de `main`/`develop`;
  correo real, proveedor institucional y autorización por roles del dominio;
  implementación del dominio. El rol de prueba no valida los roles de F6.
  La matriz `docs/requisitos.md` conserva sus estados: TP01–TP35 pendientes,
  TP36 en curso (preparación de defensa, sin práctica real).

## Corrección a un checkpoint ya publicado

`docs/checkpoints/2026-10-20-01.md` (commit `3bcc15e`, publicado en
`origin/feature/setup-inicial`) afirma que la verificación de exclusión
de `.env`/`.env.local`/`.env.example` con `git check-ignore` dio
"resultados esperados en ambos repos". Eso es impreciso: esa prueba
puntual se corrió solo en `iw3-backend-sanitized`. El mismo archivo, un
párrafo más abajo, ya dice correctamente que `iw3-frontend` no tiene esa
exclusión — esta nota aclara cuál de las dos líneas es la correcta. No se
reescribe ese commit ya publicado; esta es una nota posterior.

## Cierre de F0

Criterio de cierre de fase acordado por el equipo: requisitos de la fase
identificados, código revisado, casos relevantes verificados, documentos
y contratos actualizados, explicación comprensible por cualquier
integrante, checkpoint guardado. Para F0 en particular (inventario y
setup, sin dominio): inventario ✓, trazabilidad inicial ✓ (este documento
+ `docs/fuentes.md` + `docs/requisitos.md`), comandos propuestos y
verificados empíricamente ✓, problemas concretos identificados ✓ (ver el
checkpoint vigente), plan de F1 ✓ (ver checkpoint). No se implementaron
entidades de dominio ni se tocó la VM.

**Revisión documental de F0 aprobada el 20/10/2026.** Se revisaron el
inventario, la matriz autosuficiente, las fuentes, las decisiones y el plan
de F1. Esta aprobación evalúa la coherencia de los archivos recibidos;
no es una nueva ejecución de las pruebas ni una certificación independiente
de las transcripciones completas o del código, no incluidos en el ZIP.

El bloque documental quedó guardado y respaldado: commit `0ab399f` en
`feature/setup-inicial`, publicado en `origin` (verificado con
`git status -sb`/`git ls-remote`, sin repetir ninguna auditoría ni
prueba). **F0 queda formalmente cerrada con esta revisión** — ver
`docs/checkpoints/2026-10-20-03.md`.

**Actualización posterior a F0:** la infraestructura local y los casos de
login de F1 ya se ejecutaron. La anterior afirmación de que F1 no había
comenzado queda superada por el checkpoint 04; el checkpoint 03 se conserva
como registro histórico. Permanecen las dudas de fecha de entrega 2026,
densidad, contrato de activación, metadatos de aceptación de alarma y
stack/CI/hosting FE. En esta revisión no se consultaron nuevas fuentes de
cátedra ni se resolvieron esas dudas.

## Cierre de F1

**Evidencia de cierre:** commit `cf64c619621fb64131162cd97b10e0e13733a26a`
("chore: preparar entorno local y documentar verificaciones de F1"),
publicado en `origin/feature/setup-inicial`; `git status -sb` muestra la
rama sincronizada con el remoto y el árbol de trabajo limpio al momento
de este cierre. No se repitió ninguna prueba ni auditoría para esta
entrada: el cierre se basa en el estado Git ya verificado y en el trabajo
ya registrado en el checkpoint 04.

**F1 queda formalmente cerrada.** Se conservan, sin resolver ni dar por
superados, todos los pendientes y límites ya registrados en el checkpoint
04: arranque real sin `JWT_SECRET` no probado; `main`/`develop` sin probar
ni publicar; correo real, proveedor SMTP institucional y remitente
autorizado pendientes; autorización por roles funcionales (más allá del
rol de prueba insertado a mano) no implementada; requisitos de dominio
TP01–TP35 siguen en **pendiente**, TP36 en **en curso** sin práctica real;
fecha de entrega 2026, inconsistencia de densidad, contrato de activación,
metadatos de aceptación de alarma y stack/CI/hosting FE siguen abiertos;
Graphify permanece local, ignorado y sin regenerar.

**F2 quedó pendiente de iniciar al cierre de F1.** No se tocó código, no
se ejecutaron pruebas ni auditorías nuevas en esa entrada, y no se
regeneró Graphify para ese cierre.

## F2 — primera unidad vertical: recepción de orden (cerrada)

Rama `feature/f2-recepcion-ordenes`, creada desde el HEAD `491c143` de
`feature/setup-inicial` (F0/F1 completos). Detalle completo —contrato,
decisiones, límites y evidencia— en
[`contrato-f2-recepcion.md`](contrato-f2-recepcion.md),
[`decisiones.md`](decisiones.md) (sección F2) y
[`revision-f2-notas-pruebas.md`](revision-f2-notas-pruebas.md). Esta
sección es un resumen; no reemplaza leer esos documentos.

**Qué se implementó:** únicamente la recepción autenticada de una orden
nueva (`POST /api/v1/orders`), con sincronización por `externalCode` de
camión/chofer/cliente/producto, auditoría de la transición de creación
(`HistorialEstado`) y rechazo de `orderNumber` duplicado (`409`). Cubre
TP02 a TP07 para este caso de uso únicamente — no hay consulta,
actualización ni borrado de `Orden` ni de sus referencias; eso no está
en el alcance de esta unidad.

**Verificado (evidencia real, no solo código inspeccionado):**
- Creación válida → `201`, estado 1, con los 7 datos de TP02.
- Sincronización: segunda recepción con el mismo `externalCode` actualiza
  los campos obligatorios y conserva los opcionales omitidos (no los
  borra).
- Duplicado de `orderNumber`, incluido con referencias cambiadas → `409`,
  valores previos intactos (comparados campo por campo, no solo por
  conteo de filas), sin historial nuevo.
- Colisión concurrente de `orderNumber` (dos solicitudes simultáneas) →
  una `201`, la otra `409`, con rollback de las referencias sincronizadas
  por la que perdió la carrera.
- Validación de longitud (`@Size`, alineada con cada columna): límite
  exacto aceptado, un carácter de más rechazado con `400` y sin
  persistir nada, para un campo obligatorio y uno opcional.
- Exposición de errores: `400`/`409`/`500` devuelven `StandartResponse`
  con `devInfo` siempre `"No stack trace"` (nunca el detalle real de la
  excepción); `401` es el comportamiento por defecto de Spring Security,
  sin pasar por `StandartResponse` — son dos mecanismos distintos, no
  uno solo.
- Rollback de las dos excepciones *checked* usadas por `recibirOrden`
  (`FoundException`, `BusinessException`), probadas por separado: la
  primera con la colisión concurrente de arriba; la segunda con un caso
  histórico puntual (un `producto.nombre` de 200 caracteres que en su
  momento no tenía validación de longitud y fallaba por truncamiento de
  datos a nivel de base, con las filas ya guardadas en la misma
  transacción revertidas). **Esa evidencia de rollback se conserva como
  histórica**: con `@Size` ya agregado, ese mismo payload se rechaza
  ahora con `400` antes de llegar al negocio, así que ese camino
  puntual hacia `BusinessException` ya no es alcanzable por esa vía. No
  se debilitó ninguna validación para volver a reproducirlo ni se
  agregó un mecanismo nuevo capaz de fallar en producción solo para
  repetir la prueba; el mecanismo de rollback en sí
  (`@Transactional(rollbackFor = Exception.class)`) no cambió y sigue
  aplicando a cualquier otra causa de `BusinessException`.

**Hecho en una segunda ronda sobre esta misma unidad (documentación y
reproducibilidad, sin dominio nuevo):**
- OpenAPI: `@Operation`, `@ApiResponses` con ejemplos reales para
  201/400 (dos casos)/409/500, ejemplo de request body. Verificado
  contra `GET /v3/api-docs` con la app corriendo — no solo anotado, sino
  comprobado que el JSON generado coincide con el comportamiento real.
  Detalle en `docs/contrato-f2-recepcion.md`.
- Colección Postman versionable
  (`postman/iw3-f2-recepcion.postman_collection.json`, 6 requests:
  login, recepción válida, segunda orden reutilizando referencias,
  duplicado, entrada inválida, sin token) con aserciones de status y
  contenido, variables sin credenciales reales, y datos de prueba
  generados en tiempo de ejecución para no chocar con corridas
  anteriores. Ejecutada una vez con `postman collection run` pasando
  credenciales solo por `--env-var` (sin `postman login`, sin
  `--output`): 6/6 requests, 12/12 aserciones, 0 fallos. Las aserciones
  no afirman cambios internos de base de datos — solo lo que la
  respuesta HTTP expone. Detalle en `docs/contrato-f2-recepcion.md`.

**Pendiente dentro de esta misma unidad, según su avance real (no
alcance de F3 en adelante):**
- OpenAPI y Postman cubren solo este endpoint; falta ampliar ambos
  cuando se agreguen los endpoints de F3 en adelante (es el criterio
  normal de "ampliar en el final", no una deuda de esta unidad).
- No se revisó si otros controladores existentes (ej.
  `ProductRestController`) comparten el mismo patrón de exposición de
  `devInfo` que se corrigió acá; el alcance de la revisión fue solo
  `OrdenRestController`.

**Qué NO es parte de esta unidad (corresponde a F3 en adelante, sin
empezar):** tara/activación/clave, telemetría de masa/densidad/
temperatura/caudal, snapshot e histórico, cierre, pesaje final y
conciliación (TP08–TP19). No se tocó ningún archivo de esas fases.

## Cierre de F2

**Aprobación:** el usuario aprobó el cierre de F2 en base a la
implementación ya revisada (recepción de orden, TP02–TP07 y TP20/TP21
para la transición de creación) y a los resultados reportados de
OpenAPI/Postman en esta misma sesión. No se volvió a ejecutar la batería
manual completa ni ninguna auditoría para este cierre: se basa en el
trabajo ya verificado y registrado en el checkpoint 06 y en
`docs/contrato-f2-recepcion.md`/`docs/decisiones.md` (sección F2).

**Evidencia de cierre — Postman (TP24):** ejecución real de
`postman/iw3-f2-recepcion.postman_collection.json` con
`postman collection run`, credenciales solo por `--env-var` (sin
`postman login`, sin `--output`): **6 requests, 12 aserciones, 0
fallos**. Las aserciones verifican únicamente lo que la respuesta HTTP
expone (status code y cuerpo JSON); no afirman cambios internos de base
de datos que la API no permite observar — esa verificación quedó hecha
aparte, contra la base, durante la implementación de la unidad (ver
`docs/decisiones.md`, sección F2). Evidencia de cierre — OpenAPI
(TP23): `GET /v3/api-docs` verificado contra la app corriendo, con las 5
respuestas documentadas (201/400×2/401/409/500) y esquemas reales.

**Evidencia de cierre — Git:** el commit que agrupa esta entrada (checkpoint
`2026-10-21-01`, este archivo y los cambios de OpenAPI/Postman) queda
publicado en `origin/feature/f2-recepcion-ordenes`; el hash exacto y el
estado de `git status -sb` posterior al push están en el checkpoint
`2026-10-21-01.md` y en el resumen de cierre de esa sesión.

**F2 queda formalmente cerrada.** Se conservan, sin resolver ni dar por
superados, todos los pendientes y límites ya registrados:
- OpenAPI y Postman cubren solo el endpoint de recepción de esta unidad;
  ambos se amplían recién cuando se agreguen los endpoints de F3 en
  adelante — no es una deuda de esta unidad, es el criterio normal de
  "ampliar en el final".
- La evidencia de rollback de `BusinessException` sigue siendo
  histórica: el payload exacto que se usó para probarla ya no llega al
  negocio con `@Size` agregado (ver `docs/decisiones.md`, sección F2).
  No se repitió ni se generalizó esa prueba.
- No se revisó si `ProductRestController` (dominio de ejemplo del
  profesor) comparte el mismo patrón de exposición de `devInfo` que se
  corrigió en `OrdenRestController`; quedó fuera del alcance de esta
  unidad.
- Arranque real sin `JWT_SECRET` no probado; `main`/`develop` sin probar
  ni publicar; correo real, proveedor SMTP institucional y remitente
  autorizado pendientes; autorización por roles funcionales (más allá
  del rol de prueba insertado a mano) no implementada.
- Requisitos de dominio: TP01, TP08–TP19, TP22, TP25 en adelante siguen
  en **pendiente** (corresponden a F3 en adelante, o al cierre completo
  del segundo parcial); TP36 en **en curso** sin práctica real con una
  persona.
- Fecha de entrega 2026, inconsistencia de densidad, contrato de
  activación, metadatos de aceptación de alarma y stack/CI/hosting FE
  siguen abiertos; no se consultó ninguna fuente nueva de cátedra en
  esta sesión.
- Graphify permanece local, ignorado y **sin actualizar en este
  cierre** — decisión del equipo (20/10/2026): no actualizar Graphify en
  cada checkpoint ni al cerrar sesión por defecto, ni siquiera al cerrar
  una fase salvo pedido expreso; queda pendiente para cuando se solicite.

**F3 (tara, clave de activación de 5 dígitos, recuperación de preset y
telemetría — TP08–TP13, transición 1→2) queda pendiente de iniciar.** No
se tocó ningún archivo de esa fase en esta sesión. Antes de implementar,
revisar y presentar el contrato de tara y activación (alcance, fuente,
archivos afectados, verificación) y esperar validación, según la sección
1 de las reglas del equipo.

## F3 — primera unidad: tara y activación (implementada, revisada y aprobada)

**Alcance de esta unidad:** registro de tara (transición 1→2, con
generación de clave de activación de 5 dígitos) y operación de
activación (validación de clave, devolución de preset, idempotente
mientras la orden siga en estado 2). Cubre TP08 y TP09. No incluye
telemetría, snapshot ni histórico (siguiente unidad de F3) ni cierre/
pesaje final (F4). Rama `feature/f3-tara-activacion`, creada desde el
HEAD aprobado de F2 (`348c17d`), árbol de trabajo limpio al crearla.

**Decisiones de contrato (detalle completo y justificación en
`docs/decisiones.md`, sección F3; aprobadas por el usuario antes de
implementar):**
- `orderNumber` en el path, clave en el body; la clave se valida contra
  la orden identificada por `orderNumber`, no se busca por clave (no
  necesita unicidad global).
- Tara exitosa 200; activación exitosa 200; clave incorrecta 400; clave
  ausente/fuera de [10000,99999] 400 por validación de Bean Validation;
  orden inexistente 404; estado incompatible 409; sin JWT 401 (sin
  cambios en `SecurityConfiguration`); error no clasificado 500
  genérico, sin detalle interno (mismo criterio que F2).
- Activación idempotente mientras la orden siga en estado 2: la clave
  se valida siempre (incluso tras una activación previa), pero
  `fechaActivacion` se fija una sola vez y no se sobreescribe.
- Concurrencia: `PESSIMISTIC_WRITE` sobre la fila de la orden
  (`findByOrderNumberForUpdate`), dentro de la misma transacción que
  tara/activación. No se agregó `@Version`.
- Clave generada con `SecureRandom`, rango [10000,99999]; se devuelve
  únicamente en la respuesta exitosa de la tara; nunca se registra en
  logs ni en `observacion` de `HistorialEstado`.
- `fechaActivacion` ≠ inicio físico real de la carga: es el instante en
  que la API acepta la clave. La relación con la primera telemetría
  válida queda para la siguiente unidad.
- Nuevas excepciones de dominio (`InvalidStateException`,
  `InvalidActivationKeyException`) viven en
  `ordenes/business/exception`, sin modificar `business/execption`
  (taxonomía docente heredada).
- Rollback con `@Transactional(rollbackFor = Exception.class)`, mismo
  criterio que `recibirOrden`; ningún error de base se traduce a 409
  salvo el chequeo explícito de estado (que ocurre antes de cualquier
  escritura).

**Hallazgo corregido durante la verificación (no estaba previsto en la
propuesta inicial):** la primera respuesta de una activación exitosa
devolvía `fechaActivacion` con precisión de nanosegundos (valor en
memoria, antes de persistir), mientras que una activación repetida leía
el valor ya truncado a microsegundos por la columna `datetime(6)` de
MySQL — dos representaciones distintas del mismo instante, que hacían
fallar una comparación exacta de idempotencia. Corregido truncando a
microsegundos (`ChronoUnit.MICROS`) antes de fijar `fechaActivacion` por
primera vez (`OrdenBusiness.activar`).

**Evidencia — HTTP real contra la app corriendo (`mysqldev`, MySQL
local), mismo método que F1/F2:** tara válida (200, estado 1→2, clave en
rango); tara sobre orden inexistente (404); tara repetida (409); tara
sobre B tras intentos rechazados, prueba de que sigue en estado 1 (200);
activación antes de tara (409); activación sobre orden inexistente
(404); clave incorrecta (400); clave fuera de rango (400); clave ausente
(400); activación correcta (200); reintento con clave correcta,
`fechaActivacion` idéntica tras la corrección (200); clave incorrecta
después de una activación correcta, sigue rechazándose (400); ambos
endpoints sin JWT (401). Dos taras simultáneas sobre la misma orden
(`curl` en paralelo): una 200, otra 409. Dos activaciones correctas
simultáneas sobre la misma orden: ambas 200, con `fechaActivacion`
idéntica. **Estos dos resultados son compatibles con la serialización
prevista por el bloqueo pesimista del código, no una prueba directa de
que hubo contención efectiva del lock:** lanzar dos procesos `curl` en
paralelo no garantiza por sí solo que ambas solicitudes llegaron a
disputar el bloqueo (no se midió tiempo de espera ni se instrumentó el
acceso concurrente a nivel de base).

**Límite de esta verificación — no se leyó la base directamente.** Esta
sesión no tuvo acceso a un cliente MySQL ni a `docker exec` (sin
privilegios de `sudo` interactivo en el entorno sandboxed); por eso la
ausencia de cambios en `ordenes`/`historial_estados` ante los rechazos y
ante la carrera de tara se sostiene en la estructura del código
(`InvalidStateException` se lanza antes de cualquier `save()`, dentro de
`@Transactional`) y en evidencia HTTP indirecta (la orden B siguió
aceptando su primera tara después de los rechazos; la carrera de tara
devolvió exactamente un 200), no en una lectura de fila verificada. No
se ejecutó una prueba dedicada de 500 para esta unidad (mismo mecanismo
genérico ya probado en F2).

**Revisión de logs — alcance acotado.** Se buscaron las 4 claves de
activación generadas en esta sesión en `/tmp/f3-spring.log` (stdout/
stderr de la app local): 0 coincidencias. Esto no demuestra ausencia de
toda fuga posible: no se revisaron otros destinos de log, ni logs de
acceso HTTP, ni se probó con más claves que las generadas en esta
sesión. Nota de proceso: durante la carrera de tara sobre la orden C, la
clave de esa orden quedó impresa sin enmascarar en la salida de un
comando de la terminal durante esa prueba manual (no en ningún archivo
de reporte ni en este documento); se corrigió el enmascarado para el
resto de la sesión.

**Postman/OpenAPI (ampliación de TP23/TP24 a esta unidad):**
`postman/iw3-f3-tara-activacion.postman_collection.json` (nueva,
conserva `iw3-f2-recepcion.postman_collection.json` sin modificar), con
aserciones de `orderNumber`/`presetKg` agregadas en los casos de
activación y una reinicialización de datos (al inicio de cada corrida
completa, con valores en milisegundos para no chocar entre corridas
consecutivas) corregida tras encontrar una colisión real en la
verificación. **Última ejecución:** `postman collection run
--iteration-count 2` (dos corridas completas en el mismo proceso,
conservando variables de colección entre ellas, sin `postman login` ni
`--output`, credenciales solo por `--env-var`): **34 requests, 40
aserciones, 0 fallos** (17 requests/20 aserciones por corrida). No
ejercita las dos carreras concurrentes (no es práctico expresarlas en
el runner secuencial de Postman); esas se probaron con `curl` en
paralelo, ver evidencia arriba. `GET /v3/api-docs` verificado contra la
app corriendo: ambos paths nuevos (`/api/v1/orders/{orderNumber}/tara` y
`/api/v1/orders/{orderNumber}/activacion`) presentes con sus respuestas
documentadas.

**Archivos modificados o creados en esta unidad:**
- `iw3/src/main/java/ar/edu/iua/iw3/ordenes/model/Orden.java` (campos
  nuevos: `taraKg`, `fechaTara`, `claveActivacion`, `fechaActivacion`).
- `iw3/src/main/java/ar/edu/iua/iw3/ordenes/business/exception/InvalidStateException.java` (nuevo).
- `iw3/src/main/java/ar/edu/iua/iw3/ordenes/business/exception/InvalidActivationKeyException.java` (nuevo).
- `iw3/src/main/java/ar/edu/iua/iw3/ordenes/model/persistence/OrdenRepository.java`
  (`findByOrderNumberForUpdate`, `PESSIMISTIC_WRITE`).
- `iw3/src/main/java/ar/edu/iua/iw3/ordenes/business/IOrdenBusiness.java` y
  `OrdenBusiness.java` (`registrarTara`, `activar`).
- `iw3/src/main/java/ar/edu/iua/iw3/ordenes/controllers/dto/TaraRequest.java`,
  `TaraResponse.java`, `ActivacionRequest.java`, `ActivacionResponse.java` (nuevos).
- `iw3/src/main/java/ar/edu/iua/iw3/ordenes/controllers/OrdenRestController.java`
  (dos endpoints nuevos, dos `@ExceptionHandler` reutilizados, anotaciones OpenAPI).
- `postman/iw3-f3-tara-activacion.postman_collection.json` (nuevo).
- `docs/contrato-f3-tara-activacion.md` (nuevo).
- `docs/decisiones.md`, `docs/requisitos.md`, este archivo (documentación).

**Pendiente explícito, sin resolver en esta unidad:**
- No se agregó ninguna fila de auditoría para la activación en
  `HistorialEstado` (decisión del equipo: la activación no cambia
  `estado`, solo la transición 1→2 de la tara se audita). Si se decide
  más adelante dejar rastro también de la activación, es un cambio
  pendiente, no implementado acá.
- No se verificó el comportamiento ante una caída de conexión a mitad de
  la transacción de tara/activación (mismo límite que F2 con
  `BusinessException`).
- Lectura directa de `ordenes`/`historial_estados` para los casos de
  rechazo: pendiente (ver límite de verificación arriba); se puede
  completar si en algún momento hay acceso a `docker exec`/`mysql` sin
  intervención manual del usuario.
- Esta unidad queda **aprobada y publicada**, pero **F3 como fase sigue
  abierta**: no se declara cerrada. La segunda unidad (telemetría,
  descarte de lecturas, snapshot e histórico — TP10-TP13) sigue
  pendiente de iniciar.

**Aprobación y publicación de esta unidad:** el usuario aprobó registrar
y publicar esta unidad (tara y activación) en base al trabajo ya
revisado y a los resultados de Postman reportados en esta sesión. Antes
de publicar se corrigió una referencia incorrecta en
`docs/decisiones.md` (la frase sobre `fechaActivacion` atribuía a F2 un
criterio que en realidad se aplicó primero a la tara dentro de esta
misma unidad de F3, no en F2). Checkpoint de esta entrada:
[`checkpoints/2026-10-21-02.md`](checkpoints/2026-10-21-02.md).

## F3 — segunda unidad: telemetría (implementada, revisada y aprobada)

**Alcance:** TP10–TP13 (recepción de lecturas en estado 2 con orden
activada, descarte por negocio, snapshot, histórico). No incluye
frecuencia configurable (TP14), cierre de carga, pesaje final, alarmas
ni frontend — fuera de alcance por pedido explícito. Contrato aprobado
por el usuario antes de implementar, con los ajustes que impuso (ver
`docs/decisiones.md`, sección F3 — segunda unidad, para el detalle
completo). Rama `feature/f3-tara-activacion`, continuada desde el HEAD
`538d0cf`. **Aprobación:** el usuario aprobó esta unidad tras revisión
de código y una consulta directa de BD que ejecutó él mismo (ver
"Evidencia de histórico" abajo). Checkpoint de esta entrada:
[`checkpoints/2026-10-21-03.md`](checkpoints/2026-10-21-03.md).

**Resumen de decisiones:** precondición `estado==2` **y**
`fechaActivacion != null` (409 distinto si falta activación, decisión
de coherencia del equipo, no cita literal); `fechaInicioCarga` =
instante de procesamiento de la primera lectura aceptada
(interpretación del equipo apoyada en T1, no definición literal
cerrada), fijada una sola vez, truncada a microsegundos, misma fecha
para snapshot/detalle/inicio; descarte por negocio (no por
`@Positive`) con prioridad explícita `caudal_no_positivo` >
`masa_no_positiva` > `masa_decreciente`; sin antecedente no se evalúa
masa decreciente; densidad sin validar/convertir (duda conservada);
precisión `DECIMAL(15,3)` para masa/caudal, `DECIMAL(10,4)` densidad,
`DECIMAL(6,2)` temperatura; snapshot siempre `null` (no cero) sin
lectura aceptada previa; mismo bloqueo `PESSIMISTIC_WRITE` que
tara/activación para snapshot+histórico en la misma transacción; sin
deduplicación de repetidas; orden de procesamiento bajo el lock
explícitamente distinto de orden de llegada por red.

**Evidencia — HTTP real contra la app corriendo (`mysqldev`, MySQL
local):** lectura antes de activar (409, mensaje distinto del de
estado incompatible); dos descartes antes del primer snapshot con
campos `null`; primera lectura aceptada fija `fechaInicioCarga`;
segunda aceptada (masa mayor) la conserva; masa igual aceptada; masa
decreciente descartada sin tocar snapshot; prioridad de motivos
verificada (caudal y masa no positivos a la vez → gana caudal); orden
inexistente (404); campo ausente (400); sin token (401); carrera de
dos lecturas concurrentes (masas 500 y 800): la de 800 se procesó
primero (aceptada), la de 500 se descartó por `masa_decreciente` al
encontrar 800 como antecedente, snapshot final conservó 800 — **solo
se observó este orden de procesamiento**, el contrato documenta el
otro como igualmente posible sin afirmarlo ocurrido; dos órdenes
distintas con snapshots independientes a nivel de API.

**Evidencia de histórico (TP13) — consulta directa de BD ejecutada por
el usuario:** esta sesión no tuvo acceso a `docker exec`/`mysql`, así
que la consulta SQL preparada en `docs/contrato-f3-telemetria.md`
(columnas corregidas: `temperaturac`/`caudal_kgh`) quedó pendiente
hasta que el usuario la corrió directamente y adjuntó la salida.
**Verificado puntualmente para las órdenes 920001/920003/920005, sin
generalizar a escenarios no probados:** 3/2/2 filas de detalle
respectivamente (coincide con las lecturas aceptadas de cada una;
ninguna descartada agregó fila), masas esperadas, y
`fechaInicioCarga`/`fechaUltimaLectura` coincidentes con la
`fecha_lectura` de la primera y la última fila de detalle de cada
orden. Ningún detalle de una orden apareció bajo otra.

**Archivos nuevos/modificados:**
- `model/Orden.java` (snapshot: `masaAcumuladaKg`, `densidad`,
  `temperaturaC`, `caudalKgH`, `fechaUltimaLectura`,
  `fechaInicioCarga`).
- `model/DetalleCarga.java` (nuevo), `model/persistence/DetalleCargaRepository.java` (nuevo).
- `controllers/dto/LecturaRequest.java`, `LecturaResponse.java` (nuevos).
- `business/LecturaResultado.java` (nuevo).
- `business/IOrdenBusiness.java`/`OrdenBusiness.java` (`registrarLectura`).
- `controllers/OrdenRestController.java` (endpoint nuevo, OpenAPI).
- `postman/iw3-f3-telemetria.postman_collection.json` (nuevo).
- `docs/contrato-f3-telemetria.md` (nuevo), `docs/decisiones.md`,
  `docs/requisitos.md`, este archivo.

**Estado de F3 como fase:** sigue **abierta, no cerrada**. Con esta
unidad, TP08–TP13 quedan verificados; dentro del alcance de F3 (ver
matriz de cobertura en `docs/requisitos.md`) quedan pendientes: TP14
(frecuencia configurable, opcional en el parcial, no adelantada),
TP15–TP19 (cierre de carga, pesaje final, conciliación — F4, fuera de
alcance de esta sesión), TP20/TP21 todavía
en curso (faltan las transiciones de F4), y TP22 (cobertura completa
1–5 sin UI) no se puede declarar hasta que F4 exista. **Próximo paso:**
no se inicia F4 en esta entrada; antes de implementarla corresponde
presentar su contrato para revisión, igual que las dos unidades de F3.
