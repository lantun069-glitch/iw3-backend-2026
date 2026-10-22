# Contrato — Alarmas de temperatura: configuración, disparo, consulta y aceptación (F6, primera unidad de TP28/TP29)

Cubre configuración global, disparo, consulta y aceptación de alarmas
de temperatura. **No cubre el envío de correo** (unidad 2, pendiente):
TP28/TP29 quedan parcialmente implementados hasta cubrir ambas
unidades — no se declaran completos con esta entrega.

## Fuente

- **CONFIRMADO (T2):** dispara cuando la temperatura supera
  estrictamente un umbral configurable; destinatarios también
  configurables. Mientras una alarma esté pendiente no se reenvía
  correo "para ese caso"; tras la aceptación por un usuario
  autorizado, si la condición se repite, corresponde un correo nuevo.
- **CONFIRMADO vía NotebookLM, fragmento directo** (no solo la
  respuesta del chat): *"...vamos a realizar la aceptación de la
  alarma por el usuario, el registro de usuario fecha ahora
  observación e opcional."* (transcripción con ruido de ASR: "fecha
  ahora" = "fecha hora", "e opcional" = "es opcional"). El orden de
  palabras ubica "opcional" pegado solo al último término listado.
  **Aun así, que usuario/fecha sean obligatorios y observación el
  único opcional es un criterio del equipo apoyado en esa lectura del
  fragmento, no una demostración inequívoca de la transcripción** —
  no se afirma lo contrario.
- **DECISIÓN DEL EQUIPO, no confirmada por la cita:** el alcance "por
  orden" de la supresión de alarmas repetidas, y que tras aceptar una
  nueva lectura alta genere una alarma nueva aunque la temperatura no
  haya bajado. La cita de T2 no dice "por orden" en ningún momento ni
  resuelve ese caso límite explícitamente.

## Configuración global

- Fila única en BD (`ConfiguracionAlarma`, id fijo `1`, nunca
  autogenerado). `GET`/`PUT /api/v1/alarmas/configuracion`, ambos
  **ROLE_SUPERVISOR únicamente** — es una acción de política, no una
  operación diaria del circuito (criterio del equipo).
- `PUT` hace upsert: crea la fila si no existe, actualiza si ya
  existe (verificado: dos `PUT` sucesivos dejan una sola fila).
- Validación: `umbralTemperaturaC` debe ser representable con la
  misma precisión que las lecturas (`DECIMAL(6,2)`, `@Digits(integer
  = 4, fraction = 2)`) — 3 decimales o 5 enteros → 400. `destinatarios`
  debe ser una lista de emails separados por coma, todos con formato
  válido (expresión regular simple, sin librerías nuevas) → 400 si no.
- **Sin fila:** `GET` devuelve 404 explícito (`"No hay configuracion
  de alarmas definida"`); `registrarLectura` **no evalúa ninguna
  alarma** — no hay umbral por defecto inventado.

## Disparo

- Se evalúa **toda lectura aceptada** (no descartada por caudal/masa),
  incluso si TP14 decide no guardarla en el detalle histórico —
  verificado explícitamente con una orden con `detalleIntervaloSegundos`
  configurado: una lectura que no generó fila de detalle sí generó
  alarma.
- Condición: `temperaturaC > umbralTemperaturaC` (estrictamente mayor;
  igual al umbral no dispara) **y** la orden no tiene ya una alarma
  con `aceptada=false` (máximo una pendiente por orden).
- Cada alarma congela `temperaturaC` (valor real que la disparó) y
  `umbralAplicado` (el umbral vigente en ese momento) — si la
  configuración global cambia después, las alarmas ya disparadas
  conservan su explicación original.
- Coordinación con `aceptarAlarma`: el disparo ocurre dentro del mismo
  bloqueo de fila de la orden (`PESSIMISTIC_WRITE`, ya usado por
  `registrarLectura`) y la misma transacción; `aceptarAlarma` toma el
  mismo bloqueo antes de leer/modificar la alarma, serializando ambas
  operaciones sobre la misma orden.

## Consulta y aceptación

- `GET /api/v1/orders/{orderNumber}/alarmas` — todas las alarmas de la
  orden, ordenadas por fecha de disparo. `POST .../alarmas/{id}/aceptacion`
  — marca `aceptada=true`. Ambos: **ROLE_OPERADOR o ROLE_SUPERVISOR**
  (igual criterio que las operaciones "compartidas" de TP27).
- Aceptación: `usuarioAceptacion` del contexto de seguridad (nunca del
  body), `fechaAceptacion` del servidor, `observacion` el único campo
  opcional del body.
- Alarma inexistente **o** perteneciente a otra orden → 404 en ambos
  casos, sin distinguirlos (`findByIdAndOrden` scoped). Alarma ya
  aceptada → 409. Verificado que una alarma de una orden no se acepta
  contra otra orden (cross-order 404).
