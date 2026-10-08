# 07 · Hoja de ruta

Orden de trabajo. Claude Code ejecuta todo seguido, **parando solo tras el APK de la Fase 0**.

## Fase 0 — Spike en el OPPO
Ver `05`. Entrega: APK del spike, `docs/spec/spike-results.md` plantilla. Parada hasta tener resultados.

## Fase 1 — Núcleo de teléfono y contactos
- Proyecto, módulos, tema M3 Expressive, navegación, DI, CI base copiada de UltimateGallery.
- F1 marcador, F2 pantalla de llamada, F3 historial, F4 contactos (sin duplicados todavía), doble SIM.
- Normalización E.164.
- Hito: usarla como teléfono real durante un día sin spam ni datos.

## Fase 2 — Motor de spam local
- Función pura `decide()` con tests exhaustivos del orden de evaluación.
- Lista propia, lista blanca, reglas por prefijo, fuentes por URL, actualización diaria en WorkManager.
- `CallScreeningService` y aviso en la pantalla de llamada.
- Pantallas «Por qué se marcó» y marcar/desmarcar desde historial y llamada.
- Hito: llamada de prueba desde lista propia marcada antes de contestar.

## Fase 3 — Repo de datos y comercios
- Crear `UltimatePhone-data` alineado con UltimateMaps (ver `04`).
- Pipeline de comercios para España, Alemania y Austria como primeras regiones, y de spam con las fuentes viables de `08`.
- Manifiesto firmado, descarga, verificación e instalación en la app.
- Identificación de comercios en llamada, historial y búsqueda.
- Onboarding con elección de regiones.
- Hito: identificar un comercio real al llamar y recibir su llamada.

## Fase 4 — Sync y ajustes
- Cliente WebDAV, fusión, cola offline, ETag.
- Exportación/importación cifrada de ajustes con credenciales.
- Hito: dos teléfonos con la misma lista de spam tras sincronizar.

## Fase 5 — Contactos avanzados y grabación
- Duplicados y fusión, grupos, importar/exportar vCard completos.
- Grabación según resultado del spike.
- Hito: grabar una llamada con el alcance que permitió el spike.

## Fase 6 — Pulido y primera release
- Accesibilidad, rendimiento (50 ms de decisión, 1 s de arranque), traducciones, guía de ColorOS en el onboarding.
- Metadata de F-Droid, capturas, README, `v0.1.0`.

## Definition of Done global
- Todos los AC de `03` cumplidos o con desviación documentada.
- Funciona en el OPPO global y, con el mismo APK, en un emulador AOSP sin GMS; sin dependencias de Google ni de un fabricante concreto.
- `docs/spec/compat-matrix.md` con resultados por dispositivo o emulador.
- Ningún número sale del móvil salvo hacia el Nextcloud del usuario.
- CI verde y release firmada publicada.
- Atribución a OpenStreetMap visible y licencias del repo de datos en orden.

## Riesgos principales
| Riesgo | Mitigación |
|---|---|
| Los fabricantes (ColorOS y otros) matan procesos y la actualización diaria falla | Guía por fabricante en onboarding, medición en spike, comprobación al arrancar de datos caducados |
| Comportamiento distinto según fabricante o ROM | `CapabilityProbe`, funciones opcionales con degradación elegante, matriz de compatibilidad |
| Grabación de llamadas inviable | Decidida en spike; el producto no depende de ella |
| Pocas fuentes abiertas de spam para España | Reglas por prefijo (400), lista propia y sincronizada, URL propia; documentado en `08` |
| Cobertura parcial de comercios | Ajuste claro de expectativas en la app y en el README |
| Licencias de fuentes de datos | Auditoría en CI; excluir lo que no permita redistribuir |
| Cambio de normativa del prefijo 400 | Regla versionada en el repo de datos, actualizable sin nueva versión de la app |
| Plazo de respuesta del filtrado | Consulta en memoria/índice, test de rendimiento obligatorio |
