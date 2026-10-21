# Contrato — Tara y activación (F3, primera unidad)

Cubre la transición 1→2 (registro de tara, generación de la clave de
activación de 5 dígitos — TP08) y la operación de activación (validación
de clave, devolución de preset, idempotente — TP09). No cubre
telemetría, descarte de lecturas, snapshot ni histórico (TP10-TP13,
siguiente unidad de F3) ni cierre/pesaje final (F4).

## Endpoint 1: `POST /api/v1/orders/{orderNumber}/tara`

Autenticado con JWT, igual que el resto de la API.

### Request

```json
{ "taraKg": 12000.000 }
```

`taraKg`: `BigDecimal` obligatorio, estrictamente mayor a 0
(`@Positive`), `DECIMAL(15,3)` (`@Digits(integer=12, fraction=3)`, mismo
criterio que `presetKg`/`capacidadKg` en F2). No acepta `estado`, clave,
`fechaTara` ni el actor del historial: el servidor los genera.

### Response 200 (éxito)

```json
{
  "orderNumber": 910001,
  "estado": 2,
  "taraKg": 12000.000,
  "fechaTara": "2026-10-21T09:07:00.711802876",
  "claveActivacion": 54231
}
```

`claveActivacion` se devuelve **únicamente** en esta respuesta — ninguna
otra respuesta de esta unidad (ni la de activación) vuelve a incluirla,
y nunca se escribe en logs de aplicación ni en `observacion` de
`HistorialEstado`. Generada con `SecureRandom`, entero en
`[10000, 99999]`.

### Errores

| Código | Caso |
| --- | --- |
| 400 | `taraKg` ausente, no positivo, o con más de 3 decimales/12 enteros |
| 401 | Sin token o token inválido/alterado (estándar de Spring Security) |
| 404 | No existe una orden con ese `orderNumber` |
| 409 | La orden no está en estado 1 (incluye el caso de tara repetida) |
| 500 | Error no clasificado |

## Endpoint 2: `POST /api/v1/orders/{orderNumber}/activacion`

Autenticado con JWT, igual que el endpoint anterior.

### Request

```json
{ "clave": 54231 }
```

`clave`: `Integer` obligatorio, `[10000, 99999]` (`@NotNull @Min(10000)
@Max(99999)`), validado con Bean Validation antes de llegar al negocio.

### Response 200 (éxito)

```json
{
  "orderNumber": 910001,
  "presetKg": 10000.500,
  "fechaActivacion": "2026-10-21T09:08:39.694564"
}
```

