# 01 · Visión y alcance

## Visión
Un teléfono bonito y rápido que sustituye al «Teléfono de Google», con filtro de spam que funciona **sin enviar ni un número fuera del móvil**: descarga listas completas, decide en local y avisa antes de que cojas la llamada.

## Dentro del alcance
- App de teléfono predeterminada: marcador, pantalla de llamada propia (entrante y en curso), historial, doble SIM sin buzón de voz.
- Contactos del sistema (ContactsContract): crear, editar, eliminar, favoritos, grupos/etiquetas, importar/exportar vCard, detectar y fusionar duplicados.
- Spam local: fuentes descargadas, fuente propia por URL, lista propia, lista blanca, reglas por prefijo, aviso en pantalla configurable por nivel.
- Base local de comercios por región para identificar llamantes (nombre, categoría, icono).
- Sincronización de la lista de spam y la lista blanca con Nextcloud por WebDAV, automática, entre varios teléfonos.
- Exportación/importación de ajustes a fichero local, con credenciales de Nextcloud cifradas con contraseña.
- Grabación de llamadas, sujeta al resultado del spike (ver `05`).
- Onboarding que pregunta regiones de datos, roles, permisos y ajustes de batería.

## Fuera del alcance (v1)
- Buzón de voz visual, SMS/MMS, videollamadas, VoIP/SIP.
- Consultas online de números (ningún servicio de identificación externo).
- Sincronización de contactos propia (se delega en DAVx5 u otra cuenta del sistema).
- Funciones que dependen de la nube de Google (Call Screen, Hold for Me, Direct My Call).
- Servicios de accesibilidad para trucos de grabación.

## Decisiones cerradas
| Tema | Decisión |
|---|---|
| Nombre | UltimatePhone, `com.qtekfun.ultimatephone` |
| Rol | App de teléfono predeterminada + rol de filtrado de llamadas |
| Contactos | Del sistema, no base propia |
| Spam | Todo local, cero consultas online |
| Acción ante spam | Marcar en pantalla por defecto; configurable por nivel (avisar, silenciar, rechazar) |
| Actualización de listas | Diaria en segundo plano, solo con Wi-Fi (configurable) |
| Fuentes | Listas públicas investigadas + URL propia (p. ej. Nextcloud) + reglas por prefijo |
| Comercios | OpenStreetMap + otras fuentes abiertas, mundial, por regiones, elegidas en el onboarding |
| Comercio identificado | No es spam. Opción en Ajustes para desactivarlo |
| Sync | Lista de spam y lista blanca por WebDAV en segundo plano |
| Ajustes | Exportación/importación solo a fichero local, con credenciales cifradas |
| Doble SIM | Sí, sin buzón |
| Plataforma | Kotlin + Compose, M3 Expressive, Android 12+ |
| Distribución | GitHub Releases + F-Droid, sin release-please |
| Licencia | GPLv3 |
| Compatibilidad | **Cualquier Android 12+, con o sin GMS, de cualquier fabricante o ROM.** Requisito de primer orden (ver `02`) |
| Dispositivos de prueba | **OPPO Find X9 Ultra** (ROM global, ColorOS con servicios de Google) y **Pixel 8** (Android de Google). Más un emulador AOSP sin GApps |
