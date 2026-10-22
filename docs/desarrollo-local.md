# Desarrollo local — F1

Fecha de revisión: 20/10/2026. Backend activo: `iw3-backend-sanitized`, rama
`feature/setup-inicial`. Esta guía documenta el entorno y las pruebas reportadas
por el equipo. Se revisaron los archivos de configuración y las dos clases Java
incluidas en el ZIP; no se repitieron aquí las pruebas ni se dispuso del repo
completo. Los comandos consolidados de esta guía no se ejecutaron de nuevo.

## 1. Qué se busca demostrar

Arrancar la base docente adaptada con una BD local y verificar el circuito
usuario → login → JWT → recurso protegido. Esto prepara la infraestructura
para implementar el dominio de órdenes de carga. Los productos usados en la
prueba son ejemplos del profesor; no satisfacen requisitos de órdenes de gas.

El uso de contenedor, variables de inicialización y persistencia se relaciona
con las clases de Docker/MySQL. La imagen 8.0.43 fue identificada por el equipo
en la tabla de servicios de Clase 7 (despliegue). Su reutilización local es
una decisión propia. No se agrega framework, dependencia ni despliegue a VM.

## 2. Requisitos y archivos locales

- Docker Engine y Compose disponibles. La máquina del equipo requiere `sudo`.
- JDK 21. Ruta usada: `~/.local/opt/jdk-21.0.12.1+1`.
- Maven Wrapper de `iw3/`; no hace falta instalar Maven aparte.
- Puerto local 3306 disponible y `compose.local.yaml` en la raíz Git.
- `.env.mysql`, `.env.jwt` y, para repetir el login, `.env.testuser` locales.
  Según el registro de ejecución, se crearon con permisos 600 y quedan ignorados.
  No se incluyen sus valores reales en el repositorio ni en esta documentación.

| Archivo local | Contenido necesario | Quién lo usa |
| --- | --- | --- |
| `.env.mysql` | `DB_USERNAME`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD` | Compose; Spring necesita las dos primeras |
| `.env.jwt` | `JWT_SECRET`, clave aleatoria propia | Spring para firmar/verificar JWT |
| `.env.testuser` | Credenciales del usuario de prueba | Pruebas locales; no lo carga Spring automáticamente |
| `.env.example` | Referencia ficticia versionable | Documentación; no usar como credenciales reales |

En la máquina existente, conservar los archivos ya generados. No regenerar
contraseñas para un volumen inicializado sin cambiar también las cuentas en
MySQL. Para otra máquina, generar secretos propios y preparar estos archivos
antes del primer arranque. El usuario de aplicación debe ser distinto de root.
No se presupone el nombre de las variables internas de `.env.testuser`, cuyo
contenido no se recibió para esta revisión.

## 3. Levantar y comprobar MySQL

Desde la raíz del backend:

```bash
cd ~/code/university/iw3-integrador-2026/iw3-backend-sanitized
sudo docker compose --env-file .env.mysql -f compose.local.yaml config --quiet
sudo docker compose --env-file .env.mysql -f compose.local.yaml up -d mysql
sudo docker compose --env-file .env.mysql -f compose.local.yaml ps
sudo docker compose --env-file .env.mysql -f compose.local.yaml logs --tail=40 mysql
```

`--env-file` aporta los valores para interpolar `${...}` en Compose. No exporta
variables a una aplicación Java ejecutada por separado. `config --quiet`
valida la configuración sin imprimir la configuración resuelta con secretos.
Esperar el mensaje de MySQL `ready for connections` antes de arrancar Spring.

Comprobación con el usuario de aplicación, sin imprimir su contraseña:

```bash
sudo docker compose --env-file .env.mysql -f compose.local.yaml exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 -u "$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT VERSION() AS version, DATABASE() AS base, CURRENT_USER() AS usuario; SHOW TABLES;"'
```

Resultado registrado: versión `8.0.43`, base `iw3_2026`, usuario `iw3_app@%`.
Esta consulta conecta dentro del contenedor; por sí sola no prueba el puerto
publicado en el host. El arranque exitoso de Spring con JDBC `localhost:3306`
aporta esa evidencia adicional.

El mapeo es `127.0.0.1:3306:3306`: host 3306 → contenedor 3306, solo en loopback.
Los datos se guardan en `iw3-local_mysql-data`. Recrear el servicio conserva
ese volumen. **No usar `down -v`** para cambiar puertos o reiniciar.

## 4. Arrancar Spring con el perfil correcto

Desde la raíz del backend, en Bash y con los archivos locales propios:

```bash
cd iw3
export JAVA_HOME="$HOME/.local/opt/jdk-21.0.12.1+1"
export PATH="$JAVA_HOME/bin:$PATH"

set -a
source ../.env.mysql
source ../.env.jwt
set +a

export SMTP_USER=local-test@example.invalid
export SMTP_PASS=local-test-placeholder-not-real
export SPRING_PROFILES_ACTIVE=mysqldev
export EXPIRED_NOTIFICATION_ENABLED=false

