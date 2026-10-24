# F8.1 — Reproducibilidad local del artefacto empaquetado (BE + FE)

**Estado: pausada en este punto (2026-10-23), por decisión explícita del
usuario.** Se conserva toda la evidencia ya registrada de empaquetado y
arranque del WAR (§3) y del build de frontend (§4). No se agregó
infraestructura ni pruebas locales adicionales después de esta pausa, y el
hallazgo de CORS de §4.1 quedó re-encuadrado como lo que es: una observación
del ensayo local con dos puertos arbitrarios distintos, no un defecto
confirmado de un despliegue real todavía no definido (ver §4.1 y §7).

El objetivo real de despliegue (F8, a retomar más adelante) es **clonar
ambos repositorios en la VM asignada y ejecutar la versión acordada, con
configuración externa propia de ese entorno** - no replicar el ensayo local
de este documento tal cual. La topología real de Nginx/dominios/puertos
**todavía debe verificarse junto al usuario** (ver §7); hasta entonces, no
corresponde tratar ningún hallazgo de este ensayo local como una propiedad
garantizada del despliegue final.

Fecha: 2026-10-23. Alcance: primera unidad de F8 (`CLAUDE.md` §8), autorizada
explícitamente por el usuario, **sin VM ni configuración productiva** - solo
reproducibilidad local de los artefactos que luego se desplegarían. Reutiliza
perfiles, scripts y datos existentes de F1/F6/F7 (`docs/desarrollo-local.md`,
`.env.*` locales, usuarios de prueba ya creados); no repite las baterías de
TP30-TP35 ya registradas en los contratos de F7 de cada repositorio.

**Distinción explícita:** todo lo de este documento fue **probado en esta
máquina de desarrollo**, con los datos y usuarios ya existentes. Una
instalación limpia en otra máquina (otro JDK, otra versión de Node, un volumen
MySQL vacío) **no quedó verificada** - solo documentada paso a paso, igual
criterio que `docs/desarrollo-local.md` para F1.

## 1. Requisitos (reutilizados de F1/F6, sin cambios)

- JDK 21, Maven Wrapper de `iw3/` (sin instalar Maven aparte).
- Docker Engine + Compose, MySQL 8.0.43 vía `compose.local.yaml` (ya descrito
  en `docs/desarrollo-local.md` §2-3; no se repite aquí).
- Node ≥20.19.0 para el frontend (ver `iw3-frontend/README.md`).
- Los mismos archivos locales no versionados: `.env.mysql`, `.env.jwt`,
  `.env.testuser`/`.env.testroles` (backend) y `.env.local` (frontend, a
  partir de `.env.example`).

## 2. Variables de entorno, sin secretos

| Variable | Dónde | Contenido | Nuevo en F8.1 |
| --- | --- | --- | --- |
| `DB_USERNAME`, `DB_PASSWORD` | `.env.mysql` (BE) | credenciales del usuario de aplicación MySQL | No, reutilizado |
| `JWT_SECRET` | `.env.jwt` (BE) | clave de firma HS512 | No, reutilizado |
| `SMTP_HOST`/`SMTP_PORT`/`SMTP_USER`/`SMTP_PASS`/`SMTP_AUTH`/`SMTP_STARTTLS`/`MAIL_FROM` | exportadas en la shell (BE) | receptor SMTP **local de prueba**, nunca un correo real | No, mismo patrón ya usado en F6/F7 |
| `SPRING_PROFILES_ACTIVE=mysqldev` | exportada (BE) | perfil Spring real; **obligatoria** también al generar el artefacto si se corren los tests (ver §3) | Aclarado en esta unidad |
| `EXPIRED_NOTIFICATION_ENABLED=false` | exportada (BE) | apaga el job de productos vencidos (ejemplo docente, no del dominio de gas) | No, reutilizado |
| `VITE_API_BASE_URL` | `.env.local` o variable de build (FE) | URL absoluta del backend cuando el build **no** corre bajo el proxy de desarrollo | **Nuevo para F8.1** - ver §4 |

