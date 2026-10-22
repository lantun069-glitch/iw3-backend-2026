# Contrato — Pesaje final y conciliación (F4, segunda unidad, TP16–TP18)

Cubre TP16 (pesaje final, transición 3→4, conciliación en la misma
respuesta), TP17 (consulta posterior de la conciliación, solo en estado
4) y TP18 (fórmula de la conciliación). TP19 (promedios) sigue diferido
a F6 — ver `docs/decisiones.md`; esta unidad expone los **últimos
valores** de temperatura/densidad/caudal, identificados explícitamente
como tales (`ultimaTemperaturaC`/`ultimaDensidad`/`ultimoCaudalKgH`), no
como promedios.

## Endpoint 1: `POST /api/v1/orders/{orderNumber}/pesaje-final`

Autenticado con JWT, igual que el resto de la API.

### Request

```json
{ "pesoBrutoKg": 16080.000 }
```

`pesoBrutoKg`: `BigDecimal` obligatorio, estrictamente mayor a 0
(`@Positive`), `DECIMAL(15,3)` (`@Digits(integer=12, fraction=3)`,
mismo criterio que `taraKg`/`presetKg`/`masaAcumuladaKg`). No acepta
`estado`, `fechaPesajeFinal` ni el actor del historial: el servidor los
genera.

### Response 200 (éxito)

```json
{
  "orderNumber": 900001,
  "taraKg": 12000.000,
  "pesoBrutoKg": 16080.000,
  "netoKg": 4080.000,
  "productoCargadoKg": 4000.000,
  "diferenciaKg": 80.000,
  "ultimaTemperaturaC": 18.50,
  "ultimaDensidad": 0.5400,
  "ultimoCaudalKgH": 3000.000
}
```

### Errores

| Código | Caso |
| --- | --- |
| 400 | `pesoBrutoKg` ausente, no positivo, o con más de 3 decimales/12 enteros |
| 401 | Sin token o token inválido/alterado |
| 404 | No existe una orden con ese `orderNumber` |
| 409 | La orden no está en estado 3 (incluye el pesaje final repetido: tras el primero queda en estado 4 y esta misma rama lo rechaza) |
| 500 | Error no clasificado |

## Endpoint 2: `GET /api/v1/orders/{orderNumber}/conciliacion`

Autenticado con JWT. Sin body, sin escrituras: puede llamarse repetidas
veces y siempre devuelve el mismo resultado (ver "Cálculo compartido"
abajo).

### Response 200 (éxito)

Mismo cuerpo que el pesaje final, en la misma orden ya pesada.

### Errores

| Código | Caso |
| --- | --- |
| 401 | Sin token o token inválido/alterado |
| 404 | No existe una orden con ese `orderNumber` |
| 409 | La orden no está en estado 4 |
| 500 | Error no clasificado |

## Cálculo de la conciliación (TP18)

- `netoKg = pesoBrutoKg - taraKg`.
- `productoCargadoKg` = última masa acumulada **ACEPTADA** del snapshot
  de telemetría (`Orden.masaAcumuladaKg`), **no** el `presetKg` — T1 es
  explícito en que el preset no sustituye a la masa realmente cargada
  (ver `docs/requisitos.md`, TP18).
- `diferenciaKg = netoKg - productoCargadoKg`. **Una diferencia
  distinta de cero no causa ningún rechazo** (verificado con los tres
  signos: cero, positiva, negativa — ver "Verificación" abajo). No se
  aplica ninguna tolerancia ni redondeo a porcentaje: no está pedido por
  la fuente.
- `ultimaTemperaturaC`/`ultimaDensidad`/`ultimoCaudalKgH` = los mismos
  campos del snapshot de telemetría (`Orden.temperaturaC`/`densidad`/
  `caudalKgH`), sin ningún cálculo adicional. Son los **últimos
  valores** recibidos antes del cierre, no promedios — TP19 (promedios
  sobre el detalle almacenado) sigue **diferido a F6**, decisión ya
  registrada en la unidad anterior de F3 y confirmada aquí sin cambios.
- Sin redondeo explícito: `BigDecimal.subtract` conserva toda la
  precisión de los operandos recibidos/persistidos; no se llama a
  `setScale` en ningún punto del cálculo.

**Precondición no verificada en el código, porque ya está garantizada
por el circuito:** al llegar a estado 3 (y por lo tanto a estado 4), el
snapshot de telemetría no puede ser `null`, porque `cerrarCarga` (TP15)
ya exige al menos una lectura aceptada antes de permitir la transición
2→3, y ninguna operación posterior lo modifica. No se agregó ningún
chequeo de nulidad adicional en `calcularConciliacion` por esa razón.

