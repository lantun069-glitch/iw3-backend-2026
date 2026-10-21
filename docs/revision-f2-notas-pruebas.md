# Revisión acotada de F2 — procedimiento, resultados y límites

Nota de trabajo para la revisión pedida antes de publicar la unidad de
F2 (recepción de orden). No reemplaza `docs/decisiones.md` ni
`docs/contrato-f2-recepcion.md` — ahí está el detalle permanente; acá
solo el procedimiento de esta ronda, para que se pueda reproducir. Todos
los valores de ejemplo son ficticios. No reemplaza ni repite las pruebas
de F1. No se hizo commit/push, no se actualizó Graphify y F2 no se
declara cerrada.

Entorno: app corriendo localmente con `./mvnw -Dbuild=war -Pmysqldev
spring-boot:run`, `SPRING_PROFILES_ACTIVE=mysqldev`, MySQL local
(`compose.local.yaml`). Token JWT obtenido previamente con el usuario de
prueba de F1 (`qa_f1_dfab890c`) y reutilizado mientras no expiró.

## Punto 1 — exposición de detalles internos en respuestas de error

**Hallazgo de partida (ya conocido de la implementación original):**
una entrada inválida (`@Valid` fallido) devolvía `401` en vez de `400`,
porque el forward interno de Spring a `/error` volvía a pasar por la
cadena de seguridad y `/error` no era pública.

**Corrección aplicada:** se revirtió por completo el cambio anterior en
`SecurityConfiguration.java` (que agregaba `/error` como ruta pública) y
se agregaron dos `@ExceptionHandler` LOCALES a `OrdenRestController`
(para `MethodArgumentNotValidException` y `HttpMessageNotReadableException`),
que responden antes de que exista cualquier forward a `/error`.

**Hallazgo adicional, encontrado al ejercitar el punto 3 (no buscado a
propósito):** el catch de `BusinessException` (500) devolvía el
stacktrace completo en `devInfo`, porque el controlador le pasaba la
excepción real a `StandartResponse` y `dev.info.enabled=true` en este
perfil. El mismo patrón estaba en el catch de `FoundException` (409),
aunque ese caso no llegó a mostrar un mensaje con detalle interno en las
pruebas hechas. Corrección: ambos catch ahora pasan `null` como
excepción a `StandartResponse`.

**Comandos y resultados (valores ficticios):**

```bash
# TOKEN es una credencial (firma valida con JWT_SECRET real), aunque el
# usuario detras sea de prueba (qa_f1_dfab890c, rol ROLE_QA_TEST). Se lee
# de un archivo local fuera del repo (/tmp/.ordtoken); no se versiona ni
# se imprime su valor en ningun documento.
TOKEN=$(cat /tmp/.ordtoken)

# 1) 400 - campo obligatorio ausente (falta camion.patente)
curl -s -w "\nHTTP:%{http_code}\n" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -X POST http://localhost:8080/api/v1/orders \
  -d '{"orderNumber":900100,"fechaCargaPrevista":"2026-11-05T09:00:00","presetKg":1000,
       "camion":{"externalCode":"X","capacidadKg":1000},
       "chofer":{"externalCode":"X","nombre":"A","apellido":"B"},
       "cliente":{"externalCode":"X","razonSocial":"X"},
       "producto":{"externalCode":"X","nombre":"X"}}'
# -> 400 {"message":"camion.patente: must not be blank","code":400,"devInfo":"No stack trace"}

# 2) 400 - JSON malformado
curl -s -w "\nHTTP:%{http_code}\n" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -X POST http://localhost:8080/api/v1/orders \
  -d '{ esto no es json valido'
# -> 400 {"message":"JSON de entrada invalido o malformado","code":400,"devInfo":"No stack trace"}

# 3) 401 - sin token
curl -s -w "\nHTTP:%{http_code}\n" -H "Content-Type: application/json" \
  -X POST http://localhost:8080/api/v1/orders -d '{}'
# -> 401, cuerpo vacio (estandar de Spring Security, sin cambios)

# 4) 409 - orderNumber duplicado
curl -s -w "\nHTTP:%{http_code}\n" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -X POST http://localhost:8080/api/v1/orders \
  -d '{"orderNumber":900001, ... camion/chofer/cliente/producto con datos ficticios ...}'
# -> 409 {"message":"Ya existe una orden con orderNumber=900001","code":409,"devInfo":"No stack trace"}

# 5) GET /error sin token, para confirmar que SecurityConfiguration no cambio
curl -s -o /dev/null -w "HTTP:%{http_code}\n" http://localhost:8080/error
# -> 401 (igual que antes de esta revision: /error sigue sin ser publica)
```

