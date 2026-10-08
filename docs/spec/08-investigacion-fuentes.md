# 08 · Investigación de fuentes (8 de octubre de 2026)

Resultado de una búsqueda inicial, no de una auditoría. **Claude Code debe verificar cada punto contra la fuente primaria antes de usarlo**, sobre todo licencias.

## Spam: lo que existe de verdad

### España
- **Prefijo 400 para llamadas comerciales.** Según la prensa y blogs consultados, las llamadas comerciales deberán salir de números de nueve dígitos que empiecen por 400 a partir del **17 de octubre de 2026**, y los operadores bloquearán las comerciales que no usen ese rango. Se cita la resolución **BOE-A-2026-8409**. Los números 400 serían solo salientes. *Pendiente de verificar en el BOE y en la CNMC*, incluido el ámbito exacto y qué ocurre con empresas fuera de España.
- **Lista Robinson**: es un registro de exclusión publicitaria, no una lista de números de spam. No sirve como fuente.
- **Directorios comunitarios** (por ejemplo, sitios donde los usuarios comentan números): no se encontró licencia abierta ni descarga permitida. **No usar** salvo que se compruebe una licencia explícita. Raspar sus webs queda descartado.
- **Conclusión**: no apareció una lista abierta de números spam españoles que sea claramente redistribuible. Para España, la v1 se apoya en la regla 400, la lista propia, la lista sincronizada en Nextcloud y fuentes por URL que el usuario añada.

### Alemania / Austria
- **PhoneBlock**: proyecto de código abierto con lista comunitaria de números de spam, que se puede suscribir como agenda en Fritz!Box y móviles. Es el candidato principal para DE/AT. *Verificar*: condiciones de uso del servicio, si exige cuenta o token, formato de descarga y si se permite redistribuir o solo consumir directamente desde la app. Hay costes de servidor del proyecto: valorar no abusar de su servicio (descargas poco frecuentes, caché).
- **dontobi/SpamCalllist**: repositorio con licencia MIT, **archivado en marzo de 2024** y sustituido por PhoneBlock. Útil como dato histórico, pero antiguo.

### Francia
- ARCEP publicó una lista de numeración reservada a los operadores de llamadas comerciales, que funciona como regla por prefijo. No es prioritario salvo que se pidan más países.

### Otros
- Las bases de apps comerciales (Truecaller, Hiya y similares) son propietarias: no utilizables.
- Las listas sueltas de GitHub (gists, repos personales) varían en calidad y licencia, normalmente pequeñas, de otros países o sin mantenimiento. Solo entran tras revisar licencia y frescura.

### Cómo cubrir el hueco
1. Reglas por prefijo versionadas en el repo de datos (400 en España).
2. Lista propia con prefijos además de números, sincronizada.
3. Fuente por URL propia (por ejemplo, un fichero en el Nextcloud del usuario).
4. PhoneBlock para DE/AT si las condiciones lo permiten.
5. Opcional futuro: permitir exportar la lista propia en un formato que otros puedan compartir, sin cuentas ni servidores centrales.

## Comercios
- **OpenStreetMap**: datos bajo **ODbL 1.0**, uso comercial permitido con atribución; si se redistribuye una base derivada, aplica el compartir igual. Los sitios de terceros que ofrecen «extractores» consultan la API pública Overpass, que no está pensada para volcados masivos: **no usar Overpass para construir los paquetes**; usar extractos regionales descargables.
- **Cobertura**: depende del país y la categoría. Una medición pública sobre 966 restaurantes de Burdeos encontró teléfono en cerca del 41 %. Es un único dato, de una ciudad y una categoría; la cobertura real hay que medirla por región en el pipeline y mostrarse en la app.
- **Otras fuentes abiertas**: no se identificó ninguna validada. Candidatas a investigar: Wikidata (empresas e instituciones con teléfono), datos abiertos de administraciones públicas (servicios públicos, centros sanitarios), registros abiertos de empresas. Cada una exige comprobar licencia y fecha. Si no hay licencia clara, se descarta.

