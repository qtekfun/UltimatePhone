# 03 · Funcionalidades y criterios de aceptación

Cada bloque termina con criterios verificables. «AC» = criterio de aceptación.

## F1 · Marcador
- Teclado numérico, borrado, pegado, llamada con un toque.
- Búsqueda al escribir: coincide por número parcial y por nombre (teclas T9) contra contactos y, si no hay contacto, contra comercios locales.
- Selector de SIM visible cuando hay dos, con SIM por defecto configurable y SIM recordada por contacto.
- AC: marcar un número llama por la SIM elegida; escribir «far» propone contactos y comercios cuyo nombre empieza o contiene «far»; resultados en menos de 100 ms con 10 000 contactos.

## F2 · Pantalla de llamada
- Entrante: contestar, rechazar; sobre la pantalla de bloqueo con la pantalla encendida; como notificación emergente si el móvil está en uso.
- En curso: silenciar, altavoz, teclado DTMF, retener, añadir llamada, cambiar/combinar, ruta de audio (auricular, altavoz, Bluetooth), sensor de proximidad.
- Muestra nombre de contacto o comercio, número, SIM y, si procede, **aviso de spam** con nivel y fuente.
- AC: con el móvil bloqueado suena y se puede contestar sin desbloquear; el aviso de spam aparece antes de contestar; la llamada se puede cerrar desde la notificación.

## F3 · Historial de llamadas
- Lista con filtros: todas, perdidas, entrantes, salientes, spam, por SIM.
- Detalle de número con contacto, comercio, acciones y estadísticas básicas.
- Acciones: llamar, crear/añadir a contacto, **marcar como spam**, **añadir a lista blanca**, borrar.
- AC: marcar como spam una entrada la añade a la lista propia y la próxima llamada de ese número lanza el aviso.

## F4 · Contactos
- Lista con búsqueda, índice alfabético, favoritos arriba.
- Ficha completa: varios teléfonos con etiqueta, varios emails, foto, cumpleaños, notas, grupos.
- Crear, editar, eliminar, con selección de cuenta destino (local o cuentas sincronizadas, como la de DAVx5).
- Grupos/etiquetas: crear, asignar, filtrar.
- Importar y exportar vCard (`.vcf`), uno, varios o todos.
- **Duplicados**: detección por teléfono normalizado, email y similitud de nombre; revisión asistida con vista previa y fusión que conserve todos los datos. Nunca fusionar sin confirmación.
- AC: crear un contacto con dos teléfonos y foto aparece en la app de contactos del sistema; exportar e importar un `.vcf` en otro móvil conserva campos y foto; la fusión de dos duplicados no pierde ningún teléfono ni email.

## F5 · Motor de spam local
Ver el orden de decisión en `02`.
- **Fuentes**: catálogo incluido en la app (ver `08` para qué existe de verdad) más fuentes personalizadas por URL (HTTPS) o ruta WebDAV. Formatos admitidos: TXT de números, CSV, JSON Lines, y paquete de la app.
- **Reglas por prefijo**: por país, con nombre y nivel. Regla incluida: rango comercial **400** de España. Verificar en la resolución oficial su ámbito y fecha de entrada en vigor antes de activarla por defecto.
- **Actualización**: tarea diaria con WorkManager, solo Wi-Fi, con conditional GET (ETag / If-Modified-Since) para no descargar si no hay cambios, hash de verificación y reintentos con retroceso.
- **Niveles y acciones**: tabla en Ajustes; defecto `WARN` en todo.
- **Lista propia**: número exacto o prefijo, etiqueta, nota, fecha. Añadir y quitar desde historial, llamada y pantalla de la lista.
- **Lista blanca y favoritos**: nunca se filtran.
- **Transparencia**: pantalla «Por qué se marcó» que muestra nivel y fuente, y botón «No es spam» que lo añade a la lista blanca.
- AC: con la lista propia vacía y una fuente instalada, un número de la fuente dispara el aviso antes de sonar; un número de contacto nunca se marca; sin conexión todo sigue funcionando con los datos ya descargados; ningún tráfico de red sale durante una llamada.

## F6 · Base de comercios (local)
- Paquetes por región, instalados desde el onboarding o Ajustes → Datos.
- Identificación en llamada entrante, historial y búsqueda del marcador: nombre y categoría con icono.
- Prioridad: contacto > comercio. Un contacto nunca se sobrescribe con un comercio.
- Ajuste «Un comercio identificado no es spam» (activo por defecto).
- Atribución obligatoria a OpenStreetMap en Ajustes → Acerca de y en la pantalla de datos.
- AC: con el paquete de una región instalado, un número presente en él muestra el nombre del comercio al sonar y en el historial; sin paquete no se muestra nada ni se hace ninguna petición.

