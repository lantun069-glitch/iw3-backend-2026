# Matriz de requisitos

Este documento es autosuficiente: cada ID tiene su descripción, alcance,
fuente con ubicación, fase prevista, criterio de aceptación, estado,
implementación y evidencia. No depende de ningún archivo local para
entenderse.

Estados permitidos: **pendiente**, **en curso**, **implementado sin
verificar**, **verificado**, **bloqueado**, **no aplicable con
justificación**. Estado real a esta fecha (ver cada fila para el detalle
y la evidencia, esto es solo un resumen, no reemplaza leerlas): TP02 a
TP07 están **verificados** para la unidad de recepción de orden (F2);
TP08 y TP09 están **verificados** para la primera unidad de F3 (tara y
activación), revisada, aprobada y publicada en la rama
`feature/f3-tara-activacion` (la fase F3 sigue abierta, sin cierre);
TP10 a TP13 están **verificados** (TP13 puntualmente para las órdenes
920001/920003/920005, por consulta directa de BD ejecutada por el
usuario, no generalizado a escenarios no probados) para la segunda
unidad de F3 (telemetría), aprobada y publicada en la misma rama; TP14
sigue **pendiente**,
confirmado explícitamente no adelantado en esta unidad; TP15 a TP18
están **verificados**, incluida confirmación por consulta directa de
BD ejecutada por el usuario para ambas unidades, para las dos unidades
de F4 (cierre 2→3 y pesaje final 3→4 con conciliación, rama
`feature/f4-cierre-conciliacion`, **fase F4 cerrada para el alcance del
segundo parcial**); TP19 sigue **pendiente** (promedios diferidos a F6;
la alternativa admitida para el parcial —últimos valores, no
promedios— sí quedó cubierta en la conciliación); TP20 y TP21 están
**verificados** (los cinco timestamps, las cuatro transiciones del
circuito y, al cierre de F4, la matriz de saltos/retrocesos entre
estados); TP22 y TP25 están **verificados** para el runner del circuito
completo (rama `feature/f5-preparacion-parcial`), con evidencia de
Postman CLI más una ejecución guiada individual en Postman Desktop el
22/10 (ver fila TP25); TP23 y TP24 (OpenAPI y Postman) están
**verificados para el alcance del segundo parcial** (los siete
endpoints de dominio existentes al cierre de F4/F5, documentados y
coincidentes con el comportamiento real; siete colecciones sin huecos
ni secretos) — queda pendiente su **ampliación para el final** (roles,
alarmas, configuración); **F5 sigue abierta**: falta únicamente TP26
(defensa/repaso individual y ensayo del equipo); el resto de los
requisitos funcionales (TP01, TP27 en adelante) sigue **pendiente**,
porque las fases que les corresponden (F6, o el cierre del segundo
parcial) todavía no empezaron. TP36, de preparación para la defensa,
está en curso; todavía no se practicaron las preguntas.
De las filas de equipo/infraestructura (EQ), EQ01 y EQ02 están en curso
y EQ03 está verificado.

Fuentes citadas: **T1** = transcripción "Lectura de la consigna del
trabajo integrador" (texto completo leído, cuaderno de materia,
fuente `59f2c5bf-3d78-4e71-a60a-153dbecb9c27`). **T2** = transcripción
"Criterios de aprobación del trabajo integrador" (texto completo leído,
misma fuente de cuaderno, fuente `fa7832ee-0bc8-4933-8319-35b122979a28`).
Las citas son fragmentos literales entre comillas; donde no hay cita
literal disponible se marca **DATO FALTANTE** en vez de inventar una.
Detalle de literal/interpretación/duda para densidad, clave de activación
y descarte de lecturas: `docs/fuentes.md`.

## Segundo parcial y final

### TP01 — Sistema completo en la VM asignada
- Descripción: backend, persistencia, frontend e infraestructura de
  despliegue en la máquina virtual asignada al grupo.
- Alcance: final (objetivo general del proyecto).
- Fuente: T1, apertura — "Diseñar y desarrollar un sistema informático
  compuesto por un backend, un sistema de persistencia de datos, un
  front[end] y una infraestructura de despliegue que tiene que ser
  desarrollada en la máquina virtual que ha sido asignada a cada grupo."
- Fase prevista: F1 / F7 / F8.
- Criterio de aceptación: circuito accesible desde la VM, con
  persistencia que sobrevive a un reinicio del proceso/contenedor.
- Estado: pendiente. Implementación: —. Evidencia: —.

### TP02 — Datos recibidos para crear una orden
- Descripción: número de orden, camión, chofer, cliente, producto, fecha
  de carga prevista y preset.
- Alcance: segundo parcial y final.
- Fuente: T1, punto 1 — "¿Cuál es los datos recibidos que deben
  sincronizarse con nuestro backend? Un número de orden... un camión...
  datos de chofer... datos del cliente... datos del producto... fecha de
  carga prevista... el preset."
- Fase prevista: F2.
- Criterio de aceptación: un POST con esos 7 campos crea una orden en
  estado 1; falta de un campo obligatorio devuelve error.
- Estado: verificado (unidad de recepción únicamente, sin consulta ni
  actualización posterior). Implementación: `ordenes/controllers/OrdenRestController.java`,
  `ordenes/business/OrdenBusiness.java`, `ordenes/model/Orden.java`.
  Evidencia: `POST /api/v1/orders` con los 7 datos → 201, estado 1,
  verificado contra la base real en `iw3-backend-sanitized`
  (`feature/f2-recepcion-ordenes`).

### TP03 — Sincronización por código externo
- Descripción: las entidades recibidas de sistemas externos se
  identifican por un código externo tipo String; si no existen se crean,
  si existen se actualizan con los datos recibidos.
- Alcance: segundo parcial y final.
- Fuente: T1 — "representando con una cadena de texto, un string, para
  brindar más flexibilidad..."; "si no existen, las tengo que agregar y
  si existen deberían actualizar si vienen datos cambiados."
- Fase prevista: F2.
- Criterio de aceptación: mismo código externo no duplica entidad; un
  cambio de dato (ej. patente) se refleja en una segunda recepción.
- Estado: verificado. Implementación: `OrdenBusiness.sincronizar*()`.
  Evidencia: segunda orden con los mismos 4 `externalCode` reutilizó las
  mismas filas (sin duplicar) y actualizó `patente`/`capacidadKg`/
  `apellido` (obligatorios); los opcionales omitidos (`documento`,
  `contacto`, `descripcion` de producto) conservaron su valor anterior
  en vez de quedar en null — semántica documentada en
  `docs/contrato-f2-recepcion.md`.

### TP04 — Camión como unidad de carga completa (capacidadKg directo)
- Descripción vigente: camión con patente obligatoria, descripción
  opcional y `capacidadKg` como campo directo del camión. El camión
  completo **es** la unidad de carga ("cisterna" = el camión); no hay
  entidad `Cisterna`, ni compartimentos, ni una regla de cantidad de
  cisternas que validar.
- Alcance: segundo parcial y final.
- Fuente vigente: aclaración del profesor transmitida por el equipo,
  **posterior** a la lectura de T1 citada abajo — detalle completo en
  `docs/decisiones.md`, sección F2.
- **Antecedente superado (lectura original de T1, anexo, que esta
  aclaración reemplaza — se conserva la cita tal cual se leyó, sin
  editarla, para que quede trazable el cambio):** "pretendo mínimo que
  tenga un camión, una patente, una descripción... y [una lista de
  cisternas]... puede tener varias... a cada [cisterna] tengo que darle
  al menos un nombre y un número... y la capacidad... el total no se
  almacena." La palabra transcripta es "sistema"; INTERPRETACIÓN DEL
  EQUIPO en su momento: por contexto es "cisterna" (ver
  `docs/fuentes.md`). Esa lectura suponía una lista de cisternas con
  nombre/número y capacidad propia, con el total del camión calculado a
  partir de ellas; el modelo implementado ya no sigue esa lectura.
- Fase prevista: F2.
- Criterio de aceptación vigente: un `POST` con `camion.capacidadKg`
  crea o actualiza el camión con ese valor como campo propio (no hay
  cálculo de total ni colección de cisternas que sumar); una segunda
  recepción con el mismo `externalCode` y un `capacidadKg` distinto
  actualiza el valor ya guardado.
- Estado: verificado (según el modelo vigente, no el antecedente
  superado). Implementación: `ordenes/model/Camion.java`,
  `@Size(max=20)` en `patente` y `@Size(max=255)` en `descripcion` del
  DTO, alineados con las columnas. Evidencia: POST con
  `camion.capacidadKg` persistido y actualizado en una segunda recepción
  con el mismo `externalCode`.

### TP05 — Chofer, cliente y producto: atributos mínimos
- Descripción: chofer (nombre+apellido obligatorios, documento
  opcional), cliente (razón social obligatoria, contacto opcional),
  producto (nombre obligatorio, descripción opcional).
- Alcance: segundo parcial y final.
- Fuente: T1, anexo — "datos del [chofer], nombre y apellido, opcional
  el documento, datos del cliente, razón social... y un dato de contacto
  que es opcional, el producto tiene que tener mínimo un nombre y
  opcionalmente una descripción."
- Fase prevista: F2.
- Criterio de aceptación: crear con los opcionales ausentes funciona;
  falta de un obligatorio rechaza con error.
- Estado: verificado. Implementación: `ordenes/model/{Chofer,Cliente,Producto}.java`,
  validación `@NotBlank`/`@Valid` en `OrdenRecepcionRequest`. Evidencia:
  creación con opcionales ausentes → 201; falta de `camion.patente`
  (obligatorio) → 400 con el detalle del campo rechazado.

### TP06 — Número de orden entero único, distinto del ID interno
- Descripción: el número de orden es un entero único recibido
  externamente; no debe confundirse ni sustituirse por el ID autogenerado
  del ORM.
- Alcance: segundo parcial y final, con una decisión del equipo sobre el
  modelo de campos (ver abajo).
- Fuente: T1 — "el número de orden, que es un identificador único número
  entero que se lo recibíamos de forma externa." DECISIÓN DEL EQUIPO
  (no confirmada contra consigna escrita): usar campos separados
  `orderNumber` (el entero recibido) y un ID interno autogenerado,
  distintos entre sí.
- Fase prevista: F2.
- Criterio de aceptación: dos recepciones con el mismo número de orden no
  crean dos filas distintas; el ID interno nunca se expone como "número
  de orden" en la API.
- Estado: verificado, incluido el caso de colisión concurrente (no solo
  el camino secuencial). Implementación: `Orden.orderNumber` (columna
  `order_number`, restricción única con nombre explícito
  `uk_orden_order_number`), `OrdenRepository.existsByOrderNumber`.
  Evidencia: con `orderNumber` repetido → 409 sin crear fila nueva; dos
  solicitudes simultáneas con el mismo `orderNumber` nuevo → una 201 y
  otra 409, sin fila duplicada. El id interno (`id`) y `orderNumber`
  viajan como campos separados en la respuesta.

### TP07 — Creación en estado 1
- Descripción: toda orden recibida se crea en estado 1 (pendiente de
  pesaje inicial).
- Alcance: segundo parcial y final.
- Fuente: T1 — "una vez recibidos estos datos, la orden se crea, y en
  estado uno."
- Fase prevista: F2.
- Criterio de aceptación: el JSON de entrada no puede forzar otro estado
  inicial distinto de 1.
- Estado: verificado. Implementación: `OrdenRecepcionRequest` no tiene
  campo `estado`; `OrdenBusiness.recibirOrden` lo fija en 1 siempre.
  Evidencia: el campo no existe en el contrato de entrada, no hay forma
  de enviarlo.

### TP08 — Tara, timestamp y clave de activación (transición 1→2)
- Descripción: al registrar la tara (pesaje inicial) se genera una clave
  entera aleatoria de 5 dígitos asociada a la orden, y la orden pasa a
  estado 2.
- Alcance: segundo parcial y final.
- Fuente: T1, punto 2 — "se registra el pesaje del camión vacío que es
  la tara. Se agrega una contraseña de activación compuesta por un número
  entero de cinco dígitos... al azar... Al completar este paso, la orden
  pasa a estado dos." CONFIRMADO (ver `docs/fuentes.md`): la clave se
  genera en este mismo paso, no en otro momento.
- Fase prevista: F3.
- Criterio de aceptación: clave generada está en el rango [10000,99999];
  tara registrada solo si la orden está en estado 1; se persiste la
  fecha/hora del pesaje inicial; tras la operación la orden queda en estado 2.
- Estado: **verificado** (primera unidad de F3, rama
  `feature/f3-tara-activacion`, revisada y aprobada para publicar; la
  fase F3 sigue abierta — ver sección "F3 — primera unidad" en
  `docs/estado-proyecto.md`).
  Implementación: `POST /api/v1/orders/{orderNumber}/tara` —
  `OrdenRestController`/`OrdenBusiness.registrarTara`; clave con
  `SecureRandom`; fila nueva en `HistorialEstado` (`estadoNuevo=2`) con
  la misma fecha/hora que `fechaTara`; bloqueo `PESSIMISTIC_WRITE` sobre
  la orden para la concurrencia. Contrato completo en
  `docs/contrato-f3-tara-activacion.md`.
  Evidencia: HTTP real contra la app corriendo (`mysqldev`) — tara
  válida (200, clave en rango, estado 1→2), tara sobre orden inexistente
  (404), tara repetida (409), dos taras simultáneas sobre la misma orden
  vía `curl` en paralelo (una 200, otra 409; resultado compatible con la
  serialización prevista por el bloqueo, no una prueba directa de
  contención del lock). Colección Postman
  `postman/iw3-f3-tara-activacion.postman_collection.json`: última
  ejecución con `--iteration-count 2`, 34 requests, 40 aserciones, 0
  fallos (17 requests/20 aserciones por corrida, incluye los casos de
  tara). **No verificado por lectura directa de base** (sin cliente
  MySQL ni `docker exec` disponibles en esta sesión): ver límite en
  `docs/decisiones.md`, sección F3.

