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

## F3 — primera unidad: tara y activación (TP08/TP09)

Detalle completo del contrato en `docs/contrato-f3-tara-activacion.md`.
Aquí las decisiones y su origen. Contrato validado por el usuario antes
de implementar (ver turno anterior de esta sesión); lo que sigue es lo
efectivamente implementado y verificado.

- **Identificación de la orden y de la clave (resuelve el DATO FALTANTE
  de T1):** T1 no especifica cómo se identifica la orden en la llamada
  de tara/activación ni si hay riesgo de colisión de clave entre
  órdenes simultáneas en estado 2 (ver `docs/fuentes.md` y la relectura
  de esta sesión). DECISIÓN DEL EQUIPO: `orderNumber` en el path
  (reutiliza el identificador público ya establecido en F2), clave en
  el body (nunca en la URL, para no dejarla en logs de acceso). La
  clave se valida contra la orden ya identificada por `orderNumber`,
  nunca se busca por clave: esto resuelve el riesgo de colisión sin
  necesitar unicidad global de la clave entre órdenes concurrentes.
- **Códigos HTTP (DECISIÓN DEL EQUIPO, sin fuente de cátedra que los
  fije):** tara y activación exitosas, `200` (no `201`: ninguna de las
  dos crea un recurso nuevo). Clave incorrecta, `400` — mismo criterio
  que el resto de los `400` ya usados en `OrdenRestController`: valor
  sintácticamente válido, rechazado por una regla de negocio, no un
  problema de autenticación (se evita mezclar esa semántica con el
  `401` ya reservado para JWT). Clave ausente o fuera de
  `[10000,99999]`, `400` por Bean Validation (`@NotNull @Min(10000)
  @Max(99999)` en `ActivacionRequest`), antes de llegar al negocio.
  Orden inexistente, `404` (`NotFoundException`, ya existente en
  `business/execption`, reutilizada — hasta esta unidad no se usaba
  desde ningún controller). Estado incompatible, `409`
  (`InvalidStateException`, nueva). JWT ausente/inválido, `401`, sin
  tocar `SecurityConfiguration` (ya cubierto por `anyRequest()
  .authenticated()`, verificado en el código antes de implementar).
  Error no clasificado, `500` genérico sin detalle interno, mismo
  patrón que F2 (`ex=null`, mensaje fijo).
- **Activación idempotente (DECISIÓN DEL EQUIPO):** mientras la orden
  siga en estado 2, una clave correcta repetida devuelve el mismo
  preset y la misma `fechaActivacion` ya fijada, sin volver a
  escribirla. La clave se valida siempre, incluso después de una
  activación exitosa (no hay un atajo que la omita una vez activada).
  En estados posteriores a 2 (todavía no alcanzables por ningún
  endpoint implementado), la precondición de estado sigue rechazando
  con `409` antes de llegar a comparar la clave.
- **Concurrencia — bloqueo pesimista, sin `@Version` (DECISIÓN DEL
  EQUIPO):** `OrdenRepository.findByOrderNumberForUpdate` usa
  `@Lock(LockModeType.PESSIMISTIC_WRITE)` sobre una consulta JPQL por
  `orderNumber`, invocada dentro de la misma transacción
  (`@Transactional(rollbackFor = Exception.class)`) que hace la
  comprobación de estado y la escritura. El diseño busca serializar dos
  tara o dos activaciones concurrentes sobre la misma orden: la segunda
  debería esperar el lock de la primera y, al obtenerlo, ver el estado
  (o `fechaActivacion`) ya actualizado. Se lanzaron dos `curl` en
  paralelo (ver evidencia en `docs/estado-proyecto.md`, sección F3) y el
  resultado observado es **compatible con** ese diseño: dos taras
  simultáneas → una `200`, otra `409`; dos activaciones correctas
  simultáneas → ambas `200` con `fechaActivacion` idéntica. Esto no es
  una prueba directa de contención del lock: lanzar dos procesos `curl`
  en paralelo no garantiza por sí solo que ambas solicitudes llegaron a
  disputar el bloqueo (no se midió tiempo de espera ni se instrumentó el
  acceso concurrente a nivel de base); el resultado observado es
  consistente con la serialización prevista por el código, pero esta
  prueba por sí sola no la demuestra de forma concluyente. No se agregó
  `@Version`: la única protección de concurrencia de esta unidad es el
  bloqueo.
- **Generación de la clave:** `SecureRandom`, no
  `ThreadLocalRandom`/`Random` — decisión del equipo, sin que la fuente
  exija un generador criptográfico; se devuelve únicamente en la
  respuesta exitosa de la tara (nunca en la de activación, nunca en
  logs, nunca en `observacion` de `HistorialEstado`).
- **`fechaActivacion` no es "inicio real de carga" (DECISIÓN DEL
  EQUIPO, mismo criterio ya aplicado a la tara — no equiparar
  automáticamente un evento administrativo con el inicio real de carga,
  extendido acá a la activación):** es el instante en que la
  API acepta la clave. La relación con la primera telemetría válida
  (y si hace falta un campo separado `fechaInicioCarga`) se resuelve en
  la siguiente unidad de F3, contrastando la fuente, no en esta.
- **Auditoría — solo la transición 1→2 (DECISIÓN DEL EQUIPO):** la tara
  agrega una fila en `HistorialEstado` (`estadoNuevo=2`,
  `fechaHora=fechaTara`, `usuarioOProceso=actor` del contexto de
  seguridad), mismo patrón que la creación en F2. La activación **no**
  agrega otra fila ni modifica la existente, porque no cambia `estado`
  — T1 exige auditar "cada cambio de estado", y la activación no lo es
  en el diseño de esta unidad (ver el contrato, donde estado 2 abarca
  tanto "con tara" como "cargando"). No se creó ninguna tabla de
  auditoría nueva.
- **Excepciones de dominio fuera de `business/execption`:**
  `InvalidStateException` e `InvalidActivationKeyException` viven en
  `ordenes/business/exception` (paquete nuevo, específico de este
  dominio), para no modificar la taxonomía heredada de la cátedra
  (`NotFoundException`/`FoundException`/`BusinessException`). Sí se
  reutilizó `NotFoundException` tal cual (ya existía, no se había
  usado desde ningún controller hasta esta unidad).
- **Hallazgo durante la verificación, corregido en esta misma unidad
  (no estaba en la propuesta original):** la respuesta de una
  activación exitosa devolvía `fechaActivacion` con precisión de
  nanosegundos (el objeto `LocalDateTime` en memoria, antes de
  persistir), mientras que una activación repetida leía el valor ya
  truncado a microsegundos por la columna `datetime(6)` de MySQL —
  dos representaciones distintas del mismo instante por una diferencia
  de precisión, no un error de concurrencia ni de lógica de negocio.
  Se detectó comparando por igualdad de string las dos respuestas en
  una prueba HTTP real (`.../activacion` llamado dos veces con la
  misma clave sobre la misma orden): la primera vez devolvía
  `...502079077`, la segunda `...502079`. Corregido truncando con
  `LocalDateTime.now(ZONA_RECEPCION).truncatedTo(ChronoUnit.MICROS)`
  antes de fijar `fechaActivacion` por primera vez, en
  `OrdenBusiness.activar`. Verificado de nuevo tras el fix (DevTools
  recompiló y reinició la app automáticamente): ambas respuestas
  devuelven el mismo valor. No se aplicó el mismo truncado a
  `fechaTara` ni a `fechaRecepcion` (F2): no hacía falta para esta
  unidad porque ninguna de las dos se compara por igualdad entre dos
  respuestas distintas; queda como posible ajuste de consistencia para
  cuando sea relevante, no como bug pendiente de esta unidad.
