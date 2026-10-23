# Contrato — Notificación por correo de alarmas (F6, segunda unidad de TP28/TP29)

Cubre el envío de correo pendiente de la unidad anterior
(`docs/contrato-f6-alarmas-configuracion.md`). Con esta unidad,
**TP28 y TP29 quedan completos** para el alcance de esta entrega
(ningún correo real, solo verificado contra un receptor local de
prueba). Esto no implica que **F6** esté cerrada: queda pendiente
contrastarla contra su criterio de salida completo.

## Mecanismo: evento después del commit, fuera del bloqueo

- `OrdenBusiness.registrarLectura`, en el mismo punto donde ya
  guardaba la `Alarma` (dentro del `PESSIMISTIC_WRITE` de la orden),
  publica un evento nuevo `AlarmaDisparadaEvent` vía
  `ApplicationEventPublisher`. El evento es un objeto inmutable con
  **solo datos primitivos** (id de alarma, `orderNumber`,
  `temperaturaC`, `umbralAplicado`, `fechaDisparo`, y una **copia**
  (`List.copyOf`) de los destinatarios configurados en ese momento) —
  a propósito no lleva ninguna entidad JPA (`Alarma`/`Orden`/
  `ConfiguracionAlarma`), para no depender de una sesión de Hibernate
  que puede estar cerrada cuando el listener corra, ni de relaciones
  lazy, ni de que la configuración global siga teniendo los mismos
  valores más adelante.
- `AlarmaEmailListener.onAlarmaDisparada`, anotado
  `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`,
  consume el evento. `@EnableAsync` ya estaba habilitado en la
  aplicación (`Scheduler.java`); no se agregó infraestructura de
  async nueva, solo este listener.
- **Por qué esto alcanza para ambas garantías pedidas, sin código
  adicional:**
  - *Después del commit:* Spring solo invoca un listener
    `AFTER_COMMIT` si la transacción que publicó el evento
    efectivamente confirmó. Si `registrarLectura` revierte por
    cualquier motivo posterior a la publicación, el listener **nunca**
    se invoca — no hace falta ninguna comprobación extra para evitar
    un correo de una alarma que no quedó persistida.
  - *Fuera del bloqueo:* para cuando el listener corre (después del
    commit), el `PESSIMISTIC_WRITE` de esa transacción ya se liberó;
    `@Async` además lo saca del hilo HTTP original, así que el envío
    no demora la respuesta de la lectura que lo disparó.
- Una aceptación rápida o un cambio de configuración posterior **no
  cancela ni modifica** la notificación ya en curso: el evento ya
  lleva su propia copia de `temperaturaC`/`umbralAplicado`/
  `destinatarios`, fijada en el momento del disparo, no una
  referencia viva a la fila de configuración ni a la alarma.

## Envío y manejo de fallos

- `AlarmaEmailListener` reutiliza `EmailBusiness.sendSimpleMessage`
  (sin modificarlo), una llamada por destinatario. Una falla en un
  destinatario se captura y se loguea; **se sigue intentando con los
  demás** (no se aborta el lote completo por un destinatario
  inválido).
- **Sin reintento automático.** Documentado explícitamente: no hay
  garantía de entrega exactamente una vez. Si el proceso cae entre el
  commit y el envío, o durante el envío mismo, esa notificación se
  pierde sin más rastro que el log de error ya escrito (o ni eso, si
  la caída es anterior a loguear). Es una limitación aceptada para
  esta unidad, no un mecanismo de garantía de entrega — agregar
  reintentos o una cola persistente de envíos pendientes quedaría
  fuera de este alcance si se decide más adelante.

## Receptor SMTP local y configuración

- `spring.mail.properties.mail.smtp.auth`/`.starttls.enable` pasan a
  ser configurables por variable de entorno (`SMTP_AUTH`/
  `SMTP_STARTTLS`), **con default `true`/`true`** (el comportamiento
  de producción no cambia). Para esta verificación se usaron
  `SMTP_AUTH=false`/`SMTP_STARTTLS=false` apuntando a un receptor
  local de pruebas (`SMTP_HOST=127.0.0.1`, `SMTP_PORT=1025`, sin
  autenticación ni TLS, consistente con un servidor de prueba mínimo)
  — sin proveedores ni destinatarios reales; estos valores son del
  entorno de prueba de esta sesión, no quedan como default del
  proyecto.
- Remitente: `MAIL_FROM` (ya existente, sin cambios) apuntado a una
  dirección de prueba (`alertas-iw3@local.invalid`), consistente con
  el criterio ya usado para `SMTP_USER` en unidades anteriores.
- Receptor usado: un servidor SMTP mínimo local (socket TCP propio,
  sin dependencias nuevas), que acepta `EHLO`/`MAIL FROM`/`RCPT TO`/
  `DATA`/`QUIT`, guarda cada mensaje recibido en un archivo de texto, y
  **rechaza intencionalmente** (550) cualquier destinatario cuya
  casilla contenga la palabra "fallo" — así se simula una falla de
  entrega por destinatario sin agregar ningún interruptor al código de
  la aplicación: la falla vive enteramente en la herramienta de
  prueba, externa al repositorio.

## Verificación realizada (los 5 casos acordados)

