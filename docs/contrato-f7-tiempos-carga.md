# Contrato — ETA y tiempo transcurrido (F7, TP32/TP33)

Cubre TP32 (ETA) y TP33 (tiempo transcurrido) sobre el mismo endpoint
de TP31 (`GET /api/v1/orders`): sin endpoint nuevo. No cubre TP34
(alarmas desde UI) ni TP35 (conciliación visible solo en estado 4).

## Separación fuente docente vs. decisión del equipo

Verificado puntualmente contra NotebookLM (BE, 2026-10-23) porque la
fórmula y la visibilidad del ETA solo estaban respaldadas por
`CLAUDE.md` (un documento local del equipo, **no evidencia docente**,
aunque esté redactado en tono normativo):

- **CONFIRMADO (T2, cita literal):** el ETA se calcula con preset,
  masa acumulada y caudal por hora; el tiempo transcurrido se muestra
  **únicamente** mientras la orden está en estado 2 (esta restricción
  de estado sí es textual, pero **solo para el tiempo transcurrido**).
- **NO CONFIRMADO en el texto (verificado, no solo supuesto):** T2 no
  da una fórmula matemática explícita para el ETA, no dice que el ETA
  se muestre "solo en estado 2", y no menciona redondeo, decimales ni
  qué hacer con caudal cero o negativo.
- **INTERPRETACIÓN / DECISIÓN DEL EQUIPO (todo lo de abajo):**
  fórmula `ETA_min = max(preset_kg - masa_kg, 0) / caudal_kg_h * 60`;
  redondeo HALF_UP a 2 decimales en un único paso final; caudal nulo,
  faltante o ≤ 0 → `null` ("no estimable"); masa ≥ preset → `0` sin
  cerrar la orden automáticamente; el ETA se calcula/expone **solo
  para estado 2** (por analogía con TP33, no una exigencia textual
  propia del ETA); formato del contador HH:mm:ss con horas acumuladas
  sin reiniciar a las 24 h; convención "Sin iniciar" antes de la
  primera lectura aceptada.

Detalle completo del fragmento literal y de la consulta a NotebookLM
en `docs/requisitos.md` (TP32/TP33) y `docs/decisiones.md`.

## Qué hace

- `OrdenResumenResponse` (fila de `GET /api/v1/orders`, sin endpoint
  nuevo) agrega:
  - `fechaInicioCarga`: String ISO-8601 **con offset explícito** (ej.
    `"2026-10-23T10:15:30.123456-03:00"`), igual tratamiento que
    `fechaUltimaLectura` (que ya existía desde TP31 pero viajaba como
    `LocalDateTime` desnudo — se corrige en esta unidad). `null` hasta
    la primera lectura aceptada (ya existía como columna en
    `Orden.java` desde F3, sin cambios de almacenamiento: la
    conversión es solo de presentación, en el DTO).
  - `etaMinutos`: `BigDecimal` o `null`. Solo se completa para
    órdenes en estado 2 (`OrdenBusiness.listarOrdenes`); en 1/3/4
    queda `null` a propósito.
- `OrdenBusiness.calcularEtaMinutos(presetKg, masaAcumuladaKg,
  caudalKgH)`: método estático de paquete (sin modificador de
  visibilidad), función pura sin efectos secundarios ni acceso a
  BD/Spring — se puede probar directo desde un test sin contexto.
  No se pudo expresar en el `SELECT NEW` JPQL existente de
  `listarOrdenes` (fórmula condicional con redondeo): se completa fila
  por fila después de traer los resultados paginados, sin agregar una
  consulta adicional por fila.
- `ZONA_RECEPCION` (`America/Argentina/Cordoba`) se duplica como
  constante en `OrdenResumenResponse` en vez de depender de la capa de
  negocio desde un DTO; debe coincidir con
  `OrdenBusiness.ZONA_RECEPCION` (documentado con ese enlace explícito
  en ambos lugares).

## Casos de borde