## Cálculo compartido entre ambos endpoints

`OrdenBusiness.calcularConciliacion(Orden)` es un método privado
compartido por `registrarPesajeFinal` (TP16) y `consultarConciliacion`
(TP17): ambos devuelven exactamente el mismo resultado a partir de los
mismos valores ya persistidos (`taraKg`, `pesoBrutoKg`,
`masaAcumuladaKg`, `temperaturaC`, `densidad`, `caudalKgH`). Devuelve un
POJO de negocio (`ar.edu.iua.iw3.ordenes.business.Conciliacion`, mismo
patrón que `LecturaResultado` en la unidad de telemetría: no es un DTO
de controller), que el controller mapea a `ConciliacionResponse`.

**Verificado explícitamente con `curl`:** la respuesta del `POST` de
pesaje final y la del `GET` de conciliación inmediatamente posterior,
sobre la misma orden, son byte-a-byte idénticas (comparación JSON
directa, sin diferencias).

## Transición 3→4 y auditoría

`registrarPesajeFinal` usa el mismo bloqueo pesimista
(`findByOrderNumberForUpdate`, `PESSIMISTIC_WRITE`) y la misma
transacción con rollback (`@Transactional(rollbackFor = Exception.class)`)
que tara/activación/lectura/cierre. Guarda `pesoBrutoKg` y
`fechaPesajeFinal` (instante real de la llamada, truncado a
microsegundos por el mismo motivo de precisión de columna `datetime(6)`
que las unidades anteriores — no derivado de ninguna otra fecha, a
diferencia de `fechaFinCarga` en TP15), transiciona el `estado` a 4 y
agrega una fila en `HistorialEstado` (`estadoNuevo=4`, `fechaHora` =
la misma `fechaPesajeFinal`, `usuarioOProceso` = actor autenticado).

`consultarConciliacion` **no** usa bloqueo pesimista: es una lectura
simple (`OrdenRepository.findByOrderNumber`, sin `@Lock`) que no
participa de ninguna transición de estado — no hay razón para
serializarla contra las demás operaciones.

## Concurrencia