./mvnw -Dbuild=war -Pmysqldev spring-boot:run
```

`source` ejecuta el contenido de los archivos: usar solo los archivos propios
preparados para este proyecto. `set -a` hace que sus variables se exporten al
proceso Java. No ejecutar con trazado `set -x` ni imprimir el entorno completo.

- `-Dbuild=war` activa explícitamente el empaquetado requerido en este POM.
- `-Pmysqldev` selecciona el perfil Maven; no activa por sí solo el perfil Spring.
- `SPRING_PROFILES_ACTIVE=mysqldev` selecciona el perfil real de la aplicación.
- El perfil usa JDBC `localhost:3306/iw3_2026`. La URL base sin perfil sigue en
  `33306`; no se debe omitir la selección runtime de `mysqldev`.
- El interruptor del job queda explícitamente en `false`, también su default.
  Los valores SMTP ficticios permiten resolver las propiedades requeridas;
  **no son un bloqueo global de red ni de todas las llamadas a correo**.
  No invocar endpoints de envío durante estas pruebas.

Resultados reportados: `Perfil activo mysqldev`, inicio en aproximadamente
4,8 segundos, HTTP 8080 y job de productos vencidos deshabilitado. El tiempo
no es un requisito. Hibernate creó `categories`, `cli1_products`, `products`,
`roles`, `userroles`, `users`, usando `ddl-auto=update` heredado. Esto no equivale
a disponer de migraciones versionadas para producción.

## 5. Usuario de prueba y contrato de login

La inspección local reportó que no existe alta de usuarios en la API o negocio
actual. El procedimiento utilizado fue generar BCrypt con la utilidad heredada
`GET /demo/encodepass` (parámetro `password`), insertar un rol y un usuario, y
relacionarlos. Es una vía disponible de preparación de datos, no la única
forma posible de generar BCrypt ni una función de registro implementada.

Se verificaron las columnas con `DESCRIBE` antes de insertar. Plantilla SQL
ilustrativa (los marcadores deben sustituirse localmente; no es un script listo
para ejecutar):

```sql
INSERT INTO roles (description, name)
VALUES ('<descripcion>', '<nombre_rol>');

INSERT INTO users (account_non_expired, account_non_locked,
                   credentials_non_expired, enabled, email, username, password)
VALUES (1, 1, 1, 1, '<email_prueba>', '<usuario_prueba>', '<hash_bcrypt>');

INSERT INTO userroles (id_user, id_role)
VALUES (<id_usuario_creado>, <id_rol_creado>);
```

Obtener los IDs generados; no asumir `1` ni repetir inserciones si ya existe
el usuario. Las cuatro banderas se establecen explícitamente en `1`. Las
inicializaciones de la entidad Java no se aplican a un INSERT SQL directo.
La creación de los tres registros debería tratarse como una transacción al
preparar otro usuario; no se afirma que el procedimiento original lo hiciera.

Usuario registrado en esta prueba: `qa_f1_dfab890c`; rol: `ROLE_QA_TEST`.
La contraseña quedó solo en `.env.testuser`. No incluir contraseñas, hashes ni
JWT en capturas o documentación. La utilidad de hash usa GET: reservarla al
entorno local de prueba y evitar registrar su URL con la contraseña.

### 5.1. Usuarios de prueba por rol (F6, TP27)

Mismo procedimiento (rol + usuario + relación insertados a mano), con
una variante: el hash BCrypt se generó con un programa Java de un solo
uso (`BCryptPasswordEncoder`, reutilizando dependencias ya presentes en
el proyecto — `spring-security-crypto` y `mysql-connector-j`, sin
instalar nada nuevo) en vez de `GET /demo/encodepass`, para no exponer
la contraseña en texto plano en la URL de una petición HTTP. El
programa lee usuario/clave desde un archivo local (no desde argumentos
de línea de comandos ni variables impresas) e inserta directamente con
JDBC; no imprime contraseñas ni hashes.

Tres usuarios nuevos: uno con únicamente `ROLE_OPERADOR`, uno con
únicamente `ROLE_SUPERVISOR`, uno sin ningún rol asignado (fila
`userroles` vacía). Credenciales en `.env.testroles` (gitignored,
permisos `600`), no en `.env.testuser` (que sigue siendo del usuario de
F1, sin modificar). Detalle de la matriz de permisos y la verificación
en `docs/contrato-f6-roles-permisos.md`.

Login: `POST /api/v1/login`, cuerpo de formulario con `username` y `password`.
Respuesta correcta: HTTP 200 y JWT en el cuerpo. Para el recurso protegido,
enviar `Authorization: Bearer <token>` a `GET /api/v1/products`.

## 6. Evidencia registrada y límites

| Caso | Resultado informado |
| --- | --- |
| `GET /v3/api-docs` | 200 |
| `GET /swagger-ui.html` | 302, redirección |
| Login correcto | 200, JWT emitido |
| Productos con JWT válido | 200 |
| Login con contraseña incorrecta | 401 |
| Productos con JWT alterado | 401, error de firma esperado |
| Productos sin token | 401, probado antes y no repetido |

El equipo reportó cero correos enviados. Esta revisión confirma la salida
anticipada del job en el código recibido, pero no inspeccionó todos los otros
posibles emisores de correo. El rol de prueba no demuestra autorización por
roles del dominio. No se ejecutaron pruebas de expiración de token, arranque
real sin `JWT_SECRET`, correo real ni requisitos funcionales de gas.

## 7. Detención y continuidad

Para una ejecución en primer plano, detener Spring con `Ctrl+C` en su terminal.
Si corre en segundo plano, identificar el proceso actual antes de detenerlo;
no reutilizar ciegamente un PID anotado en otra sesión.

Para detener MySQL conservando el volumen, desde la raíz Git:

```bash
sudo docker compose --env-file .env.mysql -f compose.local.yaml stop mysql
```

No hace falta repetir pruebas ya registradas para incorporar esta documentación.
La siguiente acción es revisar el diff, guardar el checkpoint y publicar los
archivos versionables de F1. Las dudas de la consigna y el diseño del dominio
permanecen abiertas; esta guía no las resuelve.