**Límite:** no se revisaron otros controladores existentes (por ejemplo
`ProductRestController`) para ver si tienen el mismo patrón de exposición
de `devInfo`; el alcance de esta revisión fue solo `OrdenRestController`.

## Punto 2 — duplicado con referencias cambiadas, evidencia por valores

**Objetivo:** probar que un `orderNumber` ya existente, enviado con
valores de referencias **distintos** a los ya guardados, devuelve 409 y
no modifica nada — comparando valores reales, no solo cantidad de filas.

**Procedimiento:**
1. Se tomó una "foto" de los valores reales en base para `camiones` (id=1,
   `externalCode=CAM-F2-001`) y `clientes` (id=1,
   `externalCode=CLI-F2-001`), más el conteo de `historial_estados` y de
   `ordenes` con `order_number=900001`.
2. Se envió un `POST /api/v1/orders` con `orderNumber: 900001` (ya
   existente) pero con `camion.patente`, `camion.descripcion`,
   `camion.capacidadKg`, `cliente.razonSocial` y el resto de los campos
   con valores deliberadamente distintos a los guardados (ej.
   `"patente":"CAMBIADA999"`, `"razonSocial":"Razon Social Cambiada Via
   Duplicado"`).
3. Se repitió la misma consulta de la "foto" inicial y se compararon los
   valores campo por campo, no solo los conteos.

**Resultado:**
- Respuesta: `409`, `{"message":"Ya existe una orden con
  orderNumber=900001", ...}`.
- Camión id=1: `patente`, `descripcion` y `capacidadKg` **idénticos** a
  la foto inicial (no se aplicó ninguno de los valores "cambiados" del
  intento rechazado).
- Cliente id=1: `razonSocial` y `contacto` **idénticos** a la foto
  inicial.
- `historial_estados`: mismo conteo que antes (sin fila nueva).
- `ordenes` con `order_number=900001`: sigue en 1 (sin duplicado).

**Límite:** se probó con camión y cliente; no se repitió el mismo chequeo
campo por campo para chofer y producto en esta ronda (sí se verificó para
ellos el caso de alta nueva, en la unidad original; no en este intento de
duplicado puntual).

## Punto 3 — rollback de las excepciones checked usadas por `recibirOrden`

**Objetivo:** no generalizar el resultado de la prueba de concurrencia
(que solo ejercita `FoundException`) al camino de `BusinessException`,
que no estaba probado de forma empírica. Probar ese camino aparte.

**Procedimiento:**
1. Foto de conteos antes: `camiones=2, choferes=2, clientes=2,
   productos_orden=2, ordenes=3, historial_estados=3` (valores de este
   entorno de prueba, no significan nada fuera de él).
2. `POST /api/v1/orders` con `orderNumber` nuevo (900099), camión/chofer/
   cliente también nuevos y válidos (`externalCode` ficticios
   `CAM-F2-BE-001`, `CHO-F2-BE-001`, `CLI-F2-BE-001`), pero
   `producto.nombre` con 200 caracteres (la columna es `VARCHAR(150)`).
   `sincronizarCamion`/`sincronizarChofer`/`sincronizarCliente` se
   ejecutan y persisten *antes* de `sincronizarProducto()` dentro del
   mismo método `@Transactional`.
3. Resultado esperado si el rollback funciona: el guardado de `Producto`
   falla con `DataIntegrityViolationException` (truncamiento, no la
   restricción `uk_orden_order_number`), cae en el `catch (Exception e)`
   genérico, se envuelve en `BusinessException` y responde `500`; y las
   filas de camión/chofer/cliente que sí llegaron a guardarse deben
   desaparecer al revertirse la transacción completa.