| # | Caso | Resultado |
| --- | --- | --- |
| 1 | Commit exitoso → mensaje recibido con datos correctos | Lectura con `temperaturaC=35.00` (umbral 33.00) disparó 1 alarma; el receptor local recibió 1 mensaje con `orderNumber`, temperatura y umbral correctos en el cuerpo |
| 2 | Lecturas altas con alarma pendiente → sin mensajes adicionales | Una segunda lectura (`36.00`, orden ya con alarma pendiente) no generó una fila de alarma nueva ni un mensaje nuevo (el receptor siguió en 1 mensaje) |
| 3 | Aceptación + nuevo disparo → nueva notificación | Tras aceptar la alarma anterior, una lectura de `37.00` generó una alarma nueva y un segundo mensaje (receptor: 2 mensajes) |
| 4 | Rechazo de **un** destinatario → continúa con los demás; lectura y alarma persisten, sin afectar la respuesta HTTP | Con 2 destinatarios configurados (uno normal, uno que el receptor rechaza a propósito con 550), ambas lecturas respondieron 200 igual; la alarma y el snapshot de la orden quedaron persistidos (confirmado por SQL); el error del destinatario rechazado quedó solo en el log de la aplicación, sin propagarse a la respuesta HTTP ni afectar al otro destinatario (que sí recibió el mensaje). **No probado:** indisponibilidad total del servidor SMTP (conexión rechazada/timeout para todos los destinatarios a la vez) — el manejo de excepción por destinatario está escrito de forma genérica (`catch (Exception e)`) y debería cubrir ese caso igual, pero no se ejecutó ese escenario específico |
| 5 | Rollback forzado → sin alarma persistida ni envío | Prueba de integración (`AlarmaRollbackIntegrationTest`, JUnit 5 + `@SpringBootTest`) que llama a `registrarLectura` real dentro de un `TransactionTemplate` y fuerza `status.setRollbackOnly()` desde el test (sin tocar `OrdenBusiness` ni agregar ningún endpoint/interruptor). Verificado con una espera de hasta 5 s con sondeo: ni la fila de `Alarma` quedó persistida (ni el snapshot de la orden se actualizó) ni llegó ningún mensaje nuevo al receptor — el listener `AFTER_COMMIT` nunca se invocó, por el mecanismo real, no por una comprobación agregada |

No se repitieron las pruebas de la unidad anterior (configuración,
permisos, 404/409, aislamiento entre órdenes): esta unidad se
enfocó exclusivamente en el envío.

## Archivos afectados

- `AlarmaDisparadaEvent.java` (nuevo) — evento inmutable.
- `AlarmaEmailListener.java` (nuevo) — `@Async` +
  `@TransactionalEventListener(AFTER_COMMIT)`.
- `OrdenBusiness.java` — publica el evento donde ya guardaba la
  `Alarma`; nuevo campo `ApplicationEventPublisher`.
- `ConfiguracionAlarma.java` — método `getDestinatariosList()`
  (parseo del campo ya validado, para construir el snapshot del
  evento).
- `application.properties` — `SMTP_AUTH`/`SMTP_STARTTLS`
  configurables, default sin cambios (`true`/`true`).
- `AlarmaRollbackIntegrationTest.java` (nuevo, test) — verificación 5.

## Pendiente / límites

- No se probó el límite exacto 33.01 °C — ya señalado y con evidencia
  propia en la unidad anterior (`docs/contrato-f6-alarmas-configuracion.md`),
  no repetido aquí.
- **Solo se probó el rechazo de un destinatario individual** (el
  receptor lo rechaza con 550), no una indisponibilidad total del
  servidor SMTP (conexión rechazada o timeout para todos los envíos a
  la vez). El `catch (Exception e)` del listener es genérico y en
  principio cubriría ese caso también, pero no se ejecutó ese
  escenario específico — queda como límite de esta verificación, no
  como una garantía demostrada.
- Sin reintento ni cola de envíos pendientes — ver sección "Envío y
  manejo de fallos". Sin garantía de entrega exactamente una vez: si
  el proceso cae entre el commit y el envío, o durante el envío, esa
  notificación se pierde sin más rastro que el log de error (o ni eso,
  si la caída es anterior a loguear).
- El cuerpo del correo es texto simple en español, sin plantilla ni
  internacionalización — no pedido, no se agregó.
- El receptor SMTP de pruebas (`tools/smtp_test_catcher.py`) queda
  versionado en el repositorio como utilidad de pruebas (no es código
  de producción), con instrucciones mínimas en `tools/README.md`. Su
  archivo de salida (`tools/smtp-received.log`) está excluido por
  `.gitignore`.
- `AlarmaRollbackIntegrationTest` **no depende** de que el receptor
  esté corriendo para pasar (una transacción revertida nunca llega a
  intentar el envío, con o sin receptor disponible), pero reutiliza su
  mismo archivo de salida como punto de verificación compartido; el
  valor real de esa verificación negativa se apoya en haber corrido,
  en la misma sesión, los casos 1-3 con el receptor arriba — ver
  `tools/README.md` para el detalle de esta relación.
- Con esta unidad, **TP28 y TP29 quedan completos** para el alcance
  implementado (ver `docs/requisitos.md`). Esto no implica que **F6**
  en su conjunto esté cerrada: falta contrastar su criterio de salida
  completo antes de darla por terminada.
