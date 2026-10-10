# Lotería — PC01 · Desarrollo de aplicaciones móviles 2026-02

Proyecto Android nuevo para la práctica de Lotería. Esta primera entrega contiene la estructura Gradle, una pantalla inicial Compose y las dependencias de Firebase. **Esta rama añade registro/login reales con Firebase Auth y cierre de sesión; jugadas y seguimiento siguen pendientes.** Las partes se incorporan mediante PR separados.

## Integrantes

| Integrante | Código | Cuenta GitHub |
|---|---|---|
| Henry Tovar | 24100514 | HenTovar |
| Rojas | 24100478 | jutt-pixel |

Los nombres se mantienen como fueron proporcionados para el repositorio; completar los nombres de presentación requeridos por el docente antes de la entrega del curso.

## Abrir y ejecutar

1. Clonar el repositorio y abrir su raíz en Android Studio compatible con AGP 8.9.2.
2. Elegir **JDK 17** para Gradle. Instalar Android SDK Platform 35 y Build Tools 35.0.0 desde SDK Manager. `minSdk` es 23.
3. Sincronizar Gradle; conectar un dispositivo o iniciar un emulador API 23 o superior.
4. Ejecutar `app`, o compilar desde la raíz con `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`). El APK se genera en `app/build/outputs/apk/debug/`.

Versiones fijadas: AGP 8.9.2, Gradle 8.11.1, Kotlin/compiler Compose 2.1.20, Compose BOM 2025.04.01, Firebase BOM 33.12.0. El wrapper verifica el SHA256 de la distribución Gradle. `local.properties` y los directorios de build no se versionan.

### Estado de verificación

El entorno inicial no tiene Android SDK y su Java por defecto es 8. **La compilación del APK aún no está acreditada**; no se ha ejecutado `assembleDebug` con SDK Android. Sin google-services.json, la pantalla muestra instrucciones de configuración y no intenta autenticar. Se verificó `gradle help --no-daemon --max-workers=2` en contenedor Gradle 8.11.1/JDK 17: RC0, BUILD SUCCESSFUL. Esto comprueba configuración, no genera ni valida el APK.

Las pruebas Kotlin/JVM de autenticación se ejecutaron en contenedor con JDK 17: `:auth-core:test`, **19/19 verdes**. Firebase real, Compose en dispositivo y APK aún no se han probado.

## Firebase: configuración que aportará Henry

1. Crear un proyecto Firebase y registrar una app Android con paquete **`pe.esan.pc01.loteria`**.
2. Descargar `google-services.json` y ponerlo en **`app/google-services.json`** localmente. Está ignorado por Git y no debe adjuntarse al PR. No compartir claves privadas ni cuentas de servicio.
3. Habilitar Authentication → proveedor **correo/contraseña**.
4. Crear Cloud Firestore. Definir reglas antes de probar las futuras funcionalidades: usuarios autenticados; lectura únicamente de su jugada; creación de una sola jugada propia de seis números distintos 1–45 mientras el sorteo esté abierto; denegar modificar/eliminar jugadas, cabecera y bolillas desde la app. No dejar reglas abiertas ni habilitar cobros para esta base.
5. Al existir el JSON, Gradle aplica Google Services. Si falta o pertenece a otro paquete, revisar la configuración; no simular login ni conectarse a un proyecto ajeno.

Las dependencias son los módulos principales `firebase-auth` y `firebase-firestore`. Este PR no crea un proyecto Firebase ni publica reglas. La autenticación implementada y su checklist están en [docs/AUTH.md](docs/AUTH.md); la lectura de jugadas queda para la siguiente parte.

## Modelo propuesto de Firestore

| Ruta | Campos y tipos |
|---|---|
| `sorteos/{id}` | `nombre`: string, `fecha`: Timestamp, `estado`: string (`abierto`, `en_curso`, `finalizado`) |
| `sorteos/{id}/bolillas/{orden}` | `numero`: entero 1–45, `orden`: entero 1–6 |
| `sorteos/{id}/jugadas/{uid}` | `uid`: string del usuario autenticado, `numeros`: lista de seis enteros distintos 1–45 |

La relación con el sorteo queda establecida por la ruta; el ID del documento de jugada es el UID para impedir duplicados. El número de bolilla y su orden se guardan como **números**, no strings. Las seis bolillas deben ser distintas y los órdenes únicos/consecutivos. La persistencia y las reglas se implementarán en el PR de jugadas.

Aciertos = tamaño de la intersección de los conjuntos `numeros` y bolillas. No guardar aciertos, GANADOR ni SIGUE INTENTANDO en Firestore. Sólo mostrar resultado definitivo con estado `finalizado` y seis bolillas recibidas; antes mostrar espera o Sorteo en curso. La pantalla de seguimiento usará snapshot listeners del estado y de bolillas, retirados con `remove()` al salir, sin polling.

## Plan de PR y explicación para la defensa

1. **Auth:** registro/login, correo visible, navegación y errores de Firebase.
2. **Jugada:** validación de seis enteros, persistencia por UID, una sola jugada mientras esté abierto y reglas de acceso.
3. **Seguimiento:** listeners de cabecera/bolillas, orden de salida, restauración al ingresar y cancelación al salir.
4. **Cálculos:** intersección y resultado local condicionado al estado y las seis bolillas.

Cada PR indicará qué hace, cómo probarlo y cómo explicarlo; se alternan autores y la otra cuenta revisa. **No hacer merge sin OK de Henry.** El commit inicial de `main` es únicamente la base del repositorio vacío.

## Demostración obligatoria al completar las partes

Usar dos emuladores/dispositivos, cuentas distintas, mismo proyecto y sorteo. Antes de empezar registrar A: `5,12,18,23,34,41`; B: `5,12,18,23,34,42`. Cambiar de `abierto` a `en_curso` desde consola, publicar una por una `5,12,18,23,34,41`, viendo la actualización de ambas sesiones antes de seguir. Tras la quinta: cinco aciertos y sin resultado definitivo. Tras la sexta y `finalizado`: A GANADOR, B SIGUE INTENTANDO. Cerrar/reabrir una app y comprobar recuperación de jugada/bolillas y recálculo. Preparar video con ambas sesiones; esta base aún no permite esa demostración.

## Referencias técnicas

- [Compatibilidad de AGP 8.9](https://developer.android.com/build/releases/agp-8-9-0-release-notes)
- [Configuración de Compose Compiler](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [Agregar Firebase a Android](https://firebase.google.com/docs/android/setup)