### TP09 — Activación: validar clave y devolver preset
- Descripción: una operación de activación valida la clave de 5 dígitos
  y, si coincide, devuelve el preset al instrumento de carga.
- Alcance: segundo parcial y final.
- Fuente: T1 — "la contraseña de activación permite habilitar el
  instrumento de carga que junto con el preset controla la bomba... si
  coincide con esa [clave] que generó nuestro sistema le va a enviar el
  preset a ese aparato."
- Fase prevista: F3.
- Criterio de aceptación: clave correcta devuelve el preset numérico;
  clave incorrecta no lo devuelve y no inicia la carga.
- DATO FALTANTE, confirmado otra vez al releer T1 para esta unidad: el
  contrato exacto de esta llamada (cómo se identifica la orden, qué pasa
  ante colisión de claves) no está definido en T1/T2. Resuelto por
  **DECISIÓN DEL EQUIPO** (no por la fuente): `orderNumber` en el path,
  clave en el body; la clave se valida contra esa orden, nunca se busca
  por clave, lo que evita necesitar unicidad global de la clave entre
  órdenes concurrentes. Detalle en `docs/contrato-f3-tara-activacion.md`
  y `docs/decisiones.md`, sección F3.
- Estado: **verificado** (misma unidad y rama que TP08). Implementación:
  `POST /api/v1/orders/{orderNumber}/activacion` —
  `OrdenRestController`/`OrdenBusiness.activar`; idempotente mientras la
  orden siga en estado 2 (clave correcta repetida devuelve el mismo
  preset y la misma `fechaActivacion`, sin regenerarla); clave incorrecta
  → 400 con mensaje fijo, sin exponer ningún valor de clave; no modifica
  `estado` ni agrega fila en `HistorialEstado` (decisión del equipo,
  justificada en `docs/decisiones.md`). No cambia el estado: no inicia
  la carga en el sentido de avanzar el ciclo de vida (eso no corresponde
  a ningún endpoint de esta unidad).
  Evidencia: activación correcta (200, preset + `fechaActivacion`),
  clave incorrecta (400), clave incorrecta después de una activación
  correcta (sigue en 400), clave fuera de rango/ausente (400 por
  validación), activación antes de registrar tara (409), activación
  sobre orden inexistente (404), ambos endpoints sin JWT (401), reintento
  con clave correcta con `fechaActivacion` idéntica (verificado tras
  corregir un bug de precisión de timestamp encontrado en esta misma
  sesión, ver `docs/decisiones.md`), y dos activaciones correctas
  simultáneas sobre la misma orden vía `curl` en paralelo (ambas 200,
  `fechaActivacion` idéntica; resultado compatible con la serialización
  prevista por el bloqueo, no una prueba directa de contención del
  lock). Mismo límite que TP08: sin lectura directa de base en esta
  sesión.

### TP10 — Telemetría solo durante estado 2
- Descripción: recepción de masa acumulada (kg), densidad, temperatura
  (°C) y caudal (kg/h), con marca de tiempo de recepción, únicamente
  mientras la orden está en estado 2.
- Alcance: segundo parcial y final.
- Fuente: T1, punto 3 (recepción de datos en tiempo real durante la
  carga).
- Fase prevista: F3.
- Criterio de aceptación: lectura aceptada en estado 2; rechazada en
  estados 1, 3 y 4.
- Estado: **verificado** (segunda unidad de F3, rama
  `feature/f3-tara-activacion`, implementada en esta sesión, sin
  commit/push/checkpoint todavía — a la espera de aprobación explícita
  para publicar, mismo protocolo que la primera unidad). Implementación:
  `POST /api/v1/orders/{orderNumber}/lecturas` —
  `OrdenRestController`/`OrdenBusiness.registrarLectura`. Precondición
  ampliada (DECISIÓN DEL EQUIPO, no cita literal): además de estado 2,
  requiere `fechaActivacion != null` — ver
  `docs/contrato-f3-telemetria.md`. Estados 3/4 todavía no son
  alcanzables por ningún endpoint implementado, así que ese caso
  puntual del criterio no se pudo ejercitar; sí se verificó el rechazo
  en estado 1 (orden con tara pero sin activar) y el rechazo explícito
  cuando falta activación. Evidencia: HTTP real contra la app
  corriendo — lectura antes de activar (409), orden inexistente (404),
  campo ausente (400), sin token (401). Colección Postman
  `postman/iw3-f3-telemetria.postman_collection.json`: última ejecución
  con `--iteration-count 2`, 34 requests, 38 aserciones, 0 fallos (17/19
  por corrida).

### TP11 — Descarte de lecturas inválidas
- Descripción: se descartan lecturas con caudal ≤ 0, masa acumulada ≤ 0,
  o masa estrictamente menor que la última masa válida.
- Alcance: segundo parcial y final.
- Fuente: T1 — "se deben descartar los registros cuyo caudal sea menor
  igual que 0... y cuando la masa acumulada sea menor igual que 0...
  tampoco tengo que almacenar una masa menor que el valor anterior."
- Fase prevista: F3.
- Criterio de aceptación: casos límite probados; una masa igual a la
  anterior **no** se descarta por esta regla (la fuente dice "menor", no
  "menor o igual", para ese caso puntual). Si la masa es positiva e igual a la última válida y el caudal es
  positivo, la igualdad no introduce una regla adicional de descarte.
  El almacenamiento del detalle sigue la frecuencia aplicable.
- Estado: **verificado** (segunda unidad de F3). Implementación:
  `OrdenBusiness.registrarLectura`, prioridad explícita de motivos
  cuando varios aplican a la vez (DECISIÓN DEL EQUIPO, orden
  `caudal_no_positivo` > `masa_no_positiva` > `masa_decreciente`, no
  derivada de la fuente): ver `docs/contrato-f3-telemetria.md`. Sin
  antecedente (primera lectura de la orden) la regla de masa decreciente
  no se evalúa (DATO FALTANTE en la fuente para ese caso, resuelto por
  decisión del equipo). Evidencia: los tres motivos probados por
  separado; masa igual a la anterior aceptada; prioridad verificada con
  caudal y masa no positivos simultáneos (gana `caudal_no_positivo`);
  carrera de dos lecturas concurrentes con masas distintas, procesadas
  bajo el mismo bloqueo que tara/activación (`PESSIMISTIC_WRITE`) —
  resultado compatible con el diseño, no una prueba de contención
  efectiva del lock (mismo criterio que las carreras de la unidad
  anterior).

### TP12 — Snapshot de últimos valores válidos
- Descripción: el sistema mantiene siempre el último valor válido de
  masa, densidad, temperatura y caudal, con su propio timestamp,
  independientemente de si esa lectura también se guarda en el detalle
  histórico.
- Alcance: segundo parcial y final.
- Fuente: T1 — "el sistema debe conservar solo el último valor válido de
  cada atributo... siempre va a tener que sobreescribir ese último valor
  en algún lado para que esté muy accesible."
- Fase prevista: F3 / F6.
- Criterio de aceptación: una lectura válida actualiza el snapshot aunque
  no genere un registro de detalle (por la frecuencia configurada).
- Estado: **verificado** (segunda unidad de F3). Implementación: campos
  directos en `Orden` (`masaAcumuladaKg`, `densidad`, `temperaturaC`,
  `caudalKgH`, `fechaUltimaLectura`), `null` hasta la primera lectura
  aceptada — verificado explícitamente que no se inicializan en cero.
  Para esta unidad cada lectura aceptada también genera detalle (TP14
  opcional no adelantada), así que el caso "actualiza snapshot sin
  generar detalle" no se pudo ejercitar todavía — eso depende de la
  frecuencia configurable, fuera de esta unidad. Evidencia: descartes
  antes del primer snapshot devuelven `null`; lecturas aceptadas
  sucesivas actualizan los cuatro valores y `fechaUltimaLectura`; una
  lectura descartada no modifica ninguno de los dos.

### TP13 — Detalle histórico ligado a la orden
- Descripción: cada registro de detalle (masa, densidad, temperatura,
  caudal, timestamp) queda asociado a una orden específica.
- Alcance: segundo parcial y final.
- Fuente: T1, anexo (mismos cuatro datos que el snapshot, pero como
  histórico).
- Fase prevista: F3.
- Criterio de aceptación: el histórico de dos órdenes distintas no se
  mezcla.
- Estado: **verificado**, puntualmente para las órdenes
  920001/920003/920005 — no generalizado a escenarios no probados.
  Implementación: `model/DetalleCarga.java` (nueva entidad: `orden`,
  los 4 valores, `fechaLectura`), una fila por lectura aceptada.
  Evidencia: consulta SQL (preparada en
  `docs/contrato-f3-telemetria.md`, columnas corregidas
  `temperaturac`/`caudal_kgh`) ejecutada directamente contra la base
  por el usuario (esta sesión no tuvo acceso a `docker exec`/cliente
  `mysql`). Resultado: 3/2/2 filas de detalle para 920001/920003/920005
  respectivamente, coincidente con la cantidad de lecturas aceptadas de
  cada una (ninguna descartada agregó fila); masas almacenadas
  coincidentes con las esperadas; `fechaInicioCarga`/`fechaUltimaLectura`
  del snapshot coincidentes con la `fecha_lectura` de la primera y la
  última fila de detalle de cada orden; ningún detalle de una orden
  apareció bajo otra.

### TP14 — Frecuencia de almacenamiento configurable
- Descripción: la frecuencia de persistencia del detalle puede
  configurarse en base de datos, distinta de la frecuencia de recepción.
- Alcance: **opcional en el segundo parcial** (se puede guardar cada
  lectura válida), **obligatoria en el final**.
- Fuente: T1 — ejemplo "si los datos se reciben cada un segundo pero se
  decide almacenar cada 10 segundos, en un minuto voy a recibir 60
  registros de los cuales solo voy a guardar 6." T2 — "esto es opcional
  por ahora... es válido almacenar todos los registros... conforme se
  reciban... esto obviamente... lo voy a pedir para el final."
- Fase prevista: F6 (o F3 si se adelanta como opcional explícito).
- Criterio de aceptación: con un intervalo configurado, una serie de
  lecturas de prueba produce la cantidad esperada de registros, sin
  afectar la frecuencia de recepción ni el refresco de pantalla.
- Estado: **verificado**. Implementación: campo `detalleIntervaloSegundos`
  (segundos, opcional, por orden, fijado solo en la recepción, validado
  `>0` si se envía) y `fechaUltimoDetalleGuardado` en `Orden`;
  `OrdenBusiness.registrarLectura()` filtra por intervalo únicamente el
  `save()` de `DetalleCarga` — el snapshot sigue actualizándose con
  toda lectura aceptada, sin condición (TP12, sin cambios). Nombre del
  campo, unidad, que se fije solo en la recepción y la política de
  primera/siguiente lectura son DECISIÓN DEL EQUIPO (detalle completo
  en `docs/contrato-f6-frecuencia-historico.md`). Evidencia: 400 ante
  `0` y negativos; orden sin intervalo sigue generando un detalle por
  lectura aceptada (3/3, confirmado por SQL); orden con intervalo = 5 s
  genera detalle en la 1ª lectura, no en la 2ª (inmediata, snapshot sí
  actualizado), sí en la 3ª (pasado el intervalo) — confirmado por SQL,
  no solo por la respuesta HTTP; las 19 órdenes previas a esta unidad
  tienen ambas columnas nuevas en `NULL` y una de ellas (preexistente,
  sin detalle previo) generó detalle normalmente ante una lectura
  nueva, confirmando que siguen con el comportamiento anterior. Una
  lectura descartada no modifica snapshot, histórico ni
  `fechaUltimoDetalleGuardado` (valores idénticos antes/después,
  confirmado por SQL); dos órdenes con intervalos distintos (3 s y 8 s)
  con lecturas en los mismos instantes mantuvieron decisiones de
  persistencia independientes (3/3 y 2/3 filas de detalle
  respectivamente, confirmado por SQL). Colección Postman
  `postman/iw3-f6-frecuencia-historico.postman_collection.json` (9
  requests: intervalo omitido/0/-5/válido en la recepción, más tara/
  activación/lectura aceptada/lectura descartada sobre la orden con
  intervalo válido) ejecutada una vez vía Postman CLI: 9/9 requests,
  15/15 aserciones, 0 fallos — aserciones limitadas a lo observable por
  HTTP; la persistencia del detalle histórico sigue verificada por las
  consultas SQL de arriba, no repetidas por esta colección. Consultas
  SQL puntuales registradas como texto reproducible en
  `docs/contrato-f6-frecuencia-historico.md`.
  **TP14 verificado para el alcance implementado en esta unidad.**
  Pendiente explícito: TP19 (promedios) todavía no implementado: el
  histórico de una orden con intervalo configurado queda muestreado, no
  contiene todas las lecturas aceptadas — a tener en cuenta cuando se
  implemente ese promedio. TP28/TP29 (alarmas) tampoco implementados;
  siguen pendientes en F6.