- **Verificación funcional — alcance real, sin inflar la evidencia:**
  se ejecutó HTTP real contra la app levantada localmente con
  `mysqldev` y MySQL en Docker (el usuario levantó el contenedor con
  `sudo`; no se dispuso de `sudo` interactivo ni de acceso al socket de
  Docker en el entorno de ejecución de esta sesión de trabajo). **No se
  leyó la base directamente** (sin cliente `mysql`, sin `docker exec`,
  sin driver Python disponible): la ausencia de cambios en
  `ordenes`/`historial_estados` ante los rechazos se apoya en la
  estructura del código (la excepción de estado se lanza antes de
  cualquier `save()`, dentro de la misma transacción) y en evidencia
  HTTP indirecta — la orden usada solo para intentos rechazados
  (activación antes de tara, llamada sin token) aceptó su tara
  normalmente después, lo que no sería posible si hubiera quedado en
  un estado distinto de 1. No se forzó un caso real de `500` para esta
  unidad (mismo mecanismo genérico ya probado en F2, no se repitió).
- **Revisión de logs — alcance acotado, no una auditoría exhaustiva:**
  se buscaron las claves de activación generadas durante las pruebas de
  esta sesión en el log de stdout/stderr de la app local; no aparecen.
  Esto cubre solo ese archivo y solo esas claves, no todo destino de log
  posible ni toda clave posible generada en el futuro.
- **Incidente de proceso, registrado por transparencia:** durante la
  prueba de la carrera de dos taras concurrentes, la clave de
  activación de la orden usada para esa prueba quedó impresa sin
  enmascarar en la salida de un comando de la terminal durante esa
  prueba manual (no en ningún archivo de reporte, documento versionado
  ni mensaje al usuario). Se corrigió el enmascarado (patrón `jq` que
  oculta el campo) para el resto de las pruebas de la sesión.

## F3 — segunda unidad: telemetría, descarte, snapshot e histórico (TP10–TP13)

Detalle completo del contrato en `docs/contrato-f3-telemetria.md`. Aquí
las decisiones y su origen. Contrato validado por el usuario antes de
implementar.

- **Precondición de activación, además de estado 2 (DECISIÓN DEL
  EQUIPO, no cita literal):** T1 no exige textualmente que una orden
  deba estar activada para aceptar telemetría — el criterio original de
  TP10 solo pedía "estado 2". Se agregó `fechaActivacion != null` como
  precondición explícita (`409` distinto si falta) porque es coherente
  con el circuito de habilitación que T1 sí describe (sin activación no
  hay instrumento habilitado, luego no debería haber telemetría real
  que procesar) — pero esto es una interpretación de coherencia del
  equipo, no un requisito confirmado por el texto.
- **`fechaInicioCarga` = instante de procesamiento de la primera lectura
  aceptada (INTERPRETACIÓN DEL EQUIPO, no definición literal
  inequívoca):** la cita de T1 (fuente
  `59f2c5bf-3d78-4e71-a60a-153dbecb9c27`, carácter ~16938-17499,
  literal exacto, re-leído con `source_read` para evitar el parafraseo
  del chat) dice: *"...la fecha y hora de el inicio en el último
  momento que empecé a recibir la carga, la fecha y hora de fin de
  carga, ya cuando recibí el último registro..."* El ruido de ASR en
  "el último momento que empecé" no permite citarla como una definición
  inequívoca de "primera lectura válida" — pero la simetría con "fin de
  carga = cuando recibí el **último** registro" sostiene razonablemente
  que "inicio" se refiere al **primer** registro recibido, no a la
  activación ni a la tara. Es la lectura que se adopta, documentada como
  interpretación, no como cita literal cerrada.
- **Generado por el servidor al procesar, no por el instrumento:** sin
  campo de timestamp en el payload de entrada (ver DTO), consistente con
  el resto de la API (F2/F3 primera unidad).
- **Una sola fecha para snapshot, detalle y (si aplica) inicio de
  carga:** en `OrdenBusiness.registrarLectura` se genera una única
  `LocalDateTime.now(ZONA_RECEPCION).truncatedTo(ChronoUnit.MICROS)`
  por lectura aceptada, reutilizada para `fechaUltimaLectura`, la fila
  de `DetalleCarga`, y `fechaInicioCarga` si corresponde fijarla. El
  truncado a microsegundos es el mismo aprendizaje de la unidad
  anterior (bug de precisión en `fechaActivacion`): sin él, la primera
  lectura aceptada devolvería un valor en memoria (nanosegundos) distinto
  del que una relectura posterior (microsegundos, truncado por la
  columna) mostraría para el mismo instante.
- **No se tocan fechas de otras unidades:** `fechaRecepcion`,
  `fechaTara` y `fechaActivacion` no se leen ni se escriben desde
  `registrarLectura`.