- Tras aceptar, una lectura nueva que vuelva a superar el umbral
  genera una **alarma nueva** (fila nueva), no reabre la aceptada —
  incluso si la temperatura no bajó entre una lectura y la siguiente.

## Archivos afectados

- `Alarma.java`, `AlarmaRepository.java`, `ConfiguracionAlarma.java`,
  `ConfiguracionAlarmaRepository.java` (nuevos).
- `IConfiguracionAlarmaBusiness`/`ConfiguracionAlarmaBusiness` (nuevos,
  negocio de la configuración global, separado de `OrdenBusiness`
  porque no es un sub-recurso de orden).
- `IOrdenBusiness`/`OrdenBusiness` — hook de disparo en
  `registrarLectura`; `listarAlarmas`/`aceptarAlarma` nuevos.
- DTOs nuevos: `ConfiguracionAlarmaRequest/Response`, `AlarmaResponse`,
  `AceptacionAlarmaRequest`.
- `ConfiguracionAlarmaRestController` (nuevo, `GET`/`PUT` de
  configuración; replica los `@ExceptionHandler` locales de
  `OrdenRestController` — validación, JSON inválido y
  `AccessDeniedException` — por el mismo motivo ya documentado: evitar
  el forward interno a `/error`).
- `OrdenRestController` — `GET .../alarmas`, `POST .../alarmas/{id}/aceptacion`.
- `Constants.URL_ALARMAS` (nuevo).

## Hallazgo de esta unidad

Al mapear el controller de configuración, el primer intento dejó
`@GetMapping`/`@PutMapping` sin el sufijo `/configuracion` en la
anotación del método (solo en la constante de base). El resultado no
fue un 404 "normal": Spring MVC, al no encontrar ningún handler para
esa ruta exacta, dispara su propio mecanismo de "no handler found",
que también termina en el forward interno a `/error` — el mismo
patrón ya documentado para `MethodArgumentNotValidException` (F2) y
`AccessDeniedException` (TP27), ahora confirmado para una tercera
causa distinta (ruta sin handler). Corregido agregando el path
explícito a cada mapeo; no fue necesario tocar `SecurityConfiguration`
ni los `@ExceptionHandler` ya agregados (que sí funcionan correctamente
una vez que la ruta existe).

## Verificación realizada

Compilación limpia; arranque local con `mysqldev` (Hibernate creó las
tablas `alarmas` y `configuracion_alarma`); OpenAPI confirmado con
`GET /v3/api-docs` (5 endpoints nuevos). Verificado por HTTP y por
consulta SQL directa:

- Umbral con 3 decimales o 5 enteros → 400; destinatarios con formato
  inválido → 400; `PUT` válido → 200, upsert confirmado (1 fila tras
  dos `PUT`); `GET` sin fila → 404; `GET`/`PUT` con `ROLE_OPERADOR` →
  403 (solo supervisor).
- Lectura con `temperaturaC` > umbral dispara 1 alarma; lecturas
  siguientes por encima del umbral mientras está pendiente no generan
  otra (confirmado por SQL: 0 órdenes con más de una alarma
  `aceptada=0` simultánea); `temperaturaC` exactamente igual al umbral
  no dispara; lectura descartada (caudal≤0) con temperatura muy alta
  no dispara nada.
- Lectura con intervalo de TP14 configurado que no generó detalle
  igual disparó alarma (confirmado).
- Aceptación: sin token → 401; sin rol → 403; alarma inexistente →
  404; alarma de otra orden → 404 (cross-order); alarma ya aceptada →
  409; aceptación sin body (observación ausente) → 200 con
  `observacion=null`; aceptación con observación → 200 con el valor
  guardado; `usuarioAceptacion` refleja el actor autenticado (distinto
  para operador y supervisor en sendas alarmas, confirmado por SQL).
  Tras aceptar, una lectura nueva por encima del umbral generó una
  alarma nueva (id distinto), con la anterior quedando `aceptada=true`
  sin modificarse.
- Aislamiento entre órdenes: dos órdenes con alarmas propias no se
  interfieren (confirmado por SQL y por el 404 cross-order).

Colección Postman acotada
`postman/iw3-f6-alarmas-configuracion.postman_collection.json` (26
requests) corrida una vez vía Postman CLI: 26/26 requests, 32/32
aserciones, 0 fallos — aserciones limitadas a lo observable por HTTP;
la verificación SQL de arriba no se repite en la colección.

No se repitieron pruebas ya verificadas en unidades anteriores (TP14,
TP19, TP27).

## Pendiente explícito

**Unidad 2 (fuera de esta entrega): envío de correo.** Debe enviarse
después del commit de la transacción que dispara la alarma, fuera del
bloqueo de la orden, para no mandar correos de una operación que
termine revertida ni bloquear una lectura por una falla de SMTP. Se
usará un receptor local de pruebas, sin correos reales. Hasta esa
unidad, TP28/TP29 quedan **parcialmente implementados**: el disparo,
la supresión, la consulta y la aceptación funcionan, pero ninguna
alarma envía un correo todavía.