- Estado: pendiente. **Confirmado explícitamente no adelantado en la
  segunda unidad de F3** (TP10-TP13): esa unidad guarda cada lectura
  aceptada en `DetalleCarga`, sin frecuencia configurable ni
  deduplicación, por decisión explícita de mantener esto fuera de
  alcance (ver `docs/contrato-f3-telemetria.md`). **Al cierre de F3**
  (ver `docs/estado-proyecto.md`, "Cierre de F3"): queda diferido
  explícitamente para el final, conforme a su fase prevista (F6); no
  se da por cumplido ni se elimina de la matriz. Implementación: —.
  Evidencia: —.

### TP15 — Cierre explícito 2→3
- Descripción: un endpoint explícito cierra la orden, bloqueando nuevas
  lecturas de detalle.
- Alcance: segundo parcial y final.
- Fuente: T1, punto 4 — "debe implementarse un endpoint que permita
  cerrar la orden bloqueando la recepción de nuevos datos de detalle...
  la orden pasa a estado 3."
- Fase prevista: F4.
- Criterio de aceptación: una lectura enviada después del cierre no
  modifica snapshot ni detalle, y devuelve un error explícito.
- Estado: **verificado**, para el alcance de esta unidad (primera de
  F4). Implementación: `POST /api/v1/orders/{orderNumber}/cierre`
  (`OrdenRestController.cerrar`, `OrdenBusiness.cerrarCarga`), sin body.
  Precondiciones: orden existente (404 si no), en estado 2 (409 si no,
  cubre también el cierre repetido), activada (409 si no) y con al
  menos una lectura aceptada (409 si no) — las dos últimas son
  **DECISIÓN DEL EQUIPO**, no exigidas textualmente por T1 para este
  endpoint (ver `docs/contrato-f4-cierre.md`). `fechaFinCarga` se fija
  igual a `fechaUltimaLectura`, no al instante de la llamada: es el
  valor que el sistema **adopta** como fin de carga a partir de la
  última lectura aceptada, **no una medición del instante físico real**
  en que dejó de fluir producto (puede haber una brecha no medida entre
  ambos). El instante real de la llamada queda auditado por separado,
  como `fechaHora` del `HistorialEstado` de la transición a 3
  (`estadoNuevo=3`, actor autenticado). Mismo
  bloqueo pesimista (`findByOrderNumberForUpdate`) y transacción con
  rollback que tara/activación/lectura. Evidencia: probado con `curl`
  contra la app local (perfil `mysqldev`, MySQL ya levantado) recorriendo
  el camino feliz y los cuatro rechazos (404, 409×3), más lectura
  posterior al cierre (409, sin cambios en snapshot/detalle) y dos
  carreras de concurrencia (`curl` en paralelo: cierre/cierre → una
  `200`/una `409`; cierre/lectura → en la corrida observada el cierre
  ganó, la lectura concurrente fue rechazada). Confirmado también con
  `postman collection run` sobre
  `postman/iw3-f4-cierre.postman_collection.json`: 14 requests, 16
  aserciones, 0 fallos (no ejercita las carreras concurrentes, mismo
  límite que las colecciones de F3). `GET /v3/api-docs` confirma el path
  nuevo y el esquema `CierreResponse`. **Confirmado además por consulta
  directa de BD, ejecutada por el usuario** (sin acceso de esta sesión a
  cliente MySQL ni `docker exec`): para las órdenes 940556/950632/960632
  (las tres cerradas en esta sesión), una sola fila de `HistorialEstado`
  con `estado_nuevo=3` por orden (una única transición a 3) y una sola
  fila de `detalles_carga` de 4000 kg por orden, con `fechaFinCarga`
  coincidente con la `fecha_lectura` de esa fila en las tres — **acota
  qué quedó verificado: confirma que `fechaFinCarga` toma el valor de la
  última lectura aceptada, no que ese valor coincida con el instante
  físico real de fin de flujo** (ver corrección de precisión arriba).
  Esta consulta detectó además un error de nomenclatura de columna en
  `docs/contrato-f4-cierre.md` (`usuario_o_proceso` escrito donde la
  columna real es `usuariooproceso`, mismo hallazgo ya registrado desde
  F2), ya corregido ahí. Detalle completo en
  `docs/contrato-f4-cierre.md`.

### TP16 — Pesaje final 3→4 con conciliación en la misma respuesta
- Descripción: un servicio recibe el pesaje final, cambia la orden a
  estado 4 y devuelve la conciliación en esa misma operación.
- Alcance: segundo parcial y final.
- Fuente: T1, punto 5 — "una vez que se recibe ese pesaje final, ese
  mismo servicio va a cambiar la orden a estado número 4... además debe
  devolver una conciliación con los valores calculados."
- Fase prevista: F4.
- Criterio de aceptación: la respuesta de esa operación incluye la
  conciliación; se rechaza si la orden no está en estado 3.
- Estado: **verificado**, para el alcance de esta unidad (segunda de
  F4). Implementación: `POST /api/v1/orders/{orderNumber}/pesaje-final`
  (`OrdenRestController.pesajeFinal`, `OrdenBusiness.registrarPesajeFinal`).
  Precondiciones: orden existente (404 si no), en estado 3 (409 si no,
  cubre también el pesaje final repetido). Guarda `pesoBrutoKg` y
  `fechaPesajeFinal` (instante real de la llamada, no derivado de otra
  fecha), transiciona a 4 y audita en `HistorialEstado`
  (`estadoNuevo=4`, actor autenticado). Mismo bloqueo pesimista
  (`findByOrderNumberForUpdate`) y transacción con rollback que
  tara/activación/lectura/cierre. Devuelve la conciliación calculada
  (ver TP18) en la misma respuesta. Evidencia: probado con `curl`
  contra la app local recorriendo diferencia cero/positiva/negativa,
  los rechazos (404, 409 en estado 1, 409 repetido), validación 400
  (ausente/no positivo/4 decimales) y una carrera de dos pesajes
  finales concurrentes (`curl` en paralelo: uno `200`, otro `409`,
  confirmado con una conciliación posterior). Confirmado también con
  `postman collection run` sobre
  `postman/iw3-f4-pesaje-conciliacion.postman_collection.json`: 19
  requests, 21 aserciones, 0 fallos. `GET /v3/api-docs` confirma el
  path nuevo. **Confirmado además por consulta directa de BD, ejecutada
  por el usuario** (sin acceso de esta sesión a cliente MySQL ni
  `docker exec`): para las seis órdenes de esta unidad
  (1792624398/399/400/401/402/403), `peso_bruto_kg`/`fecha_pesaje_final`
  no nulos y `estado=4` en las cinco que llegaron a pesaje final, con
  los valores de bruto/neto/diferencia exactamente iguales a los
  calculados por HTTP (ver TP18); la orden 1792624401 (nunca llegó a
  pesaje final) muestra `estado=1` y esos campos en `NULL`, como se
  esperaba. Detalle completo, con la tabla reportada por el usuario, en
  `docs/contrato-f4-pesaje-conciliacion.md`.

### TP17 — Consulta de conciliación, solo en estado 4
- Descripción: endpoint separado para consultar la conciliación en
  cualquier momento posterior, pero solo para órdenes en estado 4.
- Alcance: segundo parcial y final.
- Fuente: T1 — "la conciliación podrá solicitarse en cualquier momento,
  pero solo para órdenes en estado 4 a través de un endpoint
  específico."
- Fase prevista: F4.
- Criterio de aceptación: disponible repetidas veces en estado 4;
  rechazada en estados 1, 2 y 3.
- Estado: **verificado**. Implementación:
  `GET /api/v1/orders/{orderNumber}/conciliacion`
  (`OrdenRestController.conciliacion`, `OrdenBusiness.consultarConciliacion`),
  sin escrituras ni bloqueo pesimista (lectura simple con
  `OrdenRepository.findByOrderNumber`, sin `@Lock`): no participa de
  ninguna transición de estado. Requiere estado 4 (409 en cualquier
  otro, 404 si no existe). Evidencia: probado con `curl` rechazando en
  estados 1/2/3 y aceptando repetidas veces en estado 4 con el mismo
  resultado; comparación directa con la respuesta del `POST` de pesaje
  final sobre la misma orden — **idénticas**. Detalle completo en
  `docs/contrato-f4-pesaje-conciliacion.md`.

### TP18 — Cálculo de la conciliación
- Descripción: tara, bruto (pesaje final), neto = bruto − tara, producto
  cargado = última masa acumulada, diferencia = neto − producto cargado.
- Alcance: segundo parcial y final.
- Fuente: T1 — ejemplo numérico con tara 40.000 y pesaje final 50.000 →
  neto 10.000; "esa diferencia puede existir, y ahí la decisión... de qué
  se hace con esa diferencia no es un problema nuestro sino una decisión
  humana."
- Fase prevista: F4.
- Criterio de aceptación: comparación contra un cálculo manual; una
  diferencia distinta de 0 **no** causa rechazo automático del sistema.
- Estado: **verificado**. Implementación:
  `OrdenBusiness.calcularConciliacion` (método privado compartido por
  `registrarPesajeFinal` y `consultarConciliacion`, para que ambos
  devuelvan exactamente el mismo cálculo): `netoKg = pesoBrutoKg -
  taraKg`; `productoCargadoKg` = última masa acumulada **ACEPTADA**
  del snapshot (`Orden.masaAcumuladaKg`, no el `presetKg`);
  `diferenciaKg = netoKg - productoCargadoKg`, sin tolerancia ni
  redondeo a porcentaje. Todo con `BigDecimal`, sin `setScale`
  explícito (sin redondeo silencioso). Evidencia: verificado con los
  tres signos de `diferenciaKg` contra un cálculo manual — tara 12000/
  bruto 16000/masa 4000 → diferencia `0.000`; tara 12000/bruto 16080/
  masa 4000 → `80.000`; tara 12000/bruto 15900/masa 4000 → `-100.000`;
  ninguno de los tres fue rechazado por el sistema. **Confirmado además
  por consulta directa de BD, ejecutada por el usuario**, contra los
  valores persistidos (no solo la respuesta HTTP) para los seis
  orderNumber de esta unidad — misma tabla que TP16, incluida una
  cuarta diferencia (`+500.000`, orden 1792624403, ganadora de la
  carrera de pesaje final). Detalle completo en
  `docs/contrato-f4-pesaje-conciliacion.md`.

### TP19 — Promedios de temperatura, densidad y caudal
- Descripción: promedio de cada variable sobre el detalle almacenado,
  incluido en la conciliación.
- Alcance: **opcional en el segundo parcial** (se admite el último valor
  recibido en su lugar), **obligatoria en el final**.
- Fuente: T1 — "el promedio de temperatura, según los datos
  almacenados... el promedio de la densidad y el promedio de caudal." T2
  — "también es opcional el cálculo de valores medios... podrá
  almacenarse simplemente el último valor recibido... aunque esto sí lo
  voy a requerir para el examen final."
- Fase prevista: F6 (o F4 si se adelanta como opcional explícito).
- Criterio de aceptación: promedio calculado contra un conjunto de
  detalle conocido; si no hay muestras almacenadas, la ausencia de
  promedio se documenta explícitamente, no se devuelve 0 como si fuera un
  promedio real.
- Estado: **verificado**. La fuente ya resuelve explícitamente qué
  lecturas integran el promedio: "según los datos almacenados" (T1) —
  es el histórico almacenado (`DetalleCarga`), no todas las lecturas
  aceptadas recibidas, que desde TP14 puede ser un conjunto más grande
  si la orden configuró `detalleIntervaloSegundos` (histórico
  muestreado). Implementación: `EstadisticasDetalle` (nuevo) +
  `DetalleCargaRepository.calcularEstadisticas` (`SUM`+`COUNT`, no
  `AVG`, para no perder precisión por la conversión a `Double` que
  especifica JPA para `AVG`); división `BigDecimal` en
  `OrdenBusiness.calcularConciliacion` con escala fija por campo
  (temperatura 2 decimales, densidad 4, caudal 3) y
  `RoundingMode.HALF_UP`, redondeando solo el resultado final;
  `null` si no hay ninguna fila de detalle (nunca `0`). Promedio
  aritmético simple, no ponderado por tiempo, y promedios agregados
  sin reemplazar los últimos valores existentes: DECISIÓN DEL EQUIPO
  (detalle completo en `docs/contrato-f6-promedios-historico.md`).
  Evidencia: los cuatro casos acordados, cubiertos. (1) Orden sin
  intervalo (histórico == todas las aceptadas): promedio =
  15.00/0.5500/1500.000, último valor = 20.00/0.6000/2000.000
  (distinto). (2) Orden con intervalo=5s (histórico muestreado, 3
  lecturas aceptadas, 2 guardadas): promedio = 17.83/0.5228/3000.000
  (los dos primeros requirieron redondeo HALF_UP), último valor =
  15.55 (distinto), y si el promedio incluyera la lectura excluida
  sería ≈45.21 (muy distinto). (3) Mismo promedio en la respuesta de
  pesaje-final y en el GET posterior, para ambas órdenes. (4)
  Verificación por SQL directo (no solo por HTTP): `SUM`/`COUNT` sobre
  `detalles_carga` confirma 2 filas en cada orden, y para la orden con
  intervalo confirma que la lectura excluida (99.99) no está entre
  ellas. Protección adicional, no uno de los cuatro casos y no
  ejercitada: sin ninguna fila de detalle (`COUNT=0`) los promedios
  quedan en `null`, no probado porque es inalcanzable por el circuito
  normal (`cerrarCarga` exige al menos una lectura aceptada, y la
  primera lectura aceptada de toda orden siempre genera detalle).
  Colección Postman acotada
  `postman/iw3-f6-promedios-historico.postman_collection.json` (17
  requests, 2 circuitos completos) corrida una vez: 17/17 requests,
  27/27 aserciones, 0 fallos — limitada a lo observable por HTTP; el
  caso 4 (SQL) se verificó por separado, no repetido por la colección.
  La alternativa admitida para el parcial (último valor en vez de
  promedio) sigue cubierta desde F4: ver
  `docs/contrato-f4-pesaje-conciliacion.md`.