Ninguna de estas variables tiene un valor real en este documento ni en el
repositorio; los archivos que las contienen están excluidos de Git (ver
`docs/desarrollo-local.md` §2 para el detalle ya registrado).

## 3. Backend: empaquetado y arranque del artefacto (no solo `spring-boot:run`)

`docs/desarrollo-local.md` solo documentaba `spring-boot:run` (F1). Esta
unidad verifica el **artefacto real empaquetado**, que es lo que efectivamente
se desplegaría.

```bash
cd iw3
export JAVA_HOME="$HOME/.local/opt/jdk-21.0.12.1+1"
export PATH="$JAVA_HOME/bin:$PATH"

set -a
source ../.env.mysql
source ../.env.jwt
set +a
export SMTP_USER=local-test@example.invalid
export SMTP_PASS=local-test-placeholder-not-real
export SMTP_HOST=127.0.0.1
export SMTP_PORT=1025
export SMTP_AUTH=false
export SMTP_STARTTLS=false
export MAIL_FROM=alertas-iw3@local.invalid
export SPRING_PROFILES_ACTIVE=mysqldev
export EXPIRED_NOTIFICATION_ENABLED=false

./mvnw -Dbuild=war -Pmysqldev clean package
```

- **`SPRING_PROFILES_ACTIVE=mysqldev` debe estar exportada antes de `package`,
  no solo antes de `spring-boot:run`:** sin ella, el JVM que ejecuta los tests
  (forkeado por Surefire, pero heredando el entorno del proceso Maven) no
  encuentra el perfil real y dos pruebas de contexto completo
  (`Iw3ApplicationTests`, `AlarmaRollbackIntegrationTest`) fallan con
  `Communications link failure` al intentar conectar contra la URL por
  defecto sin perfil (`CLAUDE.md`/`docs/estado-proyecto.md` ya documentaban
  ese puerto placeholder `33306`, no alcanzable). **Confirmado con evidencia:
  sin la variable, `BUILD FAILURE` (2 errores); con la variable exportada,
  `BUILD SUCCESS`**, con las 17 pruebas corriendo contra la MySQL local real
  ya usada en sesiones anteriores (incluida `AlarmaRollbackIntegrationTest`,
  que fuerza un rollback real con `TransactionTemplate` y no deja fila
  persistida - no se tocaron datos existentes).
- El artefacto generado es **`iw3/target/ROOT.war`** (perfil `war-build`,
  `finalName=ROOT`, confirmado en `iw3/pom.xml`), repaquetado como WAR
  ejecutable por el plugin de Spring Boot (`BOOT-INF/`).
- Arranque del artefacto, mismas variables que arriba, **sin `spring-boot:run`
  ni `mvnw`**:

  ```bash
  java -jar target/ROOT.war
  ```

  **Confirmado:** `Perfil activo mysqldev`, conexión a la MySQL local
  existente (HikariPool), arranque en ~6 s, mismo comportamiento que con
  `spring-boot:run` (job de productos vencidos deshabilitado, sin cambios de
  esquema destructivos - `ddl-auto=update` sobre las tablas ya creadas).

### 3.1. Preparación de usuario/roles iniciales

No se creó ningún usuario nuevo en esta unidad: se reutilizaron los usuarios
de prueba por rol ya registrados en F6 (`.env.testroles` - `ROLE_OPERADOR`,
`ROLE_SUPERVISOR`, sin rol), con el procedimiento ya documentado en
`docs/contrato-f6-roles-permisos.md`. Para una instalación limpia sin esos
datos, ese mismo procedimiento (hash BCrypt + `INSERT` directo en
`roles`/`users`/`userroles`) es el que habría que repetir - **no verificado de
nuevo en esta unidad**, solo referenciado.

## 4. Frontend: build servido localmente, sin el proxy de desarrollo

