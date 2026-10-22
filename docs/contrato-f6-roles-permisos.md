# Contrato — Roles y permisos por operación (F6, primera unidad, TP27)

Cubre únicamente TP27: autorización por rol sobre las 7 operaciones del
circuito ya implementadas en F2–F5 (`OrdenRestController`). No cubre
alarmas/correo/aceptación (TP28–TP29), frecuencia configurable (TP14)
ni promedios obligatorios (TP19): quedan para unidades siguientes de
F6.

## Fuente y alcance

- **CONFIRMADO (T2, resumen de la segunda transcripción, ver
  `docs/requisitos.md` TP27):** "deberán definir e implementar roles de
  usuario que regulen el consumo de la API." No especifica nombres de
  rol ni cuántos roles, ni qué operación corresponde a cuál.
- Todo lo que sigue (nombres de rol, cuántos roles, y el mapeo
  operación→rol) es **DECISIÓN DEL EQUIPO**, aprobada explícitamente
  por el usuario en esta unidad — no es una exigencia textual de
  cátedra. `CLAUDE.md` (instrucción local del equipo, no fuente de
  cátedra) ya anticipaba esto en su sección 6: "Elegir nombres de
  roles y permisos por operación; no presentar `ROLE_OPERATOR`/
  `ROLE_ADMIN` como nombres impuestos por el profesor."

## Matriz de permisos

| Operación | Endpoint | `ROLE_OPERADOR` | `ROLE_SUPERVISOR` | Sin ninguno de los dos roles |
| --- | --- | --- | --- | --- |
| Recepción | `POST /api/v1/orders` | 403 | 201 | 403 (o 401 sin token) |
| Tara | `POST /api/v1/orders/{orderNumber}/tara` | 200 | 200 | 403 |
| Activación | `POST /api/v1/orders/{orderNumber}/activacion` | 200 | 200 | 403 |
| Lecturas | `POST /api/v1/orders/{orderNumber}/lecturas` | 200 | 200 | 403 |
| Cierre | `POST /api/v1/orders/{orderNumber}/cierre` | 200 | 200 | 403 |
| Pesaje final | `POST /api/v1/orders/{orderNumber}/pesaje-final` | 200 | 200 | 403 |
| Conciliación (consulta) | `GET /api/v1/orders/{orderNumber}/conciliacion` | 200 | 200 | 403 |

Sin JWT (ningún token): 401 en las 7, antes de llegar a la verificación
de rol (sin cambios respecto de F2–F5).

Implementación: un `@PreAuthorize` por método en `OrdenRestController`
— `hasAuthority('ROLE_SUPERVISOR')` en `recibir()`,
`hasAnyAuthority('ROLE_OPERADOR','ROLE_SUPERVISOR')` en los otros 6.
Se usó `hasAuthority`/`hasAnyAuthority` explícitos, no `hasRole`: los
controladores de ejemplo del profesor (`AuthtestRestController`,
`MailRestController`) usan `hasRole('ROLE_ADMIN')`, que en realidad
exige la autoridad `ROLE_ROLE_ADMIN` (Spring antepone `ROLE_` a lo que
recibe `hasRole`), porque el nombre del rol ya viene con el prefijo
`ROLE_` desde la base (mismo criterio que `ROLE_QA_TEST` de F1). Esos
controladores de ejemplo no se tocaron (son del profesor, fuera de
alcance de esta unidad); simplemente no se copió ese patrón en el
código nuevo. `@EnableMethodSecurity(prePostEnabled = true)` ya estaba
habilitado en `SecurityConfiguration` desde la base heredada; no hizo
falta ningún cambio ahí.

## Hallazgo: 403 se convertía en 401 (bug pre-existente, no introducido por esta unidad)

Al verificar el caso "autenticado pero sin el rol requerido", la
respuesta real era **401**, no 403. Causa (confirmada con
`logging.level.org.springframework.security=DEBUG`): Spring Security
sí decide 403 correctamente (`AccessDeniedHandlerImpl: Responding with
403 status code`), pero `response.sendError(403)` dispara el forward
interno de Spring Boot a `/error`; como `/error` no está en la lista de
rutas públicas de `SecurityConfiguration`, ese forward vuelve a pasar
por la cadena de filtros sin la autenticación original y termina en
401. Es el **mismo problema ya documentado** en `docs/decisiones.md`
para `MethodArgumentNotValidException` (400→401), aplicado ahora a
`AccessDeniedException` (403→401) — no se había detectado antes porque
hasta esta unidad ningún endpoint usaba `@PreAuthorize`.

