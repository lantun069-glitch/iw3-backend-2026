# Contrato — Promedios de temperatura, densidad y caudal (F6, TP19)

Cubre únicamente TP19: promedio de cada variable sobre el histórico
almacenado de la orden, incluido en la conciliación. No agrega
alarmas (TP28/TP29).

## Fuente y alcance

- **CONFIRMADO (T1):** "el promedio de temperatura, **según los datos
  almacenados**... el promedio de la densidad y el promedio de
  caudal." T2: opcional en el segundo parcial (se admite último
  valor), obligatoria en el final.
- La fuente ya resuelve explícitamente qué conjunto de lecturas
  integra el promedio: **el histórico almacenado** (`DetalleCarga`),
  no todas las lecturas aceptadas que llegaron. Antes de la unidad de
  frecuencia configurable (TP14) ambos conjuntos coincidían siempre
  (toda lectura aceptada generaba una fila de detalle). Desde TP14,
  con `detalleIntervaloSegundos` configurado, el histórico queda
  **muestreado**: puede haber más lecturas aceptadas que filas de
  detalle. El promedio de esta unidad es sobre las filas
  **efectivamente guardadas**, no sobre todas las aceptadas — esto no
  era una duda abierta, está resuelto por la cita de T1 de arriba; no
  se consultó NotebookLM porque no hacía falta.
- Precisión del cálculo (aritmético simple, no ponderado por tiempo;
  escala de salida; redondeo solo del resultado final; evitar la
  conversión a `Double` del `AVG` de JPA) y mostrar los promedios como
  campos nuevos sin reemplazar los últimos valores son **DECISIÓN DEL
  EQUIPO**, aprobada explícitamente por el usuario.

## Cálculo

- Promedio aritmético simple (sin ponderar por tiempo entre lecturas).
- **Sin usar `AVG` de JPQL/Hibernate**: la especificación JPA define
  que `AVG` siempre devuelve `Double`, independientemente del tipo de
  la columna — eso perdería precisión en campos `BigDecimal`. Se usa
  `SUM(columna)` (que sí preserva `BigDecimal` para columnas
  `BigDecimal`, por la misma especificación) + `COUNT(*)`, y la
  división se hace en `OrdenBusiness` con `BigDecimal.divide(cantidad,
  escala, RoundingMode.HALF_UP)` — redondeando únicamente el resultado
  final de la división, no los sumandos ni ningún paso intermedio.
- Escala de salida por campo (misma convención que las columnas de
  origen): `promedioTemperaturaC` 2 decimales, `promedioDensidad` 4
  decimales, `promedioCaudalKgH` 3 decimales.
- Sin ninguna fila de detalle (`COUNT = 0`): los tres promedios
  quedan en `null`, nunca `0`. En la práctica esto es **inalcanzable**
  para una orden que llegó a estado 3/4 por el circuito normal:
  `cerrarCarga` exige al menos una lectura aceptada antes de permitir
  la transición 2→3, y la regla de TP14 garantiza que la primera
  lectura aceptada de toda orden genera siempre una fila de detalle —
  así que toda orden cerrada tiene como mínimo una fila. Se protege
  igual con el `COUNT`, por si ese invariante cambiara a futuro.
- No se persiste ningún campo de promedio: se calcula en vivo, en el
  mismo método compartido por `registrarPesajeFinal` y
  `consultarConciliacion` (`OrdenBusiness.calcularConciliacion`), igual
  criterio que el resto de la conciliación.

## Cómo se muestran en la conciliación

Se agregan `promedioTemperaturaC`, `promedioDensidad`,
`promedioCaudalKgH` a `Conciliacion` (capa de negocio) y
`ConciliacionResponse` (DTO del controller), **sin quitar**
`ultimaTemperaturaC`/`ultimaDensidad`/`ultimoCaudalKgH`: esos últimos
valores siguen siendo correctos por sí mismos y quitarlos rompería las
colecciones ya verificadas de F2–F5.

## Órdenes existentes