**Pregunta que esta unidad respondía, explícitamente planteada por el
usuario: ¿cómo resuelve `/api` el build de producción?** Respuesta
confirmada con evidencia, no supuesta: el proxy de `vite.config.ts` **solo
existe bajo `npm run dev`** (servidor de desarrollo de Vite). El build de
producción (`npm run build` + cualquier servidor estático, verificado aquí
con `npm run preview`) no tiene proxy - usa la URL **absoluta** que haya en
`VITE_API_BASE_URL` en tiempo de build (Vite la inlinea en el bundle; con esa
variable vacía, usa rutas relativas y 404 contra cualquier servidor estático
que no sea también el backend).

```bash
# Frontend, con el backend ya corriendo en localhost:8080
VITE_API_BASE_URL=http://localhost:8080 npm run build
npm run preview -- --port 4173 --strictPort
```

**Confirmado:** el bundle generado (`dist/assets/*.js`) contiene literalmente
`http://localhost:8080` (verificado con `grep` sobre el archivo generado, no
supuesto); `npm run preview` sirve ese build en `http://localhost:4173`
**sin ningún proxy** - un origen distinto del backend.

### 4.1. Hallazgo del ensayo local con orígenes distintos (no un defecto confirmado del despliegue)

**Encuadre explícito, corregido tras la pausa de esta unidad:** esto es lo
que se observó en un ensayo local puntual, con el backend en `:8080` y el
build servido en `:4173` - dos puertos elegidos para esta prueba, no la
topología real de la VM (todavía sin definir, ver §7). No se trata como un
defecto confirmado del despliegue ni como un cambio de código obligatorio;
si la VM finalmente sirve frontend y backend bajo el mismo origen (un único
dominio/puerto público, con Nginx haciendo de *reverse proxy* hacia ambos),
este caso de CORS entre puertos distintos **no se aplicaría tal cual**. Se
registra como algo a reconsiderar cuando se conozca esa topología (§7), no
como un bloqueo de F8.

- El **login** (`POST /api/v1/login`, `Content-Type:
  application/x-www-form-urlencoded`, sin header `Authorization` todavía) es
  una solicitud "simple" para CORS - no dispara *preflight*. **Funciona**:
  200, `Access-Control-Allow-Origin: *` presente (confirmado con `curl -i`
  pasando `Origin: http://localhost:4173`).
- Cualquier solicitud **posterior** con el JWT (`Authorization: Bearer ...`,
  agregado automáticamente por el interceptor de `httpClient.ts` a partir del
  login) **no es "simple"** - el navegador manda primero un *preflight*
  `OPTIONS`. Ese `OPTIONS` **devuelve 401** (confirmado con `curl -i -X
  OPTIONS .../api/v1/orders -H "Access-Control-Request-Headers:
  authorization"`), sin ningún header `Access-Control-Allow-*`.
- **Causa, revisada en el código, no supuesta:** `SecurityConfiguration`
  declara el mapeo CORS (`WebMvcConfigurer.addCorsMappings`,
  `allowedOrigins("*")`) pero el `SecurityFilterChain` **nunca llama a
  `http.cors(...)`** ni permite expresamente `OPTIONS` en
  `authorizeHttpRequests`. Spring Security intercepta el `OPTIONS` con
  `anyRequest().authenticated()` **antes** de que el mapeo CORS de Spring MVC
  llegue a aplicarse, y lo rechaza con 401 - el mismo 401 que vería cualquier
  otra ruta protegida sin token.
- **Por qué nunca se vio este problema en F7:** todas las pruebas de TP30-TP35
  se hicieron con `npm run dev`, donde el proxy de Vite hace que el navegador
  vea todo como el mismo origen (`localhost:5173` llamando a `/api/...` en el
  mismo origen) - el comentario de `vite.config.ts` ya lo anticipaba
  ("evita depender del CORS... durante `npm run dev`"), pero nadie lo había
  ejercitado todavía fuera de ese proxy. Por eso el criterio de salida de F7
  no quedó invalidado por este hallazgo: fue una verificación de alcance
  distinto (F8.1), no repetida de F7.

