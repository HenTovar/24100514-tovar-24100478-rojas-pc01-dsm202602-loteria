# Seguimiento en tiempo real

## Flujo y ciclo de vida

Login ↔ registro → elegir sorteo → guardar/recuperar jugada → **Seguir sorteo en tiempo real**. Volver (botón o Back de Android) regresa a la jugada; de allí se vuelve al catálogo. Cerrar sesión vuelve al login.

`FirestoreFollowGateway` abre exactamente dos `addSnapshotListener`: documento `sorteos/{sorteoId}` y colección `sorteos/{sorteoId}/bolillas` con `orderBy("orden", ASCENDING)`. Cada callback trae el snapshot completo; reemplazamos la lista, no la concatenamos. Se comprueban enteros, rangos y unicidad de orden/número. Firestore omite documentos sin `orden` en esa consulta: al crear bolillas en consola hay que incluirlo siempre.

`remember(play)` conserva un controlador por pantalla y `DisposableEffect` arranca una sola pareja de listeners. `onDispose` llama a `close()`, que retira ambos `ListenerRegistration` con `remove()`. Salir/volver/cerrar sesión libera la pareja anterior. Los callbacks tardíos se ignoran. No hay polling, temporizadores, escrituras ni resultados persistidos en este flujo. Las reconexiones y snapshots de caché los maneja el SDK; la pantalla puede mostrar el último snapshot local si no hay red, por lo que no afirma estar actualizado en el servidor.

El resaltado es la intersección en memoria de los seis números guardados y las bolillas válidas. Estado `abierto`: espera; `en_curso`: Sorteo en curso; `finalizado`: Sorteo finalizado. **Aún no se muestra GANADOR/SIGUE**: el cálculo del resultado final queda para la siguiente parte.

## Prueba que deben hacer los integrantes en Android

1. Sin cambiar las reglas publicadas del PR de jugada, abre `sorteo-prueba` y guarda A: 5,12,18,23,34,41. En otra cuenta guarda B: 5,12,18,23,34,42. Entra en seguimiento en ambos equipos.
2. En consola cambia la cabecera a `en_curso`: ambos muestran Sorteo en curso sin refrescar.
3. En la subcolección `bolillas`, crea documentos con campos **orden (entero)** y **numero (entero)**: orden 1→5, 2→12, 3→18, 4→23, 5→34, 6→41, uno por uno. La lista sigue el orden de salida; los propios acertados se resaltan. Al quinto no hay resultado final, y tampoco se calcula en este PR al sexto.
4. Cambia estado a `finalizado`; verifica el mensaje. Vuelve a jugada y reentra varias veces: sin duplicados, una pareja de listeners por pantalla. Cierra sesión y entra con la otra cuenta: debe mostrar su propia jugada.
5. Reinicia la app: recupera sesión/jugada, reentra en seguimiento y aparecen las bolillas existentes. Prueba sin red y con reglas que denieguen lectura en un proyecto de prueba: no debe caerse ni mostrar errores internos crudos.

## Validación y límite

Tests JVM de `FollowController` ejercen lifecycle, estados, orden, repetición, rangos, intersección, errores y callbacks tardíos con un gateway de prueba. No prueban el SDK Android ni Compose en dispositivo. Los integrantes deben ejecutar la lista anterior; no se afirma una prueba Android real sin haberla realizado. No se cambian reglas ni config Firebase en este PR.

El emulador local también prueba listeners del SDK JavaScript (cabecera actualizada, consulta ordenada y rechazo anónimo) bajo las reglas publicadas en el repo. Es una comprobación de Firestore/reglas, no del adaptador Android.
