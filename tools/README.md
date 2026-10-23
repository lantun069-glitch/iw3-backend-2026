# tools/

Utilidades de prueba locales, no código de producción.

## `smtp_test_catcher.py`

Receptor SMTP mínimo para verificar el envío de correo de alarmas
(TP28/TP29, F6) sin proveedores ni destinatarios reales. Detalle
completo del mecanismo y de los casos verificados con él en
`docs/contrato-f6-correo-alarmas.md`.

**Uso:**

```bash
python3 -I tools/smtp_test_catcher.py            # puerto 1025, salida en tools/smtp-received.log
python3 -I tools/smtp_test_catcher.py 1025 /ruta/propia.log   # parámetros opcionales
```

Apuntar la aplicación al receptor (variables de entorno; los valores
por defecto de `application.properties` siguen apuntando a un
proveedor real, sin cambios):

```bash
export SMTP_HOST=127.0.0.1
export SMTP_PORT=1025
export SMTP_AUTH=false
export SMTP_STARTTLS=false
export SMTP_USER=local-test@example.invalid
export SMTP_PASS=local-test-placeholder-not-real
export MAIL_FROM=alertas-iw3@local.invalid
```

Un destinatario cuya casilla contenga la palabra "fallo" (por ejemplo
`fallo@local.invalid`) es rechazado a propósito por el receptor, para
probar que una falla de **un** destinatario no impide la entrega a
los demás. Esto no simula una caída total del servidor SMTP
(indisponibilidad del servidor, no probada en esta unidad).

El archivo de salida (`tools/smtp-received.log` por defecto) queda
excluido por `.gitignore`; no contiene datos reales.

**Relación con `AlarmaRollbackIntegrationTest`:** ese test (en
`iw3/src/test/java/.../AlarmaRollbackIntegrationTest.java`) lee el
mismo archivo de salida para confirmar que una lectura cuya
transacción se revierte no agrega ningún mensaje nuevo. El test no
requiere que este receptor esté corriendo para pasar (una transacción
revertida nunca llega a intentar el envío); correrlo igual, en la
misma sesión que los casos de envío exitoso, es lo que le da valor de
evidencia real al archivo compartido — confirma negativamente contra
un receptor que sí funciona, no contra uno que nunca se usó.