Como el promedio se calcula en vivo a partir de las filas ya
almacenadas, toda orden que ya esté en estado 3/4 (de unidades
anteriores) obtiene un promedio real la primera vez que se consulte
después de esta unidad, sin migración ni backfill. Para órdenes
previas a TP14 (intervalo siempre `null`), ese promedio coincide con
"promedio de todas las aceptadas" por construcción, porque para ellas
el histórico almacenado y el conjunto de aceptadas son el mismo
conjunto.

## Archivos afectados

- `EstadisticasDetalle.java` (nuevo, capa de negocio) — resultado de
  la agregación (3 sumas + cantidad).
- `DetalleCargaRepository.java` — método `calcularEstadisticas`, JPQL
  con `SUM`+`COUNT` (no `AVG`).
- `OrdenBusiness.calcularConciliacion()` — división `BigDecimal` con
  escala/redondeo por campo; `null` si `cantidad = 0`.
- `Conciliacion.java` / `ConciliacionResponse.java` — 3 campos nuevos.
- `OrdenRestController.aConciliacionResponse()` — mapeo; OpenAPI de
  `pesajeFinal`/`conciliacion` (descripción y ejemplos actualizados).

## Verificación realizada

Compilación limpia. Los cuatro casos acordados se ejecutaron contra la
base real, con valores elegidos para que promedio, último valor y
"promedio de todas las aceptadas" sean números distintos entre sí (no
equivalentes):

| # | Caso | Entrada | Resultado |
| --- | --- | --- | --- |
| 1 | Orden sin intervalo (histórico == todas las aceptadas) | 2 lecturas: temp 10.00 y 20.00 | `promedioTemperaturaC=15.00`, `promedioDensidad=0.5500`, `promedioCaudalKgH=1500.000`; `ultimaTemperaturaC=20.00` (distinto del promedio) |
| 2 | Orden con intervalo=5s (histórico muestreado) | Lectura A (temp 20.10, primera, se guarda), lectura B (temp 99.99, aceptada, **no** se guarda por no cumplir el intervalo), lectura C (temp 15.55, pasado el intervalo, se guarda) | `promedioTemperaturaC=17.83` (de A y C únicamente: (20.10+15.55)/2=17.825→HALF_UP→17.83, **requiere redondeo**), `promedioDensidad=0.5228` (0.52275→HALF_UP→0.5228, **requiere redondeo**), `promedioCaudalKgH=3000.000`; `ultimaTemperaturaC=15.55` (≠17.83); si el promedio incluyera la lectura B, sería ≈45.21 (muy distinto) |
| 3 | Mismo promedio en pesaje-final y en el GET posterior | Las dos órdenes de los casos 1 y 2 | Valores idénticos en ambas respuestas, para ambas órdenes |
| 4 | Verificación por SQL directo (no solo por HTTP) | `SUM`/`COUNT` directo sobre `detalles_carga` para las órdenes de los casos 1 y 2 | Caso 1: 2 filas, suma/cantidad coincide con el promedio devuelto. Caso 2: 2 filas (A y C), **sin** la de la lectura B (99.99) — confirma que el promedio excluye exactamente la lectura fuera del intervalo |

**Protección adicional, no uno de los cuatro casos anteriores y no
ejercitada:** sin ninguna fila de detalle (`COUNT=0`), el código deja
los tres promedios en `null` en vez de `0`. No se forzó este
escenario porque es inalcanzable por el circuito normal (ver sección
"Cálculo" más arriba); queda documentado como protección de código,
no como una prueba ejecutada.

Colección Postman acotada
`postman/iw3-f6-promedios-historico.postman_collection.json` (17
requests: login + 2 circuitos completos, uno sin intervalo y uno con
intervalo=1s/3 lecturas con una espera sincrónica corta entre la B y
la C) ejecutada una vez vía Postman CLI: 17/17 requests, 27/27
aserciones, 0 fallos — aserciones limitadas a lo observable por HTTP;
el caso 4 (verificación SQL) se hizo por separado, sobre órdenes
creadas manualmente, no repetido por esta colección.

No se repitieron pruebas ya verificadas en unidades anteriores (TP14,
TP27, TP16-TP18).