### TP20 — Timestamps de cada evento del ciclo
- Descripción: fecha/hora de recepción inicial, de pesaje inicial, de
  inicio de carga, de fin de carga, de pesaje final.
- Alcance: segundo parcial y final.
- Fuente: T1, anexo — "la fecha y hora de recepción inicial... la fecha
  y hora en que se recibe el pesaje inicial... la fecha y hora de el
  inicio... la fecha y hora de fin de carga... la fecha y hora del
  pesaje final."
- Fase prevista: F2–F4.
- Criterio de aceptación: cada evento queda persistido con su propio
  timestamp; documentar qué evento exacto dispara cada marca (ej.: no
  igualar automáticamente "tara" con "inicio real de carga", porque T1
  los describe como pasos distintos).
- Estado: **verificado** — los cinco eventos de la fuente están
  cubiertos: `fechaRecepcion` (recepción/creación), `fechaTara` (pesaje
  inicial), `fechaActivacion`, `fechaInicioCarga` (primera lectura
  aceptada, ver TP10-TP13), `fechaFinCarga` (cierre explícito 2→3,
  TP15) y, desde esta unidad, `fechaPesajeFinal` (pesaje final 3→4,
  TP16). `fechaFinCarga` se fija igual a `fechaUltimaLectura` (última
  lectura VÁLIDA, el valor que el sistema adopta, no una medición del
  instante físico real — ver `docs/contrato-f4-cierre.md`), mientras
  que `fechaPesajeFinal` sí es el instante real de la llamada al
  endpoint de pesaje final (no derivado de ninguna otra fecha) —
  distinción explícita entre ambos campos, ver
  `docs/contrato-f4-pesaje-conciliacion.md`.
  Implementación: `Orden.fechaRecepcion`/`fechaTara`/`fechaActivacion`/
  `fechaInicioCarga`/`fechaFinCarga`/`fechaPesajeFinal`, generadas con
  zona explícita `America/Argentina/Cordoba` (ver `docs/decisiones.md`).
  Evidencia: timestamp persistido y devuelto en cada operación
  (recepción, tara, activación, lectura, cierre, pesaje final).

### TP21 — Transiciones de estado unidireccionales y auditadas
- Descripción: los estados avanzan 1→2→3→4 sin saltos ni retrocesos; cada
  transición queda registrada.
- Alcance: segundo parcial y final.
- Fuente: la secuencia 1→2→3→4 sin saltos está descripta en T1 de forma
  consistente en los 5 puntos del proceso. **CONFIRMADO (corrección: ya
  no es DATO FALTANTE)** — se leyó el tramo final de T1, no leído en la
  sesión anterior (fuente `59f2c5bf-3d78-4e71-a60a-153dbecb9c27`, aprox.
  carácter 19.700-20.300 de 21.713): *"cada cambio de estado debe quedar
  registrado en la base de datos, incluyendo fecha y hora del cambio,
  usuario o proceso que realizó la transacción, observaciones se aplica
  [sic, probable ruido de ASR por "observación si aplica"], se
  recomienda implementar un log de auditoría de estado para la
  trazabilidad completa del ciclo de vida de la orden."* La
  recomendación de un log dedicado no es la única estructura permitida;
  el requisito en sí (fecha/hora + usuario o proceso + observación si
  aplica) sí está confirmado textualmente.
- Fase prevista: F2–F4.
- Criterio de aceptación: un intento de salto o retroceso de estado se
  rechaza; se puede consultar la secuencia de transiciones de una orden.
- Estado: **verificado** — las cuatro transiciones del circuito
  (ninguno→1, 1→2, 2→3 y, desde esta unidad, 3→4, TP16) están
  implementadas y auditadas, incluyendo el rechazo de repetición en
  cada una (409). La activación **no** agrega fila en `HistorialEstado`
  (decisión del equipo: no cambia `estado`, ver `docs/decisiones.md`,
  sección F3). Implementación: `ordenes/model/HistorialEstado.java`
  (no es la única estructura posible, es la decisión del equipo para
  implementarlo). Evidencia: cada recepción válida genera una fila con
  `estadoNuevo=1`, cada tara válida con `estadoNuevo=2`, cada cierre
  válido con `estadoNuevo=3`, cada pesaje final válido con
  `estadoNuevo=4`; `usuarioOProceso` = usuario autenticado (nunca del
  JSON) en los cuatro casos. **Verificado por lectura directa de BD**
  para `estadoNuevo=1`/`2`/`3` (órdenes 940556/950632/960632, incluido
  `usuarioOProceso=qa_f1_dfab890c` en los tres estados) **y para
  `estadoNuevo=4`** (órdenes 1792624398/399/400/402/403: exactamente una
  fila por orden, sin duplicados, mismo actor y `fecha_hora` idéntica a
  `fechaPesajeFinal`; 1792624401, en estado 1, no generó ninguna fila
  con `estadoNuevo=4`). Detalle completo en
  `docs/contrato-f4-pesaje-conciliacion.md`.

  **Matriz de saltos/retrocesos — completada en el cierre de F4:** las
  ocho combinaciones (operación, estado inicial incompatible) que
  quedaban sin ejercitar tras las dos unidades de F4 —
  `registrarTara`/`activar` sobre estados 3 y 4, `registrarLectura`
  sobre estados 1 y 4, `cerrarCarga` sobre estado 4 y
  `registrarPesajeFinal` sobre estado 2 — se probaron con `curl` y
  quedaron reproducibles en
  `postman/iw3-f4-saltos-retrocesos.postman_collection.json` (25
  requests, 26 aserciones, 0 fallos): las ocho respondieron `409` con
  el mensaje de estado incompatible esperado (estado actual correcto en
  cada mensaje) y `devInfo: "No stack trace"`, sin detalle interno.
  **Límite conservado:** estos ocho rechazos se verificaron por HTTP y
  por revisión de código (la excepción de estado se lanza antes de
  cualquier `save()`, dentro de la misma transacción); **no** se
  verificaron por lectura directa de BD. Detalle completo en
  `docs/decisiones.md`, sección "F4 — cierre".

### TP22 — Sin interfaz gráfica para el segundo parcial
- Descripción: los puntos 1 a 5 se entregan como API, sin ninguna
  pantalla propia, para la presentación del segundo parcial.
- Alcance: segundo parcial (exclusión explícita; la interfaz sí se exige
  en el final, ver TP30–TP35).
- Fuente: T2 — "Deberán implementarse sin interfaz gráficas... yo no
  quiero interfaz gráficas para el segundo parcial, todo va a [ser API]."
- Fase prevista: F5.
- Criterio de aceptación: demostración completa de los 5 puntos ejecutada
  desde Postman, sin pantallas propias.
