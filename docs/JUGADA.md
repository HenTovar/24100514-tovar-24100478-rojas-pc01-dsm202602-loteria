# Parte 2: una jugada por usuario y sorteo

## Cómo defender el código

1. Después del login se listan sorteos y el usuario elige uno por nombre, fecha y estado. La cabecera debe existir en Firebase con fecha Timestamp y estado `abierto`, `en_curso` o `finalizado`.
2. `PlayController` lee exclusivamente la jugada del UID autenticado. No lista jugadas de otros. No habilita guardar hasta terminar esa lectura; si ya hay una jugada, sólo la muestra.
3. La entrada es una lista de seis enteros separados por comas. `parseNumbers` y `validNumbers` verifican cantidad, tipos, rango 1–45 y unicidad antes de llamar al repositorio. La selección permanece visible; no se calculan resultados en esta parte.
4. `FirestorePlayGateway.create` revalida en una **transacción** la cabecera del sorteo y el documento de jugada. Todas las lecturas van antes de la escritura. Si el sorteo dejó de estar abierto o ya existe jugada, no escribe. Firestore reintenta ante cambios concurrentes y las reglas vuelven a comprobar el estado y el UID en el servidor.
5. Se guarda exactamente `sorteoId`, `uid`, `numeros` en `sorteos/{sorteoId}/jugadas/{uid}`. El UID es el documento, no un ID aleatorio. Al volver a entrar se recupera ese documento; se rechazan datos almacenados con UID/sorteo/números inválidos.
6. No se guarda GANADOR, aciertos ni resultado. El seguimiento por listeners y los cálculos pertenecen a PR siguientes. No hay polling: en esta parte son lecturas puntuales, selección y actualización manual del catálogo.
7. El controlador se crea por UID. Al salir o cerrar sesión se descarta y se ignoran callbacks tardíos; no se conserva la jugada de un usuario para el siguiente.

## Configuración y prueba por Henry

**Antes de usar esta versión, volver a publicar `firestore.rules` completo en la consola**, como indica el README. El nuevo campo `sorteoId` también es obligatorio en las reglas; las reglas anteriores del PR #2 lo rechazan como campo extra. La publicación de reglas no se hace desde este PR.

Crear en la consola `sorteos/sorteo-prueba` con `nombre` (string), `fecha` (Timestamp) y `estado` (string: `abierto`). La app lee toda la colección y usa el ID real del documento seleccionado, por lo que reconoce `sorteo-prueba` sin cambiar código. No usar string para la fecha. Recompilar/ejecutar con SDK Android, Firebase configurado y Auth correo/contraseña habilitado.

- Usuario A: elegir ese sorteo por nombre y fecha y registrar `5,12,18,23,34,41`. Comprobar documento `jugadas/{UID_A}` con los tres campos.
- Usuario B: otra sesión/cuenta, registrar `5,12,18,23,34,42`. Cada sesión debe mostrar sólo su jugada.
- Intentar cinco/siete números, duplicados, 0, 46, decimales o texto: rechaza sin escribir.
- Cerrar y volver a ingresar: recuperar la misma jugada, sin opción de sobrescribirla. Cambiar de cuenta: nunca mostrar la del anterior.
- Con otro usuario sin jugada, poner la cabecera en `en_curso` o `finalizado` y actualizar el catálogo: no permitir registro. Si cambia después de elegirlo, el guardado también debe fallar desde la transacción/reglas.
- Guardar desde dos sesiones del mismo UID: debe quedar una única jugada, sin sobrescritura.
- Volver a sorteos y cerrar sesión siempre que no haya una petición en curso. Ante error de red, mostrar un error y permitir volver/reintentar.

Las jugadas creadas manualmente con el esquema antiguo de dos campos necesitan `sorteoId` para ser válidas: no las modifica la app ni este PR. Para la demostración, crear datos con el esquema actual desde la consola.

## Pruebas automatizadas y límites

**Validación ejecutada:** 21/21 pruebas Kotlin/JVM de jugada y 15/15 de reglas en emulador, RC0 en ambas. Las 19 pruebas de auth previamente verdes quedaron UP-TO-DATE en esta corrida; no se modificó su lógica.

Kotlin/JVM: `./gradlew :auth-core:test :game-core:test`, sin SDK Android. Prueban controlador/validaciones con un repositorio falso; no prueban `FirestorePlayGateway` Android.

Las pruebas de reglas usan Firestore Emulator desechable (`demo-loteria-pc01`): guardado autorizado/denegado, sorteoId ausente o ajeno, payload persistido, duplicados concurrentes y cierre entre lectura y commit. El test transaccional usa el SDK JavaScript con la misma secuencia, **no** el adaptador Android; no se atribuye como prueba de Android.

APK, Compose/dispositivo y el SDK Firebase Android real siguen pendientes de Android SDK y de la comprobación de Henry. No se hicieron escrituras ni despliegues en Firebase real.

## Fuentes

- [Transacciones Firestore](https://firebase.google.com/docs/firestore/manage-data/transactions)
- [Lectura de documentos](https://firebase.google.com/docs/firestore/query-data/get-data)
