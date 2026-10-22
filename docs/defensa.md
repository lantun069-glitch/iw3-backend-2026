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
