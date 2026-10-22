# Contrato — Frecuencia configurable de persistencia del histórico (F6, TP14)

Cubre únicamente TP14: la frecuencia con la que se persiste el detalle
histórico de telemetría puede configurarse por orden, distinta de la
frecuencia de recepción. No cubre TP19 (promedios): queda para una
unidad posterior, con una consecuencia explícita (ver sección final).

## Fuente y alcance

- **CONFIRMADO (T1):** "si los datos se reciben cada un segundo pero
  se decide almacenar cada 10 segundos, en un minuto voy a recibir 60
  registros de los cuales solo voy a guardar 6."
- **CONFIRMADO (T2):** opcional en el segundo parcial (ya cubierto:
  antes de esta unidad se guardaba cada lectura aceptada), obligatoria
  en el final.
- Todo el diseño concreto de abajo (nombre del campo, unidad, que se
  fije solo en la recepción, la política de primera/siguiente lectura,
  qué pasa con órdenes previas a esta unidad) es **DECISIÓN DEL
  EQUIPO**: ninguna fuente dice cómo se configura ni cómo se cuenta
  exactamente.

## Configuración

- Campo nuevo `detalleIntervaloSegundos` (entero, segundos), opcional
  en el JSON de recepción (`POST /api/v1/orders`).
  - Ausente o `null`: guarda el detalle de cada lectura aceptada
    (mismo comportamiento que tenía toda orden antes de esta unidad).
  - Si se envía, debe ser `> 0`: `0` o negativo devuelven **400** (
    `detalleIntervaloSegundos: must be greater than 0`), igual
    mecanismo de validación que el resto de los campos numéricos del
    mismo endpoint.
- Se fija **únicamente al recibir la orden** y queda fijo: esta unidad
  no agrega ninguna forma de modificarlo después. Es una restricción
  deliberada del alcance de esta unidad, no una limitación técnica del
  modelo (el campo es una columna más de la orden; una unidad futura
  podría agregar un endpoint de modificación si se decide que hace
  falta).
- Es **por orden**, no una configuración global: dos órdenes con
  distinto valor (o sin configurar) no se interfieren entre sí.
- Se expone en la respuesta de recepción (`OrdenRecepcionResponse.detalleIntervaloSegundos`)
  para poder confirmarlo sin consultar la base.

## Criterio de guardado del detalle

El **snapshot** (`masaAcumuladaKg`/`densidad`/`temperaturaC`/`caudalKgH`/`fechaUltimaLectura`
en `Orden`) se sigue actualizando con **toda** lectura aceptada, sin
excepción — sin cambios respecto de la unidad anterior (TP12, ya
verificado). Lo único que se filtra por frecuencia es el **detalle
histórico** (`DetalleCarga`):

1. Si la orden no tiene `detalleIntervaloSegundos` configurado (`null`):
   se guarda detalle en cada lectura aceptada. Esto incluye **toda
   orden creada antes de esta unidad**: para ellas la columna no
   existía al crearse, así que no hay forma de que tengan un valor
   distinto de `null` — siguen funcionando exactamente igual que
   antes, sin ninguna rama de código nueva que las afecte.
2. Si tiene un intervalo configurado: la **primera** lectura aceptada
   de la orden siempre se guarda como detalle (ancla la medición del
   intervalo). Las **siguientes** se guardan solo si transcurrió al
   menos ese intervalo desde el **último detalle efectivamente
   guardado** (campo nuevo `fechaUltimoDetalleGuardado` en `Orden`) —
   no desde la última lectura recibida, que puede no haber generado
   detalle. Una lectura aceptada que no cumple el intervalo actualiza
   el snapshot igual, pero no genera fila en `DetalleCarga` ni mueve
   `fechaUltimoDetalleGuardado`.
