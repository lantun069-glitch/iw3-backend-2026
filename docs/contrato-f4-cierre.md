# Contrato — Cierre explícito 2→3 (F4, TP15)

Cubre únicamente TP15: el endpoint que cierra la carga y bloquea nuevas
lecturas de detalle. No cubre pesaje final ni conciliación (TP16–TP18,
todavía pendientes de esta misma fase).

## Endpoint: `POST /api/v1/orders/{orderNumber}/cierre`

Autenticado con JWT, igual que el resto de la API. Sin body.

### Response 200 (éxito)

```json
{
  "orderNumber": 940556,
  "estado": 3,
  "fechaFinCarga": "2026-10-21T19:43:05.798897"
}
```

### Precondiciones y errores

| Código | Caso |
| --- | --- |
| 401 | Sin token o token inválido/alterado |
| 404 | No existe una orden con ese `orderNumber` |
| 409 | La orden no está en estado 2 (incluye el cierre repetido: tras el primer cierre exitoso queda en estado 3 y esta misma rama lo rechaza) |
| 409 | La orden está en estado 2 pero no tiene `fechaActivacion` (nunca se activó) |
| 409 | La orden está activada pero no tiene ninguna lectura aceptada (`fechaUltimaLectura` nula) |
| 500 | Error no clasificado |

Las dos últimas precondiciones (activación y al menos una lectura
aceptada) **no están en la fuente textual de T1** para este endpoint —
T1 solo exige "un endpoint que permita cerrar la orden bloqueando la
recepción de nuevos datos de detalle... la orden pasa a estado 3".
Son **DECISIÓN DEL EQUIPO** para esta unidad: cerrar una carga que nunca
llegó a activarse o que nunca recibió ninguna lectura válida no
corresponde a un cierre real del circuito de carga descrito en T1, y
dejarlo pasar produciría una conciliación sin ningún dato de masa
cargada que calcular (TP16–TP18, próxima unidad). No es un requisito
textual confirmado; si una fuente posterior lo contradice, se revierte
sin tocar el resto de este contrato.

## `fechaFinCarga` ≠ instante de esta llamada

`fechaFinCarga` se fija **igual a `fechaUltimaLectura`** (la última
lectura VÁLIDA recibida por la orden antes del cierre), no al instante
real en que se invoca este endpoint. Esto es una interpretación del
equipo de "fecha y hora de fin de carga" (T1, anexo, TP20): se adoptó
como valor de fin de carga el de la última lectura aceptada, en vez del
momento en que un operador decide formalizar el cierre administrativo
— ambos momentos pueden diferir (p. ej. la última lectura llegó, el
preset se alcanzó, y recién unos minutos después se llama a este
endpoint).

**Precisión necesaria sobre esta fecha:** `fechaFinCarga` es el valor
que el sistema **adopta** como fin de carga a partir del dato
disponible (la última lectura aceptada), no una prueba ni una medición
directa del instante físico real en que dejó de fluir producto. Puede
existir una brecha no medida entre ese instante físico y el momento en
que la lectura que lo refleja efectivamente llegó y fue aceptada por la
API (latencia de red, frecuencia de muestreo del instrumento, etc.):
esta unidad no tiene forma de acotar ni verificar esa brecha, y no se
afirma que sea nula.

El instante real de la llamada al endpoint de cierre **sí** se
registra, pero en otro lugar: como `fechaHora` del `HistorialEstado` de
la transición a estado 3. Esa es la "hora efectiva del cierre" (cuándo
se ejecutó administrativamente la transición), distinta de
`fechaFinCarga` (el valor adoptado para el fin de carga, según la
última lectura válida — no necesariamente el instante físico exacto).
Ambos valores se truncan a microsegundos (`ChronoUnit.MICROS`), mismo
criterio que `fechaActivacion`/`fechaUltimaLectura` en la unidad
anterior, para coincidir con la precisión real de la columna
`datetime(6)` de MySQL.

## Bloqueo de lecturas posteriores

El cierre no agrega ningún chequeo nuevo a `registrarLectura`: alcanza
con que la transición deje la orden en estado 3, porque
`registrarLectura` ya rechaza con 409 cualquier orden que no esté en
estado 2 (ver `docs/contrato-f3-telemetria.md`). Snapshot e histórico
quedan exactamente como estaban al momento del cierre — no se tocan.

## Concurrencia

Mismo mecanismo que tara/activación/lectura:
`OrdenRepository.findByOrderNumberForUpdate` con
`@Lock(LockModeType.PESSIMISTIC_WRITE)`, dentro de la misma transacción
(`@Transactional(rollbackFor = Exception.class)`) que hace el chequeo de
precondiciones y la escritura. No se usa `@Version`.

Se verificaron dos carreras con `curl` en paralelo (ver
`docs/estado-proyecto.md`, sección F4, para el detalle completo):

