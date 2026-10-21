# Decisiones del equipo

## Saneamiento de credenciales antes del primer respaldo remoto

- El historial heredado de la base docente (`upstream`, `magm3333/iw3_2026`,
  commit `b7e705de737e5b1039719807bb413ec89279e7d1`) contenia una contrasena
  de base de datos de produccion, una contrasena de base de datos de
  desarrollo, una contrasena SMTP por defecto y una clave de firma JWT
  hardcodeada, todas embebidas en archivos versionados.
- Decision del equipo: no reutilizar esas credenciales en ningun despliegue
  propio y no republicarlas en nuestro `origin` publico, aunque ya fueran
  publicas en el repositorio del profesor.
- Se reescribio el historial en una copia separada del repositorio
  (`iw3-backend-sanitized`), reemplazando esos cuatro valores en todos los
  commits de las ramas `main`, `develop` y `feature/setup-inicial`, con
  `git-filter-repo --replace-text` corrido desde un script Python extraido
  manualmente del paquete (instalado en un directorio de usuario, sin sudo
  y sin modificar el Python del sistema, porque `python3-venv` no estaba
  disponible). Autoria y mensajes de commit originales se conservaron.
- El clon local original (`iw3-backend`, con el remoto `upstream` intacto)
  no se modifico y queda como respaldo con el historial sin tocar.
- Los cuatro valores se externalizaron via variables de entorno
  (`DB_USERNAME`, `DB_PASSWORD`, `SMTP_USER`, `SMTP_PASS`, `JWT_SECRET`),
  documentadas con valores ficticios en `.env.example`. Ninguna tiene valor
  por defecto en el codigo: si falta alguna, el arranque de Spring falla
  (placeholder no resuelto) o, en el caso de `JWT_SECRET`, una validacion
  explicita en `SecurityConfiguration` rechaza un valor vacio.

## Importacion de futuras actualizaciones del repositorio del profesor

**Donde se puede consultar el repositorio del profesor hoy:** solo desde
`iw3-backend` (el clon original, intacto, que tiene configurado el
remoto `upstream` apuntando a `magm3333/iw3_2026`). La copia de trabajo
activa, `iw3-backend-sanitized`, **no tiene `upstream` configurado**
— solo tiene `origin` (el repositorio propio del equipo). No se debe
presentar `git fetch upstream` como un comando disponible parado en
`iw3-backend-sanitized` sin aclarar que primero habria que agregar ese
remoto ahi (y evaluar si corresponde hacerlo, dado el punto siguiente).

**Por qué no se integra directamente el historial original:** el
saneamiento cambia los hashes de los commits afectados y sus descendientes;
no implica necesariamente que todos los commits anteriores hayan cambiado.
Un merge o un rebase sobre el historial original podría volver a hacer
alcanzables los commits con valores eliminados. Por decisión del equipo,
no se hace merge, pull ni rebase desde la base original hacia las ramas
saneadas. Se importan únicamente cambios revisados, sin sus ancestros.

**Como identificar que hay novedades:** no sirve `git log upstream/main
^main` comparando contra el `main` de la copia saneada (sus hashes ya no
son los mismos que los de `upstream`, esa comparacion de ancestria no da
un resultado correcto). En cambio, se usa como referencia fija el ultimo
commit ORIGINAL (sin reescribir) que se importo: `b7e705de737e5b1039719807bb413ec89279e7d1`.
Parado en `iw3-backend` (donde ese hash y `upstream/main` si comparten
ancestria real), el comando correcto es:

```bash
git fetch upstream
git log b7e705de737e5b1039719807bb413ec89279e7d1..upstream/main
```

Procedimiento acordado para cada commit nuevo que aparezca ahi:

1. Revisar el diff de ese commit (`git show <commit>`) desde
   `iw3-backend`, buscando credenciales o configuracion insegura con el
   mismo criterio usado en esta revision (grep de `password`, `secret`,
   `smtp`, defaults embebidos, etc.), sin importar si a primera vista
   parece "no sensible".