3. Mismo instante (`ahora`) para la comparación del intervalo, la
   actualización del snapshot y la fecha del detalle guardado — dentro
   de la misma transacción y bajo el mismo bloqueo por fila que ya
   usaba `registrarLectura` (serializa lecturas concurrentes sobre la
   misma orden). No hay ventana donde la decisión de guardar se evalúe
   contra un instante distinto del que efectivamente se persiste.

**Consecuencia documentada (no un error):** con intervalo configurado,
`fechaUltimaLectura` del snapshot puede quedar por delante de
`fechaUltimoDetalleGuardado` — el snapshot refleja siempre la lectura
más reciente, el detalle solo las que cumplieron el intervalo. No se
fuerza un detalle adicional al cerrar la carga (`cerrarCarga`, sin
modificar en esta unidad) solo para igualarlos: es una **decisión del
equipo**, no una lectura obligada de la fuente — T1 describe
explícitamente que la mayoría de las lecturas recibidas NO se
guardan, así que forzar la última porque es la última no es necesario
para cumplir lo que pide la fuente.

## Compatibilidad con órdenes existentes

`detalleIntervaloSegundos` y `fechaUltimoDetalleGuardado` son columnas
nuevas en `ordenes`. El entorno local usa `ddl-auto=update`: Hibernate
las agrega automáticamente al reiniciar la aplicación, con valor
`NULL` para toda fila existente — **esto describe el entorno local de
desarrollo, no una migración de despliegue verificada**; antes de
cualquier despliegue real habría que decidir y probar el mecanismo de
migración (script versionado, revisión de `ddl-auto` en producción,
etc.), fuera de alcance de esta unidad.

Verificado contra la base local (no solo argumentado): las 19 órdenes
ya existentes (creadas en unidades anteriores a F6) tienen ambas
columnas en `NULL`. Por la regla 1 de la sección anterior, esto
significa que **siguen guardando detalle en cada lectura aceptada**,
igual que antes de esta unidad — se confirmó enviando una lectura
nueva a una de esas órdenes (ya activada, sin detalle previo) y
verificando por consulta directa a la base que generó su fila de
detalle, sin ninguna rama de código nueva de por medio.

## Archivos afectados

- `Orden.java` — campos `detalleIntervaloSegundos`, `fechaUltimoDetalleGuardado`.
- `OrdenRecepcionRequest.java` / `OrdenRecepcionResponse.java` — campo
  nuevo opcional, validado con `@Positive` (acepta `null`, rechaza `<=0`).
- `OrdenBusiness.recibirOrden()` — fija el campo desde el request.
- `OrdenBusiness.registrarLectura()` — gate por intervalo antes del
  `save()` de `DetalleCarga`; el bloque que actualiza el snapshot no
  se modificó.
- OpenAPI (`OrdenRestController`, `@Operation`/`@ApiResponse` de
  `recibir`/`registrarLectura`): descripción del campo nuevo, ejemplo
  de request con `detalleIntervaloSegundos` configurado, ejemplo de
  response con el campo.

## Verificación realizada

Compilación limpia. Arranque local con el perfil de desarrollo:
Hibernate agregó las dos columnas nuevas (confirmado con `DESCRIBE
ordenes`). Casos ejecutados contra la base real, por HTTP **y**
confirmados por lectura directa de `ordenes`/`detalles_carga` (no solo
por la respuesta HTTP):

