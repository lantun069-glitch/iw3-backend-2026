# Defensa — explicaciones y preguntas propuestas

Objetivo: que cualquier integrante pueda explicar esto sin depender de su
rol (evaluación individual, ver TP36 en `docs/requisitos.md`). Se agrega
una entrada por unidad de trabajo relevante. No duplica el detalle
técnico de `docs/decisiones.md`/`docs/estado-proyecto.md`; enlaza a
ellos.

**Importante:** las preguntas de cada unidad son **propuestas**, escritas
al cerrar esa unidad de trabajo. Ninguna fue practicada todavía por una
persona real contestándola en voz alta; no confundir "la pregunta está
escrita" con "alguien ya sabe responderla".

## Unidad: saneamiento de credenciales heredadas y copia de trabajo

Detalle completo: `docs/decisiones.md`.

- **Qué:** se detectaron 4 valores hardcodeados en el código heredado del
  profesor (contraseña de BD de producción, contraseña de BD de
  desarrollo, contraseña SMTP por defecto, clave de firma JWT). Se
  reescribió el historial en una copia separada del repositorio,
  reemplazando esos 4 valores en todos los commits, y se externalizaron
  por variables de entorno sin default.
- **Por qué:** decisión del equipo de no reutilizar ni republicar
  credenciales ajenas, aunque ya fueran públicas en el repositorio del
  profesor.
- **Qué concepto de clase usa:** perfiles de Spring y `@Value` con
  placeholders (`${VAR}`), igual que ya usa el código docente para otras
  propiedades (ej. `dev.info.enabled`); la diferencia es no dejar un
  valor por defecto cuando el valor es un secreto.
- **Cómo comprobarlo:** `git log <rama> -p | grep <patrón>` sobre las 3
  ramas da 0 ocurrencias de los 4 valores originales; `./mvnw clean
  compile` compila sin errores con las variables ausentes del código.
- **Preguntas propuestas (sin practicar):**
  1. ¿Por qué no alcanza con borrar el archivo con la credencial y hacer
     un commit nuevo? (Porque el valor sigue en el historial de git, en
     el commit anterior, recuperable con `git log -p` o `git show`.)
  2. ¿Por qué se usó una copia del repositorio en vez de reescribir el
     historial directamente sobre el clon original? (Para no perder el
     único respaldo intacto del historial heredado si algo salía mal en
     la reescritura.)
  3. ¿Qué pasa si `JWT_SECRET` queda definida pero vacía (`export
     JWT_SECRET=""`)? (Spring no detecta eso como "placeholder sin
     resolver", por eso se agregó una validación explícita en
     `SecurityConfiguration` que falla el arranque si el valor está en
     blanco.)

## Unidad: inventario del código heredado (F0)

Detalle completo: `docs/estado-proyecto.md`.

- **Qué:** se recorrió la estructura completa de `iw3/src/main/java`,
  los perfiles Maven y Spring, la configuración de seguridad, los
  helpers de utilidad y los ejemplos de integración/scheduling/websocket.
- **Por qué:** antes de implementar cualquier entidad de dominio hace
  falta saber qué hay realmente en el commit clonado (no lo que se
  supone que hay), para no duplicar código ya existente ni pasar por
  alto una restricción de seguridad o de perfiles ya resuelta.
- **Qué concepto de clase usa:** arquitectura en capas
  (controlador→negocio→repositorio→entidad), perfiles Spring para separar
  entornos/integraciones, `@EntityScan`/`@EnableJpaRepositories` con
  filtros de exclusión para que `mysqldev` y `mysqlprod` no convivan con
  las entidades del cliente de integración equivocado.
- **Cómo comprobarlo:** los nombres de clases y su ubicación citados en
  `docs/estado-proyecto.md` se pueden verificar abriendo esos mismos
  archivos; no son una paráfrasis.
- **Preguntas propuestas (sin practicar):**
  1. ¿Por qué `mysqldev` y `mysqlprod` no se pueden activar juntos? (Cada
     uno excluye del escaneo de entidades al paquete de integración del
     otro cliente — activarlos juntos produciría un conflicto de
     definición de beans o un dominio incompleto.)
  2. ¿Qué hace realmente `BaseRestController`? (Solo expone el usuario
     autenticado desde el contexto de seguridad; no construye respuestas
     estándar, eso lo hacen las clases de `util/`.)
  3. ¿Por qué el paquete `business`/`model` actual (Category/Product) no
     es nuestro dominio? (Es el ejemplo didáctico del profesor; nuestro
     dominio de órdenes de carga de gas se crea en F2, siguiendo el mismo
     patrón de capas pero en paquetes propios.)

