# Decisiones del equipo

## Saneamiento de credenciales antes del primer respaldo remoto

- El historial heredado de la base docente (`upstream`, `magm3333/iw3_2026`,
  commit `b7e705de737e5b1039719807bb413ec89279e7d1`) contenia una contrasena
  de base de datos de produccion, una contrasena de base de datos de
  desarrollo, una contrasena SMTP por defecto y una clave de firma JWT
  hardcodeada, todas embebidas en archivos versionados.
- Decision del equipo: no reutilizar esas credenciales en ningun despliegue
  propio y no republicarlas en nuestro `origin` publico, aunque ya fueran
  publicas en el repositorio del profesor.
- Se reescribio el historial en una copia separada del repositorio
  (`iw3-backend-sanitized`), reemplazando esos cuatro valores en todos los
  commits de las ramas `main`, `develop` y `feature/setup-inicial`, con
  `git-filter-repo --replace-text` corrido desde un script Python extraido
  manualmente del paquete (instalado en un directorio de usuario, sin sudo
  y sin modificar el Python del sistema, porque `python3-venv` no estaba
  disponible). Autoria y mensajes de commit originales se conservaron.
- El clon local original (`iw3-backend`, con el remoto `upstream` intacto)
  no se modifico y queda como respaldo con el historial sin tocar.
- Los cuatro valores se externalizaron via variables de entorno
  (`DB_USERNAME`, `DB_PASSWORD`, `SMTP_USER`, `SMTP_PASS`, `JWT_SECRET`),
  documentadas con valores ficticios en `.env.example`. Ninguna tiene valor
  por defecto en el codigo: si falta alguna, el arranque de Spring falla
  (placeholder no resuelto) o, en el caso de `JWT_SECRET`, una validacion
  explicita en `SecurityConfiguration` rechaza un valor vacio.

## Importacion de futuras actualizaciones de `upstream`

Riesgo: si se hace `git fetch upstream` seguido de un merge o rebase
directo contra una rama ya publicada con historial saneado, los commits
nuevos del profesor pueden volver a traer valores hardcodeados (los mismos
u otros nuevos). Un `git cherry-pick` normal seguido de un commit
correctivo tampoco alcanza: el commit del cherry-pick ya habria quedado
creado con el secreto adentro, aunque un commit posterior lo corrija en el
arbol de trabajo.

Procedimiento acordado:

1. `git fetch upstream` sin integrar nada todavia.
2. Revisar el diff de los commits nuevos (`git log upstream/main ^main`,
   `git show <commit>`) buscando credenciales o configuracion insegura con
   el mismo criterio usado en esta revision (grep de `password`, `secret`,
   `smtp`, defaults embebidos, etc.).
3. Si un commit nuevo toca archivos sensibles, traerlo con
   `git cherry-pick -n <commit>` (sin crear el commit todavia), corregir el
   contenido en el arbol de trabajo (por ejemplo, volver a aplicar el mismo
   patron de variables de entorno) y recien ahi hacer `git commit` con un
   mensaje que documente la correccion. Asi el valor sensible nunca llega a
   formar parte de un commit, ni siquiera de uno intermedio.
4. Si un commit nuevo no toca nada sensible, se puede integrar normalmente.
5. Nunca hacer `push --force` sobre una rama ya publicada para "arreglar"
   un secreto que se coló: hay que evitarlo antes del commit, no despues
   del push.