- **Validación vs. descarte de negocio (DECISIÓN DEL EQUIPO):**
  `LecturaRequest` no tiene `@Positive`/`@DecimalMin` en
  `masaAcumuladaKg`/`caudalKgH`: caudal/masa ≤ 0 son, según la fuente,
  un caso frecuente y esperado ("eso ocurre durante mucho tiempo... no
  me interesa"), no un error de entrada. Tratarlos como `400` habría
  sido una interpretación más estricta de lo que la fuente describe.
  Verificado: ambos casos devuelven `200` con `aceptada:false`.
- **Masa sin antecedente (DATO FALTANTE en la fuente, DECISIÓN DEL
  EQUIPO):** la regla de masa decreciente no se evalúa en la primera
  lectura de una orden (nada contra qué comparar). Verificado: dos
  descartes distintos (`caudal_no_positivo`, `masa_no_positiva`) antes
  de la primera lectura aceptada devuelven el snapshot en `null`, no en
  cero.
- **Prioridad explícita de motivos cuando varios aplican a la vez
  (DECISIÓN DEL EQUIPO, orden pedido por el usuario, no derivado de la
  fuente):** `caudal_no_positivo` > `masa_no_positiva` >
  `masa_decreciente`, implementado como cadena `if`/`else if` en ese
  orden exacto. Verificado con `caudalKgH=-1` y `masaAcumuladaKg=-5`
  simultáneos sobre una orden con snapshot ya en 1500 → devolvió
  `caudal_no_positivo` (no `masa_no_positiva` ni `masa_decreciente`,
  que también aplicarían).
- **Densidad: duda conservada, no corregida.** Mismo texto de
  `docs/fuentes.md` (rango 0–1 vs. unidad kg/m³): hay una duda abierta
  sobre si esa unidad y ese rango son coherentes entre sí, **sin que se
  haya demostrado una incompatibilidad física** — es la ambigüedad de
  la fuente, no una conclusión propia del equipo. Para esta unidad se
  almacena el valor recibido sin conversión ni validación de rango —
  decisión provisional, explícitamente no definitiva. La precisión de
  columna elegida (`DECIMAL(10,4)`) es una decisión de almacenamiento,
  no una toma de postura sobre cuál de las dos lecturas (0–1 o kg/m³)
  es la correcta.
- **Precisión/escala de los cuatro campos (DECISIÓN DEL EQUIPO):**
  `masaAcumuladaKg`/`caudalKgH` reutilizan `DECIMAL(15,3)` (mismo patrón
  que `taraKg`/`presetKg`); `densidad` usa `DECIMAL(10,4)` (generosa,
  sin tomar partido en la duda); `temperaturaC` usa `DECIMAL(6,2)`
  (generosa, admite negativos, sin rango físico impuesto). `@Digits`
  coincide exactamente con cada escala, para rechazar con `400` en vez
  de redondear en silencio un valor que no entra en esa precisión.
- **Snapshot y DetalleCarga en la misma transacción, con el mismo
  bloqueo que tara/activación:** `findByOrderNumberForUpdate`
  (`PESSIMISTIC_WRITE`) dentro de
  `@Transactional(rollbackFor = Exception.class)`. No se agregó ninguna
  infraestructura nueva de concurrencia: se reutilizó la ya aprobada en
  la unidad anterior.
- **Orden de procesamiento bajo el lock, no orden de llegada de red
  (aclaración explícita pedida por el usuario):** el mecanismo de
  descarte por masa decreciente ordena el *procesamiento* de dos
  lecturas concurrentes (cuál adquiere el lock primero), no garantiza
  que ese orden coincida con el orden real en que el instrumento las
  envió por red — no hay número de secuencia en el payload (DATO
  FALTANTE, T1 no lo menciona). Verificado con dos `curl` en paralelo
  sobre la misma orden, masas 500 y 800: la de masa 800 adquirió el
  lock primero (aceptada, fija snapshot en 800); la de masa 500, al
  procesarse después, encontró 800 como antecedente y se descartó por
  `masa_decreciente`, devolviendo el snapshot sin cambios (800). Una
  tercera lectura de confirmación (masa 800 otra vez) se aceptó,
  confirmando que el snapshot final conservó la mayor masa. **No se
  forzó ni se observó empíricamente en esta sesión el otro orden de
  procesamiento posible** (masa menor primero, en cuyo caso ambas
  lecturas podrían aceptarse) — el contrato documenta ambos casos como
  posibles sin afirmar cuál ocurrirá.
- **Repetidas: sin deduplicación (DECISIÓN DEL EQUIPO).** Coherente con
  que la igualdad de masa no se descarta (TP11): una lectura idéntica
  enviada dos veces se procesa las dos veces como lectura nueva.
  Inventar una deduplicación no pedida habría sido una idempotencia
  silenciosa no autorizada.
- **Verificación funcional — HTTP real contra la app corriendo
  (`mysqldev`, MySQL en Docker, levantado por el usuario):** lectura
  antes de activar (409, mensaje distinto al de estado incompatible);
  dos descartes antes del primer snapshot con campos `null` (no cero);
  primera lectura aceptada fija `fechaInicioCarga`; segunda lectura
  aceptada (masa mayor) la conserva igual; masa igual a la anterior se
  acepta; masa decreciente se descarta sin tocar el snapshot (sigue en
  1500); prioridad de motivos verificada; orden inexistente (404);
  campo ausente (400); sin token (401); carrera de dos lecturas
  concurrentes con masas distintas (ver punto anterior); dos órdenes
  distintas (A y E) con snapshots independientes, no mezclados a nivel
  de API.
- **Histórico (TP13) — verificado por consulta directa de BD, ejecutada
  por el usuario (no por HTTP):** esta sesión no tuvo acceso a `docker
  exec`/cliente `mysql` (sin `sudo` interactivo), así que la consulta
  SQL puntual preparada en `docs/contrato-f3-telemetria.md` (nombres de
  columna corregidos ahí: `temperaturac`/`caudal_kgh`, no
  `temperatura_c`/`caudal_kg_h`) quedó pendiente hasta que el usuario la
  corrió directamente y adjuntó la salida. **Resultado, verificado
  puntualmente para las órdenes 920001/920003/920005, sin generalizar a
  escenarios no probados:** 3/2/2 filas de detalle respectivamente
  (coincide con la cantidad de lecturas aceptadas de cada una, ninguna
  descartada agregó fila), masas almacenadas coincidentes con las
  esperadas, y `fechaInicioCarga`/`fechaUltimaLectura` del snapshot
  coincidentes con la `fecha_lectura` de la primera y la última fila de
  detalle de cada orden. Ningún detalle de una orden apareció bajo otra.

## F4 — primera unidad: cierre explícito 2→3 (TP15)

Detalle completo del contrato en `docs/contrato-f4-cierre.md`. Aquí las
decisiones y su origen.

- **Dos precondiciones adicionales a `estado==2` (DECISIÓN DEL EQUIPO,
  no cita literal):** T1 solo exige, para este endpoint, "un endpoint
  que permita cerrar la orden bloqueando la recepción de nuevos datos de
  detalle... la orden pasa a estado 3." No menciona activación ni
  lecturas previas como condición del cierre. Se agregaron
  `fechaActivacion != null` y `fechaUltimaLectura != null` (al menos una
  lectura aceptada) porque cerrar una carga que nunca se activó o que
  nunca recibió ningún dato real no corresponde al circuito de carga que
  T1 describe, y produciría una conciliación sin masa cargada que
  calcular en la próxima unidad (TP16-TP18). Es una interpretación de
  coherencia del equipo, igual criterio que la precondición de
  activación agregada a `registrarLectura` en la unidad anterior — no un
  requisito confirmado por el texto.
- **Cierre repetido sin excepción nueva:** se reutiliza el mismo chequeo
  `estado==2` que las demás precondiciones: tras el primer cierre exitoso
  la orden queda en estado 3, así que un segundo intento cae en la misma
  rama de rechazo que cualquier otro estado incompatible. No hay un
  mensaje de error distinto para "ya estaba cerrada".
- **`fechaFinCarga` = `fechaUltimaLectura`, no el instante de la llamada
  (DECISIÓN DEL EQUIPO):** interpretación de "fecha y hora de fin de
  carga" (TP20) como el valor que el sistema **adopta** a partir de la
  última lectura válida recibida, en vez del momento administrativo en
  que alguien decide cerrar la orden — ambos pueden diferir. **Corrección
  de precisión (no cambia el código, cambia cómo se describe):**
  `fechaFinCarga` es el dato que el sistema adopta según la última
  lectura aceptada, **no una medición ni una prueba del instante físico
  real** en que dejó de fluir producto — puede existir una brecha no
  medida entre ambos (latencia de red, frecuencia de muestreo), y esta
  unidad no tiene forma de acotarla. El instante real de la llamada al
  endpoint se audita de todas formas, pero en otro campo: `fechaHora`
  del `HistorialEstado` de la transición a 3. Mismo truncado a
  microsegundos (`ChronoUnit.MICROS`) que `fechaActivacion`/
  `fechaUltimaLectura`, por el mismo motivo de precisión de columna
  `datetime(6)`.
- **Misma concurrencia que tara/activación/lectura:** `cerrarCarga` usa
  `findByOrderNumberForUpdate` (`PESSIMISTIC_WRITE`) dentro de la misma
  transacción que el chequeo de precondiciones y la escritura. No se
  agrega ningún mecanismo nuevo (sin `@Version`).
- **Verificado con `curl` en paralelo (dos carreras, no una prueba
  exhaustiva de ambos órdenes posibles):** cierre/cierre sobre la misma
  orden → una `200` (estado 3, `fechaFinCarga` fijado), la otra `409`
  ("no está en estado 2, estado actual: 3"); cierre/lectura sobre la
  misma orden → en la corrida observada el cierre ganó la carrera (`200`)
  y la lectura concurrente fue rechazada (`409`); una lectura posterior
  confirmó que la orden quedó en estado 3. No se forzó deliberadamente el
  orden contrario (lectura ganando antes de que el cierre tome el lock).
- **Verificado con `curl` secuencial contra la app local (`mysqldev`,
  MySQL ya levantado por una sesión anterior) y luego con
  `postman collection run` sobre
  `postman/iw3-f4-cierre.postman_collection.json` (14 requests, 16
  aserciones, 0 fallos):** cierre sobre orden inexistente (404); cierre
  en estado 1 (409); cierre en estado 2 sin activar (409, mensaje
  distinto); cierre activada sin lecturas aceptadas (409, mensaje
  distinto); cierre válido (200, `fechaFinCarga` idéntico al
  `fechaUltimaLectura` de la lectura previa); cierre repetido (409,
  estado actual 3); lectura enviada después del cierre (409, snapshot y
  detalle sin cambios); cierre sin token (401).
- **Verificado por lectura directa de BD, ejecutada por el usuario (no
  por HTTP):** sin acceso a `docker exec`/cliente `mysql` en esta
  sesión, la consulta puntual quedó pendiente hasta que el usuario la
  corrió directamente y confirmó el resultado. **Corrección de columna
  detectada al ejecutarla:** el nombre real de la columna del actor en
  `historial_estados` es `usuariooproceso` (sin guiones bajos, dos
  mayúsculas consecutivas en `usuarioOProceso`), no `usuario_o_proceso`
  como se había escrito al redactar la consulta de esta unidad —
  corregido en `docs/contrato-f4-cierre.md`. El mismo hallazgo de
  nomenclatura ya estaba registrado desde F2 para la misma tabla/columna
  (ver sección F2 de este documento); no se había aplicado al escribir
  la consulta de F4. **Resultado, verificado puntualmente para las
  órdenes 940556/950632/960632 (las tres cerradas en la sesión de
  implementación con `curl`), sin generalizar a órdenes no probadas:**
  una sola fila con `estado_nuevo=3` por orden (una única transición a
  3, sin duplicados) y una sola fila de `detalles_carga` de 4000 kg por
  orden (coincide con la única lectura aceptada de cada una);
  `fechaFinCarga` de `ordenes` coincide con la `fecha_lectura` de esa
  fila en las tres órdenes. Esto confirma que `fechaFinCarga` toma
  efectivamente el valor de la última lectura aceptada, tal como
  implementado — **no confirma, ni puede confirmar, el instante físico
  real de fin de flujo** (ver corrección de precisión arriba). No se
  verificó en esta ronda el contenido exacto de `usuariooproceso` ni la
  relación entre `fecha_hora` de esa fila y el instante real de la
  llamada HTTP contra un valor esperado puntual.

## F4 — segunda unidad: pesaje final y conciliación (TP16–TP18)

Detalle completo del contrato en
`docs/contrato-f4-pesaje-conciliacion.md`. Aquí las decisiones y su
origen.

- **Sin precondiciones adicionales a `estado==3` (a diferencia de
  TP15):** el pesaje final no exige activación ni lecturas aceptadas
  como chequeos propios — ya están garantizadas por el circuito: para
  llegar a estado 3 la orden ya pasó por `cerrarCarga` (TP15), que sí
  las exige. Agregar el mismo chequeo otra vez habría sido redundante,
  no una protección adicional real.
- **`productoCargadoKg` = última masa acumulada ACEPTADA del snapshot,
  no el `presetKg` (CONFIRMADO por la fuente, no decisión del
  equipo):** T1 distingue explícitamente el preset del producto
  realmente cargado; `calcularConciliacion` usa
  `Orden.masaAcumuladaKg`, nunca `presetKg`.
- **`ultimaTemperaturaC`/`ultimaDensidad`/`ultimoCaudalKgH`, no
  "promedio*" (DECISIÓN DEL EQUIPO sobre el nombre de los campos, no
  sobre el cálculo):** el nombre del campo en la respuesta JSON
  refleja explícitamente que son los últimos valores del snapshot, no
  un promedio — para que un consumidor de la API no pueda confundirlos
  con TP19 (promedios, diferido a F6) solo por el nombre del campo. El
  valor en sí es simplemente `Orden.temperaturaC`/`densidad`/
  `caudalKgH`, sin cálculo adicional.
- **Sin validación cruzada entre `pesoBrutoKg` y `taraKg` (DECISIÓN DEL
  EQUIPO, por instrucción explícita del usuario):** no se rechaza un
  pesaje final cuyo bruto sea menor que la tara (neto negativo), ni se
  aplica ninguna tolerancia o umbral a `diferenciaKg`. "No rechazar por
  diferencia distinta de cero ni inventar tolerancias/porcentajes" fue
  una instrucción explícita, no una interpretación de este equipo.
- **Sin redondeo explícito:** `calcularConciliacion` usa
  `BigDecimal.subtract` dos veces (`netoKg`, `diferenciaKg`) sin ningún
  `setScale`; la precisión de los operandos recibidos/persistidos se
  conserva íntegra.
- **Cálculo compartido entre pesaje final y consulta
  (`OrdenBusiness.calcularConciliacion`, privado):** para que
  `registrarPesajeFinal` (TP16) y `consultarConciliacion` (TP17)
  devuelvan exactamente el mismo resultado a partir de los mismos
  valores persistidos, en vez de reimplementar el cálculo dos veces.
  Devuelve `ar.edu.iua.iw3.ordenes.business.Conciliacion`, un POJO de
  negocio nuevo (mismo patrón que `LecturaResultado`: no es un DTO de
  controller).
- **`consultarConciliacion` sin bloqueo pesimista (a diferencia de las
  demás operaciones de negocio sobre `Orden`):** es una lectura simple
  que no participa de ninguna transición de estado; no hay nada que
  serializar. Se agregó `OrdenRepository.findByOrderNumber` (derivado,
  sin `@Lock`) para esto, distinto de `findByOrderNumberForUpdate` (que
  sigue siendo exclusivo de las operaciones que escriben).
- **`fechaPesajeFinal` = instante real de la llamada (a diferencia de
  `fechaFinCarga` en TP15):** no hay aquí una "última lectura" de la
  que derivarlo — el pesaje final es un dato que llega en el momento en
  que se registra, sin telemetría previa equivalente. Mismo truncado a
  microsegundos que las fechas anteriores, mismo motivo de precisión de
  columna `datetime(6)`.
- **Pesaje final repetido sin excepción nueva:** mismo criterio que el
  cierre repetido (TP15) — tras el primer pesaje exitoso la orden queda
  en estado 4, así que un segundo intento cae en la misma rama de
  rechazo `estado==3` que cualquier otro estado incompatible.
- **Verificado con `curl` secuencial contra la app local (`mysqldev`,
  MySQL ya levantado) y luego con `postman collection run` sobre
  `postman/iw3-f4-pesaje-conciliacion.postman_collection.json` (19
  requests, 21 aserciones, 0 fallos):** diferencia cero (tara 12000/
  bruto 16000/masa 4000 → `0.000`), positiva (bruto 16080 → `80.000`) y
  negativa (bruto 15900 → `-100.000`); pesaje final y conciliación
  sobre orden inexistente (404) y en estados 1/2/3 incompatibles (409);
  `pesoBrutoKg` ausente/no positivo/con 4 decimales (400 los tres);
  pesaje final repetido (409); conciliación repetida (200, mismo
  resultado); sin token (401).
- **POST de pesaje final vs. GET de conciliación inmediatamente
  posterior, sobre la misma orden: verificado idénticos** (comparación
  JSON directa con `curl`, sin diferencias).
- **Verificado con `curl` en paralelo (una carrera, no una prueba
  exhaustiva de ambos órdenes posibles):** dos pesajes finales
  concurrentes sobre la misma orden, con `pesoBrutoKg` distintos para
  distinguir cuál ganó → una `200` (la que adquirió el lock primero), la
  otra `409` ("no está en estado 3, estado actual: 4"); una conciliación
  posterior confirmó que el valor persistido era el de la que ganó, no
  el rechazado.
- **Verificado por lectura directa de BD, ejecutada por el usuario (no
  por esta sesión, sin acceso a cliente `mysql` ni `docker exec` en
  este entorno, mismo límite que TP13/TP15):** consulta SQL puntual con
  el nombre de columna ya corregido (`usuariooproceso`), para las seis
  órdenes reales de esta unidad. **Resultado reportado por el
  usuario:** `estado`/`taraKg`/`pesoBrutoKg`/`masaAcumuladaKg` y los
  cálculos de `netoKg`/`diferenciaKg` coinciden exactamente con los ya
  calculados a mano contra la respuesta HTTP (0.000 / 80.000 / -100.000
  / 40.000 / 500.000 para 1792624398/399/400/402/403 respectivamente);
  1792624401 (nunca llegó a pesaje final) muestra `estado=1` y esos
  campos en `NULL`. La consulta del historial devolvió **exactamente
  una fila con `estado_nuevo=4` por cada una de las cinco órdenes
  finalizadas**, todas con `usuariooproceso=qa_f1_dfab890c` y
  `fecha_hora` idéntica a `fecha_pesaje_final` de la orden
  correspondiente; para 1792624401 el `LEFT JOIN` no devolvió ninguna
  fila de historial. Detalle completo, con la tabla reportada, en
  `docs/contrato-f4-pesaje-conciliacion.md`.

## F4 — cierre

**Aprobación:** el usuario aprobó el cierre de F4 para el alcance del
segundo parcial (TP15–TP18) en base a la implementación y evidencia ya
revisadas en ambas unidades, más la matriz de saltos/retrocesos
completada en esta entrada, sin repetir pruebas ni auditorías
anteriores.

- **Matriz de saltos/retrocesos completada:** se identificaron ocho
  combinaciones (operación, estado inicial incompatible) que ninguna
  unidad anterior había ejercitado — `registrarTara`/`activar` sobre
  estados 3 y 4, `registrarLectura` sobre estados 1 y 4, `cerrarCarga`
  sobre estado 4 y `registrarPesajeFinal` sobre estado 2. Antes de
  agregar `registrarLectura` sobre estado 1 se verificó explícitamente
  que no estuviera ya cubierta: la única prueba previa de "lectura
  antes de activar" (F3, segunda unidad) usaba una orden en estado 2
  con tara ya registrada, no una orden en estado 1 sin tara — mensaje
  distinto esperado ("no esta en estado 2", no "todavía no fue
  activada"). Las ocho se probaron con `curl` contra la app local
  (cuatro órdenes de apoyo llevadas a los estados 1/2/3/4 necesarios) y
  las ocho respondieron `409` con el mensaje de estado incompatible
  esperado (estado actual correcto en cada mensaje) y
  `devInfo: "No stack trace"`, sin detalle interno expuesto.
- **Versionadas en `postman/iw3-f4-saltos-retrocesos.postman_collection.json`
  (nueva):** 25 requests (login, cuatro órdenes de apoyo, las
  secuencias de avance necesarias para llevarlas a los estados 2/3/4, y
  las ocho combinaciones), **26 aserciones, 0 fallos**, ejecutada con
  `postman collection run`, credenciales solo por `--env-var`. No
  repite ninguna combinación ya cubierta por
  `iw3-f2-recepcion`/`iw3-f3-tara-activacion`/`iw3-f3-telemetria`/
  `iw3-f4-cierre`/`iw3-f4-pesaje-conciliacion`.
- **Límites conservados explícitamente, sin resolver en este cierre:**
  - Estos ocho rechazos **no se verificaron por lectura directa de
    BD**: la ausencia de cambios se apoya en la respuesta HTTP y en que
    la excepción de estado se lanza antes de cualquier `save()`, dentro
    de la misma transacción (revisión de código), igual criterio que
    los rechazos de unidades anteriores sin lectura directa.
  - Las carreras de concurrencia (cierre/cierre, cierre/lectura, pesaje
    final/pesaje final) siguen con alcance acotado a un solo orden de
    llegada observado por carrera, no una prueba exhaustiva de ambos
    órdenes posibles.
  - TP19 (promedios) sigue **diferido a F6**, decisión ya tomada en la
    segunda unidad de F3, no una evidencia faltante por accidente de
    F4.
  - Densidad (duda de rango/unidad, heredada de F3) sigue sin resolver.
  - Sin prueba de un caso real de `500` en ninguna unidad de F4; sin
    verificación de comportamiento ante caída de conexión a mitad de
    transacción. Por instrucción explícita, ninguno de estos dos puntos
    (ni el alcance acotado de las carreras) se trata como bloqueante
    para este cierre.
- **F4 queda formalmente cerrada para el alcance del segundo parcial.**
  TP01, TP22, TP25 en adelante siguen pendientes por fase (F5/F6, o el
  cierre completo del segundo parcial para TP22). **F5 queda pendiente
  de iniciar**, según el plan de fases vigente (`CLAUDE.md`, sección
  8): revisar cobertura de los puntos 1–5, Swagger completo, colección
  Postman sin secretos y datos de ejemplo reproducibles antes de
  declarar lista la entrega del segundo parcial.

## Flujo de ramas — confirmado antes de iniciar F5

No es una decisión nueva: coincide con `CLAUDE.md`, sección 4. Se
registra aquí porque el usuario pidió confirmarlo explícitamente como
acuerdo vigente en este punto del proyecto, antes de abrir F5.

- `feature/*` → PR a `develop` → PR a `main` cuando se autorice una
  entrega.
- `develop` acumula el desarrollo integrado y revisado (no es un
  espejo de ninguna rama `feature/*` individual).
- `main` recibe primero la entrega del segundo parcial (con su
  etiqueta `parcial-2-v1.0.0` propuesta), y más adelante la del final
  (`final-v1.0.0`). `main` no representa una entrega aprobada hasta
  que efectivamente se promueva una versión estable y se etiquete.
- Después de la entrega del parcial, el trabajo continúa con el mismo
  patrón `feature/*` → `develop` (no se abre un flujo distinto para el
  final).
- Esta entrada **no** ejecuta ningún merge, push ni cambio de rama: la
  rama de trabajo actual sigue siendo `feature/f4-cierre-conciliacion`,
  todavía sin PR a `develop`.

## F6 — roles y permisos (TP27, primera unidad)

Detalle completo del contrato, la matriz de permisos y la evidencia de
verificación en `docs/contrato-f6-roles-permisos.md`. Aquí solo las
decisiones y su origen.

- Roles `ROLE_OPERADOR`/`ROLE_SUPERVISOR` y el mapeo operación→rol
  (recepción solo `ROLE_SUPERVISOR`; tara/activación/lecturas/cierre/
  pesaje-final/conciliación-consulta con cualquiera de los dos) son
  **DECISIÓN DEL EQUIPO**, aprobada explícitamente por el usuario en
  esta unidad. T2 solo exige "roles de usuario que regulen el consumo
  de la API" (TP27), sin nombrar roles ni mapear operaciones — no es
  una exigencia textual de cátedra, y `CLAUDE.md` (instrucción local
  del equipo) ya advertía no presentar nombres de rol como impuestos
  por el profesor.
- Se usó `hasAuthority`/`hasAnyAuthority` en vez de `hasRole`, a
  propósito, para no repetir el patrón `hasRole('ROLE_ADMIN')` de los
  controladores de ejemplo del profesor (`AuthtestRestController`,
  `MailRestController`), que exige en realidad la autoridad
  `ROLE_ROLE_ADMIN` por el doble prefijo que agrega `hasRole`. No se
  modificó ese código de ejemplo (fuera de alcance de esta unidad).
- **Hallazgo:** `@PreAuthorize` denegado devolvía 401 en vez de 403,
  por el mismo bug de forward a `/error` ya documentado para
  `MethodArgumentNotValidException` (400→401) en la unidad de F2 — acá
  aplicado a `AccessDeniedException` (403→401). Mismo motivo, misma
  corrección: un `@ExceptionHandler(AccessDeniedException.class)`
  local a `OrdenRestController`, sin tocar `SecurityConfiguration` ni
  agregar `/error` a las rutas públicas (se había descartado esa
  opción la vez anterior, por el mismo motivo: ampliar una ruta pública
  de toda la aplicación por un problema de un controlador, y exponer
  sin autenticación el cuerpo de error por defecto de Spring Boot).
- Usuarios de prueba: mismo procedimiento manual de F1 (rol + usuario +
  relación insertados a mano, ver `docs/desarrollo-local.md`), pero el
  hash BCrypt se generó con un programa Java de un solo uso en vez de
  `GET /demo/encodepass`, para no exponer la contraseña en una URL HTTP
  ni en ningún log. Las contraseñas se generaron al azar, se
  regeneraron una vez durante la sesión (la primera generación quedó
  expuesta en la salida de una herramienta, corregido de inmediato por
  pedido del usuario) y quedan únicamente en `.env.testroles`
  (gitignored, permisos `600`). El usuario `qa_f1_dfab890c` de F1 no se
  modificó ni se le agregó ningún rol nuevo, por instrucción explícita.
- Consecuencia para las colecciones Postman de F2–F5: a partir de esta
  unidad necesitan un usuario con `ROLE_SUPERVISOR` en
  `testUsername`/`testPassword` (`qa_f1_dfab890c` deja de alcanzar).
  Se verificó corriendo de nuevo la colección del runner de F5 con las
  credenciales del usuario `ROLE_SUPERVISOR` nuevo: 34/34 aserciones
  OK, sin modificar el archivo de la colección.
- Esta entrada queda sin commit/push/merge, en la rama
  `feature/f6-roles-permisos` (creada desde `develop` en `a264ada`),
  por instrucción explícita de la sesión.

## F6 — frecuencia configurable de persistencia del histórico (TP14)

Detalle completo, matriz de casos y evidencia en
`docs/contrato-f6-frecuencia-historico.md`. Aquí solo las decisiones y
su origen.

- `detalleIntervaloSegundos` (segundos, opcional, por orden), nombre
  del campo, que se fije únicamente en la recepción y sin forma de
  modificarlo después en esta unidad, y la política de primera/
  siguiente lectura (primera siempre se guarda; siguientes solo si
  pasó el intervalo desde el último detalle **guardado**, no desde la
  última lectura recibida) son **DECISIÓN DEL EQUIPO**, aprobada
  explícitamente por el usuario. La fuente solo describe el fenómeno
  (recibir cada segundo, guardar cada diez) sin especificar ningún
  mecanismo de configuración.
- No forzar un detalle adicional al cerrar la carga, para igualar
  `fechaUltimaLectura` con `fechaUltimoDetalleGuardado`, es
  **decisión del equipo** — no se afirma que la fuente lo prohíba ni
  que hacerlo la contradiga; simplemente no hace falta para cumplir lo
  que pide, porque la fuente ya describe que la mayoría de las
  lecturas recibidas no se guardan.
- Las 19 órdenes creadas antes de esta unidad quedaron con
  `detalleIntervaloSegundos`/`fechaUltimoDetalleGuardado` en `NULL`
  (columnas nuevas, `ddl-auto=update` en el entorno local) — verificado
  por consulta directa, no asumido. Por diseño (intervalo `null` =
  guardar cada lectura aceptada), siguen funcionando igual que antes;
  se confirmó con una lectura real sobre una de ellas. El mecanismo de
  migración para un despliegue real (no `ddl-auto=update`) queda fuera
  de alcance de esta unidad.
- Esta entrada queda sin commit/push/merge, en la rama
  `feature/f6-frecuencia-historico` (creada desde `develop` en
  `e1bacb4`), por instrucción explícita de la sesión.

## F6 — promedios de temperatura, densidad y caudal (TP19)

Detalle completo, matriz de casos y evidencia en
`docs/contrato-f6-promedios-historico.md`. Aquí solo las decisiones y
su origen.

- Qué lecturas integran el promedio: **no es una duda abierta**. T1
  dice explícitamente "según los datos almacenados" — es el histórico
  almacenado (`DetalleCarga`), no todas las lecturas aceptadas
  recibidas, que desde TP14 puede ser un conjunto mayor si la orden
  configuró intervalo. No se consultó NotebookLM: la fuente ya
  registrada resolvía el punto concreto.
- Promedio aritmético simple, sin ponderar por tiempo; agregar los
  campos de promedio sin reemplazar los últimos valores existentes;
  escala de salida fija por campo (temperatura 2, densidad 4, caudal
  3 decimales) con `RoundingMode.HALF_UP` sobre el resultado final
  únicamente: **DECISIÓN DEL EQUIPO**, aprobada explícitamente por el
  usuario.
- `SUM`+`COUNT` en vez de `AVG`: la especificación JPA define que
  `AVG` siempre devuelve `Double`, sin importar el tipo de la columna
  de origen — usar `AVG` sobre columnas `BigDecimal` perdería
  precisión por esa conversión. `SUM` sobre una columna `BigDecimal`
  sí preserva `BigDecimal` (misma especificación), y la división final
  se hace en Java con `BigDecimal.divide(...)`, con control explícito
  de escala y redondeo.
- Promedio no persistido, calculado en vivo: mismo criterio ya usado
  para el resto de `Conciliacion`/`calcularConciliacion`.
- Los cuatro casos acordados (orden sin intervalo, orden con intervalo
  configurado/histórico muestreado, igualdad entre pesaje-final y GET
  posterior, verificación por SQL directo) se ejecutaron los cuatro —
  detalle y evidencia en `docs/contrato-f6-promedios-historico.md`.
- Caso sin ninguna muestra almacenada: **no es uno de los cuatro
  casos anteriores**, es una protección adicional del código (`null`,
  nunca `0`) que no se ejecutó como prueba, porque es inalcanzable por
  el circuito normal — `cerrarCarga` ya exige al menos una lectura
  aceptada, y la primera lectura aceptada de toda orden siempre genera
  una fila de detalle (regla de TP14). Se documenta esta imposibilidad
  en vez de forzar un escenario artificial manipulando la base
  directamente.
- Esta entrada queda sin commit/push/merge, en la rama
  `feature/f6-promedios` (creada desde `develop` en `e5dbec3`), por
  instrucción explícita de la sesión.

## F6 — alarmas de temperatura: configuración, disparo, consulta y aceptación (TP28/TP29, primera unidad)

Detalle completo, verificación y hallazgo en
`docs/contrato-f6-alarmas-configuracion.md`. Aquí solo las decisiones
y su origen.

- Configuración global (una sola fila, no por orden como
  `detalleIntervaloSegundos` de TP14): **DECISIÓN DEL EQUIPO**. T2
  habla de un único umbral en singular, sin mencionar nunca una orden.
- Alcance "por orden" de la supresión mientras una alarma está
  pendiente, y que el re-disparo tras aceptar no exija que la
  temperatura haya bajado: **DECISIÓN DEL EQUIPO**, la cita de T2 no
  lo confirma literalmente (solo dice "no debe enviarse nuevamente
  hasta que no sea aceptada", sin precisar el alcance de "ese caso" ni
  qué pasa si la condición nunca se resuelve).
- Auditoría de aceptación (usuario y fecha/hora obligatorios,
  observación el único campo opcional): apoyada en el fragmento
  directo de T2 consultado vía NotebookLM — *"el registro de usuario
  fecha ahora observación e opcional"* — pero el orden de palabras es
  una interpretación razonable, no una demostración inequívoca de la
  transcripción (criterio del equipo, no una cita que lo resuelva por
  sí sola).
- Rol para consulta/aceptación: `ROLE_OPERADOR` o `ROLE_SUPERVISOR`
  (no solo supervisor) — la fuente habla de "un operario, un
  operador... mirando esto" en lenguaje genérico, consistente con
  permitir ambos roles, igual criterio que las operaciones
  "compartidas" de TP27. Rol para la configuración global: solo
  `ROLE_SUPERVISOR` (acción de política, no operación diaria).
- `SUM`+`COUNT`/`AVG` no aplica aquí (sin promedios); se reutilizó en
  cambio el mismo patrón de bloqueo de fila (`PESSIMISTIC_WRITE`) ya
  usado por `registrarLectura`/`cerrarCarga`/etc. para coordinar el
  disparo de una alarma con su aceptación concurrente.
- El correo (TP28/TP29 completos) queda para una segunda unidad,
  explícitamente fuera de esta entrega: debe enviarse después del
  commit de la transacción, fuera del bloqueo de la orden, con un
  receptor local de pruebas en vez de correos reales.
- Esta entrada queda sin commit/push/merge, en la rama
  `feature/f6-alarmas` (creada desde `develop` en `1f71db5`), por
  instrucción explícita de la sesión.

## F6 — alarmas de temperatura: envío de correo (TP28/TP29, segunda unidad, cierra ambos)

Detalle completo, mecanismo y evidencia en
`docs/contrato-f6-correo-alarmas.md`. Aquí solo las decisiones y su
origen.

- Evento inmutable sin entidades JPA (`AlarmaDisparadaEvent`): decisión
  del equipo para no depender de una sesión de Hibernate abierta ni de
  relaciones lazy cuando el listener asíncrono corra, y para que un
  cambio posterior de configuración o una aceptación rápida no alteren
  una notificación ya en camino (el evento lleva su propia copia de
  los valores vigentes al momento del disparo).
- `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`: reutiliza
  `@EnableAsync`, ya habilitado en la aplicación (`Scheduler.java`) —
  no se agregó infraestructura nueva de async, solo el listener. Se
  prefirió este mecanismo (y no el `ApplicationEventMulticaster` con
  `SimpleAsyncTaskExecutor` ya configurado para los eventos de
  usuario) porque ese multicaster no interviene en el re-despacho que
  hace `TransactionSynchronizationManager` al llamar a un listener
  transaccional en `afterCommit()` — por eso `@Async` en el método es
  necesario para que el envío no bloquee el hilo que hizo el commit.
- Sin reintento automático: decisión del equipo, documentada
  explícitamente como limitación aceptada (no se promete entrega
  exactamente una vez), no agregada por request del usuario de una
  cola o mecanismo de reintento.
- `SMTP_AUTH`/`SMTP_STARTTLS` configurables por variable de entorno,
  con default sin cambios (`true`/`true`): permite apuntar a un
  receptor local de prueba sin alterar el comportamiento por defecto
  pensado para un proveedor real.
- Receptor SMTP local de prueba: servidor mínimo propio (socket TCP,
  sin dependencias nuevas), que simula el rechazo de **un**
  destinatario agregando la palabra "fallo" a su casilla — la falla
  vive en la herramienta de prueba externa, no en ningún interruptor
  agregado al código de la aplicación. No simula una caída total del
  servidor SMTP (esa indisponibilidad no se probó en esta unidad).
  Se versiona en el repositorio (`tools/smtp_test_catcher.py` +
  `tools/README.md`) para que la prueba de correo quede reproducible,
  no solo documentada — decisión del equipo: es una utilidad de
  pruebas, no código de producción, sin datos reales en ningún
  momento; su archivo de salida queda excluido por `.gitignore`.
- Prueba de rollback (`AlarmaRollbackIntegrationTest`): usa
  `TransactionTemplate`/`PlatformTransactionManager` reales para forzar
  la reversión desde el test, sin modificar `OrdenBusiness` ni agregar
  ningún endpoint. Primer test de integración con contexto de Spring
  de este proyecto (los anteriores, como `JwtSecurityVerificationTest`,
  eran pruebas unitarias aisladas sin contexto) — se reutilizan las
  dependencias de test ya declaradas en el POM, sin agregar ninguna
  nueva.
- Con esta unidad, TP28 y TP29 quedan **completos** para el alcance de
  esta entrega.
- Esta entrada queda sin commit/push/merge, en la rama
  `feature/f6-alarmas` (continuación de `d40a87a`), por instrucción
  explícita de la sesión.

## F6 — cierre (2026-10-22)

Cierre formal de F6 para el alcance implementado, sin pruebas nuevas:
se reutiliza la evidencia ya registrada en cada contrato de unidad
(TP14, TP19, TP27, TP28/TP29). Detalle y límites aceptados en
`docs/estado-proyecto.md`, sección "F6 — cierre".

- Decisión de alcance: el listado/filtrado de órdenes y demás
  consultas que TP31 necesita se implementan en F7, no se adelantan en
  F6 — la matriz ya asignaba TP31 a F7; esta entrada solo deja la
  precisión registrada, sin marcar ningún endpoint nuevo como cumplido.
- Rama documental `docs/f6-cierre`, creada desde `develop` en
  `24c7df3`, por instrucción explícita de la sesión.

## F7 — TP32/TP33, tiempos de carga (2026-10-23)

- **Aclaración de fuentes, por instrucción explícita de la sesión:**
  `CLAUDE.md` (y, en general, cualquier documento local del equipo)
  **no es evidencia docente** — es un borrador/síntesis del equipo,
  aunque esté redactado en tono normativo. La fórmula de ETA y la
  regla "visible solo en estado 2" que `CLAUDE.md` §6 presenta juntas
  para ETA y tiempo transcurrido no deben leerse como una cita de T2
  para ambos requisitos por igual - ver el chequeo de abajo.
- **Verificación puntual contra NotebookLM (BE, notebook
  `5f7b0e91-c874-416c-898b-a5a986471f3a`, 2026-10-23), pedida porque
  la fórmula/visibilidad del ETA solo estaban respaldadas por
  `CLAUDE.md`:** el fragmento literal de T2 (líneas 108-118 de la
  transcripción) **no da una fórmula matemática para el ETA** (sin
  resta, división ni "por 60" en el texto) y **la restricción "solo
  en estado 2" se menciona en el texto únicamente para el tiempo
  transcurrido (TP33), no para el ETA (TP32)**. Tampoco menciona
  redondeo, decimales, ni el caso de caudal cero/negativo. Detalle
  completo en `docs/requisitos.md`, TP32/TP33.
- Consecuencia práctica: la fórmula de ETA, su redondeo (HALF_UP, 2
  decimales) y que se calcule/muestre solo en estado 2 pasan a estar
  etiquetados como **INTERPRETACIÓN DEL EQUIPO** / **DECISIÓN DEL
  EQUIPO** en `docs/requisitos.md`, no como "confirmado por T2". La
  restricción de estado 2 del tiempo transcurrido (TP33) sí sigue
  siendo **CONFIRMADO** por cita literal.
- **Fechas (TP32/TP33, F7):** `fechaInicioCarga` (ya existía en
  `Orden.java`, F3) y `fechaUltimaLectura` se exponen en
  `OrdenResumenResponse` como String ISO-8601 **con offset explícito**
  (`DateTimeFormatter.ISO_OFFSET_DATE_TIME` sobre
  `valor.atZone(ZONA_RECEPCION)`), nunca como `LocalDateTime` desnudo
  - decisión explícita de la sesión para que el frontend no interprete
    una hora sin zona como hora del navegador. No se modificó el tipo
  de columna en `Orden.java` ni los valores ya almacenados: la
  conversión es solo de presentación, en el DTO. `ZONA_RECEPCION` se
  duplica como constante en `OrdenResumenResponse` (en vez de
  depender de la capa de negocio desde un DTO) - debe coincidir con
  `OrdenBusiness.ZONA_RECEPCION`, ambas documentadas con ese enlace
  explícito.
- **ETA en backend, sin endpoint nuevo:** `OrdenBusiness.calcularEtaMinutos`
  (método estático, `ar.edu.iua.iw3.ordenes.business`, visibilidad de
  paquete para poder probarlo directo desde un test sin Spring), se
  invoca fila por fila en `listarOrdenes` solo para estado 2 y llena
  `etaMinutos` en `OrdenResumenResponse`. No se pudo expresar en el
  `SELECT NEW` JPQL existente (fórmula condicional con redondeo): se
  completa después de traer los resultados, no agrega una consulta
  por fila.
- **Caudal ≤ 0, probado aislado:** `OrdenBusiness.registrarLectura` ya
  descarta esas lecturas antes de persistir el snapshot (regla de F3),
  por lo que ese caso de `calcularEtaMinutos` no es alcanzable por el
  flujo normal end-to-end. Se prueba contra el método directamente en
  `OrdenBusinessEtaTest` (JUnit puro, sin `@SpringBootTest`, sin BD),
  con esa limitación documentada en el javadoc del test, no oculta.
- **Frontend (reloj de TP33):** tick de 1 segundo vía `setInterval`
  solo para forzar el re-render (`formatearTranscurrido` calcula
  siempre contra `Date.now()`), con `clearInterval` en el cleanup del
  `useEffect` al desmontar. El reloj nunca descuenta el ETA ni simula
  avance de masa/caudal: esos valores solo cambian al volver a
  consultar el backend ("Actualizar", igual que TP31).
- **Sin framework de pruebas de UI en el frontend** (`package.json`
  sin Vitest/Jest/Testing Library): no se incorporó uno para esta
  unidad puntual, por instrucción de no agregar funcionalidades
  nuevas fuera de alcance. Los casos de estados 1/3/4, contador mayor
  a 24 h y manejo de offset quedan para la revisión visual conjunta
  con el usuario, no para un test automatizado nuevo. Sí se hizo una
  verificación puntual con Node 22.23.4 (fuera de la app) de que
  `new Date(...)` interpreta correctamente los strings con offset que
  produce el backend, con y sin fracción de segundo.
- Esta entrada queda sin commit/push/merge, en la rama
  `feature/f7-tiempos-carga` (continuación de `develop` en `491eccc`),
  por instrucción explícita de la sesión.

## Pendientes FE de la sección 13: resueltos por consulta puntual (2026-10-23)

- Consulta dirigida a NotebookLM (cuaderno "Ingeniería Web III FE",
  `0601d0ab-5564-42ab-821b-1356d44c3b67`, 28 fuentes), no una
  relectura completa: dos preguntas puntuales sobre (a) si las
  carpetas 01-13 son entregables separados o patrón de referencia, y
  (b) si sus exigencias técnicas (Zod, librerías de UI, Cypress/CI/
  hosting, informe de performance) aplican al trabajo integrador de
  gas.
- **Resultado, con cita literal:** las carpetas 01-13 describen el
  **proyecto personal autónomo de esta misma materia** (repositorio
  propio "ingenieria-web-3"), **sin ninguna mención de integración
  con otro trabajo integrador** (confirmado explícitamente, no
  inferido). Sus exigencias puntuales son del proyecto personal, no
  del integrador de gas — se documentan como "no aplica, con
  justificación" en vez de como "pendiente".
- **Por qué no se leyeron las 28 fuentes completas:** la instrucción
  de la sesión pidió reutilizar citas existentes y consultas
  puntuales, no releer todo innecesariamente; dos preguntas bien
  dirigidas alcanzaron para resolver la ambigüedad central (a qué
  proyecto corresponden las exigencias), sin necesidad de transcribir
  carpeta por carpeta.
- **Dato que sigue faltante, sin bloquear nada del integrador:** si
  el proyecto personal de la materia tiene nota propia independiente
  - las fuentes no lo aclaran ("no está en el material"); es una
  pregunta para el docente sobre OTRA pieza de evaluación, no sobre
  este trabajo.
- Con esta resolución, el criterio de salida de F7 (`CLAUDE.md` §8)
  queda satisfecho - ver `docs/estado-proyecto.md`, sección "F7 —
  contraste contra el criterio de salida".
- Esta entrada queda sin commit/push/merge, por instrucción explícita
  de la sesión (solo documentación).

## Criterio del equipo aclarado: estructura de la base docente (2026-10-23)

**Decisión del equipo, aclarada explícitamente por el usuario:** el
integrador debe **mantener** la estructura y los patrones de la base
docente oficial (`iw3-backend`, commit `b7e705d` "Backend completo")
**ampliándolos** para cumplir la consigna - no corresponde
reorganizarla, ni trasladar automáticamente herramientas exigidas en
el proyecto personal de las clases FE (ver sección anterior: eso es
un proyecto distinto, con su propio alcance y fuente).

**Contraste puntual, sin auditoría completa ni pruebas repetidas**
(comparación de listado de archivos entre `iw3-backend` e
`iw3-backend-sanitized`, más una inspección dirigida):

- **Paquetes/capas:** los 15 paquetes de la base (`auth`, `business`,
  `config`, `controllers`, `events`, `integration/cli1`,
  `integration/cli2`, `model`, `model/persistence`, `schedules`,
  `security`, `util`, `websocket`, y sus subpaquetes) están
  **intactos, sin un solo archivo removido ni renombrado** -
  verificado por diff de listados de archivos (`comm` sobre ambos
  árboles), no por lectura manual. La única adición es un paquete
  **nuevo** `ordenes` (con `business`/`controllers.dto`/
  `model.persistence`), que replica la misma convención de capas de
  la base en vez de inventar una propia.
- **Controllers:** `OrdenRestController extends BaseRestController`
  - reutiliza el controller base, no lo duplica.
- **Respuesta estándar:** `OrdenRestController` usa
  `IStandartResponseBusiness`/`StandartResponse` de `util/`, igual
  que los controllers de la base - sin una respuesta estándar
  paralela.
- **Autenticación/seguridad:** sin ningún archivo de JWT/seguridad/
  filtros dentro de `ordenes/` - la autorización de los endpoints de
  órdenes se apoya enteramente en `auth/`/`security/` ya existentes
  (`@PreAuthorize` sobre los mismos roles, sin un mecanismo nuevo).
- **Correo:** el envío de alarmas (TP28/TP29) reutiliza
  `util.EmailBusiness` ya existente en la base - no hay un
  `JavaMailSender`/`MimeMessage` nuevo dentro de `ordenes/`.

**Sin desviación concreta encontrada.** No se mezcla este criterio
con las exigencias funcionales del integrador (TP01-TP36): mantener
la estructura de la base es una condición necesaria ya cumplida, no
un sustituto de los requisitos funcionales, que se siguen rastreando
aparte en la matriz de `docs/requisitos.md`.

Esta entrada queda sin commit/push/merge, por instrucción explícita
de la sesión (solo documentación, sin auditoría completa).