**No se modificó código de producto para esto, ni antes ni después de la
pausa.** Si la topología final de la VM resulta en orígenes distintos para
frontend y backend, un cambio posible sería agregar
`http.cors(Customizer.withDefaults())` al `SecurityFilterChain` (reutilizando
el bean `corsConfigurer()` ya existente), para que el filtro de CORS de
Spring Security corra antes que la autorización. **No es un cambio obligatorio
registrado por esta unidad** - queda como una opción a evaluar recién cuando
se conozca la topología real (§7), no como pendiente bloqueante de F8.

## 5. Login y una consulta de órdenes con ambos artefactos (sin repetir baterías anteriores)

Verificado una sola vez cada uno, no una batería completa (ya cubierta en
`docs/contrato-f6-roles-permisos.md`/`docs/contrato-f7-login.md`):

- **Backend (`java -jar target/ROOT.war`), mismo origen (`curl` sin
  restricción de navegador):** login con usuario `ROLE_OPERADOR` de
  `.env.testroles` → 200, JWT recibido; `GET /api/v1/orders?page=0&size=1`
  con ese JWT → 200, `totalElements` consistente con los datos ya existentes
  (ninguna orden nueva creada por esta verificación).
- **Frontend (build servido en `:4173`) contra ese mismo backend:** el login
  cruzado funciona (ver §4.1); la consulta de órdenes posterior **no se pudo
  completar end-to-end en un navegador real** por el hallazgo de §4.1 - queda
  como límite explícito de esta unidad, no como "verificado".

## 6. Fuera de esta unidad

Sin VM, sin Nginx/HTTPS, sin Docker de producción, sin `docker compose`
distinto del ya documentado en F1. El ensayo/defensa (TP26/TP36) y el envío
de una alarma a una casilla de correo real siguen **pendientes**, no se tocan
en esta unidad.

## 7. Pausa (2026-10-23): archivos pendientes y datos de la VM a relevar

**Archivos/documentación todavía sin escribir, a completar cuando se retome
F8** (no son un bloqueo de esta pausa, solo lo que falta):

- Configuración externa propia del entorno VM: equivalentes de
  `.env.mysql`/`.env.jwt`/`.env.testroles` y `VITE_API_BASE_URL` para ese
  entorno (sin secretos versionados, mismo criterio que §2).
- Procedimiento de clonado + build + arranque **en la VM**, una vez
  confirmada la topología (§4.1) - distinto del ensayo local de este
  documento, no una copia literal de sus comandos.
- Decisión registrada sobre el hallazgo de CORS de §4.1, solo si la
  topología real termina exponiendo frontend y backend en orígenes
  distintos.

**Datos de la VM que necesitamos conocer, junto al usuario, antes de
continuar** (ninguno se asume ni se accede sin autorización):

- Topología real: ¿un solo dominio/puerto público con Nginx como *reverse
  proxy* hacia backend y archivos estáticos del frontend (mismo origen), o
  dos orígenes distintos (p. ej. subdominios o puertos separados)?
- Dominio(s)/subdominio(s) asignados y si ya tienen HTTPS vigente (el
  usuario ya informó que Nginx y HTTPS están configurados - falta confirmar
  el detalle: certificado, rutas, configuración existente de Nginx).
- Acceso: cómo se clona el repositorio en esa VM (SSH, usuario, permisos) y
  qué versiones de JDK/Node/Docker/MySQL ya están disponibles allí.
- Puertos ya ocupados o reservados en esa VM para no chocar con otros
  servicios.
- Si la MySQL de producción corre en la misma VM o es un servicio aparte.

Ninguno de estos puntos se investiga ni se asume en este documento; quedan
como la lista concreta para la próxima conversación sobre F8 con acceso a la
VM junto al usuario.
