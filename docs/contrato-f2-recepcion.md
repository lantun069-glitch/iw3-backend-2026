# Contrato — Recepción de orden (F2, unidad vertical única)

Cubre únicamente la creación de una orden en estado 1 (TP02-TP07). No
expone consulta, actualización ni borrado de `Orden` ni de sus
referencias — eso no está en el alcance de esta unidad.

## Endpoint

`POST /api/v1/orders`, autenticado con JWT (`Authorization: Bearer <token>`),
igual que el resto de la API ya construida.

### Request (ejemplo con datos ficticios)

```json
{
  "orderNumber": 900001,
  "fechaCargaPrevista": "2026-11-03T08:30:00",
  "presetKg": 10000.500,
  "camion": {
    "externalCode": "CAM-00891",
    "patente": "AB123CD",
    "descripcion": "Camión cisterna GLP",
    "capacidadKg": 12000.000
  },
  "chofer": {
    "externalCode": "CHO-00457",
    "nombre": "Marcos",
    "apellido": "Toledo",
    "documento": "30111222"
  },
  "cliente": {
    "externalCode": "CLI-00019",
    "razonSocial": "Distribuidora del Sur S.A.",
    "contacto": "compras@distribuidorasur.com.ar"
  },
  "producto": {
    "externalCode": "PRD-GLP01",
    "nombre": "GLP",
    "descripcion": "Gas licuado de petróleo"
  }
}
```

Campos obligatorios: `orderNumber`, `camion.externalCode`, `camion.patente`,
`camion.capacidadKg`, `chofer.externalCode`, `chofer.nombre`,
`chofer.apellido`, `cliente.externalCode`, `cliente.razonSocial`,
`producto.externalCode`, `producto.nombre`, `fechaCargaPrevista`,
`presetKg`. Opcionales: `camion.descripcion`, `chofer.documento`,
`cliente.contacto`, `producto.descripcion`.

`estado`, el id interno, `fechaRecepcion` y el actor del historial **no**
se aceptan en el JSON de entrada: el servidor los genera.

**Longitud máxima de los textos (`@Size`, alineada con la columna de
cada campo, verificada contra el DDL real):** `externalCode` (las
cuatro referencias) hasta 100; `camion.patente` hasta 20,
`camion.descripcion` hasta 255; `chofer.nombre`/`chofer.apellido` hasta
100, `chofer.documento` hasta 50; `cliente.razonSocial`/
`cliente.contacto` hasta 150; `producto.nombre` hasta 150,
`producto.descripcion` hasta 255. Exceder cualquiera de estos límites
devuelve `400` (por ejemplo, `"producto.nombre: size must be between 0
and 150"`), sin persistir ningún cambio — igual que un campo obligatorio
ausente, se rechaza antes de llamar a `OrdenBusiness`. No se trunca
ningún valor para que entre en la columna.

### Response 201 (éxito)

```json
{"id": 1, "orderNumber": 900001, "estado": 1, "fechaRecepcion": "2026-10-20T20:06:30.119016859"}
```

No devuelve el grafo completo (camión/chofer/cliente/producto/historial),
solo estos cuatro campos.

### Errores

| Código | Caso | Formato del cuerpo |
| --- | --- | --- |
| 400 | Campo obligatorio ausente/vacío, decimales fuera de precisión, cantidad ≤ 0 | `StandartResponse`, generado por un `@ExceptionHandler` local a `OrdenRestController` (no un `@ControllerAdvice` global) — ver `docs/decisiones.md` sobre por qué no se resuelve con el mecanismo por defecto de Spring |
| 400 | JSON malformado | `StandartResponse`, mismo mecanismo (`@ExceptionHandler` local) |
| 401 | Sin token o token inválido/alterado | Estándar de Spring Security ya existente |
| 409 | `orderNumber` ya existe (chequeo previo o colisión concurrente detectada por la restricción única de la base) | `StandartResponse` (igual que el resto de la API) |
| 500 | Cualquier otro error no clasificado | `StandartResponse` |

