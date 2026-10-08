# 05 · Fase 0 — Spike en el OPPO

Objetivo: comprobar en los móviles reales, con el mínimo código, lo que no se puede deducir leyendo documentación. El resultado decide el alcance de la grabación y los ajustes de fabricante que hay que guiar.

## Dispositivos
| Dispositivo | Papel en el spike |
|---|---|
| **Pixel 8** | Referencia de Android estándar. Marcador de serie: Teléfono de Google. Anotar si este ofrece grabación o filtrado de llamadas en tu región y cómo convive al cambiar de app. |
| **OPPO Find X9 Ultra (global)** | Caso con capa de fabricante (ColorOS): gestión agresiva de batería, permisos de pantalla completa, marcador propio o de Google según ROM. |
| **Emulador AOSP sin GApps** | Verifica la independencia de GMS (prueba 10). |

Ejecutar la matriz en los tres cuando sea posible. Si una prueba da resultados distintos entre el Pixel y el OPPO, la diferencia se anota y alimenta el `CapabilityProbe` y la guía por fabricante. Los resultados se vuelcan en `docs/spec/compat-matrix.md`.

## APK del spike
App mínima, sin diseño, con una pantalla de registro de eventos y botones de prueba.

Debe implementar:
1. Solicitud del rol `ROLE_DIALER` y del rol de filtrado de llamadas.
2. `InCallService` con `InCallActivity` mínima: contestar, rechazar, colgar, altavoz, silenciar.
3. `CallScreeningService` que registra el número recibido, mide cuánto tarda en decidir y aplica una acción de prueba (aviso, silenciar o rechazar) según una lista fija.
4. Lectura del `CallLog` y de `ContactsContract`, con crear un contacto de prueba.
5. Marcado por SIM1 y SIM2 y lectura de la SIM de cada llamada.
6. Pruebas de grabación (ver abajo).
7. Descarga de un fichero pequeño por HTTPS con `WorkManager` solo con Wi-Fi, para medir comportamiento en segundo plano.

## Matriz de pruebas
Rellenar en cada dispositivo y pegar el resultado en el repo (`docs/spec/spike-results.md`).

| # | Prueba | Cómo | Resultado esperado | Resultado OPPO | Resultado Pixel 8 |
|---|---|---|---|---|---|
| 1 | Cambio de rol de teléfono | Pedir rol, aceptar, comprobar que ColorOS no restaura el suyo | La app es el marcador por defecto tras reiniciar | | |
| 2 | Llamada entrante con móvil bloqueado | Llamar desde otro móvil | Pantalla propia sobre el bloqueo, contestar sin desbloquear | | |
| 3 | Llamada entrante con móvil en uso | Idem con pantalla encendida | Notificación emergente, no tapa la pantalla completa | | |
| 4 | Filtrado previo | Número de lista de prueba | Decisión antes de sonar, tiempo medido en ms | | |
| 5 | Silenciar y rechazar | Idem con cada acción | Se respeta y queda en historial | | |
| 6 | Doble SIM | Llamar y recibir por cada SIM | La app identifica SIM y permite elegirla | | |
| 7 | Contactos | Crear, editar, borrar; ver en app de contactos del sistema | Cambios visibles y persistentes | | |
| 8 | Segundo plano | Tarea diaria con pantalla apagada 24 h, con y sin optimización de batería | Se ejecuta; anotar ajustes necesarios | | |
| 9 | Servicios tras matar la app | Quitar de recientes y recibir llamada | La llamada se gestiona | | |
| 10 | Independencia de GMS | Comprobar que la app no usa Play Services y que nada falla con ellos deshabilitados, si el móvil lo permite. Repetir lo esencial (roles, llamada entrante, filtrado, contactos) en un emulador AOSP sin GApps | Nada falla | | |
| 11 | `CapabilityProbe` | Que el APK vuelque en pantalla qué capacidades detecta en el dispositivo (roles, grabación, pantalla completa, SIM) | Lista coherente con lo observado en las pruebas manuales | | |
| 12 | Marcador actual del móvil | Anotar cuál es el marcador por defecto (Teléfono de Google u otro de OPPO) y qué funciones se pierden al cambiar: grabación, identificación de llamadas, avisos de spam propios, marcado inteligente | Lista de qué se pierde y de choques | | |
| 13 | Convivencia con el Teléfono de Google | Con la app como predeterminada, comprobar que el Teléfono de Google no interfiere con avisos ni filtrado, y que se puede volver a él | Sin interferencias, cambio reversible | | |

## Pruebas de grabación
Investigar y medir en este orden, anotando qué ocurre en cada vía:

1. **Captura de la línea** (fuente de audio de llamada): comprobar si el dispositivo permite a una app de terceros que sea la app de teléfono predeterminada. Lo habitual es que no, salvo permisos de sistema; verificar sin asumir.
2. **Captura con micrófono** con la app como app de teléfono predeterminada: grabar con y sin altavoz, medir si se oye al interlocutor, a uno solo o a ninguno, y con qué calidad.
3. **Cualquier vía propia del fabricante o de Google** que el sistema exponga a la app de teléfono predeterminada. En ROM global, la grabación de llamadas suele ser una función del marcador de serie (que puede estar o no según región y versión), no una API abierta a terceros. Anotar si el marcador actual del móvil la ofrece y si hay algo reutilizable. Si no existe, anotarlo.
4. **Mecanismos con privilegios elevados** (por ejemplo, herramientas con permisos de depuración): solo documentar si son viables y qué requieren. No se implementan en la v1 sin decisión expresa.

Los servicios de accesibilidad como truco de grabación quedan descartados.

### Decisión según resultado
| Resultado | Alcance de F9 |
|---|---|
| Captura de línea posible | Grabación completa de ambos lados |
| Solo micrófono con altavoz | Grabación opcional con aviso claro de limitaciones y activación automática del altavoz |
| Ninguna vía fiable | Quitar F9 de la v1, dejar el hueco en la spec y documentarlo en `DEVIATIONS.md` |

## Criterios de salida de la Fase 0
- Puntos 1 a 7 de la matriz funcionan, o hay una alternativa documentada.
- Tiempo de decisión del filtrado dentro del límite del sistema con margen.
- Lista de ajustes de ColorOS necesarios para el segundo plano, para la guía del onboarding.
- Alcance de grabación decidido.

Con esto Claude Code continúa con la Fase 1. Si los puntos 1 a 3 fallan de forma que impida usar la app como teléfono principal, parar y avisar antes de seguir.