**Resultado real:**
- Respuesta: `500`, `{"message":"Error interno al procesar la
  solicitud","code":500,"devInfo":"No stack trace"}`.
- Conteos después del intento: **iguales** a los de antes (`camiones=2,
  choferes=2, clientes=2, productos_orden=2, ordenes=3,
  historial_estados=3`).
- Búsqueda explícita de `externalCode='CAM-F2-BE-001'` en `camiones`: sin
  resultados (no quedó la fila, pese a haberse guardado dentro de la
  transacción antes de que fallara el `producto`).

**Conclusión acotada:** el rollback de `@Transactional(rollbackFor =
Exception.class)` quedó verificado de forma empírica, por separado, para
los dos caminos de excepción checked usados por `recibirOrden`:
`FoundException` (vía la prueba de concurrencia ya documentada) y
`BusinessException` (vía este caso de truncamiento de datos). No se
probaron otras causas posibles de `BusinessException` (por ejemplo, una
caída de la conexión a mitad de transacción); para esas, sigue aplicando
el mismo razonamiento general sobre `rollbackFor`, pero sin una prueba
propia ejecutada en esta sesión — no se debe citar esto último como
"verificado".

## Punto 4 — verificación mínima tras agregar `@Size` al DTO

**Objetivo:** confirmar que los límites de longitud agregados al DTO se
aplican sin romper el caso válido, y que los casos rechazados no
persisten nada (campo obligatorio ya cubierto antes; acá el foco es
longitud, incluido un campo opcional).

**Procedimiento y resultados (valores ficticios, re-login necesario
porque el token anterior había expirado):**

```bash
# Compilacion (antes de reiniciar la app)
cd iw3 && ./mvnw -q -Dbuild=war -Pmysqldev compile
# -> EXIT=0

# Re-login (el token guardado de una ronda anterior habia expirado)
TOKEN=$(curl -s -X POST "http://localhost:8080/api/v1/login" \
  --data-urlencode "username=$TEST_USERNAME" --data-urlencode "password=$TEST_PASSWORD")

# A) producto.nombre de exactamente 150 caracteres (limite), resto valido
curl -s -w "\nHTTP:%{http_code}\n" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -X POST http://localhost:8080/api/v1/orders \
  -d '{"orderNumber":900150, ... "producto":{"nombre":"P"x150 ...}}'
# -> 201 {"id":5,"orderNumber":900150,"estado":1,"fechaRecepcion":"..."}

# B) producto.nombre de 151 caracteres
# -> 400 {"message":"producto.nombre: size must be between 0 and 150", ...}

# C) chofer.documento (opcional) de 51 caracteres (limite 50)
# -> 400 {"message":"chofer.documento: size must be between 0 and 50", ...}
```

**Resultado sobre persistencia:** los conteos de las seis tablas pasaron
de `camiones=2, choferes=2, clientes=2, productos_orden=2, ordenes=3,
historial_estados=3` a `3/3/3/3/4/4` — exactamente +1 en cada una,
correspondiente solo al caso A (201). Se buscó explícitamente cada
`externalCode` ficticio usado en B y C (`*-SZ-900151`, `*-SZ-900152`) y
el `order_number` de ambos: sin resultados en ningún caso — nada de los
intentos rechazados quedó persistido.

**Nota sobre el payload histórico de `BusinessException` (ver punto 3):**
se reintentó el mismo payload (`producto.nombre` de 200 caracteres) con
el DTO ya corregido: ahora devuelve `400` (`@Size`) y nunca llega a
`OrdenBusiness`. La prueba de rollback de `BusinessException` queda como
evidencia histórica de que el mecanismo funciona — no se debilitó
ninguna validación para forzarlo de nuevo por esa vía, ni se agregó un
mecanismo de fallo nuevo solo para repetirla.

## Qué no se repitió ni se tocó en esta revisión

- No se repitieron las pruebas de F1 (login, roles, email).
- No se declaró F2 cerrada.
- No se hizo commit, push ni actualización de Graphify.
- No se modificó `pom.xml` (se incluye en el zip solo para revisión de
  dependencias).