Misma corrección que en aquel caso, por el mismo motivo: se agregó un
`@ExceptionHandler(AccessDeniedException.class)` **local** a
`OrdenRestController` (no un `@ControllerAdvice` global, no un cambio
en `SecurityConfiguration`). Al resolverse ahí, nunca se llega al
forward a `/error`. Se evaluó y **se descartó** `.requestMatchers(
"/error").permitAll()` por la misma razón ya registrada la vez
anterior: ampliaría una ruta pública de toda la aplicación por un
problema de un controlador, y expondría sin autenticación el cuerpo de
error por defecto de Spring Boot.

Cuerpo de la respuesta 403 (vía `StandartResponse`, igual patrón que
400/404/409): `{"message":"No tiene los permisos necesarios para esta
operacion","code":403,"devInfo":"No stack trace"}` — sin excepción
real (`ex=null`), sin indicar qué rol faltaba específicamente. El 401
(sin token) sigue siendo la respuesta estándar de Spring Security, sin
cuerpo.

## Usuarios de prueba

Tres usuarios nuevos, mismo procedimiento manual que el usuario de F1
(`qa_f1_dfab890c`/`ROLE_QA_TEST`, ver `docs/desarrollo-local.md`
sección 5), con una diferencia: el hash BCrypt se generó con un
programa Java de un solo uso (reutilizando `spring-security-crypto` y
`mysql-connector-j`, dependencias ya presentes en el proyecto, sin
instalar nada nuevo) en vez de `GET /demo/encodepass`, para no exponer
la contraseña en texto plano en la URL de una petición HTTP ni en
ningún log. Las contraseñas se generaron al azar y se regeneraron una
vez más durante la sesión (la primera generación quedó expuesta en la
salida de una herramienta); quedan únicamente en `.env.testroles`
(gitignored, permisos `600`), nunca impresas en comandos, diffs ni
documentación.

- Usuario con **solo** `ROLE_OPERADOR` (prueba los casos permitidos de
  operador y el caso denegado de recepción).
- Usuario con **solo** `ROLE_SUPERVISOR` (prueba los casos permitidos
  de supervisor, incluida recepción).
- Usuario **sin ningún rol** asignado (fila `userroles` vacía para ese
  usuario; prueba el rechazo 403 genérico).

El usuario `qa_f1_dfab890c` de F1 **no se modificó**: sigue con
únicamente `ROLE_QA_TEST`, que no coincide con ninguna de las reglas
nuevas. Esto significa que, a partir de esta unidad, las colecciones
Postman de F2–F5 necesitan un usuario con `ROLE_SUPERVISOR` en sus
variables `testUsername`/`testPassword` para seguir pasando (se
verificó así, ver más abajo); `qa_f1_dfab890c` dejó de alcanzar.

## Verificación realizada

- Compilación (`./mvnw -Dbuild=jar compile`): sin errores.
- Arranque local con perfil `mysqldev` (MySQL 8.0.43 vía
  `compose.local.yaml`, levantado por el usuario).
- 30 verificaciones manuales vía `curl` (sin token/sin rol/rol
  denegado/rol permitido, en las 7 operaciones, sobre dos órdenes):
  30/30 según lo esperado.
- Colección Postman nueva `postman/iw3-f6-roles-permisos.postman_collection.json`
  (33 requests, 33 aserciones, vía Postman CLI): 33/33 OK, incluyendo
  el hallazgo del bug 403→401 (una corrida antes de la corrección del
  `@ExceptionHandler` mostró exactamente esa falla) y su confirmación
  posterior ya corregido.
- Regresión: la colección ya verificada
  `postman/iw3-f5-runner-circuito.postman_collection.json` (TP22/TP25,
  34 aserciones) se corrió de nuevo pasándole las credenciales del
  usuario `ROLE_SUPERVISOR` nuevo en `testUsername`/`testPassword`:
  34/34 OK, confirmando que el circuito de negocio ya verificado en F4/
  F5 sigue funcionando igual con la autorización por rol activada.
- Respuestas 401/403 inspeccionadas: sin stacktrace, sin mensaje SQL,
  sin indicar el rol específico requerido.

No se repitieron las validaciones de negocio ya cubiertas por F2–F5
(formato de campos, cálculo de conciliación, descarte de lecturas,
etc.): esta unidad solo agrega y verifica la capa de autorización.
