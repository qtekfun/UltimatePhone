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