- Estado: **verificado**, para la primera unidad de F5 (runner del
  circuito completo). Implementación:
  `postman/iw3-f5-runner-circuito.postman_collection.json` (9 requests
  encadenados automáticamente por orden: login → recepción → tara →
  activación → lectura×2 → cierre → pesaje final → conciliación, sin
  intervención manual entre pasos ni pantallas propias). Evidencia:
  `postman collection run --iteration-count 2` — **18 requests, 68
  aserciones, 0 fallos** en las dos iteraciones, cada una sobre una
  orden distinta con datos y resultado de conciliación distintos
  (diferencia esperada `+80.000` y `-100.000` respectivamente, ambas
  verificadas contra el valor exacto esperado, no solo contra "algún
  número"). Corte ante fallo comprobado dos veces: credenciales de
  login inválidas (detiene el resto de cada iteración tras el primer
  paso, 8 pasos omitidos por iteración) y `taraKg` inválido a mitad de
  camino (detiene tras el tercer paso, 5 pasos omitidos por iteración)
  — confirma que un paso roto no produce fallos en cascada en los
  pasos posteriores que dependen de sus datos. Detalle completo en
  `docs/contrato-f5-runner.md`. **Límite conservado: esta verificación
  es únicamente sobre la respuesta HTTP — no confirma por lectura
  directa de BD que lo persistido coincida con lo devuelto** (ese tipo
  de confirmación ya se hizo, unidad por unidad, en F3/F4; este runner
  no repite esa verificación).

### TP23 — Documentación OpenAPI/Swagger
- Descripción: documentación técnica de la API con OpenAPI/Swagger.
- Alcance: obligatoria en el segundo parcial; mantenerla actualizada en
  el final.
- Fuente: T2 — "la documentación técnica deberá realizarse utilizando...
  Swagger."
- Fase prevista: F2–F5.
- Criterio de aceptación: operaciones, esquemas, unidades y errores
  documentados coinciden con el comportamiento real de la API.
- Estado: **verificado para el alcance del segundo parcial** (los
  siete endpoints de dominio existentes al cierre de F4/F5 —
  recepción, tara, activación, lecturas, cierre, pesaje final y
  conciliación — están documentados y coinciden con el comportamiento
  real). Pendiente de ampliar en el final con lo que agreguen roles,
  alarmas y configuración (F6) — no se declara cumplido ese alcance.
  Implementación: `ordenes/controllers/OrdenRestController.java`
  (`@Operation`, `@ApiResponses` con ejemplos): recepción con
  201/400/409/500, tara con 200/400/404/409/500, activación con
  200/400 (clave incorrecta y clave fuera de rango)/404/409/500,
  lecturas con 200 (aceptada y descartada)/400/404/409 (dos variantes:
  estado incompatible y sin activar)/500;
  `@io.swagger.v3.oas.annotations.parameters.RequestBody` con ejemplo de
  entrada en los **siete** endpoints con body (corregido en esta
  revisión: hasta ahora solo `recibir()` tenía ejemplo de request body;
  se agregó el mismo patrón a `tara`/`activacion`/`lecturas`/
  `pesaje-final`, sin cambios de lógica ni DTOs — verificado contra
  `GET /v3/api-docs` con la app corriendo: `{"taraKg":12000.000}`,
  `{"clave":54231}`,
  `{"masaAcumuladaKg":4000.000,"densidad":0.5400,"temperaturaC":18.50,"caudalKgH":3000.000}`
  y `{"pesoBrutoKg":16080.000}` respectivamente, sin afectar el ejemplo
  ya existente de `recibir()`). Evidencia: `GET /v3/api-docs` verificado
  contra la app corriendo — `paths./api/v1/orders.post` con summary,
  description, ejemplo de request y las cinco respuestas de recepción
  (201 con ejemplo; 400 con dos ejemplos — campo obligatorio ausente y
  JSON malformado —; 401 sin cuerpo; 409 y 500 con ejemplo), todas con
  el esquema real (`StandartResponse`/`OrdenRecepcionResponse`); los
  paths `paths./api/v1/orders/{orderNumber}/tara.post`,
  `paths./api/v1/orders/{orderNumber}/activacion.post` y
  `paths./api/v1/orders/{orderNumber}/lecturas.post` presentes con sus
  respuestas documentadas (`TaraResponse`/`ActivacionResponse`/
  `LecturaResponse` para el éxito). La obligatoriedad y los límites de
  longitud/rango/precisión de los campos de los DTO
  (`OrdenRecepcionRequest` y sus 4 DTO anidados, `TaraRequest`,
  `ActivacionRequest`, `LecturaRequest`) ya aparecían correctamente en
  `components.schemas` sin anotación adicional, porque springdoc deriva
  `required`/`maxLength`/`minimum`/`maximum`/`multipleOf` directamente
  de `@NotNull`/`@NotBlank`/`@Size`/`@Min`/`@Max`/`@Digits` — se
  verificó esto contra el JSON real antes de agregar cualquier
  anotación extra, para no duplicar algo que ya funcionaba.
  `config/OpenApiConfig.java` (base docente) provee el esquema de
  seguridad Bearer JWT a nivel global, ya aplicado (`security` en la
  raíz del documento, verificado).

  **Ampliación con los endpoints de F4:** `paths./api/v1/orders/{orderNumber}/cierre.post`,
  `.../pesaje-final.post` y `.../conciliacion.get` presentes, cada uno
  con `@Operation`/`@ApiResponses` y ejemplos reales, verificados
  contra `GET /v3/api-docs` con la app corriendo en las dos unidades de
  F4 (ver `docs/contrato-f4-cierre.md` y
  `docs/contrato-f4-pesaje-conciliacion.md`). Los esquemas
  `CierreResponse`, `PesajeFinalRequest` y `ConciliacionResponse`
  aparecen en `components.schemas`.

### TP24 — Colección Postman
- Descripción: colección Postman que prueba y valida las APIs
  desarrolladas.
- Alcance: obligatoria en el segundo parcial; ampliar en el final.
- Fuente: T2 — "una colección [Postman] que permita probar y validar las
  [APIs] desarrolladas."
- Fase prevista: F2–F5.
- Criterio de aceptación: la colección se importa sin errores, usa
  variables documentadas (no credenciales fijas) y cada request es
  ejecutable contra el entorno local.
- Estado: **verificado para el alcance del segundo parcial** (las 7
  colecciones cubren exactamente los 7 endpoints de dominio existentes
  —recepción, tara, activación, lecturas, cierre, pesaje final y
  conciliación—, sin huecos; revisado en esta entrada: `testUsername`/
  `testPassword`/`jwtToken` vacíos por defecto en las 7, sin secretos ni
  tokens reales encontrados en `postman/` por `grep`). Queda "ampliar
  en el final" para los requisitos exclusivos de esa entrega — roles,
  alarmas, UI. Implementación:
  `postman/iw3-f2-recepcion.postman_collection.json` (6 requests: login,
  recepción válida, segunda orden reutilizando referencias existentes,
  duplicado de `orderNumber`, entrada inválida y solicitud sin token),
  `postman/iw3-f3-tara-activacion.postman_collection.json` (17 requests:
  login, dos órdenes de apoyo, activación antes de tara, tara sobre
  orden inexistente, tara válida, tara repetida, clave incorrecta, clave
  fuera de rango, clave ausente, activación sobre orden inexistente,
  activación válida —con aserciones de `orderNumber` y `presetKg`,
  además de `fechaActivacion`—, reintento idempotente —mismas
  aserciones—, clave incorrecta después de activar, ambos endpoints sin
  token, y una tara final sobre la orden de apoyo que confirma que
  seguía en estado 1 tras los rechazos) y
  `postman/iw3-f3-telemetria.postman_collection.json` (17 requests:
  login, dos órdenes de apoyo (una activada, otra con tara sin activar),
  lectura antes de activar, dos descartes antes del primer snapshot con
  aserciones de campos `null`, primera lectura aceptada con aserción de
  `fechaInicioCarga`, segunda aceptada que la conserva, masa igual
  aceptada, masa decreciente descartada con snapshot sin cambios,
  prioridad de motivos, orden inexistente, campo ausente y sin token),
  las tres con `postman/iw3-local.postman_environment.example.json`
  (variables sin valores reales). Variables de colección para
  URL/credenciales (`baseUrl`, `testUsername`, `testPassword`, sin
  valores por defecto sensibles) y datos de prueba generados en tiempo
  de ejecución para no chocar con ejecuciones anteriores: F2 usa un
  sufijo de timestamp en segundos; ambas colecciones de F3 regeneran sus
  `orderNumber`/`extSuffix` y limpian `jwtToken`/claves temporales al
  inicio de cada corrida completa (en el pre-request del primer
  request, sin depender de ningún flag persistente), con un valor en
  milisegundos acotado para no exceder el rango de `Integer` —
  necesario porque dos corridas completas lanzadas una detrás de otra en
  el mismo proceso pueden caer en el mismo segundo. Evidencia: ejecución
  real con `postman collection run` (sin `postman login`, sin
  `--output`, para no requerir ni dejar rastro de una cuenta externa)
  pasando credenciales solo por `--env-var` (nunca escritas en un
  archivo versionado). F2: 6/6 requests, 12/12 aserciones, 0 fallos. F3
  (tara/activación): 17/17 requests, 20/20 aserciones, 0 fallos por
  corrida; verificado además con `--iteration-count 2`: 34/34 requests,
  40/40 aserciones, 0 fallos. F3 (telemetría): 17/17 requests, 19/19
  aserciones, 0 fallos por corrida; verificado con `--iteration-count
  2`: 34/34 requests, 38/38 aserciones, 0 fallos. En ambas colecciones
  de F3, `orderNumber` distintos entre las dos corridas de cada
  `--iteration-count 2` (confirma que la reinicialización no depende de
  un flag persistente ni reutiliza datos de una corrida anterior). Las
  aserciones verifican únicamente lo que la respuesta HTTP expone
  (status y cuerpo JSON); no afirman cambios internos de base de datos
  que la API no permite observar, ni contención efectiva de un lock a
  partir de lanzar procesos en paralelo — ver `docs/decisiones.md`,
  secciones F2 y F3.

  **Ampliación con los endpoints de F4 (cierre, pesaje final/
  conciliación y matriz de saltos/retrocesos):**
  `postman/iw3-f4-cierre.postman_collection.json` (14 requests, 16
  aserciones, 0 fallos — TP15),
  `postman/iw3-f4-pesaje-conciliacion.postman_collection.json` (19
  requests, 21 aserciones, 0 fallos — TP16-TP18) y
  `postman/iw3-f4-saltos-retrocesos.postman_collection.json` (nueva en
  esta entrada: 25 requests, 26 aserciones, 0 fallos — ocho
  combinaciones operación/estado-inicial-incompatible que quedaban sin
  ejercitar: `registrarTara`/`activar` sobre estados 3 y 4,
  `registrarLectura` sobre estados 1 y 4, `cerrarCarga` sobre estado 4
  y `registrarPesajeFinal` sobre estado 2; todas rechazadas con `409` y
  el mensaje de estado incompatible esperado, sin `devInfo` con detalle
  interno). Detalle completo de cada colección en
  `docs/contrato-f4-cierre.md`, `docs/contrato-f4-pesaje-conciliacion.md`
  y `docs/decisiones.md`, sección "F4 — cierre". Mismo límite que las
  colecciones anteriores: solo verifican la respuesta HTTP, no
  confirman por lectura directa de BD la ausencia de cambios en los
  ocho rechazos nuevos ni la contención efectiva de las carreras ya
  documentadas (alcance acotado a un solo orden de llegada observado
  por carrera).

### TP25 — Simulación automatizada del circuito
- Descripción: runner/tests de Postman que simulan el circuito completo
  de una orden, de punta a punta.
- Alcance: opcional, recomendada explícitamente por el docente en el
  segundo parcial.
- Fuente: T2 — "una simulación del circuito completo... opcional, pero es
  muy [valiosa], de un valor agregado muy grande... se recomienda
  investigar la funcionalidad de test de [Postman]... les he agregado un
  video." DATO FALTANTE: el contenido de ese video no es accesible desde
  esta sesión.
- Fase prevista: F5.
- Criterio de aceptación: el runner encadena número de orden/clave/token
  automáticamente y valida cada respuesta, sin depender de variables
  globales compartidas entre ejecuciones.
- Estado: **verificado**. Implementación:
  `postman/iw3-f5-runner-circuito.postman_collection.json`:
  `orderNumber`/`extSuffix` generados en el pre-request del primer item
  a partir de la hora actual y el índice de iteración;
  `claveActivacion` capturado de la respuesta de tara; `jwtToken`
  capturado del login — los tres en variables de **colección**
  (`pm.collectionVariables`), no en variables de entorno/globales, y
  nunca impresos (sin `console.log`, sin aparecer en ninguna
  descripción de test). Cada item valida estado HTTP y contenido del
  cuerpo; ante un fallo, `pm.execution.setNextRequest(null)` corta el
  resto de esa iteración. Evidencia: igual que TP22 —
  `--iteration-count 2`: **18 requests, 68 aserciones, 0 fallos**,
  `orderNumber` distinto entre iteraciones (confirma que la
  reinicialización no depende de un flag persistente ni reutiliza
  datos de una iteración anterior); corte ante fallo comprobado en
  login (2 requests totales, 8 pasos omitidos por iteración) y en tara
  (6 requests totales, 5 pasos omitidos por iteración). Detalle
  completo en `docs/contrato-f5-runner.md`.

  **Evidencia adicional (22/10/2026):** ejecución guiada individual del
  usuario en Postman Desktop (no Postman CLI), una iteración sobre la
  misma colección — **9 requests, 34 aserciones, 0 fallos**, orden
  `678233769`. Conciliación resultante: tara 12000 kg, bruto 16080 kg,
  neto 4080 kg, masa acumulada (producto cargado) 4000 kg, diferencia
  +80 kg — coherente con la fórmula neto = bruto − tara y diferencia =
  neto − masa acumulada ya verificada en F4. Esta corrida confirma el
  runner en un entorno distinto al de la verificación original (cliente
  gráfico en vez de CLI), pero sigue siendo una ejecución guiada por el
  usuario, no automatizada en CI; defensa y repaso del código del
  runner quedan pendientes (ver TP26).

### TP26 — Presentación de 15-20 min con preguntas individuales
- Descripción: presentación operativa del circuito el día de la entrega,
  con preguntas individuales a cada integrante.
- Alcance: criterio de aprobación del segundo parcial.
- Fuente: T2 — "el día 11 cada grupo va a tener disponible unos 15, 20
  minutos para presentar... tiene que estar todo listo y tiene que estar
  funcionando en ese momento... la persona que no me contesta no
  aprueba."
- Fase prevista: F5 / F8.
- Criterio de aceptación: ensayo cronometrado, entorno funcionando antes
  de empezar a presentar, preguntas cruzadas a cada integrante.
- DATO FALTANTE: la fecha real de entrega 2026. T1 menciona "martes 11 de
  noviembre de 25" (2025); esa fecha corresponde a la cursada anterior
  (el audio es de 2025, reutilizado como material), no a este ciclo.
  Esta ausencia de fecha no bloquea preparar el requisito: no impide
  practicar la defensa ni ensayar el circuito con el equipo.
- Estado: pendiente. Falta: (a) defensa y repaso del código por cada
  integrante — incluido el runner de TP22/TP25, ejecutado hasta ahora
  solo de forma guiada e individual — y (b) un ensayo cronometrado con
  todo el equipo presente, entorno funcionando de punta a punta y
  preguntas cruzadas. Ninguna de las dos tareas requiere conocer la
  fecha de entrega para empezar. Implementación: —. Evidencia: —.

### TP27 — Roles que regulan el consumo de la API
- Descripción: roles de usuario que habilitan o restringen operaciones
  de la API.
- Alcance: final.
- Fuente: T2 — "seguridad y roles de acceso van a tener que definir e
  implementar roles de usuario que regulen el consumo de [la API]."
- Fase prevista: F6.
- Criterio de aceptación: matriz de permisos por operación, con pruebas
  de acceso permitido/denegado por rol.
- Estado: **verificado** (alcance de esta primera unidad de F6: las 7
  operaciones del circuito ya implementadas; no cubre TP28/TP29, que
  seguiran usando los mismos roles pero se implementan en una unidad
  posterior). Implementación: `@PreAuthorize("hasAuthority('ROLE_SUPERVISOR')")`
  en `recibir()`; `@PreAuthorize("hasAnyAuthority('ROLE_OPERADOR','ROLE_SUPERVISOR')")`
  en `registrarTara/activar/registrarLectura/cerrar/pesajeFinal/conciliacion`
  (`OrdenRestController`); nuevo `@ExceptionHandler(AccessDeniedException.class)`
  local al mismo controlador (hallazgo de esta unidad: sin este handler,
  el 403 de `@PreAuthorize` terminaba en 401 por el mismo bug de forward
  a `/error` ya documentado para 400 — ver `docs/contrato-f6-roles-permisos.md`
  y `docs/decisiones.md`). Las entidades `User`/`Role` (con `UserDetails`
  de Spring Security) ya existían en la base heredada y se reutilizaron
  sin modelo nuevo, tal como se había anticipado.
  Evidencia: matriz completa, hallazgo del bug 403→401 y su corrección,
  y resultado de verificación (30/30 por curl, 33/33 por la colección
  Postman nueva `postman/iw3-f6-roles-permisos.postman_collection.json`,
  34/34 de regresión en la colección ya verificada del runner F5 con el
  usuario `ROLE_SUPERVISOR` nuevo) en `docs/contrato-f6-roles-permisos.md`.
  Nombres de rol (`ROLE_OPERADOR`/`ROLE_SUPERVISOR`) y el mapeo
  operación→rol son DECISIÓN DEL EQUIPO, no texto literal de la fuente.

### TP28 — Alarma de temperatura configurable
- Descripción: umbral de temperatura y destinatarios configurables;
  correo cuando la temperatura medida supera estrictamente el umbral.
- Alcance: final.
- Fuente: T2 — "deberá añadirse una alarma configurable que envíe un
  correo electrónico cuando la temperatura registrada supera un umbral
  predefinido. Supongamos que definimos 33 grados... el valor del umbral
  y los correos de destino deberán ser configurables."
- Fase prevista: F6.
- Criterio de aceptación: con umbral=33, una lectura de 33 no dispara y
  una de 33,1 sí (umbral estrictamente superado, no igualado).
- Estado: **verificado** (dos unidades: configuración/disparo, y
  envío de correo). Implementación:
  `ConfiguracionAlarma`/`ConfiguracionAlarmaBusiness` (fila única en
  BD, `GET`/`PUT /api/v1/alarmas/configuracion`, solo
  `ROLE_SUPERVISOR`); `OrdenBusiness.registrarLectura` evalúa el
  disparo sobre toda lectura aceptada y publica `AlarmaDisparadaEvent`;
  `AlarmaEmailListener` (`@TransactionalEventListener(AFTER_COMMIT)` +
  `@Async`, reutilizando `@EnableAsync` ya habilitado) envía el correo
  vía `EmailBusiness` existente, uno por destinatario. Sin fila de
  configuración, no se evalúa ninguna alarma (sin umbral por defecto
  inventado). Verificado con umbral=33.00: una lectura de 33.00 exacto
  no dispara (igual al umbral, no "estrictamente superado"); lecturas
  por encima (35.00 en adelante) sí — no se probó puntualmente el
  límite 33.01. Correo verificado contra un receptor SMTP local de
  prueba (sin proveedores ni destinatarios reales): mensaje recibido
  con `orderNumber`/temperatura/umbral correctos; una falla en un
  destinatario no impide el envío a los demás ni afecta la respuesta
  HTTP de la lectura ni la persistencia de la alarma. Sin reintento
  automático, documentado explícitamente (sin garantía de entrega
  exactamente una vez). Detalle completo en
  `docs/contrato-f6-alarmas-configuracion.md` y
  `docs/contrato-f6-correo-alarmas.md`. El remitente sigue siendo uno
  de prueba (no el histórico `noreply@magm.com.ar` del profesor);
  confirmar remitente real antes de un envío en producción.

### TP29 — No repetir alarma pendiente; rehabilitar tras aceptación
- Descripción: mientras una alarma esté pendiente de aceptación no se
  reenvía correo para ese caso; tras la aceptación por un usuario
  autorizado, puede volver a alertarse si la condición se repite.
- Alcance: final.
- Fuente: T2 — "una vez enviada una alarma, no debe enviarse nuevamente
  hasta que no sea aceptada por un usuario autorizado... si ese problema
  se vuelve a repetir después de la aceptación, ahí sí puedo enviar
  nuevamente un correo."
- Fase prevista: F6.
- Criterio de aceptación: una secuencia de lecturas altas sin aceptación
  genera un único correo; tras aceptar, una nueva lectura que vuelva a
  superar el umbral genera un nuevo correo.
- Estado: **verificado** (dos unidades: supresión/aceptación/
  re-disparo, y envío de correo). "Genera un único correo"/"genera un
  nuevo correo" del criterio de aceptación ya se verificó
  literalmente con un receptor local de prueba: una secuencia de
  lecturas altas sin aceptar generó un único mensaje; tras aceptar,
  una lectura nueva que volvió a superar el umbral generó un segundo
  mensaje. Una lectura cuya transacción se revierte no deja alarma
  persistida ni dispara ningún envío (verificado con una prueba de
  integración que fuerza el rollback por el mecanismo real de
  transacciones, sin agregar ningún interruptor al código). Alcance
  "por orden" de la supresión y que el re-disparo no requiera que la
  temperatura haya bajado son DECISIÓN DEL EQUIPO, no confirmados
  literalmente por la cita de T2. Implementación: `Alarma` (fila por
  disparo, `aceptada` booleano, auditoría usuario/fecha/observación);
  `GET`/`POST .../orders/{orderNumber}/alarmas[...]`, `ROLE_OPERADOR`
  o `ROLE_SUPERVISOR`; `AlarmaDisparadaEvent`/`AlarmaEmailListener`
  para el envío (ver TP28). Usuario y fecha/hora de aceptación
  obligatorios, observación el único campo opcional — apoyado en el
  fragmento directo de T2 citado en
  `docs/contrato-f6-alarmas-configuracion.md` (consultado vía
  NotebookLM), no una demostración inequívoca de la transcripción.
  Evidencia: 404 en alarma inexistente y en alarma de otra orden (sin
  distinguir los dos casos); 409 en aceptación repetida; máximo una
  alarma pendiente por orden confirmado por SQL (0 órdenes con más de
  una `aceptada=0` simultánea); aislamiento entre órdenes confirmado;
  los 5 casos de envío de correo (commit exitoso, supresión mientras
  pendiente, re-disparo tras aceptar, rechazo de un destinatario
  individual sin afectar la lectura ni al resto de destinatarios,
  rollback sin alarma ni envío) verificados en
  `docs/contrato-f6-correo-alarmas.md`. No se probó la indisponibilidad
  total del servidor SMTP (solo el rechazo de un destinatario
  puntual). Sin reintento automático ante falla de envío: documentado
  explícitamente, no hay garantía de entrega exactamente una vez.

### TP30 — Login web y administración/monitoreo de órdenes
- Descripción: autenticación de usuarios y administración/monitoreo de
  órdenes desde la interfaz gráfica.
- Alcance: final.
- Fuente: T2 — "autenticación de usuarios, administración y monitoreo en
  tiempo real de las órdenes de carga."
- Fase prevista: F7.
- Criterio de aceptación: login real contra el backend, logout, sesión
  mientras dure el token, operaciones visibles según el rol.
- Estado: **verificado** para login/logout/sesión (corrección de
  estado: esta fila quedó en "pendiente" por un desfasaje de
  documentación — la unidad ya estaba implementada y probada desde
  una sesión anterior; no se repite esa batería, se reutiliza la
  evidencia ya registrada). Implementación: `iw3-frontend`
  (`LoginPage.tsx`, `AuthContext.tsx`, `httpClient.ts`,
  `ProtectedRoute.tsx`), detalle completo en
  `docs/contrato-f7-login.md` de ese repo.
  - **Evidencia manual del usuario, navegador real contra el backend
    real:** login correcto con `ROLE_OPERADOR` → redirige; credenciales
    incorrectas → error visible sin redirigir; ruta protegida sin
    sesión → redirige a `/login`; logout → vuelve a `/login`; recargar
    con sesión activa → la pierde y vuelve a pedir login. 6/6 puntos
    del checklist, correctos.
  - **Límite aceptado, no ejercitado con evidencia propia:** "operaciones
    visibles según el rol" — hoy `ROLE_OPERADOR` y `ROLE_SUPERVISOR`
    tienen exactamente las mismas pantallas disponibles (no hay
    todavía ninguna vista con visibilidad condicionada por rol en el
    frontend); la autorización real la sigue aplicando el backend en
    cada operación. No se marca como probado por falta de un caso que
    lo ejercite.

### TP31 — Datos mostrados por orden en el monitoreo, con filtros
- Descripción: número de orden, estado, [frase con ruido de ASR, ver
  abajo], masa, última temperatura/densidad/caudal; filtros y
  ordenamiento por estado y otros criterios.
- Alcance: final.
- Fuente: T2 — "mostrando el número de [orden] del estado actual, el
  cambio en el precez, la carga actual, la última temperatura y densidad
  y caudal, filtros y ordenamiento por el estado y otros criterios."
  INTERPRETACIÓN DEL EQUIPO: "el cambio en el precez" tiene ruido de ASR
  en la fuente original (no es un error de esta transcripción); por
  contexto podría ser "el camión, el preset", sin confirmar.
- Fase prevista: F7.
- Criterio de aceptación: UI comparada campo a campo contra la respuesta
  real del backend; filtros/orden probados con datos de ejemplo.
- DATO FALTANTE: confirmar la frase exacta con la consigna escrita o el
  docente antes de decidir si se muestran camión y/o preset.
- Estado: **verificado** para el alcance probado (2026-10-23, rama
  `feature/f7-monitoreo-ordenes` en ambos repos). Implementación:
  - Backend: `GET /api/v1/orders` (estado opcional 1-4, `page`/`size`
    paginados, `sort` limitado a una lista explícita de campos con
    desempate estable por `orderNumber`), `OrdenBusiness.listarOrdenes`,
    `OrdenResumenResponse`/`OrdenListadoResponse`,
    `InvalidQueryParamException`. No expone `claveActivacion` ni las
    entidades completas de camión/chofer/cliente/producto, solo
    `patenteCamion`.
  - Frontend: `ordersApi.ts`, `OrdersListPage.tsx`/`.css` (tabla,
    filtro por estado, ordenamiento por columna, paginación,
    actualización manual, "Sin datos" en nulos, cancelación de
    solicitudes en vuelo para que una respuesta vieja no pise una más
    reciente), ruta `/ordenes` protegida, enlace desde `WelcomePage`.
  - Evidencia: backend compilado (`BUILD SUCCESS`) y verificado contra
    el backend real corriendo (no mockeado) con la colección Postman
    `postman/iw3-f7-monitoreo-ordenes.postman_collection.json` vía
    Postman CLI: 15 requests, 22 assertions, 0 fallos (401 sin token,
    403 sin rol, 200 con `ROLE_OPERADOR`/`ROLE_SUPERVISOR`, filtro por
    estado, 400 en estado/page/size/sort inválidos, orden estable con
    desempate, paginación sin huecos ni duplicados sobre 87 órdenes
    reales dejadas por unidades anteriores). OpenAPI (`/v3/api-docs`)
    expone el endpoint y ambos DTOs nuevos sin pasos manuales. Frontend:
    `npm run build` (`tsc -b && vite build`) y `npm run lint` sin
    errores ni advertencias.
  - **Verificación adicional (2026-10-23, para el inicio del
    frontend, ver `docs/contrato-f7-diseno-ui.md`):** `sort=
    fechaUltimaLectura,desc` contra el backend real (87 órdenes,
    usuario `ROLE_OPERADOR`) devuelve las fechas reales en orden
    estrictamente descendente, sin ninguna fila con
    `fechaUltimaLectura` nulo intercalada — en MySQL, `ORDER BY ...
    DESC` deja los `NULL` al final (confirmado también al revés:
    `ASC` los deja primero). No hizo falta ningún ajuste de backend
    para que el frontend pueda pedir "las N órdenes con lectura más
    reciente" con `size=N&sort=fechaUltimaLectura,desc`.
  - **Prueba visual en navegador real, ejecutada por el usuario
    (2026-10-23):** tabla, filtro por estado, ordenamiento ascendente/
    descendente por columna, paginación, botón "Actualizar" sin perder
    la sesión, cambio de filtro de estado desde la página 2 que vuelve
    a la página 0/1, y "Sin datos" en las columnas de telemetría sin
    lectura — los 7 puntos del checklist, correctos. Esta es la prueba
    que las verificaciones anteriores (Postman en backend, build/lint
    en frontend) no podían cubrir por sí solas.
  - **Corrección aplicada tras la prueba manual:** la etiqueta del
    filtro de estado 2 decía "2 - En carga"; se corrige a "2 - Tara
    registrada" porque el estado 2 también incluye órdenes que ya
    registraron tara y esperan la activación (todavía no están
    cargando efectivamente). Cambio solo de texto en
    `OrdersListPage.tsx` (frontend), sin tocar contrato ni backend.
  - **No probado, no se marca como tal:** casos de error de red/
    servidor en esta pantalla, lista vacía (cero órdenes) y otros
    casos no incluidos en el checklist anterior. Quedan pendientes de
    evidencia específica antes de darlos por cubiertos.
  - **DECISIÓN DEL EQUIPO (cierre de F6, 2026-10-22):** el endpoint de
    listado/filtrado de órdenes que este requisito necesita (antes no
    existía: solo había endpoints de órdenes puntuales por
    `orderNumber`) se implementa en F7, junto con el resto del consumo
    que necesite la UI — no se adelantó en F6.
  - **DATO FALTANTE sin resolver:** la frase de T2 sobre "camión"/
    "preset" sigue ambigua (ruido de ASR); se muestran patente de
    camión y preset en la tabla como DECISIÓN DEL EQUIPO, no como
    requisito confirmado.

### TP32 — ETA (tiempo estimado de llegada al preset)
- Descripción: cálculo de ETA con preset, masa acumulada y caudal por
  hora.
- Alcance: final.
- Fuente: T2 — cita literal reconfirmada por NotebookLM (BE,
  2026-10-23) contra "Parte 2 - Criterios de aprobación del trabajo
  integrador.txt", líneas 108-118: *"También tienen que calcular el
  [ETA], que es el tiempo estimado de lle[gada]. A eso lo van a
  calcular con el pre[set]... tengo la masa acumulada que es lo que
  estoy recibiendo en tiempo real y tengo el caudal por hora. Con eso
  yo puedo calcular cuándo me falta para que se llegue a ese
  pre[set]."*
- **CONFIRMADO (T2, cita de arriba):** el ETA se calcula con preset,
  masa acumulada y caudal por hora.
- **NO CONFIRMADO, verificado explícitamente por NotebookLM (no es un
  supuesto sin chequear):** T2 **no da una fórmula matemática
  explícita** (sin restas, divisiones, `max()` ni "por 60" en el
  texto) **ni** dice que el ETA se muestre "solamente en estado 2"
  (esa restricción de estado, en el texto, se menciona únicamente
  para TP33/tiempo transcurrido, no para el ETA) **ni** menciona
  redondeo, decimales, ni qué hacer con caudal cero o negativo.
- **INTERPRETACIÓN DEL EQUIPO (no CLAUDE.md como fuente docente: ver
  nota en docs/decisiones.md sobre que las instrucciones locales no
  son evidencia de cátedra):** formalización de la cita de arriba en
  `ETA_min = max(preset_kg - masa_kg, 0) / caudal_kg_h * 60`; caudal
  nulo, faltante o ≤ 0 → null ("no estimable"); masa ≥ preset → 0 sin
  cerrar la orden automáticamente; redondeo HALF_UP a 2 decimales en
  un único paso final. **DECISIÓN DEL EQUIPO, por analogía con TP33 (no
  una exigencia textual de T2 para el ETA puntualmente):** solo se
  muestra/calcula para órdenes en estado 2 — null en 1/3/4.
- Fase prevista: F7.
- Criterio de aceptación: ejemplo manual (preset 10000/masa 4000/caudal
  3000 → 120 min, caso numérico de control ya documentado en
  CLAUDE.md §6, que tampoco es cita docente — ver la aclaración de
  arriba); caudal ≤ 0 o sin datos → null; masa ≥ preset → 0.
- Estado: **verificado** para el alcance acordado (2026-10-23).
  Implementación:
  - Backend: `OrdenBusiness.calcularEtaMinutos(presetKg,
    masaAcumuladaKg, caudalKgH)`, invocado en `listarOrdenes` solo
    para filas en estado 2; expuesto como `etaMinutos` en
    `OrdenResumenResponse` (`GET /api/v1/orders`, sin endpoint nuevo).
  - Frontend: `formatearEta` en `OrdersListPage.tsx` — "—" fuera de
    estado 2, "No estimable" si `etaMinutos` es null, minutos con 2
    decimales en el resto; nunca recalcula ni descuenta el valor con
    el reloj local (solo se actualiza al volver a consultar).
  - Evidencia: `OrdenBusinessEtaTest` (JUnit, sin Spring ni BD — es
    una función pura): 9/9 casos OK, incluye el ejemplo de 120 min,
    un caso de redondeo no exacto (128.57), un caso que distingue
    HALF_UP de HALF_EVEN (0.125→0.13), sin masa, sin caudal, caudal
    cero, caudal negativo, preset alcanzado exacto y preset superado.
    `./mvnw compile` → `BUILD SUCCESS` con el resto de la unidad (DTO,
    business, controller). Frontend: `npm run build` y `npm run lint`
    sin errores ni advertencias.
  - **Evidencia manual del usuario en navegador real contra el
    backend real (2026-10-23):** estados 1/3/4 muestran "—" en ETA
    (oculto, como documentado); orden `500321001` creada para esta
    prueba (preset 5000 kg, masa acumulada 5200 kg ≥ preset): ETA
    muestra **"0.00 min"** y la orden permanece en estado 2, sin
    cerrarse sola — confirma la rama "masa ≥ preset" sin forzar una
    transición automática. El valor de ETA también se confirmó
    **fijo** mientras el contador de tiempo transcurrido sigue
    avanzando (ver TP33) — nunca se recalcula con el reloj local.
  - **Límite aceptado, documentado y no oculto:** el caso "caudal ≤ 0"
    no se probó visualmente porque no es alcanzable por el flujo
    normal de la API (`registrarLectura` descarta esas lecturas antes
    de persistir el snapshot) — se cubre con `OrdenBusinessEtaTest`
    de forma aislada, ver arriba. Tampoco hay framework de pruebas
    automatizadas de UI instalado en este frontend; la verificación
    de esta unidad es manual, no automatizada.

### TP33 — Tiempo transcurrido, visible solo en estado 2
- Descripción: tiempo transcurrido desde el inicio de la carga, visible
  únicamente mientras la orden está en estado 2.
- Alcance: final.
- Fuente: T2 — cita literal reconfirmada por NotebookLM (BE,
  2026-10-23), mismo fragmento que TP32: *"El tiempo transcurrido
  desde que inicio de la carga, eso también lo tengo que estar
  mostrando siempre en algún lado en la interfaz gráfica, sólo
  obviamente para orden en el estado 2. [...] solamente mientras una
  orden está en el estado 2, puedo ir diciendo, mira esto, hace una
  hora que está cargando por ejemplo."*
- **CONFIRMADO (T2, cita de arriba, a diferencia de TP32):** el
  tiempo transcurrido se muestra únicamente mientras la orden está en
  estado 2 — esta restricción de estado sí es textual para este
  requisito puntual.
- Fase prevista: F7.
- Criterio de aceptación: contador visible y corriendo solo en estado 2 y con inicio real
  de carga registrado; mientras no haya iniciado, mostrar “sin iniciar”
  como convención propuesta del equipo, sin contar desde la tara;
  no confundir "tara" con "inicio real de carga" (distinción general de
  T1, aplicada aquí). DATO FALTANTE (confirmado por NotebookLM, no
  solo supuesto): T2 no dice explícitamente qué mostrar antes del
  estado 2 (ej. "sin iniciar"), ni el formato del contador (HH:mm:ss,
  minutos, etc.) ni qué pasa pasadas las 24 h; esas son convenciones a
  definir por el equipo, no requisitos confirmados.
- **DECISIÓN DEL EQUIPO:** formato HH:mm:ss con horas acumuladas sin
  reiniciar a las 24 h; el contador se actualiza localmente cada
  segundo contra `fechaInicioCarga` (sin solicitar nada al backend
  por el tick) y nunca simula avance de masa/caudal/ETA, que solo
  cambian al volver a consultar.
- Estado: **verificado** para el alcance acordado (2026-10-23).
  Implementación:
  - Backend: `fechaInicioCarga` (ya existía en `Orden.java` desde F3)
    expuesto en `OrdenResumenResponse` como String ISO-8601 con
    offset explícito de `ZONA_RECEPCION` (nunca un `LocalDateTime`
    desnudo — ver TP32/TP33 en docs/contrato-f7-tiempos-carga.md);
    `fechaUltimaLectura` recibe el mismo tratamiento de offset.
  - Frontend: `formatearTranscurrido` en `OrdersListPage.tsx`, tick
    de 1 segundo (`setInterval`, con `clearInterval` en el cleanup
    del `useEffect` al desmontar) solo para forzar el re-render; el
    cálculo real usa `Date.now()` en cada render, nunca un estado que
    se incremente a mano.
  - Evidencia: `./mvnw compile` → `BUILD SUCCESS`. Frontend: `npm run
    build`/`npm run lint` sin errores. Verificación aislada (Node
    22.23.4, fuera de la app) de que `new Date(...)` interpreta
    correctamente un string con offset explícito de 6 dígitos de
    fracción de segundo (formato real que produce
    `DateTimeFormatter.ISO_OFFSET_DATE_TIME`), con y sin fracción.
  - **Evidencia manual del usuario, navegador real contra el backend
    real (2026-10-23, varias rondas de la misma sesión):** el
    contador avanza un segundo por segundo en pantalla; una captura
    mostró `HH:mm:ss` con horas acumuladas **superiores a 24** sin
    reiniciar; el ETA permanece **fijo** mientras el contador avanza
    (ver TP32). En una ronda final: estados 1/3/4 muestran "—" en el
    contador (oculto, como documentado); estado 2 sin lecturas
    todavía muestra **"Sin iniciar"** (y "No estimable" en ETA,
    TP32) — confirma la convención del equipo para ese caso. Orden
    `500321002`, creada para esta prueba: con `fechaInicioCarga =
    2026-10-23T11:47:28.23454-03:00` y hora local aproximada
    `11:56:39`, el contador mostró **`00:09:11`** — coincide con la
    diferencia real (9 min 11 s), confirmando que el offset se
    interpreta correctamente en un navegador real (no solo en la
    verificación aislada con Node). La misma orden mostró
    `fechaUltimaLectura = 2026-10-23T11:48:01.96109-03:00`
    (distinta de `fechaInicioCarga`, tras una segunda lectura
    posterior) y `etaMinutos = 145.16` — ambos valores correctos,
    confirmando que la UI no confunde "última lectura" con "inicio de
    carga".
  - **Límite aceptado, documentado y no oculto:** no hay framework de
    pruebas automatizadas de UI instalado en este frontend
    (`package.json` sin Vitest/Jest/Testing Library); no se incorporó
    uno para esta unidad (fuera de alcance, ver instrucción de no
    agregar funcionalidades nuevas) — toda la verificación de TP32/
    TP33 es manual, en navegador real contra el backend real, no
    automatizada.

### TP34 — Visualización y aceptación de alarmas desde la UI
- Descripción: gestión de alarmas de temperatura visible en la UI, con
  aceptación por parte de un usuario autorizado.
- Alcance: final.
- Fuente: T2 — "tiene que estar disponible la gestión de alarmas de
  temperatura, se debe visualizar [las] alarmas en la interfaz
  gráfica... vamos a realizar la aceptación de la alarma por el usuario,
  el registro de usuario fecha ahora observación e opcional." Frase
  ambigua en la fuente original (no se puede determinar con certeza qué
  campos son opcionales); DECISIÓN DEL EQUIPO propuesta, no confirmada:
  registrar usuario y fecha/hora siempre, observación opcional.
- Fase prevista: F7.
- Criterio de aceptación: un usuario autorizado acepta y el backend
  persiste usuario + fecha/hora; un usuario sin permiso no puede aceptar.
- Estado: **verificado** para el alcance probado (2026-10-23).
  Implementación (solo frontend; `GET .../alarmas` y
  `POST .../alarmas/{id}/aceptacion` reutilizados sin cambios de
  contrato ni de permisos). Detalle completo en
  `docs/contrato-f7-alarmas-ui.md` de `iw3-frontend`.
  - **Evidencia manual del usuario, navegador real contra el backend
    real (2026-10-23, varias rondas):** acceso desde Órdenes; orden
    `900001` sin alarmas → mensaje de lista vacía; orden `500321003`
    (de prueba, creada para esta unidad) con una alarma de 36 °C
    aceptada sin observación → pasa a "Aceptadas" con usuario y fecha
    reales, "Sin observación", quedando 0 pendientes; "Volver a
    órdenes" desde Alarmas conserva el filtro.
  - **Corrección de formato aplicada y confirmada:** las horas se
    mostraban en 12 horas sin indicador am/pm (defecto de
    presentación del locale `es-AR`, no un error de zona horaria —
    verificado contrastando el valor crudo de la API contra la hora
    real del servidor antes de corregir nada). Corregido a 24 horas
    explícitas (`hourCycle: 'h23'`) en toda la UI, no solo en
    alarmas; confirmado visualmente: disparo `14:18:48`, aceptación
    `14:29:44`, ambos coinciden con la hora real.
  - **Límites aceptados, documentados y no ocultos:** página y
    ordenamiento al volver desde Alarmas **no se confirmaron** en
    esta prueba (solo el filtro); doble envío y conservación de la
    observación ante un error no se re-confirmaron visualmente en
    esta ronda (sí se verificó 409/403 por API en una ronda anterior,
    no en navegador). `AlarmaResponse.fechaDisparo`/`fechaAceptacion`
    siguen sin offset explícito (a diferencia de
    `OrdenResumenResponse`, ya corregido en TP32/TP33) — el frontend
    le agrega el offset fijo `-03:00` de `ZONA_RECEPCION` antes de
    interpretarla; **decisión conservada, no un cambio de backend**.
    Sin framework de pruebas automatizadas de UI, igual que TP32-TP35.

### TP35 — Conciliaciones visibles solo en estado 4
- Descripción: la UI muestra conciliaciones únicamente para órdenes en
  estado 4.
- Alcance: final.
- Fuente: T2 — "la visualización de conciliaciones, disponible solo para
  orden en el estado 4, que son las finalizadas."
- **CONFIRMADO (T2, cita de arriba):** la visualización se restringe a
  estado 4; no hizo falta NotebookLM, la cita ya estaba registrada.
- Fase prevista: F7.
- Criterio de aceptación: la UI solo muestra la vista en estado 4; el
  backend valida el estado de forma independiente de lo que la UI
  oculte.
- Estado: **verificado** para el alcance probado (2026-10-23).
  Implementación (solo frontend; `GET /api/v1/orders/{orderNumber}/conciliacion`
  reutilizado sin cambios de contrato ni de permisos — `ROLE_OPERADOR`/
  `ROLE_SUPERVISOR`, mismos que el resto de `/orders`):
  - `iw3-frontend`: `ConciliacionPage.tsx`/`.css` (ruta
    `/ordenes/:orderNumber/conciliacion`), columna "Conciliación" en
    `OrdersListPage` solo para filas en estado 4, formateo numérico
    español (punto de miles, coma decimal) con decimales fijos según
    la precisión real de cada campo, diferencia con signo explícito y
    presentación neutral (una diferencia ≠ 0 no es un rechazo
    automático). Detalle completo en
    `docs/contrato-f7-conciliacion-ui.md` de `iw3-frontend`.
  - **Evidencia manual del usuario, navegador real contra el backend
    real (2026-10-23, varias rondas):** acceso desde Órdenes solo en
    estado 4; orden `500321001` (estado 2, reusada de TP32/TP33)
    muestra el mensaje de no finalizada, **sin badge "Finalizada" ni
    datos de conciliación residuales** (corregido tras una revisión
    visual: el badge solo se renderiza si la consulta trae datos,
    limpiando el resultado anterior antes de cada consulta nueva);
    orden `633433063` (estado 4) con diferencia **-100,000 kg** y
    orden `915358` con diferencia **+80,000 kg** — valores
    positivo/negativo con formato numérico español correcto, signo
    preservado, presentación neutral; "Últimos valores" y "Promedios"
    claramente diferenciados; espaciado/alineación corregidos
    (incluye un token CSS inválido `--space-5` que existía también en
    `WelcomePage.css`, corregido); "Volver a órdenes" conserva
    filtro/página/orden.
  - **Límites aceptados, documentados y no ocultos:** el 404 (orden
    inexistente) solo se verificó por API (`curl`), no visualmente en
    navegador. El caso de promedios `null` (orden sin ningún detalle
    almacenado) no se ejercitó — es teóricamente inalcanzable por el
    circuito normal, misma razón ya documentada en TP19/TP18 (toda
    orden que llega a estado 3/4 ya tiene al menos una lectura
    aceptada, que genera detalle). Sin framework de pruebas
    automatizadas de UI instalado, igual que TP32/TP33.

### TP36 — Comprensión global y evaluación individual
- Descripción: cada integrante debe poder explicar el funcionamiento
  completo del sistema, no solo su rol principal.
- Alcance: segundo parcial y final (transversal a toda la evaluación).
- Fuente: T2 — "todos los integrantes deben comprender el funcionamiento
  completo del sistema, ya que la evaluación va a ser individual...
  Asimismo, si se trabaja en equipo cada miembro deberá compartir con el
  resto del grupo lo que haya implementado... la persona que no me
  contesta no aprueba." También confirma que grupos de hasta 4 personas
  son aceptados ("tenemos un grupo de 4, no hay problema con eso").
- Fase prevista: todas.
- Criterio de aceptación: cada integrante puede responder preguntas sobre
  cualquier parte del sistema; ver `docs/defensa.md`.
- Estado: en curso (el documento de defensa ya existe, pero ninguna
  pregunta fue practicada todavía por una persona real).
- Implementación: `docs/defensa.md` con preguntas propuestas por unidad.
- Evidencia: —.

## Equipo / infraestructura

### EQ01 — Dos repos, flujo de ramas, versiones identificables
- Descripción: dos repositorios hermanos (BE/FE), con ramas
  `feature/*` → `develop` → `main`, integrados vía PR.
- Alcance: regla/decisión del equipo (práctica de desarrollo, no un
  requisito textual de la consigna del integrador).
- Fuente: decisión del equipo, alineada con la práctica de control de
  versiones vista en las clases de frontend sobre flujo profesional de
  desarrollo (rama por unidad de trabajo, historial y PR antes de
  integrar). No se leyó el documento completo de esa clase en esta
  sesión; cita puntual DATO FALTANTE.
- Fase prevista: F0–F8.
- Criterio de aceptación: remotos, ramas e historial coherentes en ambos
  repos, con al menos una PR real revisada antes de llegar a `develop`.
- Estado: en curso. Implementación: dos repos creados, ramas
  `feature/*`/`develop`/`main` definidas, `feature/setup-inicial`
  publicada en backend, `main` publicada en frontend (solo
  `.gitignore`). Evidencia: `git log`/`git branch -vv` en ambos repos
  (detalle en `docs/estado-proyecto.md`). Falta la primera PR real a
  `develop`.
  **Flujo confirmado al cierre de F4, antes de iniciar F5:**
  `feature/*` → PR a `develop` (que acumula el desarrollo integrado) →
  PR a `main` cuando se autorice una entrega. `main` recibirá primero
  la entrega del segundo parcial (con su etiqueta) y, más adelante, la
  del final. Después del parcial, el trabajo continúa con el mismo
  patrón `feature/*` → `develop`. No es una decisión nueva —coincide
  con el flujo ya definido en `CLAUDE.md`, sección 4— sino su
  confirmación operativa para este punto del proyecto, sin cambios de
  rama todavía.
  **Primera integración real (22/10/2026):** cadena
  `feature/setup-inicial` → `feature/f2-recepcion-ordenes` →
  `feature/f3-tara-activacion` → `feature/f4-cierre-conciliacion` →
  `feature/f5-preparacion-parcial` verificada como ancestría lineal
  sobre `develop`/`main` (mismo punto de partida `d8f4c72`, sin
  commits divergentes). Integración realizada con `git merge
  --ff-only` (fast-forward, sin commit de merge ni duplicación) en vez
  de una PR abierta en la interfaz de GitHub — autorizada
  explícitamente por el usuario para esta operación puntual, dado que
  la cadena ya había sido revisada unidad por unidad en los
  checkpoints previos. `develop` y `main` quedaron ambos en `45710e3`,
  publicados en `origin`. Se creó la etiqueta anotada
  `parcial-2-local-v1` sobre `main` (también publicada), marcando la
  versión funcional local del segundo parcial — el despliegue en VM
  queda pendiente (ver `docs/estado-proyecto.md`). `develop` queda
  activa y sin cambios pendientes tras la operación.

### EQ02 — Simplicidad y decisiones trazables
- Descripción: cada cambio no trivial tiene una fuente citada o una
  decisión de equipo documentada, sin agregar complejidad por
  anticipación (librerías, patrones o servicios no pedidos por la
  consigna).
- Alcance: regla del equipo.
- Fuente: decisión del equipo (criterio de trabajo adoptado desde el
  inicio del proyecto).
- Fase prevista: todas.
- Criterio de aceptación: `docs/decisiones.md` contiene la justificación
  de cada decisión no obvia.
- Estado: en curso. Implementación: `docs/decisiones.md` con el
  saneamiento de credenciales, el procedimiento de sincronización con el
  repositorio del profesor, y los hallazgos de esta sesión. Evidencia:
  el propio archivo.

### EQ03 — Checkpoint por sesión, con auditoría y reanudación
- Descripción: cada sesión de trabajo relevante deja un checkpoint con
  estado Git real, verificaciones ejecutadas y próximo paso.
- Alcance: regla del equipo (práctica de continuidad entre sesiones).
- Fuente: decisión del equipo.
- Fase prevista: todas.
- Criterio de aceptación: existe un archivo de checkpoint por sesión
  relevante, con estado Git verificado con comandos reales, no supuesto.
- Estado: verificado. Implementación: `docs/checkpoints/2026-10-20-01.md`
  y `docs/checkpoints/2026-10-20-02.md`. Evidencia: los propios archivos,
  contrastados contra `git status`/`git log` real en cada sesión.

## FE: prácticas y criterios por confirmar

Fuente: cuaderno "Ingeniería Web III FE" (28 fuentes: 13 carpetas 01-13,
cada una con un documento y una presentación, más dos notas sueltas).

### Las dos notas sueltas, leídas en esta sesión

- **"Les comparto la información correspondiente al pr…"**: aviso del
  **primer parcial** de la materia (jueves 24/9/2026), escrito,
  individual, multiple choice (50 preguntas), sobre Flujo profesional,
  Componentes, Estado y formularios, Renderizado dinámico, Ciclo de vida
  y consumo de APIs, Routing y Estado Global. **No aplica al
  integrador**: evaluación teórica individual separada, ya rendida a la
  fecha de esta sesión. No agrega ninguna obligación de entrega sobre el
  proyecto.
- **"Todas las notas, 22/9/2026"**: apunte de repaso consolidado para ese
  mismo primer parcial (bloques teóricos 1 a 6). **No es un entregable**,
  es material de estudio. Contiene criterios de cátedra reutilizables
  como referencia técnica para F7: un componente nunca hace
  `fetch`/`axios` directo (se delega a una capa `/services`), la petición
  inicial se dispara al finalizar el montaje (`onMounted`/`useEffect`), y
  Axios con interceptores inyecta el header `Authorization: Bearer`. No
  obliga a usar React/Vue/Axios específicamente para el integrador; es
  contexto de cómo se enseña el patrón, no una obligación de la consigna.

### Carpetas 01-13 — resuelto por consulta puntual a NotebookLM (2026-10-23)

**Hallazgo central, confirmado por cita literal (NotebookLM, cuaderno
FE, fuente `01 - Documento Presentación de la materia.docx` y
siguientes):** las carpetas 01-13 describen un **"proyecto
personal"/"proyecto integrador" propio y autónomo de esta misma
materia (Ingeniería Web III)** — una aplicación web genérica que cada
alumno/grupo construye clase a clase como vehículo de aprendizaje,
con su **propio repositorio** (*"Crear un repositorio llamado:
ingenieria-web-3"*) — **distinto del trabajo integrador de órdenes de
carga de gas** (T1/T2) que es el alcance de este documento. La
consulta explícita confirmó: *"no hay ninguna mención de integración
con otra materia, ni referencia a un sistema de órdenes de carga de
gas"*. Las exigencias técnicas puntuales de cada carpeta (Zod/React
Hook Form/VeeValidate, librería de UI con justificación, informe de
performance 1-2 páginas, Test Plan + Cypress + GitHub Actions +
Vercel/Netlify) son **exigencias docentes confirmadas por cita, pero
del proyecto personal de la materia — no del trabajo integrador de
gas**. CLAUDE.md §13 ya anticipaba este criterio ("no convertir todos
los ejercicios... en funcionalidades del sistema de gas"); esta
consulta lo confirma con fuente en vez de dejarlo como supuesto.

**Categorización por carpeta** (E=exigencia docente del proyecto
personal de la materia, con fuente; J=ejemplo/patrón de clase
reutilizable, no obligatorio para el integrador de gas; D=decisión
del equipo; F=dato faltante) y contraste con el frontend del
integrador (`iw3-frontend`):

| Carpeta | Categoría y fuente | Contraste con el integrador de gas |
| --- | --- | --- |
| 01 Presentación / 02 Flujo profesional | E (repo propio "ingenieria-web-3", IA supervisada, 5 roles/agentes) — **no aplica** al integrador, es un repositorio de práctica separado | **No aplica, con justificación**: el integrador ya tiene su propio flujo de ramas/checkpoints (EQ01), inspirado en el mismo patrón pero como **D**ecisión del equipo, no por obligación de esta carpeta |
| 03 Componentes | J (Button/Card/Navbar/Modal, composición) del proyecto personal | **No aplica directamente**; el patrón de componentes reutilizables (`EstadoBadge`, `.btn*`) ya está adoptado como **D**ecisión del equipo |
| 04 Estado y formularios | J (login/registro/loading/logout) del proyecto personal | **Cumplido** para el integrador — pero por **TP30 (fuente real: T2)**, no por esta carpeta; la carpeta solo coincide en patrón |
| 05 Renderizado dinámico | J (filtros/orden/paginación) del proyecto personal | **Cumplido** para el integrador — por **TP31 (fuente real: T2)**, no por esta carpeta |
| 06 Ciclo de vida y APIs | J (capa `/services`, loading/empty/error) del proyecto personal — mismo patrón ya citado en "Todas las notas" arriba | **Cumplido como D**ecisión del equipo: `ordersApi.ts` como capa de servicios, estados de carga/error/vacío en `OrdersListPage`/`WelcomePage` |
| 07 Routing y estado global | J (rutas públicas/privadas, roles, sesión/logout) del proyecto personal | **Cumplido como D**ecisión del equipo: `ProtectedRoute`, `AuthContext` — motivado por TP30, no por esta carpeta |
| 08 CRUD profesional | E (TypeScript, servicios, formulario reutilizable, modal, toast) del proyecto personal | **No aplica**: el integrador no tiene un CRUD genérico por diseño (TP16 ya documenta que no se habilita editar/borrar libremente); no corresponde adoptar este patrón sin una necesidad real del dominio |
| 09 Formularios profesionales | E, confirmado por cita (Zod/React Hook Form/VeeValidate) del proyecto personal | **No aplica**: el integrador no tiene formularios de alta/edición de este tipo (las únicas entradas de la UI son login y observación de alarma, ya implementadas sin esas librerías) |
| 10 Arquitectura Frontend | J (separación pages/components/services/router/state/types) del proyecto personal | **Cumplido como D**ecisión del equipo: esa misma separación ya existe en `iw3-frontend/src/` |
| 11 UI profesional | E, confirmado por cita (librería de UI con justificación; Material UI/Vuetify de referencia, no obligatorias) del proyecto personal | **No aplica**: se decidió explícitamente no usar una biblioteca de componentes completa (ver `docs/contrato-f7-diseno-ui.md`), decisión ya tomada y justificada, no pendiente |
| 12 Performance | E, confirmado por cita ("ENTREGABLE OBLIGATORIO: informe técnico 1-2 páginas") del proyecto personal | **No aplica** al integrador de gas: esa obligación es del proyecto personal de la materia, no de este trabajo — no se "inventa" un entregable de performance que nadie pidió para este proyecto |
| 13 Testing y Deploy | E, confirmado por cita (Test Plan, Cypress, GitHub Actions, Vercel/Netlify) del proyecto personal | **No aplica** al integrador de gas: el hosting real de este proyecto es la VM asignada (F8), no Vercel/Netlify; no hay conflicto real porque son proyectos distintos |
| 14 Seguridad de frontend | F (carpeta reportada vacía, sin fuente en el cuaderno) | Sin contenido que contrastar |

**Dato realmente faltante, sin bloquear el integrador:** si el
proyecto personal de las carpetas 01-13 tiene una nota/entrega propia
independiente del trabajo integrador de gas, las fuentes no lo
aclaran explícitamente (confirmado por NotebookLM: *"no está en el
material"*). No es una pregunta que pueda resolver una fuente del
cuaderno — si corresponde, la resuelve el usuario con el docente; no
bloquea el cierre de F7 porque es un entregable de **otra pieza de
evaluación**, no del trabajo integrador.

## Ambigüedades que quedan igual tras esta revisión

Siguen sin resolver con la lectura de T1/T2: contrato exacto de
activación/colisión de clave (TP09), alcance de la alarma por orden vs.
global, metadatos exactos de aceptación de alarma (TP34). Sí se
resolvieron con esta lectura: el momento de generación de la clave
(TP08) y varias precisiones de conciliación/descarte (TP11, TP18).
**Resuelto en esta entrada (2026-10-23, NotebookLM FE):** stack/CI/
hosting de las carpetas 01-13 — pertenecen al proyecto personal
autónomo de la materia, no al trabajo integrador de gas; sin
conflicto real con la VM de F8 porque son entregables distintos (ver
sección "FE: prácticas y criterios por confirmar" arriba). La
inconsistencia de densidad (rango 0-1 vs. unidad kg/m³) sigue como
duda pendiente, no resuelta — ver `docs/fuentes.md`.
