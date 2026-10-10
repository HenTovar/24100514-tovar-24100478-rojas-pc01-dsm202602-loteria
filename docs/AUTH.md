# Parte 1: registro y login

## Qué hace y cómo explicarlo

`FirebaseAuthGateway` llama al SDK real: `createUserWithEmailAndPassword` crea la cuenta y `signInWithEmailAndPassword` inicia sesión. Ambas operaciones requieren el proyecto Firebase de Henry con correo/contraseña habilitado. No se escriben usuarios ni contraseñas en Firestore.

`AuthController` valida campos, elimina espacios alrededor del correo (no de la contraseña), evita solicitudes duplicadas mientras espera y convierte fallos en mensajes de interfaz. En registro exige al menos seis caracteres y confirmación coincidente; Firebase valida además su política de contraseñas. No crea una identidad al recibir éxito: el usuario/UID/correo se toman exclusivamente de `AuthStateListener`, que también recupera la sesión que conserva el SDK al reabrir la app.

La pantalla Compose observa `StateFlow`: pasa de login a registro y permite volver; al autenticarse muestra el correo y cerrar sesión. La pantalla de jugada aún no está implementada. `DisposableEffect` agrega un único listener y lo retira al destruir la composición; el controlador ignora callbacks tardíos. `remember` mantiene las contraseñas únicamente en memoria de la pantalla, sin `rememberSaveable`, y se limpian al cambiar modo o usuario. No se imprimen contraseñas, tokens ni excepciones crudas.

Si falta la configuración Firebase, la app muestra instrucciones y no simula una sesión. Las pruebas usan un proveedor falso para ejercitar lógica y ciclo de vida: **no acreditan conexión con Firebase ni la compilación de Android**.

## Prueba automática de la lógica

Validación ejecutada en contenedor Gradle 8.11.1/JDK 17: **19 tests, 0 fallos, RC0**. Con JDK 17: `./gradlew :auth-core:test`. El módulo Kotlin/JVM no requiere Android SDK ni google-services.json. Incluye validaciones, cambio de pantalla, bloqueo de doble clic, error y reintento, recuperación de identidad, logout y eliminación/ignorancia de listeners/callbacks tardíos.

## Checklist en Android con el Firebase de Henry

1. Completar configuración del README y compilar `./gradlew assembleDebug`. Abrir la app sin sesión: debe mostrar login. Si falta JSON, debe mostrar instrucciones de configuración.
2. Ir a Crear cuenta y volver al login. Correo vacío/inválido, contraseña corta y confirmación distinta deben mostrar error sin crear cuenta.
3. Registrar un correo nuevo válido: Firebase debe crear el usuario; la app debe mostrar **su correo real**. No inventar ni almacenar una contraseña en Firestore.
4. Cerrar sesión; iniciar con esa cuenta; probar contraseña incorrecta, cuenta duplicada y red ausente: debe aparecer un error legible y permitir reintento.
5. Reabrir con sesión existente: mostrar correo recuperado por Firebase. Cerrar sesión y reabrir: mostrar login, no el usuario anterior.
6. Rotar el dispositivo y pasar registro/login varias veces: sin listener duplicado ni cierre inesperado. La contraseña no se restaura tras recrear Activity.

La prueba con Firebase, emulador/dispositivo y la generación del APK queda pendiente de SDK y configuración aportados por Henry. No configurar cuentas externas ni habilitar créditos desde este PR.

## Fuentes

- [Firebase Auth por correo y contraseña](https://firebase.google.com/docs/auth/android/password-auth)
- [API FirebaseAuth y AuthStateListener](https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseAuth)

## Asistencia de IA

Propuesta para que Henry decida si la incorpora al README: «Desarrollado con asistencia de IA; cada integrante revisa, prueba y explica el código». No se presenta como decisión ya tomada.
