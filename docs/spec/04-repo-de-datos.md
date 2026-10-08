# 04 · Repo de datos: `UltimatePhone-data`

Repo **separado** de la app. Genera y publica paquetes de comercios y de spam por región. La app solo descarga paquetes ya preparados.

> **Alineación con UltimateMaps**: antes de implementar, Claude Code debe inspeccionar el repo de datos de UltimateMaps y reutilizar su estructura de manifiesto, nomenclatura de releases, verificación y workflows donde encaje. Lo que sigue es el diseño por defecto si no hay equivalencia.

## Por qué separado
- Los datos se actualizan por calendario, no por versión de la app.
- Los datos derivados de OpenStreetMap tienen licencia propia (ODbL) distinta de la GPLv3 de la app.
- La app descarga binarios pequeños y firmados, no procesa extractos enormes.

## Estructura
```
UltimatePhone-data/
  sources/
    businesses/osm.yaml          # regiones, URLs de extractos, etiquetas OSM a usar
    businesses/other-open.yaml   # otras fuentes abiertas validadas, con licencia
    spam/*.yaml                  # una ficha por fuente de spam: URL, formato, licencia, nivel
    rules/*.yaml                 # reglas por prefijo por país
  pipeline/                      # scripts de construcción (Python + osmium)
  tests/                         # tests del pipeline y de los paquetes de ejemplo
  LICENSES/                      # ODbL, licencias de cada fuente
  .github/workflows/
    build-businesses.yml         # mensual
    build-spam.yml               # diario
    verify.yml                   # PR: lint, tests, auditoría de licencias
  README.md
```

## Manifiesto
`manifest.json` en la última release, firmado (Ed25519, clave pública incrustada en la app):
```json
{
  "schema": 1,
  "generatedAt": "2026-11-01T03:00:00Z",
  "packs": [
    {
      "id": "businesses-es",
      "type": "businesses",
      "region": "ES",
      "version": "2026.11.01",
      "url": "https://github.com/qtekfun/UltimatePhone-data/releases/download/.../businesses-es.db.zst",
      "sha256": "...",
      "bytes": 0,
      "entries": 0,
      "license": "ODbL-1.0",
      "attribution": "© OpenStreetMap contributors",
      "dependsOn": []
    }
  ]
}
```
Tamaños y recuentos reales los rellena el pipeline; la app muestra el tamaño del manifiesto en el onboarding.

## Paquetes de comercios
- **Fuente**: extractos regionales de OpenStreetMap (Geofabrik u otro espejo estable). Nada de consultar Overpass en masa ni raspar webs.
- **Filtrado**: elementos con `phone`, `contact:phone` o `contact:mobile`, y una etiqueta de comercio o servicio (`shop`, `amenity`, `office`, `craft`, `healthcare`, `tourism`, etc.).
- **Normalización**: varios teléfonos por elemento; todo a E.164 con libphonenumber usando el país del elemento. Descartar los inválidos.
- **Campos**: `e164`, `name`, `brand` opcional, `category` (mapa propio a una lista corta con icono), `osm_id`.
- **Deduplicación**: un número con varios nombres conserva el de mayor calidad y guarda hasta N alternativas.
- **Regiones**: por país; países grandes por subregión. La unidad de descarga la decide el pipeline según tamaño. Cobertura mundial progresiva: empezar por España, Alemania, Austria, y ampliar por configuración, no por código.
- **Formato**: SQLite con `numbers(e164 PRIMARY KEY, name, category, ...)` y FTS5 de `name`, comprimido con zstd.
- **Cobertura esperada**: parcial. En una medición pública sobre restaurantes de una ciudad francesa, solo cerca del 41 % tenía teléfono mapeado. El producto no debe prometer cobertura total.

### Licencia y atribución (obligatorio)
- Los paquetes derivados de OpenStreetMap se publican bajo **ODbL 1.0** con atribución «© OpenStreetMap contributors».
- Si se redistribuye una base derivada, aplican las condiciones de compartir igual de la ODbL; el repo de datos debe mantener ese tratamiento y no mezclar en el mismo paquete datos con licencia incompatible.
- La app muestra la atribución y enlaza a la licencia.
- Claude Code redacta `LICENSES/` y una nota clara en el README; si hay dudas de compatibilidad con otra fuente, **esa fuente se excluye** y se anota en `DEVIATIONS.md`.

## Paquetes de spam
- Una ficha YAML por fuente con URL, formato, **licencia**, frecuencia y nivel.
- El pipeline descarga, valida, normaliza a E.164, deduplica y publica un paquete por fuente (o por país si la fuente es global).
- Solo se redistribuyen fuentes cuya licencia lo permita. Si no permite redistribuir, la fuente pasa a ser **descarga directa desde la app** (la app descarga de la URL original) y no se empaqueta.
- Reglas por prefijo (`rules/*.yaml`): paquete pequeño aparte, versionado, con fuente legal enlazada. Incluye España `400`.
- Cada paquete conserva `sourceId`, de modo que «Por qué se marcó» sea trazable.
- Estado actual de fuentes reales: ver `08-investigacion-fuentes.md`.

## Verificación en CI
- Lint de YAML, validación de esquema del manifiesto.
- Tests con paquetes de ejemplo (formato, claves, FTS).
- Auditoría de licencia: cada fuente debe declarar licencia y política de redistribución; el workflow falla si falta.
- Smoke test: abrir cada `.db` generado y consultar números conocidos.
- Firma del manifiesto con clave guardada como secret; nunca en el repo.

## Publicación
- Releases por fecha (`data-YYYY.MM.DD`) con los paquetes y el manifiesto como assets.
- La app consulta `manifest.json` de la última release y compara versiones; descarga solo lo que cambia.
