# Estado del proyecto

Último checkpoint: [`2026-10-20-04.md`](checkpoints/2026-10-20-04.md).
Fase activa: **F1 — revisión final y respaldo pendientes**. F0 está cerrada.
MySQL local, arranque con `mysqldev` y autenticación HTTP ya fueron probados
según los resultados registrados por el equipo. La guía de reproducción está
preparada en [`desarrollo-local.md`](desarrollo-local.md). Falta aprobar esta
revisión y publicar los cambios de F1; no se declara todavía el cierre formal.

## Repositorios

| Repo | Carpeta | Rama activa | HEAD | Remoto `origin` | Estado |
| --- | --- | --- | --- | --- | --- |
| Backend (copia de trabajo) | `iw3-backend-sanitized` | `feature/setup-inicial` | ver checkpoint | `git@github.com:lantun069-glitch/iw3-backend-2026.git` | `feature/setup-inicial` publicada; `main`/`develop` sin publicar, todavía con placeholders del saneamiento |
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
