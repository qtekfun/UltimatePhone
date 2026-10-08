# 06 · CI y releases

**Regla principal**: replicar el modelo de `qtekfun/UltimateGallery`. Claude Code debe leer sus workflows, su gestión de firma y su metadata de F-Droid y copiarlos, cambiando solo nombres e identificadores. Si algo de abajo discrepa de UltimateGallery, **gana UltimateGallery**.

## Resumen del modelo esperado
- GitHub Actions.
- **PR / push a rama**: compilar, lint (ktlint o detekt, el que use UltimateGallery), tests unitarios, build `debug`.
- **Release**: al empujar una etiqueta `vX.Y.Z`, build `release` firmado y publicación del APK como asset de una GitHub Release, con notas de versión.
- **Sin release-please**: las versiones y las notas se gestionan como en UltimateGallery, no con automatización de release-please.
- **F-Droid**: metadata en `fastlane/metadata/android/<locale>/` (título, descripción, capturas, changelogs por `versionCode`) para que F-Droid construya desde el repo. Cuidar que no haya dependencias propietarias ni binarios sin fuente.
- Locales: `en-US` y `es-ES`.

## Específico de UltimatePhone
- La app no incluye datos pesados. Los paquetes viven en el repo de datos y se descargan en ejecución, así que el APK es pequeño y F-Droid no ve datos de terceros dentro.
- Incrustar la **clave pública** del manifiesto de datos en el código; la privada solo existe como secret en `UltimatePhone-data`.
- Comprobar en CI que no hay permisos ni dependencias de Google, incluidas las transitivas (script que falla si aparece GMS, Firebase o ML Kit).
- Tests instrumentados en un emulador **AOSP sin GApps** además de uno con Google APIs, para vigilar la compatibilidad universal.
- Tests de la lógica pura (motor de decisión, normalización, fusión de sync, cifrado de exportación) obligatorios en CI.
- Dependabot o Renovate solo si UltimateGallery los usa.
- Documentar en el README cómo verificar la firma del APK.

## Definition of Done de una release
- CI en verde, tag creada, APK firmado adjunto, notas en inglés y español, changelog de F-Droid añadido.
