# 02 · Arquitectura

## Roles y servicios de Android
| Pieza | API | Función |
|---|---|---|
| App de teléfono | `RoleManager.ROLE_DIALER` | Recibe el control de llamadas del sistema. |
| Pantalla de llamada | `InCallService` + `InCallActivity` | Muestra llamada entrante/en curso, audio, DTMF, espera. |
| Filtrado previo | `CallScreeningService` + `ROLE_CALL_SCREENING` | Recibe el número **antes** de que suene y decide. |
| Contactos | `ContactsContract` | Lectura y escritura en la agenda del sistema. |
| Historial | `CallLog.Calls` | Lectura y borrado; la app escribe las llamadas que gestiona si el sistema no lo hace. |
| Doble SIM | `TelecomManager`, `PhoneAccountHandle`, `SubscriptionManager` | Elegir SIM al marcar, mostrar SIM por llamada. |

Permisos previstos: `READ/WRITE_CONTACTS`, `READ/WRITE_CALL_LOG`, `CALL_PHONE`, `READ_PHONE_STATE`, `READ_PHONE_NUMBERS`, `ANSWER_PHONE_CALLS`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `INTERNET`, `FOREGROUND_SERVICE` (solo si la grabación lo exige). Cada permiso se pide en contexto, con explicación, en el onboarding.

**Restricción dura**: el filtrado previo tiene un plazo de respuesta corto del sistema. La consulta de un número debe resolverse en memoria o con una única lectura indexada, en milisegundos, nunca con red ni escaneos.

## Módulos (Gradle)
```
app/                 UI shell, navegación, onboarding, DI
core/telecom/        InCallService, CallScreeningService, roles, doble SIM, audio
core/contacts/       Acceso a ContactsContract, vCard, duplicados
core/calllog/        Historial y filtros
core/spam/           Motor de decisión, listas, reglas por prefijo, lista propia, blanca
core/businesses/     Paquetes de comercios, búsqueda por número y por nombre
core/datapacks/      Manifiesto, descarga, verificación, instalación de paquetes
core/sync/           WebDAV, fusión, cola offline, cifrado de credenciales
core/settings/       Ajustes, exportación/importación cifrada
core/recording/      Grabación de llamadas (según spike)
core/designsystem/   Tema M3 Expressive, componentes
core/phonenumber/    Normalización E.164 con libphonenumber
```

## Almacenamiento
- **Room (`app.db`)**: lista de spam propia, lista blanca, SIM por contacto, fuentes configuradas, estado de sync, cola de cambios, metadatos de paquetes.
- **SQLite de solo lectura por paquete** (`packs/<id>.db`): una base por región de comercios y por fuente de spam. Tabla `numbers(e164 PRIMARY KEY, ...)` para consulta exacta, tabla `prefix_rules` para reglas, y FTS5 de nombres en comercios. Se adjuntan o consultan por separado; instalar/actualizar un paquete es sustituir el fichero.
- **DataStore**: ajustes simples.
- **Credenciales**: contraseña de aplicación de Nextcloud guardada cifrada con Android Keystore. Nunca en claro en Room ni en DataStore.
- **Normalización**: todos los números se almacenan y comparan en E.164. Los números sin prefijo se interpretan con la región de la SIM o el país elegido en ajustes. Números no válidos o con identificador oculto se tratan como «desconocido», nunca como spam.

## Motor de decisión de llamada entrante
Orden de evaluación, se detiene en la primera coincidencia:

1. **Número de emergencia** (112 y equivalentes por país) → nunca se filtra.
2. **Contacto del sistema** → mostrar contacto, sin filtrado.
3. **Lista blanca** → permitir.
4. **Lista de spam propia** → nivel `OWN`.
5. **Comercio identificado** → mostrar nombre y categoría. Si el ajuste «un comercio identificado no es spam» está activo (por defecto sí), termina aquí.
6. **Fuentes de spam descargadas** → nivel `COMMUNITY` o el que declare la fuente.
7. **Reglas por prefijo** → nivel `RULE` (por ejemplo, rango comercial 400 de España).
8. **Desconocido** → sin acción.

Cada nivel tiene su acción configurable: `WARN` (por defecto), `SILENCE`, `REJECT`. `REJECT` y `SILENCE` dejan siempre la llamada registrada en el historial marcada como spam, con el motivo (nivel y fuente). Resultado de la decisión: `{action, level, sourceId, label?, category?}`.

La decisión se calcula en `CallScreeningService` y se reutiliza en `InCallService` para pintar el aviso en la pantalla de llamada. Mismo código, una sola función pura `decide(number, context)` con tests exhaustivos.

## Sincronización (resumen; detalle en `03`)
- Un fichero por lista en la carpeta `UltimatePhone/` del Nextcloud del usuario, formato JSON Lines, con marcas de tiempo por entrada y lápidas para borrados.
- Fusión por entrada, gana el cambio más reciente; control de concurrencia con ETag (`If-Match`).
- Trabajo periódico con WorkManager, más sincronización tras cada cambio local con retardo.

## Datos descargados
Los paquetes salen del repo `UltimatePhone-data` (ver `04`). La app verifica firma y hash del manifiesto antes de instalar. Nada se procesa en el teléfono más allá de abrir bases ya preparadas.

## Compatibilidad universal
**Requisito de primer orden**: la app funciona en cualquier Android 12+ (API 31+), con o sin servicios de Google, de cualquier fabricante (Pixel, Samsung, Xiaomi, OPPO, Motorola…) y en ROMs alternativas (GrapheneOS, LineageOS, etc.). El móvil de pruebas principal es un OPPO con ROM global, pero eso no condiciona el diseño.

Reglas:
- **Cero dependencias de Google**: nada de Play Services, Firebase/FCM, ML Kit, Google Maps ni librerías que los arrastren. Revisar también dependencias transitivas.
- **Sin APIs ni intents de un fabricante como requisito.** Lo específico de un OEM solo entra como mejora opcional, detectada en ejecución, con degradación elegante cuando no existe.
- **Capacidades detectadas, no supuestas por marca**: un `CapabilityProbe` prueba en ejecución qué se puede hacer en ese dispositivo (rol de teléfono, rol de filtrado, grabación y de qué tipo, pantalla completa sobre el bloqueo, doble SIM) y guarda el resultado. La UI muestra solo lo disponible y explica lo que no.
- **Todo local**: notificaciones, tareas y avisos con APIs del framework (WorkManager, AlarmManager, notificaciones locales). Nada de push remoto.
- **Sin depender de que exista otro marcador**: la app puede ser el único teléfono del móvil. Si hay uno previo (Teléfono de Google u otro del fabricante), debe poder convivir y se puede volver a él.
- **Guía de ajustes por fabricante**: batería, arranque automático, ventanas emergentes y pantalla completa sobre el bloqueo varían mucho por OEM. El onboarding detecta el fabricante y muestra la guía correspondiente desde un recurso editable, con una guía genérica de respaldo.
- **Matriz de pruebas**: OPPO Find X9 Ultra (ColorOS global), Pixel 8 (Android de Google), emulador AOSP sin GApps y emulador con Google APIs. El Pixel es la referencia de comportamiento estándar y el OPPO, el caso con capa de fabricante. Resultados documentados en `docs/spec/compat-matrix.md`.
