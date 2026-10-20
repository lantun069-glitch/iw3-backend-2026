# Índice de fuentes

Criterio de autoridad entre fuentes, de mayor a menor: consigna oficial
vigente y aclaraciones docentes → código de la base docente pertinente al
tema → apuntes de clase → decisiones documentadas del equipo →
herramientas auxiliares. Un dato de una fuente de menor autoridad nunca
reemplaza a uno de mayor autoridad; si hay conflicto, se documenta el
conflicto, no se elige por comodidad.

Etiquetas usadas en este documento: **CONFIRMADO** (hay una cita textual
identificable que lo respalda), **INTERPRETACIÓN DEL EQUIPO** (lectura
razonable de un texto ambiguo o ruidoso, no es una cita literal),
**DUDA PENDIENTE** (la fuente no alcanza para resolverlo; no convertir en
requisito hasta confirmarlo), **DATO FALTANTE** (no hay fuente todavía).

## Transcripciones de la consigna

Hasta esta sesión solo se tenía un resumen de la segunda transcripción.
Se identificó el cuaderno de la materia en NotebookLM y se leyeron las dos
transcripciones completas.

- **Cuaderno:** "Ingeniería Web III BE" (id `5f7b0e91-c874-416c-898b-a5a986471f3a`,
  16 fuentes, rol editor).
- **T1 — `Parte 1 - Lectura de la consigna del trabajo integrador.txt`**
  (fuente `59f2c5bf-3d78-4e71-a60a-153dbecb9c27`, 21.713 caracteres,
  leída íntegra): consigna del circuito 1-5, entidades, estados,
  timestamps, reglas de descarte de lecturas y conciliación.
- **T2 — `Parte 2 - Criterios de aprobación del trabajo integrador.txt`**
  (fuente `fa7832ee-0bc8-4933-8319-35b122979a28`, 8.535 caracteres,
  leída íntegra): criterios de aprobación del segundo parcial y del
  final, evaluación individual.

Ambas son transcripciones automáticas de audio (ASR), con ruido evidente
en palabras sueltas (ej. "sistema" donde por contexto se entiende
"cisterna"; "el cambio en el precez" en T2). Ese ruido está en la fuente
original, no es un error introducido por esta lectura.

### Densidad (T1)

- **Texto literal:** "La densidad del producto es un valor entre 0 y 1 y
  la unidad de medida es kilogramos por metro cúbico, esto es un dato
  anecdótico por ahora que simplemente lo tenemos que guardar."
- **INTERPRETACIÓN DEL EQUIPO:** el valor recibido se persiste tal cual,
  sin transformarlo. "Anecdótico" se lee como "no es el foco de la
  lógica de negocio de esta entrega", **no** como "sin ninguna
  validación de rango" — esas son dos afirmaciones distintas y la
  fuente solo sostiene la primera.
- **DUDA PENDIENTE:** corroborar con la consigna escrita o el docente
  que el rango y la unidad de la transcripción sean los previstos para
  este producto y simulación. No cambiar unidades ni agregar reglas
  físicas por cuenta propia. “Anecdótico” no permite inferir por sí solo
  si los extremos del rango deben validarse. Esta duda no bloquea F1.

### Generación de la clave de activación (T1)

- **Texto literal:** "...cuando el camión arriba a la planta en el turno
  asignado, se registra el pesaje del camión vacío que es la tara. Se
  agrega una contraseña de activación compuesta por un número entero de
  cinco dígitos que se asocia a la orden... tenemos que generar una
  clave de cinco dígitos al azar... Al completar este paso, la orden
  pasa a estado dos."
- **INTERPRETACIÓN DEL EQUIPO:** la clave se genera en la misma
  operación que registra la tara, es decir, en la transición 1→2. No es
  un paso separado ni posterior.
- **DUDA PENDIENTE:** ninguna detectada en este punto puntual; a
  diferencia de la densidad, el audio es explícito y no ambiguo acá (el
  ruido de ASR en otras partes del documento no afecta a esta oración).

### Descarte de lecturas (T1)

- **Texto literal:** "Se deben descartar los registros cuyo caudal sea
  menor igual que 0... Y cuando la masa acumulada sea menor igual que
  0... tampoco tengo que almacenar una masa menor que el valor anterior,
  eso sería un error porque la masa siempre es acumulativa."
- **INTERPRETACIÓN DEL EQUIPO:** tres reglas de descarte independientes:
  caudal ≤ 0, masa acumulada ≤ 0, y masa estrictamente menor que la
  última masa válida. La igualdad entre la masa nueva y la última masa
  válida no está incluida en ninguna de las tres reglas citadas (la
  fuente dice "menor", no "menor o igual", para ese caso puntual).
- **Criterio de implementación:** no agregar un descarte por igualdad
  de masa que no aparece en las reglas citadas. Si masa y caudal son
  positivos y la masa es igual a la anterior, esa igualdad no invalida
  la lectura. La frecuencia configurada determina si se guarda detalle.

### Otros puntos de T1/T2 confirmados por la lectura completa

- Número de orden: entero, único, recibido desde el sistema externo (no
  generado internamente). Cita: "el número de orden, que es un
  identificador único número entero que se lo recibíamos de forma
  externa."
- Sincronización de entidades externas: "si no existen, las tengo que
  agregar y si existen deberían actualizar si vienen datos cambiados."
- Conciliación: "el neto por balanza... sería el pesaje final menos el
  pesaje inicial"; "esa diferencia puede existir, y ahí la decisión
  después de qué se hace con esa diferencia no es un problema nuestro
  sino una decisión humana" — una diferencia distinta de cero no implica
  rechazo automático por parte del sistema.
