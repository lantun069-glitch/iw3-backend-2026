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