2. Llevar el cambio a `iw3-backend-sanitized` sin un commit automatico:
   generar el diff/patch desde `iw3-backend` (por ejemplo `git show
   <commit> > /tmp/cambio.patch`) y aplicarlo con `git apply` sobre el
   arbol de trabajo de `iw3-backend-sanitized`, o reproducir el cambio a
   mano si el patch no aplica limpio por las diferencias ya existentes.
3. Mientras el cambio esta en el arbol de trabajo y todavia sin
   commitear, sanear lo que haga falta (por ejemplo, volver a aplicar el
   patron de variables de entorno si el commit del profesor reintrodujo
   un valor por defecto).
4. Recien ahi crear el commit propio en `iw3-backend-sanitized`, con un
   mensaje que documente de que commit original viene el cambio y que
   correccion se le aplico.
5. Actualizar en este documento cual es el "ultimo commit original
   importado" despues de cada ronda, para que la referencia de la
   comparacion siga siendo correcta.
6. Nunca hacer `push --force` sobre una rama ya publicada para "arreglar"
   un secreto que se coló: hay que evitarlo antes del commit, no despues
   del push.

## F0 — hallazgos que corrigen o confirman supuestos previos

- **Lectura completa de T1/T2 (NotebookLM, cuaderno "Ingenieria Web III
  BE"):** ver el detalle, con texto literal/interpretacion/duda
  separados, en `docs/fuentes.md`. Esa lectura resuelve a CONFIRMADO lo
  que antes era SUPUESTO A VALIDAR sobre el momento de generacion de la
  clave de activacion (transicion 1->2, junto con la tara). Sobre
  densidad, la lectura aporta la cita literal pero **no** resuelve la
  inconsistencia entre el rango (0-1) y la unidad (kg/m3); eso sigue como
  DUDA PENDIENTE, no como CONFIRMADO — no se debe asumir que "anecdotico"
  implica "sin validacion de rango" (ver `docs/fuentes.md`).
- **Perfiles Maven vs. perfiles Spring (verificado con `help:active-profiles`
  y `help:evaluate`, no solo leido del POM):** los 4 perfiles de
  ejecucion (`mysqldev`, `mysqlprod`, `cli1`, `cli2`) solo fijan la
  propiedad Maven `spring.profiles.active`. **Corrección de F1:** esa
  propiedad no se propaga automáticamente al runtime en este POM; el
  arranque probado necesita `SPRING_PROFILES_ACTIVE=mysqldev`. No
  tienen relación directa con el empaquetado. El perfil `war-build` tiene
  `activeByDefault=true` y se activa tambien con `-Dbuild=war`, pero esa
  activacion por defecto se cancela en cuanto se activa *cualquier otro*
  perfil del mismo POM por `-P` — incluidos los 4 de ejecucion, que no
  tienen nada que ver con el empaquetado. Confirmado de forma empirica:
  `./mvnw help:active-profiles -Pmysqldev` (sin `-Dbuild`) falla con
  `Unknown packaging: ${packaging.type}`, y lo mismo le pasa a
  `./mvnw spring-boot:run -Pmysqldev` sin `-Dbuild`, porque Maven resuelve
  el `<packaging>` del proyecto antes de ejecutar cualquier goal. Con
  `-Dbuild=war -Pmysqldev` quedan activos ambos perfiles
  (`help:active-profiles` los lista a los dos) y `packaging.type`
  resuelve a `war` (`help:evaluate`). Regla practica para F1: siempre
  combinar un perfil de ejecucion con `-Dbuild=war` o `-Dbuild=jar`
  explicito.
- **Herramientas verificadas en F0 (no solo mencionadas):** Postman CLI
  real (`1.75.0`, `~/.npm-global/bin/postman`), NotebookLM MCP conectado y
  usado, skill `graphify` disponible pero no ejecutada todavia (no existe
  `graphify-out/`), Chrome DevTools MCP configurado pero con conexion
  fallida (`CONNECTION_CLOSED`), no se intento usar.
- **Precision sobre que se compilo:** la compilacion y la prueba unitaria
  de JWT verificadas hasta ahora corresponden a la copia ya saneada y con
  la externalizacion de credenciales aplicada. No se compilo por separado
  el commit original del profesor sin modificar.
- **Precision sobre la prueba de JWT:** lo que se ejecuto y paso fue una
  prueba unitaria aislada (firma/verificacion con claves temporales, y
  `validateJwtSecret()` invocado por reflexion con valores nulo/vacio/en
  blanco). Eso es distinto de un arranque real de la aplicacion Spring
  con la variable de entorno `JWT_SECRET` realmente ausente del proceso:
  esa prueba de arranque real **no se ejecuto**. La afirmacion de que
  Spring falla al no poder resolver el placeholder si falta la variable
  es un razonamiento sobre el comportamiento documentado de Spring, no
  una prueba ejecutada y observada en esta sesion.


## F1 — entorno local y arranque efectivo (20/10/2026)

- Se usa `mysql:8.0.43`. La lectura directa de la sección 5.1 de Apuntes
  Clase 7 reportada por el equipo identifica esa imagen en el despliegue de
  la cátedra. Reutilizarla para desarrollo local es una decisión del equipo,
  no una exigencia de Clase 3. No se valida como fuente el comando detallado
  que una respuesta de consulta atribuyó a Clase 3 sin respaldo textual.
- MySQL se publica en `127.0.0.1:3306`; Spring corre en el host y utiliza
  `jdbc:mysql://localhost:3306/iw3_2026` bajo `mysqldev`. La base sin perfil
  conserva `33306`; por ello la guía exige activar explícitamente `mysqldev`.
  No se modifica `mysqlprod`, pensado para el servicio `mysql:3306`.
- El volumen `iw3-local_mysql-data` conserva los datos al recrear el servicio.
  No se usa `down -v`. Cambiar el archivo de variables no rota las contraseñas
  de una base ya inicializada: credenciales y contenido del volumen deben
  mantenerse coherentes.
- `.env.mysql` contiene `DB_USERNAME`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`;
  `.env.jwt` contiene `JWT_SECRET`. Son archivos locales ignorados, con
  permisos 600 según la verificación del equipo. Compose lee el primero
  mediante `--env-file`; Spring recibe las variables exportadas en su proceso.
  El root de MySQL es para infraestructura; la aplicación usa `iw3_app`.
- Tres intentos de arranque usaron la URL base `33306` porque no había perfil
  Spring activo. La solución fue exportar `SPRING_PROFILES_ACTIVE=mysqldev`.
  No se cambió el POM ni se agregaron dependencias. La comprobación de una
  propiedad con `help:evaluate` no equivalía a verificar un perfil runtime.

## F1 — correo y prueba de autenticación

- `EmailBusiness` recibe el remitente desde `mail.from`. `MAIL_FROM` toma por
  defecto `SMTP_USER`; no se impone una cuenta real ni un proveedor institucional.
  `SMTP_HOST` y `SMTP_PORT` permiten reemplazar los defaults heredados.
- `NOTIFY_TEST_RECIPIENT` no tiene destinatario real por defecto. Su valor vacío
  **no es un interruptor de envío**: el código no omite el envío por estar vacío.
  `EXPIRED_NOTIFICATION_ENABLED=false` evita que `Scheduler.expired()` consulte
  productos y envíe correos. No deshabilita `EmailBusiness` globalmente.
- Las pruebas locales usaron valores SMTP ficticios y no enviaron correo,
  según el reporte de ejecución. Probar SMTP real y el remitente autorizado
  queda fuera del cierre técnico de esta infraestructura local.
- Se utilizó un usuario local `qa_f1_dfab890c` y el rol `ROLE_QA_TEST` para
  probar autenticación. Al no existir alta de usuarios en la API inspeccionada
  por el equipo, se generó BCrypt con la utilidad heredada y se insertaron
  usuario, rol y relación directamente en la BD. Es preparación de datos de
  prueba, no implementación del alta ni de los roles funcionales del integrador.
- Se verificaron login y acceso protegido correctos, rechazo de contraseña
  incorrecta, de JWT alterado y de acceso sin token. No se probaron expiración,
  autorización por roles del dominio ni alta/recuperación de usuarios.
- Graphify se ejecutó después de F0; sus resultados permanecen locales.
  La exclusión se publicó en `62a9d53`. El grafo no reemplaza estas pruebas.

La guía operativa y los límites de la evidencia están en
`docs/desarrollo-local.md` y `docs/checkpoints/2026-10-20-04.md`.

## F2 — recepción de orden (unidad vertical única)

Detalle completo del contrato en `docs/contrato-f2-recepcion.md`. Aquí
solo las decisiones y su origen.

- **Aclaración posterior sobre "cisterna" (transmitida por el equipo,
  no es texto de T1):** "cisterna" se toma como el camión completo, una
  única unidad de carga; no hay compartimentos ni una regla de cantidad
  de cisternas. No se modifica la cita literal ya registrada en
  `docs/fuentes.md` sobre "nombre y número" por cisterna — esa cita
  sigue ahí tal cual, esta es una interpretación posterior y distinta.
  Consecuencia: no existe entidad `Cisterna`; `Camion` tiene
  `capacidadKg` como campo directo, sin colección ni suma. Se revisó si
  `nombre`/`numero` seguían teniendo sentido como campos del camión:
  no se incorporaron, porque `patente` ya identifica al camión y no hay
  fuente que pida un campo adicional.
- **`DECIMAL(15,3)` para `presetKg`/`capacidadKg`:** decisión técnica
  para esta unidad. T1 solo da ejemplos enteros (10.000 kg), lo cual no
  prueba que los decimales estén prohibidos. Se valida con
  `@Digits(integer=12, fraction=3)` para rechazar con 400 un valor que
  no entra en esa precisión, en vez de redondear en silencio.
- **Zona horaria explícita `America/Argentina/Cordoba`:** decisión
  técnica, para no depender de la zona por defecto del proceso. Se
  genera una sola instancia de fecha/hora para `Orden.fechaRecepcion` y
  `HistorialEstado.fechaHora` de la transición de creación.
  `fechaCargaPrevista` se asume recibida ya en esa zona, sin offset.
- **Duplicado de `orderNumber` (409):** chequeo temprano
  (`existsByOrderNumber`) más restricción única en base con nombre
  explícito (`uk_orden_order_number`), para distinguir una colisión de
  `orderNumber` de cualquier otra violación de integridad — no se
  traduce cualquier error de integridad a "orden duplicada". Verificado
  con una prueba de concurrencia real (dos solicitudes simultáneas con
  el mismo `orderNumber`): una obtuvo 201, la otra 409, y las
  referencias sincronizadas por la solicitud que perdió la carrera
  quedaron revertidas por completo.
- **Duplicado con referencias cambiadas, evidencia por valores (no solo
  por conteo):** se repitió el caso anterior en forma secuencial (no
  concurrente), enviando un `orderNumber` ya existente junto con valores
  de camión/cliente deliberadamente distintos a los ya guardados. Se
  confirmó `409` y, comparando los valores reales en la base antes y
  después (no solo la cantidad de filas), que el camión y el cliente
  existentes **no cambiaron ningún campo** y que `historial_estados` no
  ganó una fila nueva. Contar filas nuevas no alcanzaba para probar esto:
  una fila podía seguir existiendo en la misma cantidad y, aun así, haber
  sido sobreescrita con los valores nuevos; se verificó que no fue así.
- **Rollback de las excepciones checked usadas por `recibirOrden`
  (`FoundException`, `BusinessException`):** `@Transactional(rollbackFor
  = Exception.class)` es necesario porque ambas son excepciones *checked*
  (extienden `Exception` directamente) y Spring no las revierte por
  defecto (el rollback automático sin esa anotación solo cubre
  `RuntimeException`/`Error`). Se probaron ambos caminos por separado,
  sin generalizar el resultado de uno al otro:
  - **`FoundException`** (colisión de `orderNumber` detectada por la
    restricción única de la base, no por el chequeo previo): probado con
    la concurrencia real descripta arriba. Las referencias sincronizadas
    por la solicitud que perdió la carrera quedaron revertidas.
  - **`BusinessException`** (cualquier otra violación de integridad):
    probado aparte, con un caso distinto y deliberado — un
    `producto.nombre` de 200 caracteres contra una columna `VARCHAR(150)`,
    en una orden con `orderNumber` nuevo y camión/chofer/cliente también
    nuevos. `sincronizarCamion`/`sincronizarChofer`/`sincronizarCliente`
    se ejecutan (y guardan) **antes** que `sincronizarProducto()` dentro
    del mismo método `@Transactional`; el guardado de `Producto` es el que
    falla (`DataIntegrityViolationException` por truncamiento de datos,
    no por la restricción `uk_orden_order_number`), cae en el `catch
    (Exception e)` genérico y se envuelve en `BusinessException` → `500`.
    Se comparó el conteo de las seis tablas relevantes
    (`camiones`, `choferes`, `clientes`, `productos_orden`, `ordenes`,
    `historial_estados`) antes y después del intento, y se buscó
    explícitamente el `externalCode` ficticio de camión usado en la
    prueba (`CAM-F2-BE-001`): ningún conteo cambió y esa fila no existe.
    Es decir, el camión/chofer/cliente que sí llegaron a guardarse dentro
    de la transacción también se revirtieron cuando la operación completa
    falló más adelante por `producto`. Antes de este caso puntual, el
    rollback de `BusinessException` no estaba probado de forma empírica
    en esta unidad — solo se infería del mismo mecanismo de
    `rollbackFor`, sin una prueba que lo ejercitara; ahora sí está
    verificado para este camino específico (truncamiento de datos vía
    `sincronizarProducto`). No se probaron otras causas posibles de
    `BusinessException` (por ejemplo, una falla de conexión a la base a
    mitad de la transacción); para esas, el mecanismo de `rollbackFor`
    sigue aplicando por el mismo razonamiento general, pero sin prueba
    propia ejecutada en esta sesión.
  - **Nota post-revisión (después de agregar `@Size` al DTO, ver más
    abajo):** el payload exacto usado para esta prueba
    (`producto.nombre` de 200 caracteres) ya no llega a
    `OrdenBusiness`/la base — se reintentó tal cual y ahora lo rechaza
    la validación `@Size(max=150)` con `400`, antes de cualquier intento
    de guardado. La evidencia de rollback de `BusinessException` arriba
    sigue siendo válida como lo que probó en su momento (un
    truncamiento real a nivel de base, con las filas ya guardadas en la
    transacción revertidas), pero queda como evidencia histórica: ese
    camino específico (truncamiento de `producto.nombre`) ya no es
    alcanzable por esa vía. No se debilitó ninguna validación para
    volver a reproducirlo, ni se agregó ningún mecanismo nuevo capaz de
    fallar en producción solo para repetir la prueba; el mecanismo de
    `rollbackFor = Exception.class` sigue intacto y aplica a cualquier
    otra causa de `BusinessException` que sí pueda seguir ocurriendo
    (por ejemplo, otra violación de integridad no cubierta por una
    validación de Bean Validation).
- **Actor del historial desde el contexto de seguridad:** se reutiliza
  `BaseRestController.getUserLogged()` ya existente; el JSON de entrada
  nunca lo provee. Mantener la recepción autenticada con JWT para esta
  unidad es una decisión técnica propuesta, no una exigencia docente.
- **Auditar la creación con `HistorialEstado`:** el requisito de
  auditar *cada cambio de estado* (fecha/hora + usuario o proceso +
  observación si aplica) es **CONFIRMADO**, no una decisión del equipo.
  Cita y ubicación verificable: fuente "Parte 1 - Lectura de la
  consigna del trabajo integrador.txt" (cuaderno "Ingeniería Web III
  BE", fuente `59f2c5bf-3d78-4e71-a60a-153dbecb9c27`), tramo final del
  documento (aprox. carácter 19.700-20.300 de 21.713): *"cada cambio de
  estado debe quedar registrado en la base de datos, incluyendo fecha y
  hora del cambio, usuario o proceso que realizó la transacción,
  observaciones se aplica [sic, probable ruido de ASR por "observación
  si aplica"], se recomienda implementar un log de auditoría de estado
  para la trazabilidad completa del ciclo de vida de la orden."* Lo que
  **sí** es decisión del equipo: auditar específicamente la transición
  de creación (ninguno→1) en esta unidad, porque es el primer cambio de
  estado del ciclo de vida — no una interpretación distinta del
  requisito en sí. Esto corrige a `docs/requisitos.md` (TP21), que
  tenía esta cita como DATO FALTANTE por no haberse leído el tramo final
  completo de T1 en la sesión anterior.
- **Hallazgo en `/error` y corrección SIN tocar `SecurityConfiguration`
  (revisión posterior a la primera versión de esta unidad):** al validar
  con `@Valid` una entrada inválida, Spring Boot resuelve la excepción de
  validación internamente (confirmado en el log,
  `DefaultHandlerExceptionResolver`), pero el forward interno a `/error`
  volvía a pasar por la cadena de filtros de seguridad, y `/error` no
  estaba en la lista de rutas públicas — el cliente terminaba recibiendo
  401 en vez de 400. **Se descartó** la primera corrección que se había
  probado (`.requestMatchers("/error").permitAll()` en
  `SecurityConfiguration`, igual patrón que `/demo/**`): quedó revertida
  por completo (`git diff` de ese archivo vacío) porque ampliaba una ruta
  pública de toda la aplicación por un problema de un solo controlador, y
  porque exponía sin autenticación el cuerpo de error por defecto de
  Spring Boot con stacktrace completo. La corrección final agrega dos
  métodos `@ExceptionHandler` **locales** a `OrdenRestController`
  (`MethodArgumentNotValidException`, `HttpMessageNotReadableException`):
  al resolver la excepción ahí mismo, nunca se llega al forward a
  `/error`, y no hace falta tocar `SecurityConfiguration`. Verificado:
  `GET /error` sin token sigue devolviendo `401` (la ruta sigue sin ser
  pública), y las dos entradas inválidas ahora devuelven `400` con cuerpo
  propio (`StandartResponse`).
- **Hallazgo adicional sobre fuga de detalle interno, descubierto al
  ejercitar el caso de `BusinessException` del punto anterior (no estaba
  buscado, surgió de esa prueba):** los catch de `FoundException` (409) y
  `BusinessException` (500) en `OrdenRestController.recibir()` le pasaban
  la excepción real (`e`) a `StandartResponse`. Con
  `dev.info.enabled=true` (perfil `mysqldev`), `getDevInfo()` devuelve
  `ExceptionUtils.getStackTrace(ex)` — la primera ejecución real del caso
  `BusinessException` lo mostró de forma concreta: el cuerpo de la
  respuesta `500` incluía el stacktrace completo, el mensaje SQL
  (`Data truncation: Data too long for column 'nombre'...`) y el `insert`
  literal. Esto no era un cambio nuevo de esta revisión: ya estaba así
  desde la implementación original de la unidad, documentado entonces
  como "aceptable para esta etapa local, a revisar antes de cualquier
  despliegue" — la revisión actual decidió corregirlo ya, porque la
  instrucción de no devolver detalle de excepciones al cliente no estaba
  acotada a 400/401. Corrección: ambos catch ahora pasan `null` como
  `ex` (igual patrón que los dos `@ExceptionHandler`); el 409 conserva el
  mensaje propio de `FoundException` (no expone causa interna); el 500
  usa un mensaje genérico fijo en vez de `e.getMessage()`, porque ese
  mensaje puede terminar reflejando el `toString()` de la causa interna
  según cuál de los tres constructores `@Builder` de `BusinessException`
  se haya invocado (la clase tiene tres constructores anotados con
  `@Builder`, herencia de la base docente, no de esta unidad). Se
  probaron cinco casos en total, pero son de dos tipos distintos: cuatro
  pasan por `OrdenRestController` y usan `StandartResponse` (400 por
  campo ausente, 400 por JSON malformado, 409 por duplicado, 500 por el
  caso de truncamiento) — los cuatro devuelven `"devInfo":"No stack
  trace"` y ningún mensaje con detalle interno; el quinto (401 sin token)
  no pasa por el controlador ni por `StandartResponse` — lo responde
  Spring Security con cuerpo vacío, sin `devInfo` ni ningún otro campo,
  porque ahí no hay una excepción de negocio que suprimir.
- **`@Size` en el DTO, alineado con el largo real de cada columna
  (hallazgo de la revisión del zip):** el DTO de entrada
  (`OrdenRecepcionRequest`) validaba presencia (`@NotBlank`/`@NotNull`)
  pero no longitud máxima; un texto más largo que su columna llegaba
  hasta el guardado y fallaba ahí con `DataIntegrityViolationException`
  (el mismo mecanismo que se usó a propósito para la prueba de rollback
  de `BusinessException`, ver más abajo). Se agregó `@Size(max=...)` a
  cada campo de texto, con el máximo igual a la columna real (no un
  valor arbitrario): `externalCode` 100 en las cuatro referencias;
  `camion.patente` 20, `camion.descripcion` 255; `chofer.nombre`/
  `chofer.apellido` 100, `chofer.documento` 50; `cliente.razonSocial`/
  `cliente.contacto` 150; `producto.nombre` 150, `producto.descripcion`
  255. Se mantuvo `@NotBlank` donde ya estaba. Verificado con tres casos
  reales contra la app corriendo: `producto.nombre` de exactamente 150
  caracteres (en el límite) con el resto válido → `201`; `producto.nombre`
  de 151 caracteres → `400` (`"producto.nombre: size must be between 0
  and 150"`); `chofer.documento` (opcional) de 51 caracteres (límite 50)
  → `400` (`"chofer.documento: size must be between 0 and 50"`). Para
  los dos casos rechazados se compararon los conteos de las seis tablas
  antes y después (sin cambios) y se buscó explícitamente el
  `externalCode` ficticio usado en cada intento (sin resultados) — no
  quedó nada persistido de ninguno de los dos.
- **Hallazgo de nomenclatura de columnas (no es un bug, es solo para
  consultas SQL manuales futuras):** Hibernate deriva
  `usuarioOProceso` → columna `usuariooproceso` (sin guion bajo, por
  dos mayúsculas consecutivas en el nombre del campo Java) en
  `historial_estados`. Se verificó con `DESCRIBE` antes de escribir
  cualquier consulta manual, igual que con `idUser` en F1.
- **Qué NO se implementó en esta unidad, deliberadamente:** CRUD o
  consulta para `Orden`/`Camion`/`Chofer`/`Cliente`/`Producto` (no
  pedido); pruebas automatizadas con JUnit (se verificó con HTTP real
  contra la app corriendo localmente, mismo método que F1, no se
  escribió una suite nueva); documentación OpenAPI detallada por
  anotación (`@ApiResponse` por caso) más allá del `@Tag` básico — el
  contrato queda documentado en `docs/contrato-f2-recepcion.md` en vez
  de en anotaciones exhaustivas, para esta unidad.