- **Cierre/cierre** sobre la misma orden: una respondió `200` (estado 3,
  `fechaFinCarga` fijado), la otra `409` ("no está en estado 2, estado
  actual: 3") — serialización correcta, sin doble transición.
- **Cierre/lectura** sobre la misma orden: en la corrida observada, el
  cierre ganó la carrera (`200`) y la lectura concurrente fue rechazada
  (`409`); una lectura posterior confirma que la orden quedó en estado 3
  (no se reabrió, no se aceptó el detalle concurrente). No se forzó
  deliberadamente el orden contrario (lectura ganando la carrera): el
  resultado observado es compatible con la serialización del lock, no
  una prueba exhaustiva de ambos órdenes de llegada posibles.

## Excepciones de dominio

Se reutiliza `InvalidStateException` (ya existente desde F3, ver
`docs/contrato-f3-tara-activacion.md`) para las tres precondiciones de
409 — no se creó una excepción nueva para "sin activación" ni para "sin
lecturas aceptadas": las tres son, en esencia, "la orden no cumple la
condición de negocio requerida para transicionar", igual que su uso ya
establecido en `registrarLectura`.

## Documentación OpenAPI y colección Postman (ampliación de TP23/TP24)

**OpenAPI:** el endpoint está anotado con `@Operation` + `@ApiResponses`
con ejemplos reales para `200`, `401`, `404`, `409` (los tres casos) y
`500`. Verificado contra `GET /v3/api-docs` con la app corriendo: el
path `/api/v1/orders/{orderNumber}/cierre` está presente, con las
respuestas esperadas y el esquema `CierreResponse` en
`components.schemas`.

**Postman:** colección nueva y separada,
`postman/iw3-f4-cierre.postman_collection.json` (conserva las
colecciones de F2/F3 sin modificar), 14 requests: login, dos órdenes de
apoyo, cierre sobre orden inexistente (404), cierre en estado 1 (409),
tara válida sobre A, cierre en estado 2 sin activar (409), activación
válida, cierre activada sin lecturas (409), lectura válida, cierre
válido (200, verifica `fechaFinCarga == fechaUltimaLectura` de la
lectura anterior), cierre repetido (409), lectura después del cierre
(409) y cierre sin token (401). **No ejercita las dos carreras
concurrentes** (cierre/cierre, cierre/lectura): el runner secuencial de
Postman no permite expresar dos requests verdaderamente simultáneas; se
probaron con `curl` en paralelo (ver sección "Concurrencia" arriba).
Ejecutada con `postman collection run`, credenciales solo por
`--env-var` (sin `postman login`, sin `--output`): **14 requests, 16
aserciones, 0 fallos**.

## Verificación ejecutada en esta unidad

Con la app arrancada localmente (`mysqldev`, MySQL ya levantado por una
sesión anterior, ver `docs/desarrollo-local.md`) y JWT real de
`.env.testuser`, se probaron con `curl` —fuera de la colección
Postman— cada rechazo individual en el orden en que una orden real los
atravesaría (cierre en estado 1 → tara → cierre sin activar → activación
→ cierre sin lecturas → lectura válida → cierre exitoso → cierre
repetido → lectura posterior al cierre → sin token), más las dos
carreras de concurrencia descriptas arriba. Todos los resultados
coincidieron con lo documentado en esta unidad antes de escribir la
colección Postman (que repite el camino feliz y los rechazos
secuenciales, no las carreras).

## Verificación directa de BD ejecutada por el usuario

Igual que en la segunda unidad de F3 (TP13), esta sesión no tuvo acceso
a un cliente MySQL ni a `docker exec` directo en este entorno. La
consulta puntual se preparó y quedó pendiente hasta que el usuario la
corrió directamente contra la base y confirmó el resultado.

**Corrección de nombre de columna, detectada por el usuario al
ejecutarla:** el nombre real de la columna del actor en
`historial_estados` es `usuariooproceso` (sin guiones bajos, por las
dos mayúsculas consecutivas de `usuarioOProceso` — Hibernate no inserta
separador entre mayúsculas consecutivas), **no** `usuario_o_proceso`
como se había escrito en la versión anterior de esta sección. Este
mismo hallazgo ya estaba registrado desde F2 (ver `docs/decisiones.md`,
sección F2, "Hallazgo de nomenclatura de columnas") para la misma tabla
y columna; esta sección no lo había aplicado al redactar la consulta de
F4. La consulta corregida:

```sql
SELECT o.order_number, o.estado, o.fecha_fin_carga,
       h.estado_nuevo, h.fecha_hora, h.usuariooproceso
FROM ordenes o
JOIN historial_estados h ON h.orden_id = o.id
WHERE o.order_number = <orderNumber>
ORDER BY h.fecha_hora;
```

**Resultado, verificado puntualmente para las órdenes
940556/950632/960632 (las tres de la sesión de implementación/curl de
esta unidad), sin generalizar a órdenes u escenarios no probados:** las
tres muestran exactamente una fila de `historial_estados` con
`estado_nuevo=3` (una sola transición a estado 3 por orden, sin
duplicados — compatible con el rechazo observado del cierre repetido y
de la carrera cierre/cierre) y exactamente una fila de `detalles_carga`
de 4000 kg (coincide con la única lectura aceptada que recibió cada
orden antes de su cierre en esta sesión). `fechaFinCarga` de `ordenes`
coincide con la `fecha_lectura` de esa fila de detalle en las tres
órdenes — **esto confirma que el valor adoptado por el sistema para
`fechaFinCarga` es efectivamente el de la última lectura aceptada,
tal como implementado; no es, ni pretende ser, una medición directa del
instante físico en que realmente dejó de fluir producto** (ver sección
anterior). No se verificó el valor de `usuariooproceso` ni `fecha_hora`
de esa fila contra un valor esperado puntual en esta ronda (la consulta
no se adjuntó con ese nivel de detalle); se confirmó la cardinalidad
(una fila por orden) y la coincidencia de `fechaFinCarga`.

## Límite de la verificación de esta unidad

La fila de `HistorialEstado` generada por el cierre ya fue confirmada
por consulta directa (ver arriba) para las tres órdenes de esta sesión,
no para el universo general de casos. El contenido exacto de
`usuariooproceso` (coincide con el actor autenticado) y la relación
exacta entre `fecha_hora` de esa fila y el instante real de la llamada
HTTP no se verificaron con ese nivel de detalle en esta ronda — se
infieren del código (se guardan en la misma transacción, antes de
confirmar) y de la ausencia de error en la respuesta HTTP, igual que
antes de esta consulta.