| Caso | `etaMinutos` | Tiempo transcurrido (frontend) |
| --- | --- | --- |
| Sin ninguna lectura aceptada (`masaAcumuladaKg`/`caudalKgH` null) | `null` ("no estimable") | "Sin iniciar" (estado 2) |
| Sin activación (estado 1) | `null` (no se calcula) | "—" (oculto) |
| Caudal = 0 o negativo | `null` ("no estimable") | n/a (telemetría distinta) |
| Masa ≥ preset, en estado 2 | `0.00` | sigue corriendo (sin cierre automático) |
| Cerrada/Finalizada (estado 3/4) | `null` (no se calcula) | "—" (oculto, por T2 literal en TP33) |
| Dato desactualizado (sin lecturas nuevas hace rato) | el último valor calculado, sin recalcular con el reloj | sigue corriendo contra `fechaInicioCarga` (dato real, no estimado) |

Sin umbral de obsolescencia inventado: la UI solo aclara "Estimación
según última lectura; actualización manual" y sigue mostrando
`fechaUltimaLectura`, sin ocultar ni marcar como inválido un dato
simplemente por antigüedad (no hay fuente que fije ese límite).

## Caudal ≤ 0: probado aislado, no end-to-end

`OrdenBusiness.registrarLectura` ya descarta las lecturas con
`caudal <= 0` antes de persistir el snapshot (regla de F3): ese caso
de `calcularEtaMinutos` no es alcanzable por el flujo normal de la
API. Se prueba contra el método directamente en
`OrdenBusinessEtaTest` (JUnit puro, sin `@SpringBootTest`, sin base de
datos), con esa limitación documentada en el javadoc del test, no
oculta.

## Verificación realizada en esta unidad

- `OrdenBusinessEtaTest`: 9/9 casos OK — ejemplo de control de la
  fuente (preset 10000/masa 4000/caudal 3000 → 120 min), un caso de
  redondeo no exacto (128.57), un caso que distingue explícitamente
  HALF_UP de HALF_EVEN (0.125 → 0.13, no 0.12), sin masa, sin caudal,
  caudal cero, caudal negativo, preset alcanzado exacto y preset
  superado.
- `./mvnw compile` → `BUILD SUCCESS` (DTO, business y controller de
  esta unidad).
- Frontend: `npm run build` (`tsc -b && vite build`) y `npm run lint`
  sin errores ni advertencias.
- Verificación aislada con Node 22.23.4 (fuera de la app, sin backend
  corriendo) de que `new Date(...)` interpreta correctamente un string
  con offset explícito de 6 dígitos de fracción de segundo (formato
  real de `DateTimeFormatter.ISO_OFFSET_DATE_TIME`), con y sin
  fracción, y aplica el offset `-03:00` correctamente.

## Evidencia manual del usuario — verificación completa (2026-10-23)

Confirmado en navegador real contra el backend real, en varias rondas
de la misma sesión:

- El contador de tiempo transcurrido avanza segundo a segundo, con
  horas acumuladas **superiores a 24** sin reiniciar.
- El ETA permanece **fijo** mientras el contador sigue avanzando — el
  tick de 1 segundo nunca recalcula ni descuenta `etaMinutos`.
- Estados 1/3/4: contador y ETA ocultos (`—`).
- Estado 2 sin lecturas todavía: **"Sin iniciar"** (contador) y **"No
  estimable"** (ETA).
- Orden `500321001` (creada para esta prueba, preset 5000 kg, masa
  acumulada 5200 kg ≥ preset): ETA **"0.00 min"**, la orden permanece
  en estado 2 sin cerrarse sola.
- Orden `500321002` (creada para esta prueba): `fechaInicioCarga =
  2026-10-23T11:47:28.23454-03:00`, contador `00:09:11` a las
  `11:56:39` locales — coincide con la diferencia real (9 min 11 s).
  `fechaUltimaLectura = 2026-10-23T11:48:01.96109-03:00` (distinta de
  `fechaInicioCarga`, tras una segunda lectura) y `etaMinutos =
  145.16`, ambos correctos.

**TP32/TP33 quedan verificados para este alcance** (ver
`docs/requisitos.md`).

## Límites aceptados (no ocultos)

- Caudal ≤ 0: no es alcanzable por el flujo normal de la API
  (`registrarLectura` lo descarta antes de persistir); se cubrió de
  forma aislada con `OrdenBusinessEtaTest`, no visualmente.
- No hay framework de pruebas automatizadas de UI instalado en este
  frontend; no se incorporó uno para esta unidad, por instrucción
  explícita de no agregar funcionalidades nuevas fuera de alcance —
  toda la verificación de TP32/TP33 es manual, no automatizada.
