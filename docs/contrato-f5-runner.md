# Contrato — Runner del circuito completo (F5, primera unidad, TP22/TP25)

Cubre TP22 (demostración completa de los 5 puntos desde Postman, sin
pantallas propias) y TP25 (runner opcional recomendado, que encadena
`orderNumber`/clave/token automáticamente, sin depender de variables
globales compartidas). No incluye revisión integral de Swagger/
colecciones existentes (TP23/TP24 en su forma final) ni el ensayo de
presentación (TP26, cuya programación depende de acordar fecha, no de
que esté bloqueado técnicamente).

## Colección: `postman/iw3-f5-runner-circuito.postman_collection.json`

9 requests encadenados sobre una sola orden por iteración, sin
intervención manual entre pasos: login → recepción (1) → tara (1→2) →
activación → lectura 1 (aceptada) → lectura 2 (aceptada, masa mayor) →
cierre (2→3) → pesaje final (3→4, con conciliación) → consulta de
conciliación (debe coincidir con el pesaje final).

### Propagación automática, sin imprimir ni exportar secretos

`orderNumber`/`extSuffix` se generan en el pre-request de "01 - Login"
a partir de `Date.now()` y el índice de iteración (`pm.info.iteration`);
`claveActivacion` se captura de la respuesta de tara; `jwtToken` se
captura de login. Los tres quedan únicamente en variables de
**colección** (`pm.collectionVariables`), nunca en variables de
entorno/globales ni impresos por `console.log`; ningún test incluye el
valor real de `jwtToken`/`claveActivacion` en su descripción, solo
comparaciones.

### Validación de estado y contenido en cada paso, con corte en cascada

Cada item valida el código HTTP **y** el cuerpo de la respuesta (no
solo el status). Si alguna validación de un item falla, ese item llama
a `pm.execution.setNextRequest(null)`, lo que detiene el resto de **esa
iteración** — las iteraciones siguientes (`--iteration-count`) arrancan
de nuevo desde "01 - Login" con sus propios datos, no dependen de si la
iteración anterior falló. Esto evita que un paso roto (p. ej. una tara
rechazada, sin `claveActivacion` capturada) produzca fallos en cascada
en los pasos posteriores que dependen de sus datos.

### Datos distintos por iteración, con valor esperado de conciliación

| Iteración (índice) | taraKg | masa1 | masa2 (última aceptada) | pesoBrutoKg | netoKg esperado | diferenciaKg esperada |
| --- | --- | --- | --- | --- | --- | --- |
| 0 | 12000.000 | 2000.000 | 4000.000 | 16080.000 | 4080.000 | **+80.000** |
| 1 | 10000.000 | 1500.000 | 3000.000 | 12900.000 | 2900.000 | **-100.000** |

El dataset se elige por `pm.info.iteration % 2`, no al azar: cada
iteración tiene un resultado de conciliación distinto y predecible, y
el item 8 compara `diferenciaKg` (y `netoKg`/`productoCargadoKg`/
`taraKg`/`pesoBrutoKg`/últimos valores de telemetría) contra el valor
esperado de **esa** iteración, no solo contra "algún número".

## Verificación ejecutada en esta unidad

Con la app arrancada localmente (`mysqldev`, MySQL ya levantado):

- **`postman collection run --iteration-count 2`** (camino feliz, dos
  órdenes distintas, una por iteración): **18 requests, 68 aserciones,
  0 fallos.** `orderNumber` distinto entre ambas iteraciones
  (confirmado en la salida), confirmando que la reinicialización de
  variables no depende de un flag persistente ni reutiliza datos entre
  iteraciones.
- **Corte en cascada, caso 1 (falla en el primer paso):** se corrió la
  misma colección con credenciales de login deliberadamente incorrectas
  (`--env-var` con usuario/contraseña inexistentes, no persistido en
  ningún archivo). Resultado: **2 requests ejecutados en total** (solo
  "01 - Login" en cada una de las dos iteraciones), **4/4 aserciones
  fallidas** reportadas y **los 8 pasos restantes omitidos en ambas
  iteraciones** — confirma que `pm.execution.setNextRequest(null)`
  detiene el resto de cada iteración ante un fallo temprano.
- **Corte en cascada, caso 2 (falla a mitad de camino):** sobre una
  copia temporal de la colección (no versionada, descartada después de
  la prueba) con `taraKg` forzado a `0` en el item de tara (rechazado
  por `@Positive`, 400), se corrió `--iteration-count 2`: **6 requests
  ejecutados en total** (login, recepción y tara en cada iteración;
  activación/lectura/cierre/pesaje final/conciliación omitidos en las
  dos) — confirma que el corte funciona también desde la mitad del
  circuito, no solo ante una falla en el primer paso.
- Se corrigió `postman.setNextRequest(null)` (API deprecada, generaba
  una advertencia en la salida de `postman collection run`) por
  `pm.execution.setNextRequest(null)` en los 10 lugares donde aparecía;
  se repitió el camino feliz después del cambio — mismo resultado (18
  requests, 68 aserciones, 0 fallos), sin advertencias.

## Límites de esta unidad

- Solo verifica lo que la respuesta HTTP expone; no lee la base de
  datos (no hace falta: el circuito ya fue validado unidad por unidad
  con evidencia de BD en F3/F4 — ver `docs/contrato-f4-cierre.md` y
  `docs/contrato-f4-pesaje-conciliacion.md`; este runner demuestra el
  camino feliz de punta a punta, no agrega una regla de negocio nueva).
- No ejercita casos de error/validación (404/400/409/401): esos ya
  están cubiertos en las colecciones de F2/F3/F4, que esta unidad no
  modifica ni reemplaza.
- No incluye la revisión integral de Swagger/Postman (TP23/TP24
  completos) ni el ensayo de presentación (TP26) — quedan para
  unidades posteriores de F5.
- Sin cambios de código Java en esta unidad: es automatización de
  pruebas sobre endpoints ya implementados y verificados.