## F7 · Sincronización con Nextcloud (lista de spam y lista blanca)
- Alta con URL del servidor, usuario y **contraseña de aplicación**; prueba de conexión.
- Ficheros en `UltimatePhone/`: `spam-list.jsonl`, `whitelist.jsonl`.
- Entrada: `{id, kind: number|prefix, value, label, note, updatedAt, deviceId, deleted}`.
- Fusión: por `id`, gana `updatedAt` más reciente; empates resueltos por `deviceId`; lápidas con caducidad larga configurable.
- Escritura con ETag y reintento si hay conflicto de versión.
- Sincroniza tras cambios locales con retardo y periódicamente en segundo plano; indica último sync y errores.
- Sin conexión: los cambios quedan en cola y se aplican al volver.
- AC: añadir un número en el teléfono A aparece en el B tras sincronizar; borrar en B lo elimina en A; dos cambios simultáneos al mismo número se resuelven sin perder otros cambios; credenciales no visibles en copias de seguridad ni logs.

## F8 · Exportación e importación de ajustes (solo fichero local)
- Exporta a un fichero (SAF) con ajustes, fuentes, niveles, lista propia, lista blanca y **credenciales de Nextcloud**.
- Cifrado: contraseña del usuario → KDF resistente (Argon2id; si no hay librería adecuada, PBKDF2-HMAC-SHA256 con iteraciones altas) → AES-256-GCM. Formato versionado.
- Importar pide la contraseña, valida la integridad y permite elegir qué restaurar.
- No hay exportación de ajustes a la nube; la sincronización remota es solo de las listas (F7).
- AC: exportar en un móvil e importar en otro deja la app configurada y sincronizando; un fichero alterado o con contraseña incorrecta falla sin aplicar nada.

## F9 · Grabación de llamadas
Depende del resultado del spike (`05`). Alcance mínimo definido por el spike; funciones objetivo:
- Grabar bajo demanda y automáticamente (todas, desconocidos, contactos elegidos).
- Guardado en carpeta elegida por el usuario (SAF), formato configurable, lista de grabaciones con reproducción.
- **Aviso legal** al activar por primera vez y opción de anunciar la grabación.
- Indicador visible durante la grabación.
- AC: según nivel que permita el dispositivo en el spike; si solo hay captura por micrófono, la app lo indica claramente en Ajustes y no promete grabar al interlocutor.

## F10 · Onboarding
1. Bienvenida y qué hace la app.
2. Rol de app de teléfono y rol de filtrado de llamadas.
3. Permisos con explicación.
4. **Regiones de datos**: elegir países/regiones de comercios y fuentes de spam con tamaño estimado por región y aviso de Wi-Fi.
5. Nextcloud (opcional, saltable).
6. Ajustes de batería, arranque automático, ventanas emergentes y pantalla completa sobre el bloqueo, con guía según el fabricante detectado (ColorOS y otros) y una guía genérica de respaldo.
7. Resumen y primera descarga.
- AC: se puede completar sin cuenta ni red; cada paso saltable puede hacerse luego desde Ajustes; los datos descargados se pueden quitar.

## F11 · Ajustes
Apariencia (claro/oscuro/sistema, color dinámico), SIM, spam (niveles, comercios, fuentes, actualización), datos (regiones, espacio usado, actualizar, borrar), sync, exportación/importación, grabación, avanzado (registro de depuración sin números completos), acerca de (licencias, atribución a OpenStreetMap).

## Requisitos no funcionales
- Arranque en frío a la pantalla del marcador en menos de 1 s en el móvil objetivo.
- Resolución de decisión de llamada en menos de 50 ms con todos los paquetes instalados.
- Accesibilidad: TalkBack, tamaños de fuente grandes, contraste.
- Español e inglés completos.
- Sin trackers, sin analíticas.
- **Compatibilidad universal**: funciona en cualquier Android 12+ con o sin GMS y de cualquier fabricante; las funciones que dependan de las capacidades del dispositivo (grabación, pantalla completa sobre el bloqueo, doble SIM) se activan según detección en ejecución. AC: la app instala, arranca y completa el onboarding en un emulador AOSP sin GApps; el filtrado, los contactos, los paquetes de datos y la sync funcionan igual con y sin Google Play Services.
