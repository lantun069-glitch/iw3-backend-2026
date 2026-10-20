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