`estado` no aparece en esta respuesta porque **no cambia**: la activación
ocurre dentro del mismo estado 2 generado por la tara (T1 describe el
estado 2 como "con pesaje inicial registrado", que abarca tanto "ya
tiene clave, esperando activación" como "ya está cargando").

### Idempotencia

Mientras la orden siga en estado 2, repetir la activación con la **misma
clave correcta** devuelve el mismo `presetKg` y la **misma
`fechaActivacion`** ya fijada — no se vuelve a escribir ni se genera una
fecha nueva. La clave se valida siempre, incluso después de una
activación exitosa: no hay un atajo que la omita. Una clave incorrecta
sigue devolviendo `400` aunque la orden ya esté activada.

`fechaActivacion` se trunca a microsegundos
(`ChronoUnit.MICROS`) antes de fijarse por primera vez, para que
coincida exactamente con la precisión real de la columna `datetime(6)`
de MySQL — ver `docs/decisiones.md`, sección F3, sobre el bug que esto
corrige (sin el truncado, la respuesta de la primera activación, con el
valor en memoria en nanosegundos, no coincidía con la de una activación
repetida, que relee el valor ya truncado por la base).

### Errores

| Código | Caso |
| --- | --- |
| 400 | `clave` ausente o fuera de `[10000, 99999]` (validación) |
| 400 | `clave` no coincide con la de la orden (`InvalidActivationKeyException`) — el mensaje nunca incluye ningún valor de clave |
| 401 | Sin token o token inválido/alterado |
| 404 | No existe una orden con ese `orderNumber` |
| 409 | La orden no está en estado 2 (incluye activación antes de registrar tara) |
| 500 | Error no clasificado |

## `fechaActivacion` ≠ inicio real de carga

`fechaActivacion` es el instante en que la API acepta la clave, no el
inicio físico real de la carga. La relación con la primera telemetría
válida (y si hace falta un campo separado `fechaInicioCarga`) se resuelve
en la siguiente unidad de F3, contrastando la fuente — no se asume acá.

## Identificación de la orden y de la clave

T1 no especifica cómo se identifica la orden en estas llamadas ni si hay
riesgo de colisión de clave entre órdenes simultáneas (DATO FALTANTE,
confirmado al releer la fuente para esta unidad — ver
`docs/decisiones.md`, sección F3). DECISIÓN DEL EQUIPO: `orderNumber` en
el path (mismo identificador público ya usado en F2), clave en el body
(nunca en la URL, para no quedar en logs de acceso). La clave se valida
contra la orden ya identificada por `orderNumber`, nunca se busca por
clave: no necesita unicidad global entre órdenes concurrentes.

## Concurrencia

`OrdenRepository.findByOrderNumberForUpdate` usa
`@Lock(LockModeType.PESSIMISTIC_WRITE)` sobre una consulta JPQL por
`orderNumber`, dentro de la misma transacción
(`@Transactional(rollbackFor = Exception.class)`) que hace la
comprobación de estado y la escritura, tanto en tara como en activación.
No se usa `@Version`. Se lanzaron dos `curl` en paralelo (ver
`docs/decisiones.md` y `docs/estado-proyecto.md`, sección F3) y el
resultado observado es compatible con esa serialización, no una prueba
directa de contención del lock: dos taras simultáneas sobre la misma
orden → una `200`, otra `409`; dos activaciones correctas simultáneas →
ambas `200` con `fechaActivacion` idéntica.

## Auditoría

La tara agrega una fila en `HistorialEstado` (`estadoNuevo=2`,
`fechaHora` = la misma instancia de `fechaTara`, `usuarioOProceso` = el
actor autenticado), mismo patrón que la creación en F2. La activación
**no** agrega ni modifica ninguna fila de `HistorialEstado`, porque no
cambia `estado` — decisión del equipo, ver `docs/decisiones.md`, sección
F3, para la justificación completa contra el requisito confirmado de
T1 ("cada cambio de estado").

## Excepciones de dominio

`InvalidStateException` e `InvalidActivationKeyException` son nuevas,
viven en `ar.edu.iua.iw3.ordenes.business.exception` (específico de este
dominio) y no modifican la taxonomía heredada
(`business/execption/{NotFoundException,FoundException,BusinessException}`).
Se reutilizó `NotFoundException` (ya existente, no se había usado desde
ningún controller hasta esta unidad) para el caso de orden inexistente.

## Documentación OpenAPI y colección Postman (ampliación de TP23/TP24)

**OpenAPI:** ambos endpoints anotados con `@Operation` + `@ApiResponses`
con ejemplos reales para `200`, `400` (clave incorrecta y clave fuera de
rango), `401`, `404`, `409` y `500`. Verificado contra `GET /v3/api-docs`
con la app corriendo: ambos paths nuevos
(`/api/v1/orders/{orderNumber}/tara` y
`/api/v1/orders/{orderNumber}/activacion`) presentes.

**Postman:** colección nueva y separada,
`postman/iw3-f3-tara-activacion.postman_collection.json` (conserva
`iw3-f2-recepcion.postman_collection.json` sin modificar), 17 requests:
login, dos órdenes de apoyo, activación antes de tara (409), tara sobre
orden inexistente (404), tara válida, tara repetida (409), clave
incorrecta (400), clave fuera de rango (400), clave ausente (400),
activación sobre orden inexistente (404), activación válida, reintento
idempotente (misma `fechaActivacion` y mismo `orderNumber`/`presetKg`),
clave incorrecta después de activar (400), ambos endpoints sin token
(401) y una tara final sobre la orden de apoyo B, que confirma que
seguía en estado 1 después de los intentos rechazados (activación antes
de tara, llamada sin token) — no que esos rechazos no tuvieron ningún
otro efecto lateral posible, algo que esta prueba no puede demostrar.
**No ejercita las dos carreras concurrentes**
(dos taras o dos activaciones simultáneas): no es práctico expresarlas
en el runner secuencial de Postman; se probaron con `curl` en paralelo.
Ejecutada con `postman collection run`, credenciales solo por
`--env-var` (sin `postman login`, sin `--output`): **17 requests, 20
aserciones, 0 fallos**.

## Límite de la verificación de esta unidad

La ausencia de cambios en `ordenes`/`historial_estados` ante los
rechazos se apoya en la estructura del código (la excepción de estado se
lanza antes de cualquier `save()`, dentro de la misma transacción) y en
evidencia HTTP indirecta, no en una lectura directa de la base: esta
sesión no tuvo acceso a un cliente MySQL ni a `docker exec` (sin `sudo`
interactivo en el entorno sandboxed). Ver el detalle completo en
`docs/decisiones.md` y `docs/estado-proyecto.md`, sección F3.
