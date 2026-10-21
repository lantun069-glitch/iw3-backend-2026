# Contrato — Telemetría: descarte, snapshot e histórico (F3, segunda unidad)

Cubre TP10–TP13: recepción de lecturas durante estado 2, descarte por
negocio, snapshot de últimos valores válidos e histórico por orden. No
cubre frecuencia configurable (TP14, opcional en el parcial, no
adelantada en esta unidad), cierre de carga, pesaje final, alarmas ni
frontend.

## Endpoint

`POST /api/v1/orders/{orderNumber}/lecturas`, autenticado con JWT
(`Authorization: Bearer <token>`), mismo criterio de identificación por
`orderNumber` en el path que tara/activación.

### Request

```json
{ "masaAcumuladaKg": 4000.000, "densidad": 0.54, "temperaturaC": 18.5, "caudalKgH": 3000.000 }
```

Los cuatro campos son obligatorios (`@NotNull`). **Deliberadamente sin
`@Positive`/`@DecimalMin`:** caudal/masa ≤ 0 son un caso de negocio
esperado y frecuente (CONFIRMADO, T1: "eso ocurre durante mucho
tiempo... no me interesa"), no un error de formato — se descartan en
el negocio (sección "Reglas de descarte"), no se rechazan con `400`.
No acepta timestamp del cliente: el servidor lo genera.

### Response 200 (siempre, salvo 404/409/401/400/500)

```json
{
  "orderNumber": 920001,
  "aceptada": true,
  "motivoDescarte": null,
  "masaAcumuladaKg": 4000.000,
  "densidad": 0.5400,
  "temperaturaC": 18.50,
  "caudalKgH": 3000.000,
  "fechaUltimaLectura": "2026-10-21T10:07:00.845232",
  "fechaInicioCarga": "2026-10-21T10:07:00.845232"
}
```

Una lectura descartada no es un error: responde `200` con
`aceptada:false`, `motivoDescarte` con el motivo, y el snapshot
**previo** (sin cambios). Si todavía no hubo ninguna lectura aceptada
para la orden, todos los campos de snapshot (incluidas sus fechas) son
`null` — **no se inicializan en cero**, verificado explícitamente.

### Errores

| Código | Caso |
| --- | --- |
| 400 | Algún campo ausente, o con más decimales/enteros que la precisión admitida |
| 401 | Sin token o token inválido/alterado |
| 404 | No existe una orden con ese `orderNumber` |
| 409 | La orden no está en estado 2, **o** no fue activada todavía (ver precondiciones) |
| 500 | Error no clasificado |

## Precondiciones de estado y activación

`estado == 2` **y** `fechaActivacion != null`. Si falta la activación,
`409` con mensaje explícito ("todavía no fue activada"), distinto del
mensaje de estado incompatible.

**DECISIÓN DEL EQUIPO, no cita literal:** T1 no dice explícitamente que
la API deba exigir activación antes de aceptar lecturas; el criterio de
aceptación original de TP10 solo menciona "estado 2". Esta precondición
adicional es coherente con el circuito de habilitación que T1 sí
describe (la clave "habilita el instrumento de carga que junto con el
preset controla la bomba... y se va a comenzar la carga" — sin
activación, no hay instrumento habilitado, luego no debería haber
telemetría real que procesar). No se atribuye como exigencia textual.

## Reglas de descarte

**CONFIRMADO** (T1, ya citado en `docs/fuentes.md`): se descarta si
`caudalKgH <= 0`, si `masaAcumuladaKg <= 0`, o si `masaAcumuladaKg` es
**estrictamente menor** que la última masa aceptada. La igualdad
(`masaAcumuladaKg == últimaMasaAceptada`) **no se descarta** — la
fuente dice "menor", no "menor o igual" (TP11).

**Sin antecedente (DECISIÓN DEL EQUIPO, DATO FALTANTE en la fuente):**
para la primera lectura de una orden (snapshot todavía `null`), la
regla de masa decreciente no se evalúa — solo caudal > 0 y masa > 0.

**Prioridad explícita cuando varios motivos aplican a la vez (DECISIÓN
DEL EQUIPO, no está en la fuente):** `caudal_no_positivo` →
`masa_no_positiva` → `masa_decreciente`. Verificado con una lectura con
`caudalKgH=-1` y `masaAcumuladaKg=-5` simultáneos → `caudal_no_positivo`.

Densidad y temperatura **nunca** participan de la evaluación de
descarte.

## Densidad: duda conservada

**DUDA PENDIENTE, sin resolver por cuenta propia** (igual que en
`docs/fuentes.md`): la fuente dice "un valor entre 0 y 1" y, en la
misma frase, "kilogramos por metro cúbico" — hay una duda abierta sobre
si esa unidad y ese rango son coherentes entre sí, **sin que esta
unidad haya demostrado una incompatibilidad física**. No se afirma que
"0–1 kg/m³" sea incorrecto ni que sea correcto; es la ambigüedad de la
fuente, no una conclusión propia. **Para esta unidad:** se
almacena el valor recibido tal cual, sin conversión ni validación de
rango — decisión provisional. La precisión de columna (`DECIMAL(10,4)`)
es una decisión técnica de almacenamiento, no una toma de postura sobre
la duda (ver "Precisión" abajo).

## `fechaInicioCarga`

**INTERPRETACIÓN DEL EQUIPO apoyada en T1, no una definición literal
inequívoca** (ver `docs/decisiones.md` para la cita completa y la nota
sobre ruido de ASR): se fija en el instante de **procesamiento de la
primera lectura aceptada** para esa orden, generado por el servidor
(no por el instrumento). Se fija **una sola vez**: lecturas aceptadas
posteriores no la modifican. No se equipara a `fechaActivacion` (ya
descartada como "inicio real" en la unidad anterior) ni a ninguna fecha
de otra unidad — ninguna fecha de recepción/tara/activación se toca
desde esta unidad.

**Precisión:** igual que `fechaActivacion` (ver `docs/decisiones.md`,
unidad anterior), truncada a microsegundos (`ChronoUnit.MICROS`) antes
de persistir, para que la respuesta no difiera de una relectura
posterior por un artefacto de precisión de columna. Una misma lectura
usa **la misma instancia de fecha/hora** para `fechaUltimaLectura`, la
fila de `DetalleCarga`, y (si corresponde) `fechaInicioCarga`.

## Snapshot vs. histórico — alcance parcial vs. final

- **Snapshot** (columnas directas en `Orden`: `masaAcumuladaKg`,
  `densidad`, `temperaturaC`, `caudalKgH`, `fechaUltimaLectura`,
  `fechaInicioCarga`): se actualiza con **cada lectura aceptada**.
  CONFIRMADO: "siempre tengo que tener el último valor... independiente
  de si lo guardo en el detalle".
- **Histórico** (`DetalleCarga`, nueva entidad: `orden`, los 4 valores,
  `fechaLectura`): una fila por lectura **aceptada**.
- **Alcance parcial (esta unidad):** cada lectura aceptada genera una
  fila de histórico, sin frecuencia configurable ni deduplicación —
  TP14 confirma que esto es válido y explícitamente opcional para el
  parcial. **Alcance final (no implementado acá):** frecuencia
  configurable en BD, distinta de la de recepción (TP14, fuera de esta
  unidad).

## Respuesta ante repetidas, fuera de orden y concurrentes

- **Repetidas:** una lectura idéntica enviada dos veces se procesa
  **las dos veces como lectura nueva** — no hay deduplicación (coherente
  con que la igualdad de masa no se descarta). Decidir lo contrario
  habría sido inventar una idempotencia no pedida.
- **Fuera de orden (estado):** orden que no está en estado 2, o sin
  activar, → `409` explícito — uso incorrecto de la operación, no un
  dato de negocio esperado.
- **Fuera de orden (secuencia de red):** sin número de secuencia en el
  payload (DATO FALTANTE, T1 no lo menciona), las lecturas se procesan
  en el orden en que cada una **adquiere el bloqueo** sobre la fila de
  la orden (`PESSIMISTIC_WRITE`) — no necesariamente el orden en que
  llegaron por red al servidor. Una lectura "vieja" que adquiere el
  bloqueo después de una ya procesada, con masa menor, se descarta por
  la regla de monotonía — comportamiento esperado dado que no hay forma
  de distinguir "llegó tarde" de "es menor".
- **Concurrentes:** se habla de **orden de procesamiento bajo el
  bloqueo** (`PESSIMISTIC_WRITE`, mismo mecanismo que tara/activación),
  no de garantía de orden de llegada por red. Para dos lecturas
  concurrentes con masas distintas: si se procesa primero la menor,
  ambas pueden aceptarse; si se procesa primero la mayor, la menor se
  descarta por `masa_decreciente`. En los dos casos el snapshot final
  conserva la mayor masa. No se afirma que ambas se acepten siempre.
  Verificado empíricamente un caso real (mayor procesada primero → la
  menor se descartó; snapshot final = la mayor); el otro orden de
  procesamiento es posible pero no se forzó en esta sesión (ver
  `docs/decisiones.md`).

## Precisión y escala (DECISIÓN DEL EQUIPO)

| Campo | Columna | `@Digits` | Nota |
| --- | --- | --- | --- |
| `masaAcumuladaKg` | `DECIMAL(15,3)` | `integer=12, fraction=3` | Reutiliza el patrón ya usado para `taraKg`/`presetKg` |
| `caudalKgH` | `DECIMAL(15,3)` | `integer=12, fraction=3` | Igual criterio que masa (ambos "peso por unidad") |
| `densidad` | `DECIMAL(10,4)` | `integer=6, fraction=4` | Precisión deliberadamente generosa: no toma partido en la duda 0–1 vs. kg/m³ |
| `temperaturaC` | `DECIMAL(6,2)` | `integer=4, fraction=2` | Generosa, admite negativos; sin rango físico impuesto |

Ningún valor se redondea en silencio: `@Digits` rechaza con `400` lo
que no entra en esa precisión, igual criterio que F2/F3 (primera
unidad).

## Concurrencia y transacción

Misma protección que tara/activación: `findByOrderNumberForUpdate`
(`PESSIMISTIC_WRITE`) dentro de `@Transactional(rollbackFor =
Exception.class)`. Snapshot y `DetalleCarga` se escriben en la **misma
transacción** que la comprobación de estado/activación/descarte.

## Verificación del histórico (TP13) — consulta directa de BD

La comprobación de que el histórico no mezcla órdenes, y de que una
lectura descartada no agrega fila en `DetalleCarga`, requería lectura
directa de la base (esta unidad no agrega un endpoint de consulta para
`DetalleCarga`). Esta sesión no tuvo acceso a `docker exec`/cliente
`mysql`; la consulta se preparó y quedó pendiente de ejecución hasta
que el usuario la corrió directamente contra la base y adjuntó la
salida. Nombres de columna corregidos en esta revisión:
`dc.temperaturac` y `dc.caudal_kgh` (no `temperatura_c`/`caudal_kg_h`,
como se había escrito antes de verificar el nombre real generado por
Hibernate).

```sql
SELECT o.order_number, COUNT(dc.id) AS filas_detalle
FROM ordenes o LEFT JOIN detalles_carga dc ON dc.orden_id = o.id
WHERE o.order_number IN (920001, 920003, 920005)
GROUP BY o.order_number;

SELECT o.order_number, dc.masa_acumulada_kg, dc.densidad, dc.temperaturac, dc.caudal_kgh, dc.fecha_lectura
FROM detalles_carga dc JOIN ordenes o ON o.id = dc.orden_id
WHERE o.order_number IN (920001, 920003, 920005)
ORDER BY o.order_number, dc.fecha_lectura;
```

**Resultado (verificado para estas tres órdenes puntuales, no
generalizado a escenarios no probados):** `920001` (A) → 3 filas de
detalle; `920003` (C) → 2 filas; `920005` (E) → 2 filas. En los tres
casos coincide con la cantidad de lecturas **aceptadas** de cada orden
en esta sesión (las descartadas no agregaron fila), las masas
almacenadas coinciden con las esperadas, y `fechaInicioCarga`/
`fechaUltimaLectura` del snapshot coinciden respectivamente con la
`fecha_lectura` de la primera y la última fila de detalle de cada
orden. Ningún detalle de una orden aparece bajo otra — aislamiento
confirmado para A/C/E.

## Documentación OpenAPI y Postman (ampliación de TP23/TP24)

**OpenAPI:** endpoint anotado con `@Operation` + `@ApiResponses`, con
ejemplos para `200` (aceptada y descartada), `400`, `401`, `404`, `409`
(dos variantes: estado incompatible y sin activar) y `500`. Verificado
contra `GET /v3/api-docs`: el path nuevo
(`/api/v1/orders/{orderNumber}/lecturas`) está presente.

**Postman:** colección nueva y separada,
`postman/iw3-f3-telemetria.postman_collection.json` (conserva las dos
colecciones anteriores sin modificar), 17 requests. Mismo patrón de
reinicialización ya corregido en la unidad anterior (milisegundos
acotados, sin flag persistente, limpieza de `jwtToken` al inicio de
cada corrida). Ejecutada con `postman collection run --iteration-count
2`: **34 requests, 38 aserciones, 0 fallos** (17/19 por corrida). No
ejercita la carrera concurrente (se probó con `curl` en paralelo); el
histórico se verificó aparte, por consulta directa de BD (ver sección
anterior).