## Fuentes consultadas
- NordVPN, «¿Cómo bloquear llamadas spam en España?» — https://nordvpn.com/es/blog/como-evitar-llamadas-spam/
- BandaAncha, «Cómo funciona el nuevo prefijo 400…» — https://bandaancha.eu/articulos/como-funciona-nuevo-prefijo-400-usaran-11733
- BandaAncha, «La CNMC quiere que salga gratis devolver una llamada…» — https://bandaancha.eu/articulos/cnmc-pide-salga-gratis-devolver-llamada-11718
- CNMC, nota sobre numeración de atención al cliente y llamadas comerciales — https://www.cnmc.es/eu/node/419489?back=news
- El Derecho, «A partir de octubre será obligatorio el prefijo 400…» — https://elderecho.com/a-partir-de-octubre-sera-obligatorio-el-prefijo-400-para-las-llamadas-comerciales
- Aunoa, «Prefijo 400 en llamadas comerciales…» — https://aunoa.ai/blog/prefijo-400-llamadas-comerciales/
- GitHub, dontobi/SpamCalllist (archivado) — https://github.com/dontobi/SpamCalllist
- Perfil de GitHub Sponsors de PhoneBlock (haumacher) — https://sponsors.ecosyste.ms/accounts/haumacher
- LinuxFr, «Agir contre les appels commerciaux» — https://linuxfr.org/news/agir-contre-les-appels-commerciaux
- Apify, OSM POI Extractor (nota sobre ODbL y cobertura) — https://apify.com/dataio/osm-poi-extractor

---

# Verification against primary sources (2026-10-08)

Written in English per the repo rules. Where it conflicts with the notes above, this section wins.

## Prefix 400 (Spain)

Primary source: BOE-A-2026-8409, *Resolución de 14 de abril de 2026, de la Secretaría de Estado de
Telecomunicaciones e Infraestructuras Digitales, por la que se atribuyen recursos públicos de numeración
para la prestación del servicio de llamadas comerciales y se establecen las condiciones para su uso*
(BOE núm. 93, 16 April 2026). https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-8409

- **Entry into force:** 17 April 2026 (the day after publication).
- **Operational deadline:** the range must be operational within 6 months of entry into force, so around
  **17 October 2026**. From then on, companies covered by Ley 10/2025 may only use `NXY = 400` for commercial calls
  (except the exemptions of that law's transitional provision).
- **Scope:** nine-digit numbers `400xxxxxx`, **outbound only** (no incoming calls, not usable for customer
  service). Calls from the range are treated as originating from a fixed access network and may only terminate on
  geographic, mobile or nomadic numbers. Applies to Spanish numbering; says nothing about foreign companies.
- CNMC press note (1 April 2026) is consistent: commercial calls go through the 400 range, the implementation period
  was extended from four to six months. https://www.cnmc.es/eu/node/419489

**Consequence for the app (deviation, see `DEVIATIONS.md` D-001):** a `400` caller is a *legitimate commercial call
that identifies itself*, not spam. The built-in rule is therefore an informational `RULE` level labelled
"commercial call" (default action `WARN`, user-configurable), and it must not be described as a spam list. A
commercial call from a non-400 number after the deadline is the violation, and the app cannot detect that reliably.

## Licences of candidate sources

| Source | Code licence | Data licence / redistribution | Decision |
|---|---|---|---|
| PhoneBlock (haumacher/phoneblock) | GPL-3.0 | No data licence or redistribution grant found in the repository (README, COPYRIGHT, INTEGRATIONS.md). Authenticated API use needs a per-user bearer token. | **Not packaged** in `UltimatePhone-data`. Optional direct download from the app only after the user's own PhoneBlock token and only if the service terms allow it; not enabled in v0.x. |
| dontobi/SpamCalllist | MIT | Archived March 2024, superseded by PhoneBlock. | Historical only, **excluded** (stale). |
| Community directories (Spanish) | none found | none | **Excluded**, no scraping. |
| Lista Robinson | n/a | Advertising opt-out register, not a spam list. | Not a source. |
| Truecaller, Hiya and similar | proprietary | proprietary | Excluded. |
| OpenStreetMap (businesses) | n/a | ODbL 1.0 with attribution; share-alike for derived databases. | Allowed, packaged under ODbL. |