## Unidad: lectura directa de las transcripciones (T1/T2) vía NotebookLM

Detalle completo: `docs/fuentes.md`.

- **Qué:** se identificó el cuaderno NotebookLM de la materia y se leyó
  el texto completo de ambas transcripciones, no solo un resumen de
  segunda mano de una de ellas.
- **Por qué:** se asumía que la segunda transcripción no era accesible
  desde esta sesión de trabajo; resultó ser falso, y la fuente primaria
  tiene más precisión que cualquier resumen (permite distinguir cita
  literal de interpretación, y detectar el ruido propio del audio
  transcripto).
- **Cómo comprobarlo:** los IDs de fuente y notebook quedaron citados en
  `docs/fuentes.md`; cualquiera con acceso al mismo cuaderno puede
  releerlas.
- **Preguntas propuestas (sin practicar):**
  1. ¿Por qué no se decidió todavía si la densidad necesita una
     validación de rango? (El docente la describe como un dato
     "anecdótico" que se guarda tal cual, pero "anecdótico" no es lo
     mismo que "sin ninguna validación posible"; además el rango 0-1
     junto con la unidad kg/m³ requiere corroboración para el producto
     y la simulación de la consigna; no se cambió por criterio propio —
     sigue como duda pendiente para la consigna escrita o el docente.)
  2. ¿Por qué la clave de activación se genera junto con la tara y no en
     otro momento? (T1 lo dice de forma directa: "se agrega una
     contraseña de activación... en la tara"; es la transición 1→2.)
  3. ¿Por qué seguimos sin saber la fecha real de entrega 2026? (La
     transcripción menciona noviembre de 2025, que corresponde a la
     cursada anterior reutilizada como material; no hay confirmación
     docente de la fecha 2026 todavía.)

## Unidad: cierre explícito 2→3 (F4, primera unidad, TP15)

Detalle completo: `docs/contrato-f4-cierre.md`, `docs/decisiones.md`
sección "F4 — primera unidad".

- **Qué:** `POST /api/v1/orders/{orderNumber}/cierre`, sin body. Cierra
  la carga (transición 2→3) y, a partir de ese momento, cualquier
  lectura de telemetría sobre esa orden se rechaza porque deja de estar
  en estado 2 (reutiliza el chequeo ya existente de `registrarLectura`,
  no se agregó un chequeo nuevo para esto). Requiere que la orden
  exista, esté en estado 2, tenga `fechaActivacion` y al menos una
  lectura aceptada (`fechaUltimaLectura` no nula); las dos últimas son
  decisiones del equipo, no texto literal de la consigna.
  `fechaFinCarga` queda igual a la última lectura válida recibida, no al
  instante en que se llama a este endpoint.
- **Por qué:** T1 pide explícitamente "un endpoint que permita cerrar la
  orden bloqueando la recepción de nuevos datos de detalle... la orden
  pasa a estado 3" (punto 4). Las dos precondiciones agregadas (activada,
  con lecturas) evitan cerrar una carga que nunca llegó a ocurrir
  realmente, lo que dejaría a la próxima unidad (conciliación, TP16-18)
  sin ningún dato de masa cargada para calcular. Separar `fechaFinCarga`
  (valor adoptado según la última lectura aceptada) del instante
  administrativo de la llamada sigue el mismo criterio que ya se usó con
  `fechaActivacion` vs. inicio real de carga en la unidad anterior: son
  dos preguntas distintas ("¿qué valor adopta el sistema?" vs. "¿cuándo
  se registró la transición?") — ninguna de las dos mide ni prueba el
  instante físico real en que dejó de fluir producto.
- **Cómo comprobarlo:** `curl` contra la app local y
  `postman collection run` sobre
  `postman/iw3-f4-cierre.postman_collection.json` (14 requests, 16
  aserciones, 0 fallos) — ver evidencia completa en
  `docs/contrato-f4-cierre.md`. Dos carreras de concurrencia con `curl`
  en paralelo (cierre/cierre, cierre/lectura) confirmaron que el bloqueo
  pesimista serializa ambos casos sin dejar un estado inconsistente.
- **Preguntas propuestas (sin practicar):**
  1. ¿Por qué `fechaFinCarga` no es simplemente "ahora" cuando se llama
     al endpoint de cierre? (Porque la fuente describe el fin de carga
     como el momento del último registro recibido, y se adoptó ese valor
     en vez del momento en que un operador decide formalizar el cierre;
     esos dos instantes pueden diferir, y el segundo de todas formas
     queda auditado por separado en `HistorialEstado`. **Importante:**
     `fechaFinCarga` es el valor que el sistema adopta a partir de la
     última lectura aceptada, no una medición del instante físico real en
     que dejó de fluir producto — eso no está probado ni se puede probar
     con los datos disponibles.)
  2. ¿Por qué un cierre repetido no tiene un mensaje de error propio tipo
     "la orden ya está cerrada"? (Porque después del primer cierre
     exitoso la orden ya está en estado 3, así que cae directamente en
     el mismo chequeo "no está en estado 2" que cualquier otro estado
     incompatible — no hace falta un camino especial para ese caso.)
  3. ¿Cómo se sabe que una lectura concurrente con el cierre no puede
     "colarse" después de que la orden ya pasó a estado 3? (El mismo
     bloqueo `PESSIMISTIC_WRITE` sobre la fila de la orden que ya se usa
     para tara/activación/lectura: quien pierde la carrera por el lock
     relee el estado ya actualizado por quien la ganó, dentro de la misma
     transacción — se verificó con dos `curl` en paralelo, no es solo un
     argumento teórico.)

## Unidad: pesaje final y conciliación (F4, segunda unidad, TP16–TP18)

Detalle completo: `docs/contrato-f4-pesaje-conciliacion.md`,
`docs/decisiones.md` sección "F4 — segunda unidad".

- **Qué:** `POST /api/v1/orders/{orderNumber}/pesaje-final` (sin
  precondiciones propias más allá de `estado==3`: transiciona a 4,
  guarda `pesoBrutoKg`/`fechaPesajeFinal` y devuelve la conciliación en
  la misma respuesta) y `GET /api/v1/orders/{orderNumber}/conciliacion`
  (solo lectura, solo estado 4, mismo cálculo). `netoKg = pesoBrutoKg -
  taraKg`; `productoCargadoKg` es la última masa acumulada ACEPTADA
  (no el preset); `diferenciaKg = netoKg - productoCargadoKg`, sin
  rechazo ni tolerancia por su signo. `ultimaTemperaturaC`/
  `ultimaDensidad`/`ultimoCaudalKgH` son los últimos valores del
  snapshot, explícitamente no llamados "promedio".
- **Por qué:** T1 pide un servicio que, al recibir el pesaje final,
  cambie la orden a 4 y devuelva la conciliación en la misma operación
  (TP16), más un endpoint separado para consultarla después, solo en
  estado 4 (TP17). La fórmula (TP18) viene del ejemplo numérico de la
  fuente (tara/bruto/neto) más la distinción explícita entre preset y
  producto realmente cargado. Nombrar los últimos valores como tales
  (no "promedio") evita que alguien los confunda con TP19, que sigue
  diferido a F6 por decisión ya tomada en la unidad anterior.
- **Cómo comprobarlo:** `curl` contra la app local probando los tres
  signos de `diferenciaKg` (cero/positiva/negativa) contra un cálculo
  manual, y `postman collection run` sobre
  `postman/iw3-f4-pesaje-conciliacion.postman_collection.json` (19
  requests, 21 aserciones, 0 fallos). La respuesta del `POST` y la del
  `GET` inmediatamente posterior, sobre la misma orden, se compararon
  directamente y son idénticas. Una carrera de dos pesajes finales
  concurrentes con `curl` en paralelo confirmó que el mismo bloqueo
  `PESSIMISTIC_WRITE` ya usado en las unidades anteriores también
  serializa esta transición.
- **Preguntas propuestas (sin practicar):**
  1. ¿Por qué el pesaje final no vuelve a chequear activación ni
     lecturas aceptadas, si el cierre (TP15) sí lo hacía? (Porque esas
     dos condiciones ya están garantizadas por el circuito: para llegar
     a estado 3 la orden tuvo que pasar por el cierre, que las exige.
     Repetir el chequeo ahí habría sido redundante, no una protección
     adicional real.)
  2. ¿Por qué `productoCargadoKg` no es el `presetKg`? (Porque el preset
     es el objetivo de carga, no lo que realmente se cargó — T1 es
     explícito en que no deben confundirse; se usa la última masa
     acumulada aceptada del snapshot de telemetría.)
  3. ¿Qué pasa si `pesoBrutoKg` resulta menor que `taraKg` (neto
     negativo)? (No se rechaza: la instrucción fue explícita en no
     inventar tolerancias ni rechazos por el signo de la diferencia. El
     sistema calcula y devuelve el valor tal cual; qué hacer con una
     diferencia así es una decisión humana posterior, no un chequeo de
     la API.)

## Unidad: cierre de F4 — matriz de saltos/retrocesos

Detalle completo: `docs/decisiones.md`, sección "F4 — cierre".

- **Qué:** se identificaron ocho combinaciones (operación, estado
  inicial) que ninguna unidad de F2–F4 había ejercitado —
  `registrarTara`/`activar` sobre estados 3 y 4, `registrarLectura`
  sobre estados 1 y 4, `cerrarCarga` sobre estado 4 y
  `registrarPesajeFinal` sobre estado 2 — y se probaron contra cuatro
  órdenes llevadas a los estados correspondientes. Las ocho devolvieron
  `409` con el mensaje de estado incompatible esperado, sin detalle
  interno. Quedaron reproducibles en
  `postman/iw3-f4-saltos-retrocesos.postman_collection.json` (25
  requests, 26 aserciones, 0 fallos).
- **Por qué:** el criterio de salida de F4 pide "pruebas de saltos/
  retrocesos" sobre el circuito completo; hasta esta entrada solo se
  habían probado casos representativos (p. ej. tara repetida,
  activación antes de tara), no cada combinación entre los cuatro
  estados. Varias de estas combinaciones recién se volvieron
  testeables con F4, porque antes no existían órdenes en estado 3 o 4.
- **Cómo comprobarlo:** antes de agregar el caso de `registrarLectura`
  sobre estado 1, se revisó la colección de F3 para confirmar que no
  estuviera ya cubierto — la única prueba previa usaba una orden en
  estado 2 (con tara), no en estado 1 — para no duplicar un caso ya
  probado con otro nombre.
- **Preguntas propuestas (sin practicar):**
  1. ¿Por qué hizo falta una unidad aparte para esto, si cada operación
     ya tenía su propio chequeo de estado? (Porque el chequeo en sí no
     es lo que faltaba probar — el código ya lo tenía desde cada
     unidad — sino la *evidencia* de que efectivamente se comporta
     igual contra estados que antes de F4 nunca podían existir, como
     una orden ya en estado 4.)
  2. ¿Esto prueba que ninguno de los ocho rechazos modificó la base de
     datos? (No: se verificó por HTTP y por revisión de código —la
     excepción se lanza antes de cualquier `save()`—, no por una
     lectura directa de BD. Es el mismo límite que ya tenían los
     rechazos de unidades anteriores.)
  3. ¿Por qué no se exigió también probar un error `500` real o ambos
     órdenes posibles de cada carrera de concurrencia antes de cerrar
     F4? (Fue una decisión explícita: ninguno de los dos es un
     requisito confirmado del plan para este cierre: quedan como
     límites documentados, no bloqueantes.)

## F6 — primera unidad: roles y permisos (TP27)

- **Qué hace:** agrega `@PreAuthorize` a las 7 operaciones de
  `OrdenRestController`. Recepción exige `ROLE_SUPERVISOR`; las otras
  6 aceptan `ROLE_OPERADOR` o `ROLE_SUPERVISOR`.
- **Por qué:** TP27 exige roles que regulen el consumo de la API; el
  mapeo concreto (qué rol para qué operación) es decisión del equipo,
  no texto literal de la fuente.
- **Concepto de clase:** autorización declarativa con Spring Security
  (`@EnableMethodSecurity`, `@PreAuthorize`, `hasAuthority`/
  `hasAnyAuthority`), distinta de autenticación (401 sigue siendo "sin
  identidad"; 403 es "con identidad, sin permiso").
- **Cómo comprobarlo:** colección Postman
  `postman/iw3-f6-roles-permisos.postman_collection.json` con 3
  usuarios de prueba (operador, supervisor, sin rol); 33 aserciones.
  También reproducible con `curl` y JWT de cada usuario.
- **Preguntas propuestas (sin practicar):**
  1. ¿Por qué `hasAuthority('ROLE_SUPERVISOR')` y no
     `hasRole('ROLE_SUPERVISOR')`, si los controladores de ejemplo del
     profesor usan `hasRole`? (Porque `hasRole` antepone `ROLE_` a lo
     que recibe: `hasRole('ROLE_ADMIN')` exige la autoridad
     `ROLE_ROLE_ADMIN`, que ningún usuario tiene — es un bug ya
     presente en el código de ejemplo, no reproducido acá.)
  2. ¿Por qué un usuario autenticado pero sin el rol correcto
     inicialmente recibía 401 en vez de 403, y cómo se solucionó sin
     tocar `SecurityConfiguration`? (Spring Security decidía 403
     correctamente, pero el forward interno a `/error` —no público—
     volvía a pasar por la cadena de seguridad y lo convertía en 401;
     se agregó un `@ExceptionHandler(AccessDeniedException.class)`
     local al controlador, mismo patrón ya usado para el 400 de
     validación en F2.)
  3. ¿Alcanza con revisar la anotación para confirmar que
     `ROLE_SUPERVISOR` también puede hacer tara/activación/cierre/
     pesaje-final, ya que la anotación es la misma que para
     `ROLE_OPERADOR`? (No se tomó por supuesto: se ejecutó un segundo
     circuito completo —"Orden B"— enteramente con `ROLE_SUPERVISOR`
     para confirmarlo con evidencia, no solo por lectura del código.)

## F6 — segunda unidad: frecuencia configurable del histórico (TP14)

- **Qué hace:** agrega `detalleIntervaloSegundos` (por orden, opcional,
  fijado solo al recibir la orden). Sin configurar, se guarda cada
  lectura aceptada (igual que antes). Configurado, solo se guarda la
  primera lectura y luego una cada intervalo cumplido, contado desde el
  último detalle **guardado**, no desde la última lectura recibida.
- **Por qué:** TP14 exige que la frecuencia de persistencia sea
  configurable y distinta de la de recepción; el mecanismo concreto es
  decisión del equipo, no texto literal de la fuente.
- **Concepto de clase:** separar dos responsabilidades que antes
  coincidían siempre (actualizar el snapshot vs. escribir el
  histórico) — el snapshot sigue siendo incondicional, el histórico
  pasa a ser una muestra.
- **Cómo comprobarlo:** verificado por consulta SQL directa, no solo
  por la respuesta HTTP — una orden con intervalo=5s generó 2 filas de
  detalle en 3 lecturas (la intermedia, enviada antes de cumplirse el
  intervalo, no generó fila pero sí actualizó el snapshot).
- **Preguntas propuestas (sin practicar):**
  1. ¿Por qué el intervalo se mide desde el último detalle *guardado*
     y no desde la última *lectura recibida*? (Porque si se midiera
     desde la última lectura, una lectura descartada por caudal/masa
     inválida, o una que no generó detalle por no cumplir el
     intervalo, correría el ancla sin haber guardado nada — se
     perdería el control real de cuántos segundos pasaron desde el
     último dato efectivamente persistido.)
  2. ¿Qué pasa con las órdenes creadas antes de esta unidad? (Las
     columnas nuevas quedan en `NULL` porque no existían al crearse esas
     órdenes; un intervalo `null` significa "guardar cada lectura
     aceptada", así que siguen funcionando exactamente igual que antes
     — se confirmó con una lectura real sobre una de ellas, no solo
     por lectura del esquema.)
  3. ¿Por qué no se fuerza un detalle al cerrar la carga, para que el
     histórico siempre termine con la última lectura? (Es una decisión
     del equipo, no una exigencia de la fuente: la fuente ya describe
     que la mayoría de las lecturas recibidas no se guardan: forzar la
     última porque es la última no hacía falta para cumplir eso.)

## F6 — tercera unidad: promedios de temperatura, densidad y caudal (TP19)

- **Qué hace:** agrega `promedioTemperaturaC`/`promedioDensidad`/
  `promedioCaudalKgH` a la conciliación, calculados sobre el histórico
  almacenado (`DetalleCarga`) de la orden, sin reemplazar los últimos
  valores existentes.
- **Por qué:** la fuente dice explícitamente "el promedio... según
  los datos almacenados" — no es ambiguo sobre qué conjunto de
  lecturas promediar.
- **Concepto de clase:** por qué `AVG` de JPQL no sirve para esto —
  la especificación JPA define que `AVG` siempre devuelve `Double`,
  sin importar el tipo de columna de origen, lo que puede perder
  precisión en columnas `BigDecimal`; `SUM`+`COUNT`+división
  `BigDecimal` explícita evita ese problema.
- **Cómo comprobarlo:** una orden con intervalo configurado, con una
  lectura intermedia que queda fuera del histórico (actualiza el
  snapshot pero no genera detalle): el promedio resultante ignora esa
  lectura, confirmado por consulta SQL directa sobre `detalles_carga`.
- **Preguntas propuestas (sin practicar):**
  1. ¿Por qué el promedio no es simplemente el promedio de todas las
     lecturas que llegaron? (Porque la fuente pide el promedio "según
     los datos almacenados", y desde que existe frecuencia
     configurable (TP14) lo almacenado puede ser un subconjunto de lo
     recibido — promediar sobre "lo recibido" sería un criterio
     distinto al que pide la fuente.)
  2. ¿Por qué no se usó `AVG` directamente en la consulta? (Porque la
     especificación de JPA fija que `AVG` devuelve `Double` sin
     importar el tipo de la columna original, lo que puede perder
     precisión en una columna `BigDecimal`; se usó `SUM`+`COUNT` y una
     división `BigDecimal` con escala y redondeo explícitos.)
  3. ¿Qué pasa si una orden no tiene ninguna lectura guardada en el
     histórico? (Los promedios quedan en `null`, nunca en `0` — pero
     en la práctica esto no puede ocurrir para una orden que llegó a
     estado 4 por el circuito normal, porque cerrar la carga ya exige
     al menos una lectura aceptada, y la primera lectura aceptada de
     toda orden siempre genera una fila de detalle.)

## F6 — cuarta unidad: alarmas de temperatura (TP28/TP29, primera unidad)

- **Qué hace:** configuración global de umbral/destinatarios; dispara
  una alarma cuando una lectura aceptada supera el umbral, salvo que
  ya haya una pendiente para esa orden; permite consultarlas y
  aceptarlas (`ROLE_OPERADOR` o `ROLE_SUPERVISOR`), registrando
  usuario y fecha/hora.
- **Por qué:** T2 exige alarma configurable con supresión mientras
  está pendiente; el correo en sí queda para la próxima unidad.
- **Concepto de clase:** por qué el disparo y la aceptación comparten
  el mismo bloqueo de fila (`PESSIMISTIC_WRITE`) que ya usaba
  `registrarLectura` — para que una aceptación y un disparo
  concurrentes sobre la misma orden no puedan decidir "no hay
  pendiente" a la vez de forma inconsistente.
- **Cómo comprobarlo:** una orden con `detalleIntervaloSegundos`
  configurado (TP14) recibe una lectura que no genera detalle
  histórico pero sí dispara una alarma — confirma que la alarma mira
  la medición real, no el histórico muestreado.
- **Preguntas propuestas (sin practicar):**
  1. ¿Por qué la configuración de alarmas es global y no por orden,
     si la frecuencia de persistencia (TP14) sí es por orden? (Decisión
     del equipo: la fuente habla de un umbral único, en singular, sin
     mencionar nunca una orden — a diferencia de TP14, donde la fuente
     da el ejemplo explícito de "guardar cada 10 segundos" por carga.)
  2. ¿Cómo se sabe que usuario y fecha/hora son obligatorios y solo la
     observación es opcional, si la transcripción original tiene ruido
     de reconocimiento de voz? (Se consultó el fragmento directo de la
     fuente vía NotebookLM, no solo la respuesta ya interpretada: el
     orden de palabras — usuario, fecha, hora, observación "opcional" —
     deja ese calificativo pegado al último término. Es una lectura
     razonable del fragmento, no una certeza absoluta.)
  3. ¿Por qué un 404 no distingue "la alarma no existe" de "la alarma
     existe pero es de otra orden"? (Mismo criterio que otras consultas
     scoped por orden: no hace falta revelar que un id existe bajo otra
     orden para resolver el caso de uso; ambos devuelven el mismo
     mensaje genérico.)