El `401` **no** pasa por `OrdenRestController` ni por
`StandartResponse`: lo responde Spring Security antes de llegar al
controlador (cuerpo vacío, sin `devInfo` ni ningún otro campo JSON — no
hay nada que suprimir ahí, porque no hay excepción de negocio
involucrada). Los otros cuatro casos (los dos `400`, el `409` y el
`500`) sí pasan por los `catch`/`@ExceptionHandler` de
`OrdenRestController`, y en los cuatro el campo `devInfo` de
`StandartResponse` no expone la excepción real: todos pasan `null` como
`ex`, así que `getDevInfo()` devuelve siempre `"No stack trace"` para
esta unidad, sin depender de `dev.info.enabled`. El `409` conserva el
mensaje propio de `FoundException`; el `500` usa un mensaje genérico
fijo (no `e.getMessage()`, que podría reflejar la causa interna).
Verificado con los cinco casos reales (ver `docs/decisiones.md`, sección
F2).

## Cantidades: `DECIMAL(15,3)`

`presetKg` y `capacidadKg` son `BigDecimal`, mapeados a `DECIMAL(15,3)`
(hasta 12 enteros + 3 decimales). La validación `@Digits(integer=12,
fraction=3)` rechaza con 400 un valor con más de 3 decimales o más de 12
enteros — no se redondea en silencio. Deben ser estrictamente mayores a
0 (`@Positive`). Esto es una **decisión técnica** para esta unidad, no
una exigencia textual: T1 solo da ejemplos enteros, lo cual no implica
que los decimales estén prohibidos (ver `docs/fuentes.md`).

## Fecha y hora: zona explícita

`fechaCargaPrevista` se recibe como `LocalDateTime` en formato ISO-8601
local (`AAAA-MM-DDTHH:mm:ss`, sin offset). **Se asume que ese valor ya
está en hora local America/Argentina/Cordoba**; el backend no convierte
ninguna zona. `fechaRecepcion` (de la orden) y `fechaHora` (del
historial de creación) se generan con
`LocalDateTime.now(ZoneId.of("America/Argentina/Cordoba"))`, **la misma
instancia de fecha/hora para ambas filas** — no se calculan por
separado. Esta convención de zona es una decisión técnica explícita,
para no depender de la zona por defecto de la máquina donde corra el
proceso.

## Sincronización de referencias (`Camion`, `Chofer`, `Cliente`, `Producto`)

Búsqueda por `externalCode`; si no existe se crea, si existe se
actualiza. Semántica exacta:
- Campos **obligatorios** (patente, capacidadKg, nombre, apellido,
  razónSocial, nombre de producto): se sobreescriben siempre con el
  valor recibido, exista o no la entidad.
- Campos **opcionales** (descripción, documento, contacto): si el JSON
  trae el campo en `null` u omitido, se **conserva** el valor ya
  guardado (no se borra). Al crear la entidad por primera vez, un
  opcional omitido queda `null`. No hay borrado implícito de un dato ya
  guardado por omitirlo en una recepción posterior.
- No hay normalización de `externalCode` (no se recorta espacios, no se
  cambia mayúsculas/minúsculas) — se guarda tal cual llega.

## `Camion`: aclaración posterior sobre "cisterna"

Ver `docs/decisiones.md`, sección F2, para el registro completo. En
resumen: una aclaración del profesor transmitida por el equipo,
**posterior** a la interpretación de T1 ya documentada en
`docs/fuentes.md` (que no se modifica), establece que "cisterna" = el
camión completo, una única unidad de carga. No hay entidad `Cisterna`
ni colección de compartimentos; `capacidadKg` es un campo directo de
`Camion`.

## Número de orden duplicado y concurrencia

1. Chequeo temprano (`existsByOrderNumber`) antes de sincronizar
   cualquier referencia: si ya existe, `409` inmediato, sin tocar
   camión/chofer/cliente/producto.
2. Restricción única en base (`uk_orden_order_number`, con nombre
   explícito) como protección real ante una colisión concurrente: si
   dos solicitudes pasan el chequeo temprano casi al mismo tiempo, la
   base solo acepta una; la otra recibe `DataIntegrityViolationException`,
   que se traduce a `409` **solo si el nombre de esa restricción
   específica aparece en la causa** — cualquier otra violación de
   integridad se trata como error genérico (`500`), no como "orden
   duplicada".