Se verificó con `curl` en paralelo (dos pesajes finales simultáneos
sobre la misma orden, con valores de `pesoBrutoKg` distintos para poder
distinguir cuál ganó): una respuesta `200` (la que adquirió el lock
primero, con su conciliación calculada) y la otra `409` ("no está en
estado 3, estado actual: 4"). Una consulta posterior de conciliación
confirmó que el valor persistido correspondía exactamente al que ganó
la carrera, no al rechazado. Resultado observado en una sola corrida,
no una prueba exhaustiva de ambos órdenes de llegada posibles — mismo
alcance que las carreras ya documentadas en TP15.

## Verificación ejecutada en esta unidad

Con la app arrancada localmente (`mysqldev`, MySQL ya levantado) y JWT
real de `.env.testuser`, se probó con `curl`, fuera de la colección
Postman:

- **Diferencia cero:** tara 12000, bruto 16000, masa 4000 →
  `netoKg=4000.000`, `diferenciaKg=0.000`.
- **Diferencia positiva:** tara 12000, bruto 16080, masa 4000 →
  `netoKg=4080.000`, `diferenciaKg=80.000`.
- **Diferencia negativa:** tara 12000, bruto 15900, masa 4000 →
  `netoKg=3900.000`, `diferenciaKg=-100.000`.
- Pesaje final y conciliación sobre orden inexistente (404); sobre
  orden en estado 1 (409, "no está en estado 3" / "no está en estado
  4"); conciliación sobre orden en estado 2 y en estado 3 (409 en
  ambos, antes del pesaje final).
- `pesoBrutoKg` ausente, `=0`, y con 4 decimales (los tres, 400).
- Pesaje final sin token (401).
- Pesaje final repetido (409, estado actual 4); conciliación repetida
  (200, misma respuesta cada vez).
- POST de pesaje final vs. GET de conciliación inmediatamente posterior:
  **idénticos**.
- Carrera de dos pesajes finales concurrentes (`curl` en paralelo):
  un `200`, un `409`, confirmado por una consulta de conciliación
  posterior (ver "Concurrencia" arriba).

Confirmado también con `postman collection run` sobre
`postman/iw3-f4-pesaje-conciliacion.postman_collection.json`: **19
requests, 21 aserciones, 0 fallos** (no ejercita diferencia cero/
negativa ni la carrera de concurrencia — mismo límite documentado que
las colecciones anteriores de F3/F4; esos casos se probaron con `curl`,
ver arriba). `GET /v3/api-docs` confirma ambos paths nuevos
(`/api/v1/orders/{orderNumber}/pesaje-final` y
`/api/v1/orders/{orderNumber}/conciliacion`) y los esquemas
`PesajeFinalRequest`/`ConciliacionResponse`.

## Verificación directa de BD — ejecutada y confirmada por el usuario

Igual que en TP13 y TP15: esta sesión no tuvo acceso a un cliente MySQL
ni a `docker exec` directo en este entorno. La consulta puntual (nombre
de columna ya corregido en TP15, `usuariooproceso` sin guiones bajos)
quedó preparada para las seis órdenes reales de esta unidad —
1792624398 (diferencia cero), 1792624399 (diferencia positiva),
1792624400 (diferencia negativa), 1792624401 (quedó en estado 1,
nunca llegó a pesaje final), 1792624402 (repetición/consulta
antes-después) y 1792624403 (ganadora de la carrera de pesaje final) —
hasta que el usuario la ejecutó directamente contra la base y confirmó
el resultado.

```sql
SELECT o.order_number, o.estado, o.tara_kg, o.peso_bruto_kg,
       o.fecha_pesaje_final, o.masa_acumulada_kg
FROM ordenes o
WHERE o.order_number IN (1792624398, 1792624399, 1792624400, 1792624401, 1792624402, 1792624403)
ORDER BY o.order_number;

SELECT o.order_number, h.estado_nuevo, h.fecha_hora, h.usuariooproceso
FROM historial_estados h
JOIN ordenes o ON o.id = h.orden_id
WHERE o.order_number IN (1792624398, 1792624399, 1792624400, 1792624401, 1792624402, 1792624403)
  AND h.estado_nuevo = 4
ORDER BY o.order_number;
```

**Resultado confirmado por el usuario (reportado en el checkpoint de
esta unidad, no re-ejecutado ni re-verificado por esta sesión):**

| orderNumber | Estado | Tara | Bruto | Masa | Neto | Diferencia |
| --- | --- | --- | --- | --- | --- | --- |
| 1792624398 | 4 | 12000.000 | 16000.000 | 4000.000 | 4000.000 | 0.000 |
| 1792624399 | 4 | 12000.000 | 16080.000 | 4000.000 | 4080.000 | 80.000 |
| 1792624400 | 4 | 12000.000 | 15900.000 | 4000.000 | 3900.000 | -100.000 |
| 1792624401 | 1 | NULL | NULL | NULL | NULL | NULL |
| 1792624402 | 4 | 12000.000 | 16040.000 | 4000.000 | 4040.000 | 40.000 |
| 1792624403 | 4 | 12000.000 | 16500.000 | 4000.000 | 4500.000 | 500.000 |

Los seis valores de bruto/neto/diferencia coinciden exactamente con los
calculados a mano por esta sesión antes de la consulta (ver "Verificación
ejecutada en esta unidad" arriba); para 1792624403 el valor persistido
es el que ganó la carrera por HTTP (`16500.000`), no el rechazado
(`16000.000`). La consulta del historial devolvió **exactamente una
fila de `estado_nuevo=4` por cada una de las cinco órdenes finalizadas**
(sin duplicados, compatible con el rechazo observado del pesaje
repetido y de la carrera), todas con `usuariooproceso=qa_f1_dfab890c`
(el usuario de prueba de F1) y `fecha_hora` **idéntica** a
`fecha_pesaje_final` de la orden correspondiente (reportado por el
usuario como "fecha_coincide=1" para las cinco). Para 1792624401, el
`LEFT JOIN` contra `historial_estados` con `estado_nuevo=4` no devolvió
ninguna fila (todos los campos del historial en `NULL`), confirmando
que esa orden nunca transicionó a estado 4.

## Excepciones de dominio

Se reutiliza `InvalidStateException` (ya existente desde F3) para los
409 de ambos endpoints — ningún caso de esta unidad necesitó una
excepción nueva.

## Documentación OpenAPI y colección Postman (ampliación de TP23/TP24)

**OpenAPI:** ambos endpoints anotados con `@Operation` + `@ApiResponses`
con ejemplos reales. Verificado contra `GET /v3/api-docs` con la app
corriendo.

**Postman:** colección nueva y separada,
`postman/iw3-f4-pesaje-conciliacion.postman_collection.json` (conserva
las colecciones anteriores sin modificar), 19 requests — ver detalle en
"Verificación ejecutada en esta unidad" arriba.