- Segundo parcial sin interfaz gráfica: "Deberán implementarse sin
  interfaz gráficas... yo no quiero interfaz gráficas para el segundo
  parcial."
- Frecuencia de almacenamiento y promedios, opcionales en el parcial:
  "esto es opcional por ahora... es válido almacenar todos los
  registros... conforme se reciban" / "también es opcional el cálculo de
  valores medios... aunque esto sí lo voy a requerir para el examen
  final."
- Alarma de temperatura: "una vez enviada una alarma, no debe enviarse
  nuevamente hasta que no sea aceptada por un usuario autorizado... si
  ese problema se vuelve a repetir después de la aceptación, ahí sí
  puedo enviar nuevamente un correo."
- Fecha mencionada en T1 para la entrega: "el día martes 11 de noviembre
  de 25" (2025) con ajuste y presentación el día 18. Esa fecha
  corresponde a la cursada anterior (el audio es de 2025, reutilizado
  como material). **DATO FALTANTE:** la fecha real de entrega 2026.
- Grupos: "nosotros tenemos una particularidad, tenemos un grupo de 4, no
  hay problema con eso" — grupos de hasta 4 integrantes son aceptados.
- Evaluación individual: "la persona que no me contesta no aprueba" —
  cada integrante debe poder responder preguntas sobre cualquier parte
  del sistema, no solo la propia.

## Apuntes de clase (BE)

Mismo cuaderno, 11 PDFs "Apuntes Clase 2" a "Apuntes Clase 11" (+ "Clase
11 Complemento"), un aviso de clase cancelada, y enlaces a
`github.com/magm3333/iw3_2026` (base docente 2026) y
`github.com/magm3333/iw3_2025` (año anterior, **no autoritativa para
este trabajo**, solo contexto). No se leyó el contenido completo de los
11 PDFs en esta sesión; se consultarán puntualmente por tema cuando una
unidad de implementación lo requiera, citando clase y página.

## Apuntes de clase y avisos (FE)

Cuaderno "Ingeniería Web III FE" (id
`0601d0ab-5564-42ab-821b-1356d44c3b67`, 28 fuentes): 13 carpetas 01-13
(cada una con un `.docx` y un `.pptx`), más dos notas sueltas que se
leyeron en esta sesión:

- **"Les comparto la información correspondiente al pr…"**: aviso del
  **primer parcial** de la materia (jueves 24/9/2026), escrito,
  individual, multiple choice (50 preguntas), sobre Flujo profesional,
  Componentes, Estado y formularios, Renderizado dinámico, Ciclo de vida
  y consumo de APIs, Routing y Estado Global. **No aplica al
  integrador**: es una evaluación teórica individual separada, ya
  rendida a la fecha de esta sesión. No agrega ninguna obligación de
  entrega sobre el proyecto integrador.
- **"Todas las notas, 22/9/2026"**: apunte de repaso consolidado
  ("bloques teóricos" 1 a 6) para ese mismo primer parcial — flujo
  profesional/Git/IA, componentes, estado/formularios, renderizado
  dinámico, ciclo de vida y consumo de APIs. **No es un entregable**,
  es material de estudio. Contiene criterios de cátedra reutilizables
  como referencia técnica para F7 (no como obligación de entrega): por
  ejemplo, la regla de que un componente nunca hace `fetch`/`axios`
  directo (se delega a una capa `/services`), el momento de disparo de
  la petición inicial (`onMounted`/`useEffect` tras el montaje), y el
  uso de interceptores de Axios para inyectar el header
  `Authorization: Bearer`.

No se leyeron los 13 documentos numerados en esta sesión (el foco era
backend); quedan pendientes antes de F7.

## Base docente del backend (código)

- `https://github.com/magm3333/iw3_2026`, commit
  `b7e705de737e5b1039719807bb413ec89279e7d1` (rama `main`, "Backend
  completo", 15/10/2026). Inspeccionado por lectura directa de archivos.
  **Precisión sobre la compilación:** lo que se compiló y probó en esta
  sesión es la copia ya saneada y con la externalización de credenciales
  aplicada (`iw3-backend-sanitized`), **no** el commit original del
  profesor sin modificar. No se compiló ese commit por separado; no
  corresponde afirmar que "la base docente compila" como hecho general,
  solo que nuestra copia modificada compila.

## Herramientas auxiliares

- **Skills de revisión/desarrollo:** listado extenso disponible en esta
  sesión de trabajo. No se verificó cada una individualmente; se usan
  solo las que aporten a una tarea concreta.
- **Graphify:** disponible como capacidad de esta sesión, pero **no
  ejecutada** todavía sobre este repositorio — no existe `graphify-out/`.
  Ejecutarlo hoy solo graficaría la base docente sin cambios propios.
- **NotebookLM (servidor MCP):** conectado y funcional en esta sesión (se
  usó para leer T1, T2 y las dos notas FE citadas arriba).
- **Chrome DevTools (servidor MCP):** configurado pero **falló la
  conexión** en esta sesión (`CONNECTION_CLOSED`). No se intentó usar.
- **Postman CLI:** verificado real, `postman --version` → `1.75.0`
  (`~/.npm-global/bin/postman`). No se usó todavía.

## Pendientes de esta sección

- Leer puntualmente los 11 PDFs de apuntes BE cuando una unidad concreta
  lo requiera.
- Leer los 13 documentos numerados del cuaderno FE antes de F7.
- Confirmar fecha de entrega 2026 con el docente (sigue DATO FALTANTE).
- Resolver la inconsistencia físic/unidad de densidad con la consigna
  escrita o el docente antes de cerrar F2.