| Caso | Resultado |
| --- | --- |
| `detalleIntervaloSegundos: 0` en la recepción | 400, mensaje de validación |
| `detalleIntervaloSegundos: -5` en la recepción | 400, mensaje de validación |
| Orden sin intervalo, 3 lecturas aceptadas | 3 filas de detalle (consulta SQL) |
| Orden con intervalo = 5 s, lectura 1 | 1 fila de detalle (primera lectura, siempre se guarda) |
| Misma orden, lectura 2 inmediatamente después | snapshot actualizado (confirmado por el cuerpo de la respuesta); **sin** fila nueva de detalle (confirmado por consulta SQL: solo existen las filas de las lecturas 1 y 3, nunca la de la lectura 2) |
| Misma orden, lectura 3 pasados >=5 s | fila de detalle nueva (consulta SQL) |
| Orden preexistente (creada antes de esta unidad, activada, sin detalle previo), 1 lectura nueva | fila de detalle generada (consulta SQL) — confirma que las 19 órdenes previas, todas con ambas columnas nuevas en `NULL`, siguen guardando cada lectura aceptada |
| Lectura descartada (caudal<=0) sobre una orden con intervalo configurado | snapshot, `fechaUltimaLectura` y `fechaUltimoDetalleGuardado` idénticos byte a byte antes/después (consulta SQL); sin fila nueva de detalle |
| Dos órdenes con intervalos distintos (3 s y 8 s) con lecturas interleaved en los mismos instantes (t=0, t=4s, t=9s) | orden de 3 s: 3/3 lecturas guardadas como detalle; orden de 8 s: 2/3 (la de t=4s no, por no cumplir su propio intervalo) — confirmado por SQL, sin interferencia entre ambas |

No se repitieron las validaciones de negocio ya cubiertas por F2–F5
(formato de campos, descarte de lecturas inválidas, cálculo de
conciliación, autorización por rol de F6 anterior).

## Comandos SQL usados para verificar (sin script versionado en el repo)

Las consultas de la tabla anterior se ejecutaron con un programa Java
de un solo uso (JDBC, mismo mecanismo que la preparación de usuarios
de la unidad de roles), no con un cliente `mysql` ni con un script
versionado en `postman/` o `docs/`. Quedan registradas aquí para que
sean reproducibles sin depender de ese programa puntual:

```sql
-- Columnas nuevas (confirmar que ddl-auto=update las agrego)
DESCRIBE ordenes;

-- Snapshot + columnas nuevas de una orden
SELECT order_number, detalle_intervalo_segundos, masa_acumulada_kg, densidad,
       temperaturac, caudal_kgh, fecha_ultima_lectura, fecha_ultimo_detalle_guardado
FROM ordenes WHERE order_number = :orderNumber;

-- Filas de detalle historico de una orden
SELECT dc.masa_acumulada_kg, dc.fecha_lectura
FROM detalles_carga dc JOIN ordenes o ON o.id = dc.orden_id
WHERE o.order_number = :orderNumber ORDER BY dc.id;

-- Compatibilidad con ordenes previas a esta unidad
SELECT COUNT(*) AS total,
       SUM(detalle_intervalo_segundos IS NULL) AS con_intervalo_null,
       SUM(fecha_ultimo_detalle_guardado IS NULL) AS con_fecha_null
FROM ordenes WHERE order_number < 950000;
```

**No existe una colección Postman para TP14.** La unidad anterior
(TP27, roles) sí dejó `postman/iw3-f6-roles-permisos.postman_collection.json`;
esta unidad se verificó solo con `curl` + las consultas SQL de arriba,
sin agregar una colección nueva. Es una limitación a resolver si se
quiere una verificación reproducible sin depender de ejecutarla a
mano otra vez.

## Consecuencia para TP19 (promedios, pendiente)

A partir de esta unidad, el detalle histórico de una orden con
intervalo configurado queda **muestreado**: no contiene todas las
lecturas aceptadas, solo las que cumplieron el intervalo. Cuando se
implementen los promedios (TP19, unidad posterior), el promedio sobre
`DetalleCarga` será un promedio sobre esa muestra, no sobre el total
de lecturas recibidas ni aceptadas — coherente con lo que pide la
fuente ("promedios... sobre registros almacenados"), pero hay que
tenerlo presente al documentar y explicar esos promedios: no son un
promedio de "todo lo que pasó durante la carga", sino de lo que el
intervalo configurado decidió conservar. No se implementa ningún
cálculo de promedio en esta unidad.