3. Ambos caminos están dentro de la misma transacción
   (`@Transactional(rollbackFor = Exception.class)`), necesaria porque
   `FoundException`/`BusinessException` son excepciones *checked* y
   Spring no las revierte por defecto. El rollback se verificó por
   separado para cada camino (no se generalizó un resultado al otro):
   para `FoundException`, con la prueba de concurrencia real descripta
   arriba; para `BusinessException`, con un caso distinto (violación de
   integridad no relacionada a `orderNumber`, ver `docs/decisiones.md`,
   sección F2) que confirmó que también se revierten las referencias ya
   guardadas en la misma transacción antes de la falla.

## Actor del historial

Se obtiene de `BaseRestController.getUserLogged()` (contexto de
seguridad ya autenticado), nunca del JSON de entrada. Mantener la
recepción autenticada con JWT y tomar el actor del contexto es una
**decisión técnica propuesta por el equipo**, no una exigencia de la
cátedra (T1/T2 no dicen si este paso requiere autenticación).

## Auditoría de la creación (`HistorialEstado`)

Requisito **confirmado** (TP21) — no es una decisión del equipo sin
respaldo. Cita completa, ubicación y aclaración sobre qué sí es decisión
del equipo (auditar específicamente la creación) en
`docs/decisiones.md`, sección F2.

## Documentación OpenAPI y colección Postman (TP23, TP24)

**OpenAPI:** el endpoint está anotado con `@Operation` (summary +
description) y `@ApiResponses` con ejemplos reales para `201`, `400`
(dos ejemplos: campo obligatorio ausente y JSON malformado), `401` (sin
cuerpo), `409` y `500`; el request body tiene un ejemplo completo con
los mismos valores ficticios que este documento. Verificado contra
`GET /v3/api-docs` con la app corriendo: las cinco respuestas aparecen
con el esquema real (`StandartResponse` para errores,
`OrdenRecepcionResponse` para el éxito), y `components.schemas` ya
mostraba `required`/`maxLength` correctos para `OrdenRecepcionRequest` y
sus 4 DTO anidados **antes** de agregar cualquier anotación nueva —
springdoc los deriva directamente de `@NotNull`/`@NotBlank`/`@Size`, así
que no se duplicó esa información a mano. El esquema de seguridad Bearer
JWT (`config/OpenApiConfig.java`, base docente) ya se aplica a nivel
global (`security` en la raíz del documento OpenAPI), verificado.

**Postman:** colección versionable en
`postman/iw3-f2-recepcion.postman_collection.json`, con 6 requests en
orden: login, recepción válida, segunda orden reutilizando las mismas
referencias (`externalCode`) con un `orderNumber` distinto, duplicado de
`orderNumber`, entrada inválida (campo obligatorio ausente) y solicitud
sin token. Cada request tiene aserciones de status code y, donde aplica,
de contenido del cuerpo (mensaje, `code`, `devInfo`). **Límite
deliberado:** las aserciones verifican solo lo que la respuesta HTTP
expone; no afirman que un dato se haya sincronizado en la base, porque
esta unidad no expone un endpoint de consulta para
Camion/Chofer/Cliente/Producto — esa verificación se hizo aparte,
directamente contra la base, durante la implementación (ver
`docs/decisiones.md`, sección F2).

Variables de colección: `baseUrl`, `testUsername`, `testPassword`
(ninguna con valor real por defecto), `jwtToken` (se completa en tiempo
de ejecución con la respuesta de login, nunca se imprime ni se guarda en
un archivo versionado). Los datos de prueba (`orderNumber1`,
`orderNumber2`, `orderNumberInvalid`, `extSuffix`) se generan en un
script de pre-request a partir de la hora actual, para que correr la
colección varias veces no choque con órdenes/referencias de ejecuciones
anteriores.

`postman/iw3-local.postman_environment.example.json` es un ejemplo sin
valores reales (mismo criterio que `.env.example`); el archivo real
(`postman/iw3-local.postman_environment.json`, si se crea) queda
excluido en `.gitignore`. La ejecución verificada en esta unidad pasó las
credenciales únicamente por `--env-var` en el comando de `postman
collection run`, sin login en una cuenta de Postman ni uso de `--output`
(que requiere esa cuenta): 6/6 requests, 12/12 aserciones, 0 fallos.
