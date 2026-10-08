# CLAUDE.md — UltimatePhone

## Qué es este repo
App Android de teléfono, contactos y spam local (`com.qtekfun.ultimatephone`). La especificación vive en `docs/spec/`. Antes de tocar código, lee `docs/spec/README.md` y el documento de la fase en curso.

## Reglas
- **Idioma**: código, commits, issues, PR y documentación del repo en inglés. La app se entrega en español e inglés (strings en `values/` en inglés y `values-es/`).
- **Stack**: Kotlin, Jetpack Compose, Material 3 Expressive con color dinámico, minSdk 31 (Android 12), Room, WorkManager, Hilt o Koin (elige uno y no mezcles).
- **Privacidad**: ningún número de teléfono sale del dispositivo salvo hacia el Nextcloud configurado por el usuario. Sin analíticas, sin crash reporting remoto, sin SDK de Google. **La app debe funcionar en cualquier Android 12+, con o sin GMS y de cualquier fabricante**: nada de APIs de un OEM como requisito, capacidades detectadas en ejecución con degradación elegante.
- **Red**: solo para (a) descargar paquetes del repo de datos, (b) fuentes de spam definidas, (c) WebDAV del usuario. Todo configurable y desactivable.
- **Licencia**: GPLv3 para el código de la app. Los datos derivados de OpenStreetMap se tratan según `docs/spec/04-repo-de-datos.md` (ODbL).
- **Nunca** registrar números de teléfono completos en logs de producción.
- **Tests**: toda lógica pura (normalización, motor de decisión, fusión de sync, cifrado de exportación) con tests unitarios. Un PR sin tests de lo que cambia no se mergea.
- **CI y releases**: copiar el modelo de `qtekfun/UltimateGallery` (workflows, firma, F-Droid metadata). Si `docs/spec/06-ci-releases.md` y UltimateGallery difieren, **gana UltimateGallery**.
- **Repo de datos**: `qtekfun/UltimatePhone-data`. Inspeccionar el repo de datos de UltimateMaps y alinearse con su estructura de manifiesto, releases y verificación cuando aplique.
- **Git**: ramas por fase, PR, merge si los checks pasan.
- **Parar solo** en la Fase 0 (necesita el APK probado en el OPPO). El resto se ejecuta de seguido.
- Si una decisión de la spec choca con la realidad técnica, documenta la desviación en `docs/spec/DEVIATIONS.md` y sigue con la alternativa más cercana a la intención original.
