# UltimatePhone — paquete SDD

App Android de **teléfono, contactos y filtro de spam 100 % local**, de la familia Ultimate (`com.qtekfun.ultimatephone`).
Pensada para ejecutarse con Claude Code de forma spec-driven, de una sola tirada, con la Fase 0 como único punto que necesita tu móvil.

## Contenido

| Archivo | Para qué sirve |
|---|---|
| `CLAUDE.md` | Reglas permanentes para Claude Code dentro del repo (copiar a la raíz). |
| `01-vision-y-alcance.md` | Qué es, qué no es, decisiones cerradas en la entrevista. |
| `02-arquitectura.md` | Roles de Android, módulos, almacenamiento, motor de decisión de spam. |
| `03-funcionalidades.md` | Especificación funcional con criterios de aceptación. |
| `04-repo-de-datos.md` | Repo separado `UltimatePhone-data`: paquetes de comercios y spam por región. |
| `05-fase0-spike-oppo.md` | Spike en el OPPO: viabilidad de rol, pantalla de llamada, filtrado y grabación. |
| `06-ci-releases.md` | CI y releases al estilo UltimateGallery. |
| `07-hoja-de-ruta.md` | Fases, orden de trabajo y Definition of Done. |
| `08-investigacion-fuentes.md` | Qué se encontró sobre fuentes de spam y comercios, con huecos y riesgos. |

## Cómo lanzarlo

1. Crea el repo `qtekfun/UltimatePhone` y el repo `qtekfun/UltimatePhone-data`.
2. Copia esta carpeta a `docs/spec/` del repo de la app y `CLAUDE.md` a la raíz.
3. Pide a Claude Code: «Lee `docs/spec/README.md` y ejecuta la hoja de ruta completa. Para solo tras construir el APK del spike (Fase 0) y espera mis resultados.»
4. Pruebas el APK del spike en el OPPO, rellenas la tabla de resultados de `05-fase0-spike-oppo.md` y le dices que continúe.

## Decisiones pendientes de confirmar (no bloquean)

- **Autonomía de Git**: la spec asume trabajar con ramas y PR y mergear si pasan los checks, como en otros repos Ultimate. Ajústalo en `CLAUDE.md` si no quieres esto aquí.
- **Layout del repo de datos**: pediste hacerlo «como en UltimateMaps». No tengo ese repo delante, así que `04-repo-de-datos.md` propone un diseño propio y manda a Claude Code a **inspeccionar el repo de datos de UltimateMaps y alinearse con él** donde difiera.
- **Prefijo 400**: la obligatoriedad desde el 17 de octubre de 2026 viene de prensa y blogs, no del BOE leído directamente. Claude Code debe verificarlo en la resolución antes de fijar la regla.
