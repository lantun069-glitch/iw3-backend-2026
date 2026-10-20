# Estado del proyecto

Último checkpoint: [`docs/checkpoints/2026-10-20-02.md`](checkpoints/2026-10-20-02.md).
Fase activa: **F0** (inventario y setup). No cerrada todavía — ver sección
"Cierre de F0" al final de este documento.

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

**Cómo se conectan los perfiles Maven con los perfiles Spring:** son dos
mecanismos distintos. Los 4 perfiles Maven de "ejecución"
(`mysqldev`/`mysqlprod`/`cli1`/`cli2`) no activan nada por sí mismos; cada
uno solo fija la propiedad Maven `spring.profiles.active` a un valor fijo
(ej. `mysqldev`), que el plugin de Spring Boot Maven propaga como
variable de entorno/propiedad de sistema al arrancar la aplicación. Es
Spring, no Maven, el que después usa ese valor para decidir qué beans
`@Profile("...")` cargar y qué `application-<perfil>.properties` fusionar.
Los perfiles Maven de "empaquetado" (`war-build`/`jar-build`) son un
mecanismo completamente aparte: solo deciden `packaging.type` y el scope
de Tomcat, sin relación con `spring.profiles.active`.

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
  de ejecución con `-Dbuild=war` o `-Dbuild=jar` explícito. Nunca activar
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
  deshabilitado, sesión `STATELESS`, rutas públicas (`/login`,
  Swagger/OpenAPI, `/ui/**`, `/demo/**`), resto autenticado. **Modificado
  por nosotros**: inyecta `jwt.secret` por `@Value` y lo valida al arrancar
  (`validateJwtSecret`, `@PostConstruct`) — ver `docs/decisiones.md`.
- `JWTAuthorizationFilter`: filtro HTTP Basic/JWT, lee el token de header o
  query param. **Modificado por nosotros**: recibe `jwtSecret` por
  constructor en vez de leer una constante fija.
- `AuthRestController`: login (`/login`) devuelve JWT firmado; endpoint
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
- `util/EmailBusiness.java`: envío de correo simple vía
  `JavaMailSender`. Nota: `setFrom("noreply@magm.com.ar")` está
  hardcodeado (no es secreto, es un remitente del dominio del profesor);
  si se reutiliza para las alarmas de F6 hay que decidir si se cambia el
  remitente (DECISIÓN DEL EQUIPO pendiente).
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
- `schedules/Scheduler.java`: dos jobs de ejemplo (`@Scheduled` fijo y
  cron) + un job real `expired()` que busca productos por vencer
  (`cli2`) y manda un mail con `EmailBusiness`. Usa
  `@Value("${expired.product.send.to:magm3333@gmail.com}")` — default con
  el mail del profesor; no es secreto, pero si se reutiliza este patrón
  para la alarma de temperatura (F6) hay que sacar ese default también.
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
- Todo lo demás listado en "Inventario" es código del profesor, sin
  modificar.

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
- **Pendiente, explícitamente no probado:** arranque real de la
  aplicación Spring Boot, con o sin `JWT_SECRET` definida (requiere una
  base MySQL real, todavía no levantada) — la afirmación de que Spring
  falla al no resolver el placeholder si falta la variable es un
  razonamiento sobre su comportamiento documentado, no una prueba
  ejecutada; compilación/pruebas sobre `main`/`develop` (solo se verificó
  `feature/setup-inicial`); cualquier requisito de dominio (TP01-TP36:
  ninguno implementado todavía, F0 es solo inventario).

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

Queda guardar este bloque en un commit y respaldarlo en la rama de trabajo.
Después puede iniciarse F1. El arranque completo, la prueba HTTP de login y
la reproducción del entorno siguen pendientes. Las dudas de dominio se
resuelven antes de implementar sus requisitos, sin bloquear el entorno local.
