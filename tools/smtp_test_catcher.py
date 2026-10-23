#!/usr/bin/env python3
"""Receptor SMTP minimo para pruebas locales de TP28/TP29 (F6).

No es un servidor SMTP compliant ni esta pensado para produccion: solo
implementa lo minimo (EHLO/MAIL FROM/RCPT TO/DATA/QUIT) para que
JavaMailSender pueda conectarse, enviar y recibir una respuesta. No usa
librerias de terceros (socket estandar de Python), para no agregar una
dependencia nueva al proyecto solo para esto.

Uso (ver docs/contrato-f6-correo-alarmas.md para el detalle completo):

    python3 -I tools/smtp_test_catcher.py [puerto] [archivo_de_salida]

Default: puerto 1025, archivo de salida
./tools/smtp-received.log (gitignored, ver .gitignore).

Apuntar la aplicacion a este receptor con variables de entorno, sin
tocar ningun valor por defecto (que sigue siendo un proveedor real):

    export SMTP_HOST=127.0.0.1
    export SMTP_PORT=1025
    export SMTP_AUTH=false
    export SMTP_STARTTLS=false
    export SMTP_USER=local-test@example.invalid
    export SMTP_PASS=local-test-placeholder-not-real
    export MAIL_FROM=alertas-iw3@local.invalid

Simulacion de falla de UN destinatario (sin tocar el codigo de la
aplicacion): cualquier direccion cuya casilla contenga la palabra
"fallo" (ej. fallo@local.invalid) se rechaza con 550 al RCPT TO. El
resto de los destinatarios de ese mismo envio se procesan normalmente,
asi que esto solo ejercita el caso "un destinatario individual falla,
los demas se entregan igual" - no simula una caida total del servidor
SMTP (eso no se probo en esta unidad, ver docs/contrato-f6-correo-alarmas.md).

AlarmaRollbackIntegrationTest (iw3/src/test/.../AlarmaRollbackIntegrationTest.java)
lee el mismo archivo de salida para confirmar que una lectura
revertida no agrega ningun mensaje nuevo. Ese test no requiere que
este receptor este corriendo para pasar (una transaccion revertida
nunca llega a intentar el envio, con o sin receptor disponible); se
documenta la relacion para que quede claro que, si se corre junto con
los casos 1-3 de esa unidad en la misma sesion, el archivo compartido
es evidencia real de que no se agrego un mensaje de mas - no al reves.
"""

import os
import socket
import sys
import threading

DEFAULT_PORT = 1025
# Relativo a este archivo, no al directorio de trabajo desde el que se
# invoque: siempre queda en tools/smtp-received.log, sin importar si se
# corre desde la raiz del repo o desde iw3/.
DEFAULT_OUTFILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "smtp-received.log")


def handle(conn, outfile):
    conn.settimeout(10)
    f = conn.makefile("rwb")

    def send(line):
        f.write((line + "\r\n").encode())
        f.flush()

    send("220 localtest.invalid SMTP catcher ready")
    mail_from = None
    rcpt_to = []
    data_mode = False
    data_lines = []
    while True:
        line = f.readline()
        if not line:
            break
        line = line.decode(errors="replace").rstrip("\r\n")
        if data_mode:
            if line == ".":
                data_mode = False
                body = "\n".join(data_lines)
                with open(outfile, "a") as out:
                    out.write("---MESSAGE---\n")
                    out.write("FROM: %s\n" % mail_from)
                    out.write("TO: %s\n" % ",".join(rcpt_to))
                    out.write(body + "\n")
                    out.write("---END---\n")
                send("250 OK queued")
                mail_from = None
                rcpt_to = []
                data_lines = []
            else:
                data_lines.append(line)
            continue
        upper = line.upper()
        if upper.startswith("EHLO") or upper.startswith("HELO"):
            send("250-localtest.invalid")
            send("250 OK")
        elif upper.startswith("MAIL FROM:"):
            mail_from = line.split(":", 1)[1].strip()
            send("250 OK")
        elif upper.startswith("RCPT TO:"):
            addr = line.split(":", 1)[1].strip().strip("<>")
            rcpt_to.append(addr)
            if "fallo" in addr.lower():
                send("550 No such user here (simulado, solo para pruebas)")
            else:
                send("250 OK")
        elif upper.startswith("DATA"):
            send("354 End data with <CR><LF>.<CR><LF>")
            data_mode = True
            data_lines = []
        elif upper.startswith("QUIT"):
            send("221 Bye")
            break
        elif upper.startswith("RSET"):
            mail_from = None
            rcpt_to = []
            send("250 OK")
        elif upper.startswith("NOOP"):
            send("250 OK")
        else:
            send("250 OK")
    conn.close()


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PORT
    outfile = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_OUTFILE

    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", port))
    srv.listen(5)
    print("SMTP catcher de pruebas escuchando en 127.0.0.1:%d, salida en %s" % (port, outfile), flush=True)
    while True:
        conn, _ = srv.accept()
        t = threading.Thread(target=handle, args=(conn, outfile), daemon=True)
        t.start()


if __name__ == "__main__":
    main()
