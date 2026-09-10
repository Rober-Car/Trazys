# AGENTS.md - Contexto del proyecto GestorPro

Lee este archivo completo antes de modificar el proyecto.

> ## ⚠️ CHECKPOINT 2026-09-10 (II) — HORARIO MULTI-TRAMO + "DÍAS ESPECIALES" + RULES DESPLEGADAS (REANUDAR AQUÍ)
>
> **HEAD del desarrollador: `75d0eb2 "otros"` (rama `master`).** Working tree **SIN commit** (NO revertir).
> Este bloque SUSTITUYE al checkpoint 2026-09-10 (I), que queda como histórico.
>
> ### Estado verificado (compila; tests OK; Rules 204/204)
> 1. **HORARIO (independiente de sesiones)** en `negocios_publicos/{negocioId}`:
>    - `horarioCentro`: semanal con **VARIOS TRAMOS por día** (día → lista de `{apertura,cierre}`); lista vacía = cerrado.
>    - `horarioActividades`: semanal (`idServicio`+hora).
>    - `horarioExcepciones`: fechas concretas con **varios tramos** (`{fecha, tramos:[...]}`); lista vacía = cerrado; prioridad sobre el semanal.
>    - El parseo (admin `HorarioSerializacion` y cliente `HorarioParseo`) **admite también el formato antiguo** (un tramo con `cerrado`) → sin pérdida de datos.
> 2. **ADMIN:** Ajustes → NEGOCIO: "Mi negocio", "Horario del centro", "Horario de actividades".
>    - Centro: "Aplicar a toda la semana" (tramos a los días abiertos; los cerrados se mantienen) + edición por día (añadir/editar/eliminar tramos) + validación (apertura < cierre, sin solapes) + **"Días especiales"** (terminología visible; antes "Excepciones"), con crear/editar/eliminar idéntico.
>    - Actividades: selector de día + chips de actividades existentes + hora + CRUD (guarda `idServicio`).
> 3. **CLIENTE:** Home con **exactamente 6 cards** en 3 filas de 2: **Reservas, Rutinas, Horario del centro, Actividades, Ajustes, Notificaciones**. Dos pantallas SEPARADAS: `HorarioCentroClienteScreen` y `ActividadesClienteScreen` (rutas `HORARIO_CENTRO` / `HORARIO_ACTIVIDADES`). En el horario del centro, si la fecha del día seleccionado tiene configuración propia, se muestra directamente su resultado ("Cerrado" o sus tramos), **sin mencionar "excepción"**. El card "Actividades" anterior se llama ahora "Reservas" (solo texto; navegación a `Routes.CLASES` intacta).
> 4. **Notificación `CAMBIO_HORARIO`:** switch en `configuracion_notificaciones/{id}.cambioHorario.activa`; guardar el horario del **CENTRO** crea `notificaciones` tipo `CAMBIO_HORARIO` (TODOS) si está activo. El horario de **ACTIVIDADES NO notifica**.
> 5. **Persistencia ante rotación** (`rememberSaveable` + savers propios) y estilo azul corporativo `#1E88E5`.
> 6. **FIX de permisos:** `NegocioRepository.guardarHorario` escribe **solo** en `negocios_publicos/{id}` (antes también `negocios/{id}`, cuya regla exige `adminUid` → "No tienes permisos").
>
> ### Rules + DEPLOY (HECHO)
> - `firebase.json` **ya incluye** `"firestore": { "rules": "firestore.rules" }` (además de `functions` y `hosting`).
> - Suite completa de Rules **204/204** en emulador.
> - **Desplegado** en `gestorpro-50e83`: ruleset `078c7b83-528b-48e6-8183-ede6d4a5dc29` (updateTime `2026-09-10T15:15:05Z`). Verificado por API: SHA-256 remoto == local.
> - Warnings de compilación de Rules (inofensivos): patrón ternario null (L61) y funciones no usadas `sesionDelNegocio` / `sesionAccesiblePorCliente`.
>
> ### Verificación de la tanda
> `:app` (**190 tests**) y `:appCliente` (**33 tests**): `assembleDebug` + `testDebugUnitTest` OK. Rules 204/204. `git diff --check` limpio (solo el log del emulador tiene trailing whitespace).
>
> ### Para reanudar
> 1. Probar en dispositivo el guardado real de horario (centro multi-tramo, días especiales, actividades).
> 2. Decidir commit agrupado del working tree (no hay commit nuestro).
> 3. (Opcional) Añadir `firestore-tests/firestore-debug.log` a `.gitignore`.

> ## 🗄️ CHECKPOINT 2026-09-10 (I) — HORARIO (modelo antiguo, un tramo) + DEPLOY PENDIENTE (HISTÓRICO)
>
> **HEAD del desarrollador: `75d0eb2 "otros"` (rama `master`).** El working tree conserva **SIN commit**
> 25 cambios (**NO revertir**; lista en `git status`). Sin commit/push/deploy nuestros.
>
> ### Qué se cerró en esta tanda (compila; tests unitarios OK; Rules 204/204)
> 1. **HORARIO (nuevo, INDEPENDIENTE de sesiones).** Se guarda en `negocios_publicos/{negocioId}` (NO en
>    `negocios`): `horarioCentro` (semanal, un tramo por día), `horarioActividades` (semanal, `idServicio`+hora)
>    y `horarioExcepciones` (fechas concretas, SEPARADAS del semanal y con PRIORIDAD sobre él).
> 2. **ADMIN:** Ajustes → sección **NEGOCIO** agrupa "Mi negocio", "Horario del centro" y
>    "Horario de actividades" (rutas `HORARIO_CENTRO`/`HORARIO_ACTIVIDADES`).
>    - **Horario del centro:** "Aplicar horario a toda la semana" (apertura/cierre a los 7 días abiertos;
>      los cerrados siguen cerrados) + edición individual + excepciones (crear/editar/eliminar).
>    - **Horario de actividades:** selector de día (scroll horizontal), chips de actividades existentes
>      (seleccionables), hora, añadir/editar/eliminar; guarda `idServicio` (no el nombre).
> 3. **CLIENTE:** card **"Horario"** en Home (solo vinculados+activos) → `HorarioScreen` con "HORARIO DEL
>    CENTRO" y "HORARIO DE ACTIVIDADES" (días scroll horizontal + tarjetas nombre/hora, sin imágenes).
>    El card "Actividades" del CLIENTE se renombró a **"Reservas"** (solo texto; rutas/clases internas intactas).
> 4. **Notificación CAMBIO_HORARIO:** switch propio en `configuracion_notificaciones/{id}.cambioHorario.activa`;
>    al guardar el horario del CENTRO se crea `notificaciones` tipo `CAMBIO_HORARIO` (TODOS) si está activo.
>    El horario de ACTIVIDADES NO notifica.
> 5. **Persistencia ante rotación:** `rememberSaveable` + savers propios (centro, excepciones, actividades,
>    día, servicio) + flag `precargado` (señal `cargado` del `HorarioViewModel`) para no sobrescribir ediciones.
> 6. **Estilo:** azul corporativo `#1E88E5` (títulos, chips, switches, acentos); eliminado el verde/teal.
> 7. **FIX de permisos al guardar:** `NegocioRepository.guardarHorario` escribía en `negocios/{id}` **y**
>    `negocios_publicos/{id}` en un batch; la regla de `negocios` exige `adminUid`, produciendo
>    "No tienes permisos". Ahora escribe **solo** en `negocios_publicos/{id}` (fuente que lee `leerHorario`/CLIENTE).
> 8. **Otras correcciones de la conversación (mismo working tree):** `AsistentesSesionScreen` (recuadro tipo
>    perfil + color de icono estable/aleatorio por nombre); `DetalleServicioScreen` (card de sesión con color
>    distinto); `ProgramarSesionesScreen` (hora global para todos los días + persistencia ante rotación).
>
> ### Rules (`firestore.rules` local — 204/204 en emulador)
> - `negocios_publicos` create/update: `hasOnly([...,"horarioCentro","horarioActividades","horarioExcepciones"])`;
>   update solo `esAdmin() && usuarioActual().negocioId == negocioId` (propietario); `get/list` autenticado;
>   `delete:false`. Otro ADMIN (negocioId distinto) y CLIENTE → denegado.
> - `configuracion_notificaciones` create/update: `hasOnly([...,"cambioHorario"])` + `cambioHorario.activa is bool`.
> - `notificaciones` (create ADMIN) y `notificaciones_por_destinatario` (create): `tipo` incluye `CAMBIO_HORARIO`.
>
> ### ⚠️ ESTADO DE DEPLOY (CRÍTICO — HORARIO aún NO funciona en producción)
> - Ruleset **desplegado** `17c46034-e079-4223-b26d-3a72ee3832cc` (updateTime 2026-09-09T17:51Z) **NO**
>   incluye horario → guardar horario da "No tienes permisos".
> - **`firebase.json` NO tiene sección `firestore`** (solo `functions` y `hosting`). Por eso
>   `firebase deploy --only firestore:rules` falla: "No targets in firebase.json match '--only firestore:rules'".
>   **Antes de desplegar** hay que añadir `"firestore": { "rules": "firestore.rules" }`.
> - El diff local↔desplegado es **SOLO** lo relativo a HORARIO (aditivo) → es seguro desplegar únicamente rules.
>
> ### Verificación de la tanda
> `:app` y `:appCliente`: `compileDebugKotlin`, `assembleDebug` y `testDebugUnitTest` OK. Rules **204/204**.
> `git diff --check` limpio (solo avisos CRLF preexistentes).
>
> ### Para reanudar
> 1. Añadir `"firestore": { "rules": "firestore.rules" }` a `firebase.json`.
> 2. `npm --prefix firestore-tests test` → 204/204.
> 3. `& ".\firestore-tests\node_modules\.bin\firebase.cmd" deploy --only firestore:rules`.
> 4. Verificar el nuevo `rulesetName`/`updateTime`.
> 5. Probar en dispositivo el guardado real de horario (centro, excepciones, actividades).
> 6. Decidir commit agrupado del working tree (no hay commit nuestro).

> ## ⚠️ CHECKPOINT 2026-09-09 — SISTEMA DE RESERVAS (FASES 1–4) + CORRECCIONES + DEPLOYS (REANUDAR AQUÍ)
>
> Estado real al cierre de la tanda. **HEAD del desarrollador: `e31e701` "Internacionalizacion ES/EN por
> bloques en Admin y Cliente"** (en `origin/master`; sin pendientes nuestros de push). El **working tree
> conserva SIN commit** el sistema de reservas completo y sus correcciones (**NO revertir**; 31 cambios en
> `git status`). Se han realizado DEPLOYS autorizados en producción `gestorpro-50e83`: las callable
> `reservar` y `cancelarReserva` (v2, europe-west1, nodejs20) y el **ruleset de Firestore de FASE 4**
> (cierre del acceso directo del CLIENTE). NO se desplegó Storage ni otras Functions.
>
> ### Sistema de reservas del CLIENTE (FASE 1–4)
> - **FASE 1 (backend):** Cloud Functions callable `reservar({sesionId})` y `cancelarReserva({sesionId})`
>   en `functions/lib/reservas.js` + reglas puras en `functions/lib/plan_reservas.js` (Admin SDK; no pasan
>   por Rules). Escriben `reservas/{clienteId}_{sesionId}`, `sesiones/{id}.asistentes.{clienteId}=nombre`
>   (solo nombre), agenda derivada `clientes/{clienteId}/agenda/{fecha}` = `{negocioId, fecha, sesiones:
>   {sesionId: idServicio}}`, y leen `servicios/{id}.permiteCombinarDia` (default true si falta). Tests
>   puros 24/24.
> - **FASE 2 (appCliente):** `ReservaRepository` ahora invoca las callables (europe-west1); modelos
>   `Servicio.permiteCombinarDia` y `Sesion.asistentes`; parseo en `SesionRepository`. Asistentes
>   inicialmente en la tarjeta de sesión (luego movidos, ver correcciones).
> - **FASE 3 (cascadas ADMIN):** `ReservaRemotoRepository` limpia la agenda en batches ≤400 tras eliminar
>   sesión/reservas y al cancelar (retira asistente y entrada de agenda); `BajaClienteRemotoRepository`
>   pasa por `cancelarReservaRemota`; `eliminarMiCuenta` (Functions) borra la subcolección agenda y retira
>   asistentes. Rules: match agenda `clientes/{clienteId}/agenda/{fecha}` (ADMIN de SU negocio gestiona;
>   CLIENTE solo `get` de la suya) y `sesiones` update ADMIN admite `asistentes`.
> - **FASE 4 (Rules):** se cierra el acceso directo del CLIENTE: reservas sin `create/delete` de CLIENTE,
>   sesiones sin `update` de plazas/asistentes del CLIENTE, agenda solo lectura propia, servicios
>   `permiteCombinarDia` solo ADMIN. Helpers huérfanos eliminados. Suite Rules **197/197**.
>
> ### Correcciones reales (diagnóstico + arreglo)
> 1. **ADMIN — `permiteCombinarDia`:** no existía en `:app` (solo backend/Rules/CLIENTE). Añadido en
>    `ServicioEntity` + **Room v19** (`MIGRACION_18_19` recrea `servicio` con la columna NOT NULL 1),
>    `ServicioRemotoRepository` (create/update), `ServicioViewModel.crearServicio(+param)`,
>    `EditarServicioScreen` (switch "Permitir combinar…" SIEMPRE en alta y edición, default true) y
>    `HidratacionMapeadores` (default true si el campo remoto falta).
> 2. **CLIENTE — asistentes:** fuera de la tarjeta de sesión (tarjeta compacta de nuevo). Nueva pantalla
>    **`AsistentesSesionScreen`** + `AsistentesSesionViewModel`, ruta `asistentes_sesion/{idSesion}`,
>    acceso con botón "Ver asistentes" SOLO en estado RESERVADA, `SesionRepository.obtenerSesionPorId`,
>    y `SesionVisible` sin el campo `asistentes`. UI: lista con icono de persona + nombre y separadores,
>    encabezado con total (plurals `asistentes_total`).
> 3. **CLIENTE — cancelar reserva con varias reservas:** bug en `cancelarReserva` (Functions): se hacía
>    `tx.get(agendaRef)` DESPUÉS de `tx.delete/update` → error del SDK "Firestore transactions require all
>    reads…". Reordenada la Transaction para hacer TODAS las lecturas antes de las escrituras. **Deploy
>    autorizado solo de `cancelarReserva`** ya ejecutado y verificado.
>
> ### Verificación (última)
> `:app` y `:appCliente`: `testDebugUnitTest` y `assembleDebug` BUILD SUCCESSFUL. Functions puras 47/47
> (24 de reservas + resto) y Rules **197/197**. `git diff --check` limpio. **Sin commit/push.** Los
> bloques inferiores (i18n y anteriores) quedan como histórico ya superado en parte por este checkpoint.

> ## ⚠️ CHECKPOINT 2026-09-09 (I18N) — INTERNACIONALIZACIÓN ES/EN IMPLEMENTADA POR BLOQUES EN `:app` Y `:appCliente` (REANUDAR AQUÍ)
>
> Estado real al cierre de la tanda de i18n. **HEAD del desarrollador: `64de014` "Ignorar archivos Shelf
> de Android Studio"** (sobre `9cd98bd`; nada nuestro commiteado). El **working tree conserva SIN commit**
> TODO el trabajo de i18n de esta tanda (**NO revertir**; lista completa en `git status`). Sin push, sin
> deploy, sin tocar Firebase/Firestore/Rules/Functions/Storage. La infraestructura de idioma
> (`IdiomaAplicacion`, preferencia en DataStore, `attachBaseContext`+`recreate`) quedó commiteada por el
> desarrollador en tandas previas; nosotros añadimos a `util/IdiomaAplicacion` (ambos módulos) los
> helpers `textoDe(base, recurso[, vararg])` que resuelven recursos en el idioma elegido desde capas sin
> composición (repositorios/ViewModels vía `@ApplicationContext`).
>
> ### Qué se ha implementado (todo compilando y con tests OK)
> - **Recursos:** `:app` → `values/strings.xml` y `values-en/strings.xml` (48 y 48 claves, solo bloque
>   AUTH de ADMIN); `:appCliente` → `values/strings.xml` y `values-en/strings.xml` (**237 y 237 claves**).
>   Claves ES/EN idénticas en ambos módulos; prefijos por bloque (`auth_*`, `inicio_*`, `vinculacion_*`,
>   `perfil_*`, `cuenta_*`, `estado_*`, `accion_*`, `foto_*`, `home_*`, `denuncia_*`, `config_*`,
>   `eliminar_*`, `terminos_*`, `privacidad_*`, `notif_*`). `values-en/` es carpeta nueva en ambos módulos.
> - **`:app` (ADMIN) — bloque AUTENTICACIÓN:** `LoginScreen`, `RegistroScreen`, `RecuperarPasswordScreen`
>   (textos → `stringResource`), `AutenticacionRepository` (mensajes de login/registro/recuperación →
>   recursos vía `textoDe`), `MainViewModel` (validaciones visibles → recursos). Se dejó intacto
>   `validarCambioContrasena` (función pura testada) y los mensajes de `cambiarContrasena` (bloque Cuenta
>   pendiente en ADMIN).
> - **`:appCliente` (CLIENTE) — bloques completados:** AUTENTICACIÓN (Login/Registro/Recuperar),
>   INCORPORACIÓN (`EleccionInicioScreen`, `InicioScreen`/código+DNI, `CompletarPerfilScreen`,
>   `BotonSelectorFoto`), MI PERFIL (`MiPerfilScreen`, `EditarPerfilScreen`), MI CUENTA (`CuentaScreen` +
>   `DialogoCambiarContrasena`), HOME (`HomeScreen` + `AvisoMorosidad` desacoplado de `indexOf("aquí")`),
>   CONFIGURACIÓN (`ConfiguracionScreen`, `EliminarCuentaScreen`) y NOTIFICACIONES
>   (`ListaNotificacionesScreen`, `NotificacionesScreen` preferencias de avisos).
> - **Errores/validaciones visibles localizados** en repos/ViewModels del CLIENTE vía `@ApplicationContext`
>   + `IdiomaAplicacion.textoDe`: `AutenticacionRepository`, `VinculacionRepository`,
>   `PerfilPendienteRepository`, `ClienteRepository`, `SolicitudRepository`, `DenunciaRepository` (solo
>   errores que llegan a pantallas), `MainViewModel` (cliente y admin) y `NotificacionesClienteViewModel`.
> - **Acoplamientos corregidos:** `estadoTexto`/`estadoColor` de MiPerfil ahora dependen del **código
>   remoto** (enum), no del texto traducido; `AvisoMorosidad` usa anotación posicional
>   (`pushStringAnnotation`) en vez de `indexOf("aquí")`; `EditarPerfilScreen` detecta el gate de Términos
>   por un flag del VM (`requiereTerminosParaFoto`), no por `startsWith`; `DenunciaRepository` conserva la
>   función pura `validarDatosDenuncia` + `MotivosDenuncia.etiqueta` para tests y resuelve la traducción en
>   la capa de UI/recursos (`recursoDeMotivo`) o replicando la semántica localizada.
> - **Mantenidos intactos a propósito:** función pura `validarCambioContrasena` (+ sus tests
>   `CambiarContrasenaTest` y `DenunciaReglasTest`), lógica/esManual/origen de notificaciones, contenido
>   **remoto** `Notificacion.titulo/mensaje`, patrón de fechas `dd/MM/yyyy…`, orden de cards/badge del Home,
>   navegación, Firestore/Firebase.
> - **EXCLUIDO del alcance (documentado en auditorías):** cuerpo legal (contenido largo) de
>   `PoliticaPrivacidadScreen` y `TerminosDeUsoScreen` (solo se tradujo la interfaz/cabeceras/versión);
>   pantallas del ADMIN fuera de AUTH (Home, Configuración, Cuenta/cambio de contraseña, Perfil de cliente,
>   Servicios/Actividades, Economía, Solicitudes, Gestión de notificaciones…); `ClasesScreen`/Actividades y
>   Rutinas del CLIENTE; `InformacionLegalScreen` (sin ruta en AppNavigation); web `/terminos`/`/privacidad`.
>
> ### Verificación (última, working tree)
> `:app:compileDebugKotlin`+`:app:assembleDebug` OK y `:appCliente:compileDebugKotlin`+`:app:assembleDebug`
> OK; `:app:testDebugUnitTest` y `:appCliente:testDebugUnitTest` → BUILD SUCCESSFUL. Comprobación
> automática ES/EN: claves idénticas, sin claves sin uso, todos los `R.string` usados presentes en ambos
> idiomas. `git diff --check` limpio (solo avisos CRLF preexistentes). Sin commit/push/deploy.
>
> ### Para reanudar
> 1. Revisar y decidir el **commit agrupado** de todo el working tree de i18n (no hay commit nuestro aún).
> 2. Siguientes bloques i18n candidatos: ADMIN (resto de pantallas) y CLIENTE (Actividades/`ClasesScreen`,
>    Rutinas, `TerminosDeUsoScreen`/`PoliticaPrivacidadScreen` cuerpos legales largos con decisión de
>    granularidad, si se pide). Recordar que `EditarPerfilScreen` (fuera de alcance en su bloque) también
>    se internacionalizó de pasada como dependencia directa del flujo de perfil.
> 3. Pendientes del proyecto no i18n siguen vivos (ver checkpoints inferiores): prueba funcional de
>    `notificacionInmediata`, Rules de `denuncias` sin desplegar, bucket de Storage, logs de diagnóstico,
>    `fallbackToDestructiveMigration`, etc.

> ## ⚠️ CHECKPOINT 2026-09-09 — DEPLOY FUNCTION NOTIFICACIÓN INMEDIATA + INDICADOR DE LECTURA ADMIN + HOME CLIENTE (REANUDAR AQUÍ)
>
> Estado real al cierre de la tanda. **HEAD del desarrollador: `a0bc03b` "correocioens"** (en
> `origin/master`, sin pendientes de push; `77e3641` "seguridad de google play" ya había commiteado
> denuncias 2C-2, Login Cliente, web `/terminos` y la documentación previa; `a0bc03b` incorpora la
> retirada 2C-3 de notificaciones y sus tests). El working tree conserva cambios SIN commit que
> MEZCLAN trabajo en curso del desarrollador y cambios nuestros (**NO revertir**; lista en
> `git status`).
>
> ### Trabajo SIN commit en el árbol (NO revertir)
> 1. **En curso del desarrollador (internacionalización/idioma, NO tocar sin consultar):** nuevo
>    `util/IdiomaAplicacion.kt` en `:app` y `:appCliente`, y modificados `GestorProApplication`/
>    `MainActivity`, `PreferencesRepository`, `MainViewModel`, `SolicitudesViewModel`, `MenuCard`,
>    `HomeScreen` (Admin), `ConfiguracionScreen`/`PreferenciasScreen` (Admin y Cliente), etc.
> 2. **Nuestros cambios de esta tanda (sin commit):**
>    - **Decisión cerrada `notificaciones/{id}.estado`:** `estado` sigue siendo SOLO el estado de
>      ENVÍO (PENDIENTE/ENVIADA/PROGRAMADA/CANCELADA/ERROR); la lectura vive en
>      `notificaciones_por_destinatario/{clienteId}_{notificacionId}` (`leida`/`fechaLeida`). NO se
>      crea estado LEIDA. **Implementado** (Admin): `LecturaNotificacion(leidas,total)` +
>      `NotificacionRemotoRepository.obtenerLecturaBuzones(negocioId)` (UNA consulta por negocio),
>      `NotificacionesViewModel.lecturaPorNotificacion` (best-effort: si falla la lista sigue sin
>      indicador) y `GestionNotificacionesScreen` con indicador independiente del chip de estado
>      ("Leída"/"Sin leer" si 1 destinatario; "X/Y leídas" si varios; sin indicador si total==0).
>      Textos en español temporales (i18n pendiente).
>    - **`firebase.json`:** añadida la sección `"functions": { "source": "functions" }` (aprobada)
>      para poder desplegar funciones. Resto intacto.
>    - **Home del CLIENTE (`HomeScreen.kt`):** solo reorden de cards en la cuadrícula (2 columnas):
>      Fila 1 = Actividades | Rutinas; Fila 2 = Ajustes | Notificaciones. Sin cambios de contenido/
>      acciones; el badge de no leídas sigue en el card "Notificaciones". `:appCliente` compila.
>
> ### Firebase (producción) — desplegado y verificado
> - **Cloud Function `notificacionInmediata` DESPLEGADA** (v2, `onDocumentCreated("notificaciones/{id}")`,
>   región `europe-west1`, nodejs20) → hará `PENDIENTE→ENVIADA` (claim atómico + fechaEnvio) y envío FCM
>   real para inmediatas MANUAL creadas a partir del despliegue. Verificado con `firebase functions:list`
>   (junto a `eliminarMiCuenta`). Primer intento falló por permisos del Eventarc Service Agent (400) y
>   el reintento tras unos minutos funcionó. **PENDIENTE: prueba funcional manual de envío** (estado y push).
> - Avisos de Firebase a revisar: **Node.js 20 deprecado (decommission 2026-10-31)** y
>   `firebase-functions` desactualizada; error de limpieza de imágenes de build (posibles restos en
>   `gcr.io/.../eu/gcf`). Sin acciones tomadas (requiere decisión).
> - `firestore.rules` con `denuncias` **NO desplegado** todavía (solo commiteado). Hosting `/terminos`
>   y resto de web ya desplegados y verificados previamente.
>
> ### Verificación (última)
> `:app` y `:appCliente`: unit tests y assembleDebug OK en tandas anteriores. `:appCliente:compileDebugKotlin`
> OK tras el reorden. `git diff --check` limpio (avisos CRLF preexistentes). Sin commit/push de lo de arriba.

> ## ⚠️ CHECKPOINT 2026-09-08 (2) — FASE 2C-3 RETIRADA DE NOTIF. MANUALES + FOTOS EN EL SELECTOR DE CLIENTES + DIAGNÓSTICO "MARCAR COMO LEÍDA" (REANUDAR AQUÍ)
>
> Estado real al cierre de la tanda. **HEAD del desarrollador: `77e3641 "seguridad de google play"`**
> (el bloque anterior con HEAD `63d74f2` queda SUPERADO: el desarrollador commiteó su contenido y las
> fases posteriores —Login Cliente, web `/terminos`, denuncias 2C-2— en `0f5d332`/`84d23ef`/`77e3641`).
> El **working tree conserva SIN commit** lo de esta tanda (NO revertir): FASE 2C-3 (retirada),
> la corrección de fotos del selector, el borrado de la basura `.idea/shelf/…` y el log del emulador.
> Sin commit, sin push, sin deploy.
>
> ### 1. FASE 2C-3 — Retirar notificaciones MANUALES publicadas (moderación UGC)
> - Regla pura `util/RetiradaNotificacionReglas.kt`: **retirable** = `origen == "MANUAL"` y estado
>   `PENDIENTE`/`ENVIADA`. Automáticas/preconfiguradas (BAJA_CONFIRMADA/SOLICITUD_BAJA/VINCULACION) y
>   programadas aún no publicadas NUNCA se retiran con esta acción (estas últimas usan la cancelación
>   existente).
> - `NotificacionRemotoRepository.retirarNotificacionManual(notificacionId)`: idempotente (doc inexistente
>   → éxito); valida la regla; borra `notificaciones/{id}` + los buzones deterministas
>   `{clienteId}_{notificacionId}` de `idsClientes` en lotes ≤500 (helper companion `idDeBuzon`,
>   reutilizado también en `crearBuzones`). NO borra denuncias.
> - `NotificacionesViewModel.retirarNotificacion` + `retirandoNotificacion` (guarda anti doble pulsación;
>   éxito → snackbar + recarga; fallo → `errorSincronizacion`).
> - `GestionNotificacionesScreen`: acción **"Retirar"** (roja) solo en cards manuales publicadas, spinner
>   mientras retira y diálogo de confirmación ("dejará de estar disponible… acción permanente"). La
>   cancelación de programadas y el resto de la pantalla quedan intactos.
> - **Rules NO modificadas** (ya permiten `delete` ADMIN en `notificaciones` y buzones). Denuncias no se
>   borran al retirar (el ADMIN las marca revisadas). Tests `RetiradaNotificacionReglasTest` (14).
>
> ### 2. Corrección confirmada — Fotos vacías en el selector de clientes
> - Causa: `SeleccionarClientesScreen` llamaba a `ClienteItem` SIN `idCliente` ni `obtenerFotoCacheada`
>   (a diferencia de `ClientesScreen`), por lo que las fotos remotas (URL de Storage, flujo nuevo) se
>   intentaban con la URL cruda y quedaban vacías.
> - **Corregido** (`SeleccionarClientesScreen.kt`): se pasa `idCliente = cliente.idCliente` y
>   `obtenerFotoCacheada = clienteViewModel::cargarFotoLocal` (misma carga autenticada que `ClientesScreen`).
>   Sin tocar `FotoClienteStorage`/`FotoClienteCache`/Rules/Storage/modelo.
>
> ### 3. DIAGNÓSTICO "marcar como leída" (SIN cambios de código)
> - Flujo existe y funciona: `ListaNotificacionesScreen` (Card onClick) →
>   `NotificacionesClienteViewModel.marcarLeida` → `NotificacionRepository.marcarComoLeida(docId)` que
>   hace `update leida=true + fechaLeida` sobre el buzón.
> - Evidencia real (Firestore, lectura autorizada): buzón `1716402750_n_1788893959120_6989` →
>   `leida=true` + `fechaLeida=2026-09-08T19:00:01Z` (el CLIENTE SÍ lo marcó); buzón
>   `1100806408_n_1788883813058_8107` → sigue `leida=false` y `updateTime` intacto (el update nunca llegó).
> - Rules desplegadas (ruleset `b4559665`, release `cloud.firestore` updateTime 2026-09-06) permiten el
>   `update` CLIENTE de su buzón (leida/fechaLeida, firebaseUid == uid): NO bloquean.
> - Causa raíz NO cerrada: en el buzón no actualizado el update no llegó a Firestore (APK antiguo sin el
>   flujo, identidad distinta o error silencioso logueado en `NotificacionRepository`); no se identifica
>   divergencia UI↔Firestore en el código actual. **Corrección mínima recomendada (optimista + recarga)
>   NO implementada.** Verificar Logcat `NotificacionRepository` en el dispositivo.
>
> ### Verificación (working tree)
> `:app:testDebugUnitTest` OK, `:app:assembleDebug` OK, Rules **182/182** (sin cambios). `git diff
> --check` limpio salvo avisos CRLF/whitespace preexistentes (log del emulador y `.idea/misc.xml`).

> ## ⚠️ CHECKPOINT 2026-09-08 — CAMBIO DE CONTRASEÑA REAL + GATE NOTIF. MANUALES (FASE 2B-1) + LOGIN CLIENTE + WEB /TERMINOS (2C-1) + DENUNCIAS UGC (2C-2) (REANUDAR AQUÍ)
>
> Estado real al cierre de la tanda. **HEAD del desarrollador: `63d74f2` "correcion contraseñas"**
> (ahead de `origin/master` en 1; **sin push**). El working tree conserva cambios SIN commit de
> las últimas fases (**NO revertir**; lista en `git status`). El bloque anterior (post-recuperación
> `0b1d370`) queda SUPERADO: su working tree fue commiteado por el desarrollador en `0b1d370`/
> `84d23ef`/`0f5d332` (tanda UGC/Términos/Identidad) y parte de lo de esta tanda también.
>
> ### Commiteado por el desarrollador en `63d74f2` (NO revertir; sin push)
> 1. **Cambio de contraseña REAL en ADMIN y CLIENTE** (todo lo implementado en esta tanda):
>    `AutenticacionRepository.cambiarContrasena(actual, nueva)` en `:app` y `:appCliente`
>    (reautenticación `EmailAuthProvider` + `updatePassword`, solo tras reauth correcta; sin
>    persistir contraseñas) + validador puro `validarCambioContrasena(actual,nueva,repetida)`;
>    `MainViewModel.cambiarContrasena(actual,nueva,repetida)` (3 args) con `_cambiandoContrasena`;
>    Admin `CuentaScreen` (diálogo real con carga/error/éxito "Contraseña actualizada
>    correctamente.") y Cliente `CuentaScreen` (nueva tarjeta "Cambiar contraseña" + diálogo, éxito
>    por snackbar). Tests `CambiarContrasenaTest` en ambos módulos.
> 2. **FASE 2B-1 — Gate de Términos para notificaciones MANUALES (Admin):** `util/GateUgcNotificaciones.kt`
>    (regla pura `esPublicacionManual`/`decidir` + `ResultadoGateUgc`); `NotificacionesViewModel`
>    con gate ANTES de publicar (inmediata, programada y reintento) y estado `requiereAceptarTerminos`;
>    `CrearNotificacionScreen` con aviso + botón "Ver y aceptar los Términos de uso" →
>    `Routes.TERMINOS_CONDICIONES` sin perder el formulario. Automáticas (BAJA_CONFIRMADA/
>    SOLICITUD_BAJA/VINCULACION) NUNCA bloqueadas. Tests `GateUgcNotificacionesTest` (11).
> 3. Incluye basura `.idea/shelf/…` (irrelevante).
>
> ### Cambios SIN commit en el árbol (pendientes; NO revertir)
> 4. **Estética Login Cliente (`:appCliente/.../ui/auth/LoginScreen.kt`):** ahora igual que el Login
>    Admin: desaparece la "tarjeta gris" (`Card.containerColor = surface`), bloque de logo (remoto o
>    icono), cabecera interna "Iniciar sesión", espaciados, `unfocusedBorderColor`, spinner y pie
>    "© 2026 Trazys". Solo presentación; lógica intacta.
> 5. **FASE 2B-2 (motivo de baja) — DECISIÓN: NO implementar.** Verificado: el CLIENTE no tiene campo
>    "Motivo" (CuentaScreen llama a `solicitarBaja(null)`); no hay UGC de texto que gatear. Sin cambios.
> 6. **FASE 2C-1 — Términos en la web:** creado `web/terminos/index.html` (contenido de los Términos
>    neutrales de `:app`, formato web) y enlazado `/terminos` desde portada, `/privacidad` y
>    `/eliminar-cuenta` (nav + pie + referencias). **DEPLOY autorizado SOLO hosting** ya ejecutado →
>    `https://trazys.web.app/terminos` (y resto) verificados 200.
> 7. **FASE 2C-2 — Sistema de denuncias UGC (implementado, SIN deploy de Rules):**
>    - `firestore.rules`: bloque `denuncias/{denunciaId}` (create autenticado del MISMO negocio,
>      `denuncianteUid == auth.uid`, whitelists tipo/motivo/estado PENDIENTE, sin autodenuncia,
>      usuario denunciado del negocio; `get` ADMIN del negocio o CLIENTE solo suya; `list` solo ADMIN;
>      `update` ADMIN solo `estado`; `delete` false). Sin tocar otras colecciones.
>    - `:app`: `data/firebase/DenunciaRepository.kt`, `ui/viewmodel/DenunciasViewModel.kt`,
>      `ui/components/DialogoDenuncia.kt`, `ui/configuracion/GestionDenunciasScreen.kt` (lista +
>      "Marcar como revisada"; Configuración → sección MODERACIÓN → "Denuncias"; ruta `DENUNCIAS`),
>      y en `PerfilClienteAdministradorScreen` menú ⋮ → "Denunciar contenido" (foto remota) /
>      "Denunciar usuario" (si `firebaseUid`).
>    - `:appCliente`: `data/firebase/DenunciaRepository.kt`, `ui/components/DialogoDenuncia.kt`,
>      `MainViewModel.denunciarNotificacion/denunciarLogoNegocio`, `⋮` en cada notificación MANUAL
>      (`ListaNotificacionesScreen`) y `⋮` "Denunciar el centro" en el Home (logo/negocio).
>    - Modelo: `denuncias/{id}` = negocioId, denuncianteUid, usuarioDenunciadoUid?, tipo
>      (FOTO_CLIENTE/NOTIFICACION/LOGO_NEGOCIO), referencia, motivo (INAPROPIADO/ACOSO/OFENSIVO/
>      SUPLANTACION/OTRO), descripcion?, fecha, estado (PENDIENTE/REVISADA).
>    - Tests Rules **PRUEBA 151–163** → suite **182/182**; unit `DenunciaReglasTest` en `:app` y
>      `:appCliente`. Pendiente reconocido: sin UI de "retirar notificación entregada" (Rules lo
>      permiten); sin bloqueo de usuarios (no hay P2P; el ADMIN gestiona con BAJA/ARCHIVADO).
>    - **Rules NO desplegadas.**
>
> ### Verificación (última)
> Rules Firestore/Storage **182/182** (`npm --prefix firestore-tests test`). `:app` y `:appCliente`:
> `testDebugUnitTest` y `assembleDebug` → BUILD SUCCESSFUL. `git diff --check` limpio (solo avisos
> CRLF preexistentes). Sin commit/push de las fases 4–7 (el desarrollador commiteó solo 1–2).

> ## ⚠️ CHECKPOINT (post-recuperación `0b1d370`) — TANDA UGC / TÉRMINOS / IDENTIDAD (REANUDAR AQUÍ)
>
> Estado real al cierre de la tanda reciente. **HEAD del desarrollador: `0b1d370 "Correcion lineas de
> termninos y seguridad,"`** (por encima de `0e98921 "Preparación de Trazys para Google Play"`, punto
> estable de referencia). El working tree tiene cambios SIN commit (ver `git status`; NO revertir):
> `data/local/PreparadorLocalCuenta.kt`, `ui/viewmodel/ClienteViewModel.kt`, `ui/viewmodel/MainViewModel.kt`
> (Admin), `ui/auth/RegistroScreen.kt` (Admin), `ui/configuracion/MiNegocioScreen.kt` (Admin), y
> `VinculacionRepository.kt`, `EditarPerfilScreen.kt`, `RegistroScreen.kt`, `HomeScreen.kt`,
> `ui/viewmodel/MainViewModel.kt` (Cliente).
>
> ### Qué se cerró en esta tanda (working tree, SIN deploy/commit)
> 1. **Términos en Registro (:app y :appCliente):** checkbox obligatorio + enlaces a Términos y Política
>    (dos líneas), alineados en la misma columna y con el checkbox alineado a la primera línea. La
>    pantalla de Términos refleja la aceptación persistida (uid + versión + fecha): no repite el botón si
>    ya aceptó la vigente y muestra "Términos aceptados — versión 1.0".
> 2. **Configuración Admin:** Política de privacidad y Términos como entradas de lista (sin cards),
>    estilo app Cliente.
> 3. **Gates de Términos para FOTOS:** cliente (edición de foto), VÍA 2 (foto pendiente sin romper la
>    vinculación) y ADMIN (alta/edición/reintento de fotos; en edición de cliente existente no se escribe
>    la ruta local como foto remota). En EditarPerfil hay aviso con enlace a `Routes.TERMINOS_CONDICIONES`.
> 4. **Gate del LOGO:** bloqueado en `MainViewModel.sincronizarLogoNegocio` ANTES de
>    `NegocioRepository.guardarLogoRemoto` (sin `putFile` ni actualización de Firestore); MiNegocio
>    muestra aviso y botón "Ver y aceptar los Términos de uso"; la imagen seleccionada se conserva.
> 5. **Aislamiento de identidad Admin entre cuentas:** `refrescarIdentidadLocal()` solo expone la caché
>    si `uid == uid_propietario_datos_locales`; `PreparadorLocalCuenta.resolver` limpia la identidad de
>    DataStore antes de adoptar (owner null sin datos). Evita mostrar nombre/logo de la cuenta anterior.
> 6. **UI tarjeta de estado (Cliente Home):** si no hay fecha real no se muestra "Fecha no disponible";
>    solo estado ("Activo"/"Registrado"); con fecha se muestra "Hasta el …".
> 7. **Análisis (sin implementar):** la regla "sin movimientos → REGISTRADO en VÍA 1" NO se aplica por
>    conflicto con Rules (acceso solo ACTIVO) y con la activación manual (pendiente decisión de negocio).
>    Diagnóstico del rechazo de email en Registro Admin (sin corrección): causado por carácter invisible
>    no estándar y por el mapeo de `FirebaseAuthInvalidCredentialsException` a "formato no válido";
>    corrección recomendada: normalizar email y validar con `Patterns.EMAIL_ADDRESS` en cliente.
>
> ### Verificación
> `:app` y `:appCliente`: `testDebugUnitTest` y `assembleDebug` → BUILD SUCCESSFUL. `git diff --check`
> limpio. Sin commit, sin push, sin deploy.

> ## ⚠️ CHECKPOINT 2026-09-07 — MARCA TRAZYS + PRE-BETA + UGC (REANUDAR AQUÍ)
>
> Estado REAL al cierre de la última tanda. **HEAD del desarrollador:** `b863397` ("foto storage
> implementado"). El **working tree tiene cambios SIN commit** (NO revertir): rebranding Trazys,
> eliminación de cuenta, web Trazys, Términos (parcial) y verificación general. **Sin commit por
> nuestra parte.** No inventar: todo lo descrito está verificado en el árbol o desplegado.
>
> ### Marca
> - Nombre visible **Trazys** (Admin) / **Trazys Cliente** (Cliente): `app_name`, textos/fallbacks
>   visibles, copyright, política de privacidad (apps + web), `ORIGEN_APP`, backup `trazys_backup.zip`,
>   `settings.gradle.kts` rootProject. NO se cambiaron applicationId/namespace/paquetes/clases/Firebase
>   (Project ID `gestorpro-50e83`). Repositorio GitHub sigue como `Rober-Car/GestorPro` (remote sin
>   cambiar). Icono sin rediseñar.
>
> ### Firebase desplegado (verificado por API)
> - Firestore Rules doc-first: release `cloud.firestore` → ruleset `b4559665-ca2f-424b-80ea-3d2e133cc9b9`.
> - Storage Rules doc-first: release `firebase.storage/…` → `c68b395b-c4c6-4aa3-94b1-564f75311600`.
> - Function callable v2 **`eliminarMiCuenta`** en `europe-west1` (Node 20). `functions/package.json`
>   engines subido a 20 (Node 18 retirado). `npm install` hecho en `functions/`.
> - Hosting site **`trazys`** creado y **desplegado**: `https://trazys.web.app` (+ `/privacidad`,
>   `/eliminar-cuenta`). Estructura `web/` con carpetas `index.html` y CSS compartido.
> - Eliminación cuenta (Function): CLIENTE borra cuenta + datos personales y **conserva** movimientos,
>   índice y ficha mínima (idCliente, negocioId, nombre, apellidos, dni, firebaseUid null, foto "");
>   ADMIN borra todo el negocio. Sin cambios de Rules. Auth se borra al final. Idempotente.
>
> ### Términos de uso (FASE 1 parcial)
> - Núcleo listo: `TerminosDeUso.VERSION="1.0"` (claves uid/version/fecha en DataStore),
>   `PreferencesRepository.terminosAceptados(uid)/guardarAceptacionTerminos(uid)`,
>   `MainViewModel.terminosAceptados()/aceptarTerminos()` (Admin y Cliente), tests del verificador.
> - Pantallas reales `TerminosDeUsoScreen` en `:app` y `:appCliente`; rutas/navegación (Admin ruta
>   nueva `TERMINOS_CONDICIONES`; Cliente repunta la existente), acceso en Configuración (Admin añadida).
> - **PENDIENTE FASE 1:** checkbox obligatorio + enlaces en Registro de ambas apps (bloquea registrar
>   sin aceptar); tests de flujo de Registro.
> - Fases posteriores (aún NO): gates de subida de UGC (foto/logo/notificaciones), denuncias/
>   retirada, moderación, web `/terminos`.
> - UGC según Play: sí (foto de perfil del CLIENTE visible al ADMIN, logo del negocio visible a
>   clientes, notificaciones del ADMIN a clientes). Observaciones privadas del ADMIN NO son UGC.
>
> ### Pendientes pre-beta conocidos
> - Terminar Fase 1 UGC (Registro), gates y web `/terminos` + denuncias.
> - Commitear el working tree; renombrar repositorio GitHub a Trazys y actualizar `origin` (requiere
>   acción del propietario); nombre visible del proyecto Firebase en consola.
> - Revisión de política: sustituir afirmaciones obsoletas ya corregidas en apps/web (logo en Storage,
>   FCM); revisar detalle de Funciones/FCM para producción.
>
> - Estado anterior en los CHECKPOINT previos de este archivo y en AGENTS/CONTEXTO (histórico).


> ## ⚠️ CHECKPOINT 2026-09-06 (FASE FINAL PRE-BLAZE — 3 correcciones UX + selector GRUPO; continuar mañana desde aquí)
>
> Estado para REANUDAR. **HEAD del desarrollador: `cb44d1e`** (2026-09-05 18:27). **Working tree con
> cambios SIN commit** (lista en `git status`; NO revertir): 3 correcciones UX (orden de clientes,
> selector individual de notificación, colores de Switches) + el selector GRUPO de notificación +
> esta documentación. Se hizo **commit del contenido anterior** en `cb44d1e`/`5184af9`.
>
> ### Cambios SIN commit en el árbol (pendientes de revisión/commit)
> - `model/Cliente.kt` + `data/entity/ClienteEntity.kt`: el modelo expone `apellidos` (para ordenar
>   apellido→nombre; sin migración Room).
> - `ui/clientes/ClientesScreen.kt`: la lista se ordena alfabéticamente natural por
>   `apellidos→nombre` (case-insensitive) DESPUÉS de filtros/búsqueda; búsqueda/filtros/selección
>   intactos.
> - `ui/notificaciones/CrearNotificacionScreen.kt`: el selector de cliente INDIVIDUAL y el control
>   de selección GRUPO pasan a una `Surface` tonal clicable con icono + chevron (ya no parecen campos
>   de texto). Lógica/navegación intactas.
> - Switches: regla visual ON = azul corporativo `#1E88E5`; excepciones de riesgo (morosidad y baja)
>   conservan rojo: `ui/clientes/PerfilClienteAdministradorScreen.kt` (Pago realizado azul; "Exento de
>   morosidad" rojo), `ui/components/DialogoEdicionMovimiento.kt`, `ui/servicios/EditarServicioScreen.kt`,
>   `ui/notificaciones/ConfigNotificacionesScreen.kt` (morosidad/baja confirmada rojo, recordatorio azul),
>   `appCliente/.../NotificacionesScreen.kt` (Recibir avisos azul).
> - Docs (esta petición): `AGENTS.md`, `CONTEXTO_PROYECTO.md`, `CONVERSACION_EXPORTADA.md`.
>
> ### Verificación
> - `:app:testDebugUnitTest` → **146/146**; `:app:assembleDebug` → BUILD SUCCESSFUL.
> - `:appCliente:testDebugUnitTest` → **17/17**; `:appCliente:assembleDebug` → BUILD SUCCESSFUL.
> - `git diff --check` limpio (solo avisos CRLF preexistentes). Sin cambios de Rules/backend.
>
> ### Resumen del estado global (bloques anteriores)
> HEAD `cb44d1e` ya contiene: Room v18 + `MIGRACION_17_18` (probada en teléfono físico), Economía de
> gestión + editor compartido, barra contextual común, selección/vinculación de clientes, arreglos de
> reservas y baja durable de actividades. Detalle en el CHECKPOINT 2026-09-05 anterior.
>
> ### Para mañana
> 1. Revisar y decidir el commit agrupado de los cambios SIN commit (o aplicar correcciones que
>    surjan de la prueba visual).
> 2. Seguir pendientes: retirar logs `[DIAG alta]`/`ClasesDiagnostico`, `fallbackToDestructiveMigration`
>    antes de producción, Storage/bucket + Blaze/Functions, VÍA 2/fecha nacimiento opcional, limpiar
>    `%TEMP%\opencode\roomtest`.

> ## ⚠️ CHECKPOINT 2026-09-05 (CIERRE — gestión masiva Economía/Clientes, vinculación, reservas, baja durable de actividades, Room v18 y migración 17→18 PROBADA EN DISPOSITIVO)
>
> Estado REAL al cierre de la tanda larga reciente. **HEAD del desarrollador: `cb44d1e`**
> (`cORRECCIONES DEPSUES DE LAS PREUBAS`, 2026-09-05 18:27, rama `master`). **Working tree limpio**
> (`git status` vacío). Este checkpoint SUPERSEDE al bloque 2026-09-05 anterior (que describía el árbol
> con HEAD `f616891` y cambios SIN commit): el desarrollador commiteó esa tanda y el resto en los commits
> `5184af9`/`08c0c96`/`cb44d1e`. **No revertir nada.**
>
> ### Código/esquema
> - **Room v18** (`ClientesDatabase`, `version = 18`) con **`MIGRACION_17_18`** en `AppModule`: crea la
>   tabla `servicio_desactivacion_pendiente` (`idServicio` PK, `desde`). La librería Room sigue en 2.8.4.
> - **Migración 17→18 PROBADA EN TELÉFONO FÍSICO (2026-09-05):** se instaló el APK del commit `5184af9`
>   (esquema 17, sin `MIGRACION_17_18`), se crearon datos (8 clientes, 1 actividad/servicio, 2 sesiones,
>   2 movimientos), se instaló encima el APK de `cb44d1e` (esquema 18) **sin desinstalar ni borrar datos**,
>   y se verificó por lectura real de la BD (WAL incluido): `user_version` 17 → **18**, existe
>   `servicio_desactivacion_pendiente`, filas conservadas (8/1/2/2), sin errores de migración en logcat,
>   **sin** recreación destructiva por `fallbackToDestructiveMigration()`. Datos de respaldo y worktrees
>   temporales (`r17`/`r18`) en `%TEMP%\opencode\roomtest` (fuera del repo).
>
> ### Funcionalidad nueva cerrada en HEAD (esta conversación)
> - **Economía como pantalla de gestión:** selección múltiple (long-press) solo sobre INGRESOS, barra
>   contextual común, operaciones masivas (`MovimientoRepository.actualizarMovimientos/eliminarMovimientos`
>   reutilizando el núcleo individual bajo el Mutex; `MovimientoPago.resolverLote`), editor de movimiento
>   **compartido** `ui/components/DialogoEdicionMovimiento.kt` (perfil + Economía), filtro de fechas
>   `util/MovimientoFiltro.kt` sobre `fechaInicio` (solo ingresos; borrador/aplicado).
> - **Clientes:** selección múltiple y operaciones masivas (activar/archivar/dar de baja; Editar con 1) con
>   barra común; estado de VINCULACIÓN en el perfil ("Cuenta vinculada/no vinculada" por `firebaseUid`),
>   filtro de cuenta (Todos/Vinculados/No vinculados) en el menú `⋮` (el estado sigue en los chips
>   superiores; combinables; "Limpiar filtros" solo limpia cuenta; punto azul solo con filtro de cuenta);
>   reconcilia `firebaseUid` local desde Firestore al reanudar la lista (sin polling). `model/Cliente`
>   ahora expone `firebaseUid`.
> - **Notificaciones:** los no vinculados no reciben envío (resolución de destinatarios por `firebaseUid`);
>   mensaje claro para Individual sin cuenta; aviso de destinatarios en superficie tonal suave con icono ⚠
>   (creación de notificación). Sin cambios de Rules.
> - **Reservas (appCliente):** cancelar NO se bloquea por `plazas==capacidad`/completa (guard eliminado);
>   error de carrera por la última plaza se muestra como "No quedan plazas disponibles." (solo si tras un
>   PERMISSION_DENIED la sesión real tiene ≤0 plazas; si no, se mantiene el mensaje de permisos); el error
>   de una operación anterior se limpia al reanudar `ClasesScreen` y se refrescan las plazas tras fallar.
> - **Reservas (Admin, edición de sesión):** al cambiar la capacidad se usan las **reservas reales de
>   Firestore** (`ReservaRemotoRepository.contarReservasDeSesionRemoto` + `SesionViewModel
>   .cargarSesionConReservasActivas`); `plazasDisponibles = (nuevaCapacidad − inscritos).coerceAtLeast(0)`
>   (`util/CapacidadSesion.kt`). Arregla la causa raíz del "No tienes permisos" al cancelar tras reducir
>   capacidad. Si `nuevaCapacidad < reservas` se conserva la regla existente (plazas = 0), sin inventar.
> - **Baja/desactivación durable de Actividades:** la tabla `servicio_desactivacion_pendiente` persiste la
>   desactivación con la **frontera original** (`desde`) cuando la cascada remota falla;
>   `DesactivacionServicioSincronizador` converge de forma idempotente (sesiones futuras+reservas remotas y
>   servicio `activo=false`), se reintenta al arrancar (`MainViewModel`) y al abrir Actividades
>   (`ServicioViewModel.cargarServicios`), y `reactivar`/`reintentarSincronizacion` convergen antes de
>   activar. `util/BajaServicioReglas.kt` centraliza la frontera pasada/futura. No cambia Rules ni el modelo.
>
> ### Verificación (HEAD, árbol limpio)
> - `:app:testDebugUnitTest` → **146/146**; `:app:assembleDebug` → BUILD SUCCESSFUL.
> - `:appCliente:testDebugUnitTest` → **17/17**; `:appCliente:assembleDebug` → BUILD SUCCESSFUL.
> - `git diff --check` limpio (solo avisos CRLF preexistentes). Sin cambios de Rules (no se ejecutó suite).
> - Migración física 17→18 verificada (ver arriba). No hubo commit/push/deploy de la parte de pruebas.
>
> ### Pendientes / notas para retomar
> - Trabajo temporal (worktrees `r17`/`r18`, APK Room 17 y Room 18, backups de la BD del teléfono) en
>   `%TEMP%\opencode\roomtest`; se puede limpiar. La app Admin del teléfono quedó en esquema 18 con los
>   datos de prueba (los anteriores se descartaron por decisión del propietario; backup guardado).
> - Seguir pendientes previos sin cerrar (logs de diagnóstico `[DIAG alta]`/`ClasesDiagnostico`, retirar
>   `fallbackToDestructiveMigration` antes de producción, Storage/bucket + Blaze/Functions, etc.).
> - Regla vigente: estado de cuenta (filtro) y estado administrativo son independientes; `firebaseUid` es
>   la única fuente de vinculación.

> ## ⚠️ CHECKPOINT 2026-09-05 (permisos ADMIN nuevo + identidad/aislamiento + hidratación central Room + misc)
>
> Estado REAL al cierre de la tanda de trabajo reciente. **HEAD del desarrollador: `f616891`**
> ("Modificacion de posicion y estilos en perfil cleintes, arregaldo el problema de un admind qeu entra
> en la base de datos d eotro admid, solucionado la visibilidad de la contraseña y otros"). El working
> tree tiene cambios SIN commit de esta tanda (**NO revertir**; lista en `git status`): la corrección de
> aislamiento de identidad visual, el fix de teclado de Mi negocio, el guard "sin negocio" de
> Solicitudes y la **hidratación central de Room**.
>
> ### DEPLOY AUTORIZADO (único de la tanda): Firestore Rules con `codigos_maestros`
> - Diagnóstico previo (PC nuevo, ADMIN creado desde cero): la APK nueva ya escribe
>   `codigos_maestros/{codigo}` en `crearNegocio()`, pero el ruleset desplegado (`cd36cbc9`, 2026-09-03)
>   NO contenía esa colección → la Transaction de crear negocio fallaba (PERMISSION_DENIED). Además, un
>   ADMIN con `usuarios/{uid}.negocioId == null` recibía PERMISSION_DENIED al abrir Notificaciones y
>   Solicitudes (rules-are-not-filters sobre listados de negocio).
> - Se desplegó **únicamente** `firestore.rules` local (validado **165/165**) a `gestorpro-50e83` →
>   ruleset **`projects/gestorpro-50e83/rulesets/9d38a26c-0dae-41bf-b691-7f3f55138dbc`**
>   (createTime 2026-09-04T21:24:26Z), verificado por API (contiene `match /codigos_maestros` y la
>   validación cruzada en `negocios`/`negocios_publicos`). NO se desplegó Storage, Functions ni nada más.
>
> ### Cambios cerrados en el árbol actual (código, SIN commit)
> 1. **Fix teclado en Mi negocio (`ui/configuracion/MiNegocioScreen.kt`):** el contenido pasa a
>    `verticalScroll(rememberScrollState())` + `imePadding()` (mismo patrón que AñadirClienteScreen);
>    con el teclado oculto el diseño es idéntico. El campo "Código maestro" ya no queda tapado.
> 2. **Aislamiento de identidad visual Admin1→Admin2 (`NegocioRepository`, `MainViewModel`,
>    `PreferencesRepository`):** el nombre/logo de la cuenta anterior ya no se hereda. Nuevo
>    `estadoNegocioDeCuenta(): EstadoNegocioDeCuenta` (`SinSesion/Error/SinNegocio/ConNegocio`) que
>    distingue "no tiene negocio CONFIRMADO" de "no se pudo comprobar". `refrescarIdentidadLocal()` es
>    suspend y se espera antes de `Listo`; `refrescarIdentidadRemota()` vacía DataStore+memoria cuando el
>    negocio confirmado es `null` y conserva la caché ante errores/offline; `cerrarSesion()` limpia la
>    identidad en memoria y DataStore. `decidirPropietarioIndeterminado(conservar=true)` aplica la verdad
>    remota tras adoptar.
> 3. **Solicitudes sin negocio (`ui/solicitudes/SolicitudesScreen.kt`):** guard `negocioOk`; si
>    `existeNegocioPropio() == false` NO consulta Firestore y muestra `SinNegocioContenido`
>    ("No puedes gestionar solicitudes todavía… Crear mi negocio" → `Routes.MINEGOCIO`). Con negocio el
>    flujo es idéntico.
> 4. **Hidratación CENTRAL de Room tras WIPE (regresión "CrossFit no reaparece"):**
>    - Nuevo `data/repository/HidratadorCacheLocal.kt` (coordinador) + `util/HidratacionMapeadores.kt`
>      (mapeos puros). Lecturas remotas nuevas en los repos:
>      `ServicioRemotoRepository.obtenerServiciosRemotosDelNegocio`,
>      `SesionRemotoRepository.obtenerSesionesRemotasDelNegocio`,
>      `ReservaRemotoRepository.obtenerReservasRemotasDelNegocio`,
>      `MovimientoRemotoRepository.obtenerMovimientosRemotosDelNegocio` (todas filtran por
>      `negocioId == uid` y propagan errores); `ClienteRemotoRepository.obtenerClientesRemotosDelNegocio`
>      ya no traga el error de lista.
>    - Orden: clientes → servicios → sesiones → reservas (solo si su cliente y sesión existen) →
>      movimientos → recálculo de morosidad/deuda por cliente afectado (motor MovimientoMorosidad).
>      Inserción SOLO si la fila no existe (sin duplicados, sin REPLACE destructivo).
>    - Se dispara best-effort tras WIPE/adopción (`CambioCompletado`, `decidir…false`, `Descartar`,
>      `AdoptadoSilencioso`) **solo si la cuenta actual tiene negocio confirmado y `negocioId == uid`**,
>      con marcador `cache_hidratada_uid` en DataStore (evita repetir en cada login; un fallo de red no
>      lo marca y permite reintentar). No se ejecuta en MismaCuenta con caché ya hidratada. NUNCA hidrata
>      para una cuenta sin negocio. Al crear negocio se borra el marcador.
>    - **Limitación documentada:** los GASTOS no tienen espejo remoto → no son recuperables tras WIPE
>      (se abordará en fase posterior si se decide replicarlos).
>
> ### Verificación ejecutada (working tree)
> - `npm --prefix firestore-tests test` → **165/165** (antes del deploy; sin cambios de Rules en la tanda).
> - `:app:testDebugUnitTest` → **98/98** (85 previos + **13 nuevos** `HidratacionMapeadoresTest`);
>   `:app:assembleDebug` → BUILD SUCCESSFUL.
> - Diagnóstico de "Restaurar copia": **falsa alarma** — el backup real contenía `clientes=2`,
>   `movimientos=0`; la transacción hace COMMIT y Room queda exactamente con el contenido del backup
>   (por eso "Cliente Import Test" permanece y el movimiento 1994741218 no aparece). Se retiró la
>   instrumentación temporal sin cambios funcionales.
>
> ### Estado de producción (verificado solo-lectura)
> - Ruleset desplegado = `9d38a26c` (con `codigos_maestros`). `usuarios`: 5 (ADMIN `rdKOD…` con negocio
>   "prueba de negocio"/`654321`; ADMIN `BW8a…` sin negocio; 1 CLIENTE sin negocio; 2 CLIENTES vinculados
>   a `rdKOD…`). `negocios`/`negocios_publicos`/`codigos_maestros`: 1 doc cada uno. `clientes`: 2.
>   `notificaciones`/`solicitudes`: 0.
>
> ### Pendientes inmediatos
> 1. Commit agrupado del working tree de esta tanda (identidad + MiNegocio teclado + Solicitudes +
>    hidratación + tests).
> 2. Pruebas manuales pendientes del propietario: aislamiento Admin1/Admin2 (identidad vacía con Admin2,
>    identidad propia al volver), hidratación completa tras WIPE (servicio/sesión/reserva/movimiento
>    reaparecen), Solicitudes sin negocio (sin PERMISSION_DENIED), y keyboard de Mi negocio.
> 3. Limpieza conocida: `firestore-tests/firestore-debug.log`; logs `[DIAG alta]`/`[DIAG sesiones]`/
>    `ClasesDiagnostico`; `fallbackToDestructiveMigration` antes de producción.
> 4. Seguir pendientes previos sin cerrar: Storage/bucket (logo), Blaze/Functions, tests Android
>    dedicados (backup/owner/hidratación en fase instrumentada), VÍA 2/fecha de nacimiento opcional.


> ## ⚠️ CHECKPOINT 2026-09-04 (última tanda documental — actualización informativa)
>
> Este bloque describe el estado REAL del árbol al cierre de la tanda de trabajo reciente
> (características de identidad, backups y unicidad del código maestro). **HEAD del desarrollador:
> `500bae3`** (últimos commits del desarrollador `500bae3` / `2463f11` / `300829c`, que incluyen entre
> otros cambios de estilos de perfil, visibilidad de contraseña y una corrección de un ADMIN que podía
> entrar en datos de otro ADMIN). El **working tree NO está commiteado** (ver `git status`): los cambios
> SIN commit de esta tanda son los siguientes y **NO deben revertirse**:
> `ReservaDao/ServicioDao/SesionDao`, `data/export/ExportManager.kt`, `NegocioRepository.kt`,
> `AppNavigation.kt`, `LoginScreen.kt`, `DatosScreen.kt`, `MiNegocioScreen.kt`, `HomeScreen.kt`,
> `DatosViewModel.kt`, `MainViewModel.kt` (Admin); `VinculacionRepository.kt` y `HomeScreen.kt`
> (Cliente); `firestore.rules`; `firestore-tests/firestore.rules.test.cjs` (+ basura del emulador
> `firestore-debug.log`).
>
> ### Cambios cerrados en el árbol actual (código, SIN deploy)
> 1. **Backup v1 (`ExportManager` reescrito):** ZIP con `manifest.json` + `media/`; exportar,
>    importar (merge atómico) y restaurar (replace completo atómico con `clearAllTables()`); validación
>    estricta de `negocioId` contra `usuarios/{uid}`; normalización del `negocioId` de filas al negocio
>    actual; DNI duplicado con distinto idCliente → aborto total; backups JSON legacy → rechazo
>    explícito; recálculo + publicación del resumen económico de los clientes afectados; fotos en ZIP
>    (checkbox), logo NO empaquetado (se recupera de Firestore). No toca `uid_propietario_datos_locales`.
> 2. **Aislamiento de cuenta/propietario local (`data/local/PreparadorLocalCuenta.kt` + guard de
>    `MainViewModel`/`AppNavigation`):** owner por `uid_propietario_datos_locales`, WIPE en cambio de
>    cuenta, bloques de propietario indeterminado y pendientes; **corregido** el crash de arranque
>    (orden de inicialización de los `MutableStateFlow` de identidad).
> 3. **"Nueva actividad" sin negocio:** reutiliza el componente compartido `SinNegocioContenido`
>    (creado en `ui/components/`) en lugar de navegar directo a Mi negocio; no crea nada en Room.
> 4. **Identidad única del centro:** el Admin refresca nombre/logo desde `negocios_publicos/{negocioId}`
>    al arrancar/login (DataStore como caché, fallback si no hay red); el nombre se actualiza
>    **inmediatamente** en la UI tras guardar (se comparte la instancia Activity-scoped de
>    `MainViewModel` entre Home/MiNegocio/Login — PARTE A); cabeceras de Admin y Cliente URL-aware
>    (Coil: URL remota o ruta local) con nombre con ellipsis.
> 5. **Unicidad GLOBAL del código maestro (PARTE B, SIN deploy):** colección `codigos_maestros/{codigo}`
>    (`{negocioId}`); `NegocioRepository.crearNegocio` y `guardarCodigoMaestro(nuevo, anterior)` en
>    `runTransaction` (reserva/libera el código atómicamente, rechaza códigos ocupados); VÍA 1 del
>    Cliente resuelve **solo** por `codigos_maestros/{codigo}` (sin `whereEqualTo/limit(1)`; fallo
>    explícito si no existe o incoherente con `negocios_publicos`). VÍA 2 intacta.
> 6. **`firestore.rules` LOCAL** con bloque `codigos_maestros` (get autenticado, list/update false,
>    create/delete ligados al negocio y coherentes con `negocios`/`negocios_publicos` vía getAfter) y
>    validación cruzada en `negocios`/`negocios_publicos`. **NO desplegado.**
>
> ### Verificación ejecutada (working tree)
> - `npm --prefix firestore-tests test` → **165/165** (PRUEBA 137–150 nuevas de códigos maestros).
> - Unit tests `:app` y `:appCliente` → BUILD SUCCESSFUL; `assembleDebug` ambos módulos → OK.
> - **Sin deploy:** el ruleset desplegado en `gestorpro-50e83` sigue siendo el anterior (SIN
>   `codigos_maestros`). No se desplegó Firestore Rules, Storage, Functions, ni se tocaron datos.
>
> ### Estado de producción (solo lectura verificado)
> - Negocio **Coliseo** (`bug1uPQ9UnPJ4wUWkEeUS8g2J9D3`) y negocio **prueba**
>   (`SBgEVx1wraREVMKxfc0dTys4lT13`) comparten `codigoMaestro = 123456` (duplicado real). Índices del
>   negocio Coliseo intactos (2). La **migración NO se ha ejecutado**.
>
> ### Próximos pasos pendientes (AUTORIZACIÓN del propietario)
> 1. **Migración/despliegue del código maestro (orden definitivo):** corregir el duplicado en consola
>    (prueba → `654321` en `negocios` y `negocios_publicos`) → crear `codigos_maestros/123456` y
>    `codigos_maestros/654321` → tests → **desplegar `firestore.rules`** → instalar APK Admin y Cliente
>    nuevas → probar VÍA 1. No hay transición retrocompatible segura: se acepta ventana de
>    mantenimiento corta (no crear/cambiar negocio entre deploy de Rules y APK nueva).
> 2. **Storage/logo:** pendiente de habilitar el bucket (infraestructura, por separado).
> 3. **Tests Android dedicados** del backup y del guard de propietario (fase final).

## Proyecto

- **Nombre:** GestorPro
- **Descripción:** sistema Android para gestionar clientes, clases, reservas, cuotas, gastos y datos económicos de un negocio deportivo. Formado por **dos aplicaciones independientes** que comparten el mismo proyecto Firebase/Firestore:
  1. **GestorPro Admin** (`:app`) — gestión del negocio por el administrador.
  2. **GestorPro Cliente** (`:appCliente`) — registro, vinculación y perfil del cliente.
- **Plataforma:** Android.
- **Min SDK:** 26. **Target SDK:** 36. **Compile SDK:** 36.1.

### GestorPro Admin (`:app`)

- **Package:** `com.roberto.gestorpro`.
- **Application:** `GestorProApplication`.
- **Activity principal:** `MainActivity`.
- **Módulo:** `app`.

### GestorPro Cliente (`:appCliente`)

- **Package:** `com.roberto.gestorpro.cliente`.
- **Application:** `GestorProClienteApplication`.
- **Activity principal:** `MainActivity`.
- **Módulo:** `appCliente`.
- **google-services.json:** `appCliente/google-services.json` (no versionado; está en `.gitignore`).
- La app Cliente **no usa Room** (fuente de verdad = Firestore) ni Gson.

## Stack tecnológico

Las versiones oficiales del proyecto están en `gradle/libs.versions.toml`.

| Área | Tecnología | Versión configurada |
|---|---|---|
| Lenguaje | Kotlin | 2.2.10 |
| Android Gradle Plugin | AGP | 9.1.1 |
| UI | Jetpack Compose + Material 3 | BOM 2026.02.01 |
| Navegación | Navigation Compose | 2.9.3 |
| Inyección de dependencias | Hilt | 2.60.1 |
| Base de datos local (solo Admin) | Room | 2.8.4 |
| Preferencias | DataStore Preferences | 1.1.7 |
| Imágenes | Coil Compose | 3.3.0 |
| Serialización auxiliar (solo Admin) | Gson | 2.11.0 |
| Backend | Firebase Authentication + Firestore | Firebase BOM 34.16.0 |
| Almacenamiento de imágenes (solo Admin) | Firebase Storage | Firebase BOM 34.16.0 |

- `firebase-storage` está registrado en el catálogo (`libs.firebase.storage`) y se aplica **solo en `:app`** (el Cliente carga las URLs por HTTP con Coil, sin SDK de Storage).

Reglas relacionadas con dependencias:

- No añadir una dependencia sin avisar y explicar su necesidad.
- Comprobar primero si la funcionalidad ya está cubierta por una dependencia existente.
- Usar el catálogo `libs.versions.toml` para las nuevas dependencias cuando sea posible.
- Avisar antes de modificar `build.gradle.kts` o `gradle/libs.versions.toml`.
- No cambiar versiones por iniciativa propia.

## Arquitectura actual

GestorPro utiliza **MVVM con repositorios**, no una Clean Architecture estricta. La estructura vigente es:

```text
UI Compose -> ViewModel -> Repository -> Room / DataStore / Firebase
```

- En **Admin**, Room es la fuente de verdad local y Firestore el espejo remoto (réplica write-through).
- En **Cliente**, Firestore es la fuente de verdad; solo hay DataStore para preferencias locales.

No se debe exigir ni introducir una capa `domain/usecase` como requisito automático. Si una tarea necesita mejorar la arquitectura, debe proponerse primero y hacerse de forma incremental.

Reglas de arquitectura vigentes:

- La UI renderiza el estado y emite acciones del usuario.
- Los ViewModels coordinan el estado de pantalla y lanzan operaciones con `viewModelScope`.
- Los ViewModels acceden a datos mediante repositorios, no directamente mediante DAOs.
- Los DAOs y entidades Room pertenecen a `data`.
- Las clases de `model` contienen modelos compartidos de la aplicación.
- `AppModule` centraliza la configuración de Hilt, Room y repositorios (uno por aplicación).
- `ClientesDatabase` es la base de datos Room principal (solo Admin).
- `PreferencesRepository` encapsula DataStore (uno por aplicación).
- `MainActivity` es la única Activity de cada aplicación.
- `AppNavigation` y `Routes` centralizan la navegación (una pareja por aplicación).
- **La Vía B (enlace individual/deep link) está DESCARTADA.** No se implementa ni se reutiliza nada relacionado: sin `vinculaciones`, sin `codigoVinculacion`, sin deep links.

El código existente contiene deuda arquitectónica y no se debe refactorizar de forma masiva como parte de otra tarea. Si se detecta una inconsistencia, se informa y se propone por separado.

## Estructura principal

```text
app/                                     -> GestorPro Admin
└── src/main/java/com/roberto/gestorpro/
    ├── GestorProApplication.kt          -> Application de Hilt
    ├── MainActivity.kt                  -> única Activity y punto de entrada Compose
    ├── navigation/                      -> AppNavigation.kt y Routes.kt
    ├── model/                           -> modelos y enums
    ├── data/
    │   ├── converter/                   -> conversores de Room
    │   ├── dao/                         -> DAOs Room
    │   ├── database/                    -> ClientesDatabase
    │   ├── entity/                      -> entidades Room
    │   ├── export/                      -> exportación de datos
    │   ├── firebase/                    -> Firebase (Autenticacion, Negocio, ClienteRemoto, ServicioRemoto, SesionRemoto, ReservaRemoto)
    │   └── repository/                  -> repositorios de datos
    ├── di/AppModule.kt                  -> dependencias de Hilt
    └── ui/
        ├── auth/                        -> login y registro (ADMIN)
        ├── clases/                      -> código ANTIGUO de Clase/SesionClase (transitorio)
        ├── clientes/                    -> clientes y perfiles (+ diálogo de servicios contratados)
        ├── components/                  -> componentes Compose reutilizables
        ├── configuracion/               -> cuenta, negocio, preferencias y datos
        ├── economia/                    -> movimientos y gastos
        ├── home/                        -> inicio de administrador
        ├── servicios/                   -> SERVICIOS y SESIONES (nuevo modelo)
        ├── theme/                       -> tema, colores y tipografía
        ├── utils/                       -> FotoUtils
        └── viewmodel/                   -> ViewModels

appCliente/                              -> GestorPro Cliente
└── src/main/java/com/roberto/gestorpro/cliente/
    ├── GestorProClienteApplication.kt   -> Application de Hilt
    ├── MainActivity.kt                  -> única Activity y punto de entrada Compose
    ├── navigation/                      -> AppNavigation.kt y Routes.kt
    ├── model/                           -> Cliente y EstadoCliente
    ├── data/
    │   ├── firebase/                    -> Autenticacion, Negocio, PerfilPendiente,
    │   │                                   Cliente, Vinculacion (VÍA 1 y VÍA 2)
    │   └── repository/                  -> PreferencesRepository (DataStore)
    ├── di/AppModule.kt                  -> dependencias de Hilt
    └── ui/
        ├── auth/                        -> login, registro, recuperar, inicio código+DNI,
        │                                   completar perfil, mi perfil, editar perfil, cuenta,
        │                                   configuración
        ├── components/                  -> MenuCard, BotonSelectorFoto
        ├── home/                        -> HomeScreen y ClasesScreen (placeholder sin Firestore)
        ├── theme/                       -> tema, colores y tipografía
        ├── utils/                       -> FotoUtils (galería + cámara)
        └── viewmodel/                   -> MainViewModel
```

## Funcionalidades principales

### GestorPro Admin
- Registro/login/logout reales con Firebase Authentication (rol `ADMIN` fijo).
- Creación y edición del negocio con código maestro (`negocios` + `negocios_publicos`).
- Alta, edición, consulta, archivado y restauración de clientes (Room + réplica Firestore con write-through).
- Réplica de clientes que crea `clientes/{idCliente}`, `indices_clientes/{negocio}_{dni}` y `clientes_privados/{idCliente}` en un único Batch.
- Cambio de DNI de un cliente manteniendo el índice atómico (borra el viejo y crea el nuevo en el mismo Batch).
- **Gestión de SERVICIOS y SESIONES (nuevo modelo):** `ServiciosScreen` (ACTIVOS/DE BAJA, crear/editar/dar de baja/reactivar/eliminar), detalle del servicio con la sesión del día, `ProgramarSesionesScreen` (desde/hasta + día con hora propia + duración + capacidad), `EditarSesionScreen` ("Ver / editar sesión") y `SesionReservasScreen` (clientes reservados). Modelo `Cliente → Servicio → Sesión → Reserva` (sin entidad Clase). `ClaseEntity`/`SesionClaseEntity` siguen en Room solo de forma TRANSITORIA.
- **Servicios contratados del cliente (Room, perfil ADMIN):** `ClienteEntity.serviciosContratados: List<Int>` (ids de `ServicioEntity`); selector multi-servicio en el perfil del cliente (solo servicios activos; los ids inactivos se conservan).
- **Reservas en Room:** creación/cancelación ATÓMICAS con `RoomDatabase.withTransaction` (comprueba sesión, plazas > 0, servicio activo, sin duplicado por `(idSesion, idCliente)`); cascadas de reservas al regenerar/desactivar/eliminar servicios y al eliminar sesiones. Movimientos independientes.
- **Reservas en Firestore:** `reservas/{clienteId}_{sesionId}` con Transaction (crear = reserva + `plazasDisponibles-1`; cancelar = delete + `+1`, sin superar capacidad); `ReservaRemotoRepository`.
- Gestión de movimientos, cuotas y gastos.
- Configuración del negocio, nombre, logo, tema, cuenta y datos.
- Subida del logo del negocio a Firebase Storage (`negocios/{negocioId}/logo.jpg`) y guardado de su URL en `negocios` + `negocios_publicos` (mismo WriteBatch).
- Selección de foto de perfil desde galería o cámara.

### GestorPro Cliente
- Registro/login/logout reales con Firebase Authentication (rol `CLIENTE` fijo).
- Recuperación de contraseña (solo `FirebaseAuth.sendPasswordResetEmail`).
- Pantalla inicial "¿Tu gimnasio ya te ha registrado?" con la opción **"No tengo vinculación"**.
- **Sin vínculo:** el CLIENTE puede completar su perfil (`perfiles_pendientes/{uid}`), entrar al **Home sin vincular** (aviso visible) y navegar (Mi perfil, Clases placeholder, Mi cuenta, Configuración) sin consultar Firestore para clases.
- **Vincular con mi gimnasio** (código maestro + DNI) desde el Home:
  - **VÍA 1:** vincular el UID a una ficha existente creada por el ADMIN (sin crear duplicados).
  - **VÍA 2:** si `indices_clientes/{negocioId}_{dni}` NO existe, crear la ficha con los datos de `perfiles_pendientes/{uid}` + índice + `usuarios/{uid}` en la misma Transaction. **NO es un error** que el índice no exista.
- Ver y editar los datos personales: sin vínculo se lee/escribe `perfiles_pendientes/{uid}` (el **DNI es editable**); vinculado se lee/escribe `clientes/{idCliente}` y el **DNI queda bloqueado**.
- El perfil pendiente se borra **solo cuando la vinculación se completa con éxito** (nunca ante errores).
- Nunca muestra ni edita `observaciones` ni datos administrativos.
- Persistencia local solo con DataStore (caché; la fuente de verdad remota es Firestore, incluidos `negocios_publicos/{id}` para nombre y logo).
- Refresco de datos públicos del negocio al arrancar con sesión restaurada (`cargarEstadoLocal()` en `destinoInicial()`); si no hay conexión se conserva la caché.

## Contrato remoto de Firestore

Firestore es la capa remota compartida por ambas aplicaciones. En Admin, Room es la base de datos local y Firestore el espejo remoto (réplica write-through sin cola offline). En Cliente, Firestore es la fuente de verdad.

Colecciones remotas actuales:

```text
usuarios/{uid}
negocios/{negocioId}
negocios_publicos/{negocioId}
clientes/{idCliente}
clientes_privados/{idCliente}        <- observaciones y datos internos (solo ADMIN)
indices_clientes/{negocioId}_{dni}   <- unicidad y localización negocio+DNI
perfiles_pendientes/{uid}            <- perfil temporal del CLIENTE sin negocio
servicios/{idServicio}               <- SERVICIOS del negocio (nuevo modelo)
sesiones/{idSesion}                  <- sesiones de un servicio (sin Clase)
reservas/{clienteId}_{sesionId}      <- reserva atómica cliente+sesión
solicitudes/{solicitudId}
movimientos/{movimientoId}
```

**No existe** la colección `vinculaciones` (Vía B descartada) ni el campo `codigoVinculacion`.

Reglas de identidad y pertenencia:

- El UID de Firebase es el ID del documento `usuarios/{uid}`.
- Los roles remotos son exactamente `ADMIN` y `CLIENTE`.
- `negocioId` es un `String` en Firestore (el `negocioId` del ADMIN es su UID).
- `clienteId`, `idCliente`, `idServicio`, `idSesion` y `sesionId` se manejan como enteros (`int64`) cuando forman parte de los datos.
- Los documentos de `clientes` y `servicios` usan el identificador numérico convertido a texto en la ruta, por ejemplo `clientes/2`, `servicios/7`.
- `firebaseUid` es un `String`; en `clientes` nace `null` (ficha creada por el ADMIN) y solo lo rellena el CLIENTE al vincularse.
- `serviciosContratados` es un array de **enteros** (ids de `servicios`). **`clientesPermitidos` ya NO se usa** en sesiones (el acceso del CLIENTE se calcula en Rules con `get(clientes)` + `get(servicios)`).
- Un administrador solo puede acceder a su negocio, identificado por `adminUid` y `negocioId`.
- Un cliente solo puede acceder a sus datos, sus reservas, sus solicitudes y las sesiones de servicios que tenga contratados y activos.
- Los clientes nunca pueden acceder a `movimientos` ni a `clientes_privados`.

Estructura clave de documentos:

```text
usuarios/{uid} = { rol, activo, clienteId, negocioId }
negocios/{negocioId} = { adminUid, nombre, codigoMaestro, logo }
negocios_publicos/{negocioId} = { nombre, codigoMaestro, logo }
clientes/{idCliente} = { idCliente, negocioId, firebaseUid, nombre, apellidos, dni,
                         telefono, email, foto, fechaNacimiento, fechaRegistro,
                         fechaAlta, fechaBaja, estado, tieneLlave,
                         serviciosContratados: [int...], fechaInicioActual, fechaFinActual }
                     (sin observaciones ni codigoVinculacion)
clientes_privados/{idCliente} = { negocioId, observaciones }
indices_clientes/{negocioId}_{dni} = { negocioId, dni, clienteId }
perfiles_pendientes/{uid} = VÍA 1: { dni, negocioId } | VÍA 2: { nombre, apellidos, dni, telefono, email, foto, fechaNacimiento }
servicios/{idServicio} = { idServicio, negocioId, nombre, descripcion, activo }
sesiones/{idSesion} = { idSesion, negocioId, idServicio, fecha, hora,
                        duracionMinutos, capacidad, plazasDisponibles }
reservas/{clienteId}_{sesionId} = { idReserva, negocioId, sesionId, clienteId, fechaReserva }
```

### Almacenamiento remoto de imágenes (Firebase Storage, solo Admin)

- Ruta del logo del negocio: `negocios/{negocioId}/logo.jpg` (`negocioId` = uid del ADMIN). Al cambiar el logo se sobrescribe; no se conservan históricos.
- **No se guarda la imagen en Firestore**: solo la URL de descarga (`logo`) en `negocios` + `negocios_publicos` (mismo WriteBatch, atómico).
- **Bucket:** el por defecto de `google-services.json` (`project_info.storage_bucket`). **PENDIENTE en producción:** el bucket `gestorpro-50e83.firebasestorage.app` debe crearse/habilitarse en Firebase Console antes de subir el primer logo; hasta entonces la subida falla con "Object does not exist at location".
- Reglas de Storage (`storage.rules`, versionado): lectura para cualquier usuario autenticado; escritura solo para el ADMIN propietario (`usuarios/{uid}.rol == "ADMIN" && usuarios/{uid}.negocioId == negocioId`). Un ADMIN no puede escribir el logo de otro negocio; CLIENTE y no autenticados, denegado. Resto del bucket bloqueado.
- El Cliente descarga el logo por su URL con Coil (HTTP), sin SDK de Storage.
- **Alcance actual vs decisiones (2026-09-03):** este apartado describe la implementación presente
  (solo logo del negocio; escritura solo del ADMIN). Las decisiones de producto sobre logos/fotos
  remotas compartidas y sus permisos (incluida la escritura de la propia foto del cliente por el
  CLIENTE) están en el bloque «Decisiones de producto — Notificaciones, Cloud Functions y Storage».
  Las `storage.rules` actuales aún no contemplan fotos de clientes ni escritura del CLIENTE, y **no
  deben modificarse por este bloque**. Tamaño máximo decidido: **10 MB por fotografía con
  compresión/redimensionado automático en la app** (las `storage.rules` preparadas usan 5 MB de
  referencia y se alinearán al implementar; la implementación de compresión sigue PENDIENTE, ver bloque D).

Flujos funcionales remotos:

- Un ADMIN nuevo puede registrarse con `negocioId = null` y debe crear su propio negocio con código maestro.
- La creación del negocio, `negocios_publicos/{id}` y la asignación de `usuarios/{uid}.negocioId` deben ejecutarse en el mismo Batch.
- Las solicitudes solo representan altas y bajas. Sus valores remotos son `ALTA` y `BAJA`.
- Los servicios definen el catálogo del negocio; las sesiones son instancias concretas de un servicio (`sesiones/{id}.idServicio`); las reservas relacionan un cliente con una sesión mediante `sesionId`.
- Un cliente no solicita una clase mediante `solicitudes`; primero debe tener contratado el servicio y después puede reservar una sesión autorizada.

### Alta y vinculación del CLIENTE — dos vías

- **VÍA 1 — ADMIN crea primero:**
  1. ADMIN crea la ficha → réplica en un Batch: `clientes/{idCliente}` (`firebaseUid: null`, `negocioId` del ADMIN) + `indices_clientes/{negocioId}_{dni}` + `clientes_privados/{idCliente}`.
  2. CLIENTE se registra/inicia sesión → `usuarios/{uid}` con `clienteId: null`, `negocioId: null`.
  3. Desde el Home sin vincular pulsa "Vincular con mi gimnasio" e introduce código maestro + DNI → la app resuelve `negocioId` en `negocios_publicos`.
  4. **Declaración temporal de VÍA 1:** la app escribe en `perfiles_pendientes/{uid}` únicamente `{ dni, negocioId }` (con `SetOptions.merge()`, sin destruir un perfil completo previo) para que las Rules validen el acceso al índice y a la ficha.
  5. Lee `indices_clientes/{negocioId}_{dni}` → obtiene `clienteId`.
  6. Transaction de vinculación: `clientes/{idCliente}.firebaseUid = uid` + `usuarios/{uid}` → `{clienteId, negocioId}`. **No se crea segunda ficha.**
  7. Se borra `perfiles_pendientes/{uid}` **solo al completar la vinculación con éxito**.
- **VÍA 2 — CLIENTE crea primero:**
  1. CLIENTE se registra → `usuarios/{uid}` con `clienteId: null`, `negocioId: null`.
  2. "No tengo vinculación" → completa su perfil completo → `perfiles_pendientes/{uid}` (nombre, apellidos, dni, telefono, email, foto, fechaNacimiento). Se guarda y pasa directamente al **Home sin vincular** (no busca ficha ni ejecuta vinculación todavía).
  3. Desde el Home pulsa "Vincular con mi gimnasio" e introduce código maestro + DNI:
     - Si **no existe** `indices_clientes/{negocioId}_{dni}` → Transaction: crear `clientes/{idCliente}` con los datos del perfil + crear el índice + actualizar `usuarios/{uid}` + borrar `perfiles_pendientes/{uid}`.
     - Si **existe** y `firebaseUid == null` → vincular a la ficha existente (VÍA 1).
     - Si **existe** y `firebaseUid != null` → rechazar ("ese DNI ya está vinculado").
- **`perfiles_pendientes/{uid}` admite DOS modos:** VÍA 1 (declaración mínima `{ dni, negocioId }`) y VÍA 2 (perfil completo). **Solo se borra al completar la vinculación con éxito**; ante errores (falta de perfil, red, permisos, fallo intermedio) se conserva para no perder los datos del usuario.
- **Cliente autenticado sin vínculo:** puede tener perfil pendiente y navegar por el Home sin vincular (aviso visible, Clases placeholder sin consultar Firestore). `destinoInicial`/`destinoTrasAutenticar` llevan a Home si existe ficha o perfil pendiente; a la pantalla inicial solo si no hay ninguno.
- **DNI editable pre-vinculación:** mientras no está vinculado el CLIENTE edita su perfil en `perfiles_pendientes/{uid}` (el DNI puede cambiar). Una vez vinculado, la ficha vive en `clientes/{idCliente}` y el DNI queda bloqueado para el CLIENTE; solo el ADMIN lo cambia manteniendo el índice atómico.
- **Unicidad:** el documentId determinista `{negocioId}_{dni}` garantiza una única ficha por negocio+DNI. La Transaction sobre el índice serializa la concurrencia.
- Tras vincularse, el CLIENTE puede editar solo sus datos personales (`nombre`, `apellidos`, `telefono`, `email`, `foto`, `fechaNacimiento`). **No** puede modificar `dni`, `negocioId`, `firebaseUid`, `estado`, `serviciosContratados`, fechas administrativas, `tieneLlave` ni `observaciones`.
- El `dni` solo lo cambia el ADMIN, y al hacerlo debe mantener el índice atómico (borrar el índice viejo y crear el nuevo en el mismo Batch).
- El código maestro es independiente de las vinculaciones; cambiarlo no afecta a clientes ya vinculados.
- El mismo `idCliente: Int` se comparte entre Room y Firestore. Los clientes creados por el ADMIN se replican con write-through inmediato (sin cola offline); si la réplica falla, el dato local se conserva, se informa al ADMIN y queda preparada una operación de reintento manual. Las Rules prohíben `delete` en `clientes`: el borrado local se refleja como baja lógica remota.
- Los valores remotos de `clientes.estado` son exactamente los nombres del enum: `ACTIVO`, `BAJA`, `ARCHIVADO`, `REGISTRADO`. `MOROSO` se calcula desde movimientos y nunca se almacena.
- Las consultas de sesiones y reservas deben diseñarse para ser compatibles con las Security Rules; las Rules no funcionan como filtros posteriores.

Security Rules (`firestore.rules`):

- El archivo versionado del proyecto es `firestore.rules`.
- Todo está bloqueado por defecto salvo las rutas declaradas expresamente.
- Las funciones de Rules usan el documento `usuarios/{uid}` para resolver rol, estado, `clienteId` y `negocioId`; no se usan Custom Claims.
- `getAfter()` solo debe utilizarse en operaciones atómicas que actualicen todos los documentos relacionados.
- **VÍA 2:** la creación directa se valida con `creacionDirectaValida()` — el Batch/Transaction incluye la ficha nueva ligada al UID autenticado, su índice y deja `usuarios/{uid}` coherente post-operación.
- **VÍA 1:** la vinculación a una ficha existente se valida con `vinculacionDniValida()` — ficha libre (`firebaseUid == null`), pertenece al negocio indicado, DNI igual al del perfil pendiente y coherencia negocio/cliente/usuario post-Batch.
- **Índices:** `indices_clientes` se crea solo en el mismo Batch que su ficha (`indiceCreadoPorAdmin` / `indiceCreadoPorCliente`); `update` prohibido; `delete` solo ADMIN al cambiar el DNI; `list` prohibido; `get` restringido a que el **DNI y el `negocioId`** del índice coincidan con los declarados en `perfiles_pendientes/{uid}` (o ADMIN de su negocio).
- **`perfiles_pendientes/{uid}`:** `create/get/update/delete` solo del propio uid; `hasOnly` admite DOS modos — VÍA 1 `{ dni, negocioId }` (declaración temporal) o VÍA 2 perfil completo `{ nombre, apellidos, dni, telefono, email, foto, fechaNacimiento }`; `list` prohibido.
- **`clientes/get` CLIENTE VÍA 1 (sin vínculo):** regla adicional que permite a un CLIENTE con `clienteId == null && negocioId == null` leer **solo** la ficha cuyo `dni` y `negocioId` coinciden con su `perfiles_pendientes/{uid}` y cuyo documentId es su `idCliente`. No puede leer fichas arbitrarias, de otros negocios, de otros DNIs ni enumerar (`list` solo ADMIN). Imprescindible para que `transaction.get(clientes/{idCliente})` de la vinculación funcione.
- **`clientes/get` CLIENTE VÍA 2 (`resource == null`):** regla adicional que permite a un CLIENTE sin vínculo con perfil pendiente leer un documento **inexistente** (`resource == null`), necesaria para que la Transaction de VÍA 2 compruebe que `clientes/{idCliente}` aún no existe. No permite leer los datos de fichas existentes de otros.
- **`clientes_privados/{idCliente}`:** solo el ADMIN del negocio puede leer/crear/actualizar; `delete` prohibido.
- **`clientes`:** el CLIENTE solo puede leer su propia ficha (`firebaseUid == uid`) y editar solo sus campos personales; `list` solo ADMIN.
- Un CLIENTE solo puede vincularse una vez (`usuarios/{uid}` exige `clienteId == null` y `negocioId == null`).
- `negocios_publicos/{id}` permite `get/list` a cualquier autenticado; `create/update` solo el ADMIN del negocio (campos `codigoMaestro`, `nombre`, `logo`).
- **`servicios/{idServicio}`:** solo el ADMIN del negocio crea/modifica/elimina; el CLIENTE vinculado puede `get` servicios ACTIVOS de su negocio (necesario para la Transaction de reserva). create/update validan `hasOnly`, tipos y `negocioId`.
- **`sesiones/{idSesion}`:** solo el ADMIN del negocio crea/modifica/elimina; la creación exige un servicio existente, del negocio y ACTIVO (`servicioValidoParaSesion`). El CLIENTE vinculado puede `get/list` solo sesiones de servicios contratados y activos de su negocio. `resource == null` en get ADMIN para comprobación de existencia.
- **`reservas/{clienteId}_{sesionId}` (documentId determinista):** creación y cancelación ATÓMICAS (Transaction). El CLIENTE crea su reserva (validada contra Firestore con `reservaCreaValida`: negocio, servicio contratado+activo, `plazas == anterior-1 && >= 0`) y la elimina devolviendo la plaza (`reservaEliminadaValida`: `== anterior+1 && <= capacidad`). El decremento/incremento de `plazasDisponibles` de la sesión solo se permite al CLIENTE si la Transaction crea/elimina la reserva (`reservaCreadaEnTransaccion` / `reservaEliminadaEnTransaccion`). El ADMIN consulta y elimina reservas de su negocio con ajuste de plazas. `update` de CLIENTE: false.
- **Storage Rules (`storage.rules`):** lectura para cualquier autenticado; escritura solo para el ADMIN propietario (`negocios/{negocioId}/logo.jpg`, `negocioId == usuarios/{uid}.negocioId`). Resto del bucket bloqueado.
- Las Rules deben probarse con los casos ADMIN, CLIENTE, VÍA 1 y VÍA 2 antes de publicarse (`npm --prefix firestore-tests test`).

Los datos reales de prueba, UIDs, códigos y `negocioId` no se documentan en estos archivos; se usan placeholders para evitar guardar identificadores concretos en el repositorio.

Existe una diferencia pendiente entre el modelo local y el contrato remoto: `TipoSolicitud.kt` y `SolicitudEntity.kt` todavía contienen `CLASE` y deben adaptarse posteriormente a `ALTA`/`BAJA` mediante una tarea específica de Room y su migración.

## Convenciones de código

- Responder siempre en español.
- Mantener los nombres de clases, funciones, variables, rutas y paquetes existentes en español.
- No renombrar identificadores existentes para convertirlos al inglés salvo petición expresa.
- Clases y composables con PascalCase.
- Variables y funciones con camelCase.
- Comentarios y documentación en español.
- Seguir el estilo del archivo relacionado que ya exista.
- Preferir funciones pequeñas y responsabilidades claras.
- Los composables hijos deben recibir datos y lambdas; evitar pasarles ViewModels cuando se pueda.
- Usar `StateFlow` para estado observable y `collectAsStateWithLifecycle()` en nuevas pantallas o zonas modificadas.
- Usar `viewModelScope` para operaciones iniciadas por un ViewModel.
- Preferir recursos de `strings.xml` para textos de UI nuevos.
- Añadir KDoc útil a nuevas clases y funciones públicas, sin exigir una reescritura documental del código existente.

## Reglas de calidad y seguridad

- No usar el operador `!!` en código nuevo.
- No realizar operaciones de Room, DataStore, Firebase o red en el hilo principal.
- No almacenar contraseñas, tokens ni claves privadas en texto plano.
- No incluir claves secretas en el código cliente ni en archivos versionados (`google-services.json` está en `.gitignore`).
- No eliminar código existente sin explicar el motivo.
- No corregir bugs no solicitados si no son necesarios para la tarea actual; informar de ellos como deuda o riesgo.
- No introducir Activities adicionales sin justificarlo explícitamente.
- Respetar `allowBackup`, las migraciones Room y los datos de prueba; cualquier cambio de producción debe avisarse.
- El DNI identifica la ficha dentro del negocio y **nunca** debe poder modificarlo un CLIENTE; su cambio solo lo hace el ADMIN manteniendo el índice atómico.

El código actual tiene algunos usos de `!!`, `collectAsState()` y strings hardcodeados. Se consideran advertencias y deuda técnica: deben señalarse cuando afecten a una tarea, pero no se debe iniciar una migración general sin pedirlo.

## Comandos del proyecto

Ejecutar desde `C:\Users\Roberto\AndroidStudioProjects\GestorPro`:

```powershell
# Compilar Admin (debug)
.\gradlew.bat :app:assembleDebug

# Compilar Cliente (debug)
.\gradlew.bat :appCliente:assembleDebug

# Compilar ambos
.\gradlew.bat assembleDebug

# Tests de Firestore + Storage Rules (emulador: firestore y storage)
npm --prefix firestore-tests test

# Despliegue de Rules (solo tras validar con los tests)
& ".\firestore-tests\node_modules\.bin\firebase.cmd" deploy --only firestore:rules
& ".\firestore-tests\node_modules\.bin\firebase.cmd" deploy --only storage:rules

# Auditoría (DRY-RUN) previa al backfill de indices_clientes (solo lectura)
node firestore-tests/auditoria_backfill_indices.cjs
```

## Tests

- **Rules de Firestore + Storage:** `npm --prefix firestore-tests test` (**151 pruebas** en los emuladores `--only firestore,storage`): las previas PRUEBA 6B/6C (Vía A), 9B, 33A–33H, 77–81, 82–88 (`horaDesdeReserva`), 99–108 (solicitudes de baja), 109–112 (bloqueo de BAJA), 113–120 (regresión sesiones/borrado solicitudes/aviso baja), 121–128 (acceso solo ACTIVO + notificación VINCULACION) **y 129–136 (resumen económico remoto: ADMIN de su negocio ALLOW, otro negocio DENY, CLIENTE no modifica moroso/deuda/fechaEntradaMorosidad/exentoMorosidad/fechas)**. Deben pasar **antes** de desplegar las Rules.
- **Unit tests Admin:** `:app:testDebugUnitTest` (**68/68**): MovimientoMorosidad (regla deuda = TODOS los PENDIENTES, dos causas, `exentoMorosidad`, `fechaEntradaMorosidad`=detección), MovimientoFirestore (resumen con `exentoMorosidad`), IdMovimiento (rango alto), MovimientoPrecio/Pago, NotificacionConfig.
- **Test unitario appCliente:** `:appCliente:testDebugUnitTest` cubre el rechazo de Vía A cuando no existe el índice `negocioId_DNI`.
- **Tests unitarios de helpers de Cloud Functions:** `node --test functions/test/ids.test.js functions/test/tokens.test.js` (**13/13**, sin necesidad de `npm install` en `functions/`; solo cubren los módulos puros `ids.js` y `tokens.js`).
- **Test de aislamiento del alta (temporal, Sesión XVIII):** `firestore-tests/diagnostico_alta_cliente.test.cjs` (7/7) reproduce el payload real de `crearClienteRemoto()` contra las Rules locales para aislar el PERMISSION_DENIED del alta. Ejecutar con: `cd firestore-tests; Copy-Item ..\firestore.rules firestore.rules.generated -Force; .\node_modules\.bin\firebase.cmd emulators:exec --project gestorpro-rules-test --only firestore "node --test diagnostico_alta_cliente.test.cjs"`. No forma parte del suite oficial.
- Los tests de Android se mantienen para la fase final del proyecto salvo que el desarrollador los solicite expresamente antes. No crear archivos de test automáticamente durante una funcionalidad normal.

## Convenciones específicas de Firebase y navegación

- **Recuperación de contraseña:** usar exclusivamente `FirebaseAuth.sendPasswordResetEmail`. El mensaje de éxito debe ser **genérico** ("Si el email existe, recibirás un enlace…") para no revelar qué cuentas existen; ante errores de autenticación (usuario inexistente, email inválido…) se responde con el mismo mensaje genérico. Solo se comunican fallos reales (p. ej. sin conexión). Validar email no vacío y formato antes de llamar a Firebase (`android.util.Patterns.EMAIL_ADDRESS`).
- **Rutas con parámetros de query:** construir siempre sustituyendo el placeholder, nunca concatenando. En GestorPro Cliente no existen rutas con query (la Vía B está descartada); si se añade una ruta con placeholder, usar `Ruta.replace("{param}", valor)`.
- **Fotos:** la lógica de guardado vive en `ui/utils/FotoUtils.kt` (`guardaFotoEnInterna`; además `crearFotoTemporal`, `uriDeFotoTemporal`, `guardarFotoDeCamara` en Admin y también en appCliente). No duplicar esa función en pantallas. La cámara usa `TakePicture()` con `FileProvider` (`${applicationId}.fileprovider`, `res/xml/file_paths.xml`); el guardado se hace solo en el callback del resultado, nunca justo después de `launch()`. El selector común es `ui/components/BotonSelectorFoto.kt`.
- **Vinculación del CLIENTE:** el flujo de código maestro + DNI vive en `appCliente` (repositorio `VinculacionRepository`, pantalla de vinculación accesible desde el Home). Las operaciones críticas (VÍA 1 y VÍA 2) deben ejecutarse en Transaction. Nunca reintroducir Vía B/deep links.
- **Logo del negocio:** la subida vive en `NegocioRepository.guardarLogoRemoto()` (`:app`): `putFile` a `negocios/{uid}/logo.jpg` → `downloadUrl` → WriteBatch con `logo` en `negocios` + `negocios_publicos`. El Cliente lee `negocios_publicos/{id}.logo` y lo muestra con Coil. Requiere el bucket habilitado en Firebase Console.

## Modelo económico definitivo (decisiones del propietario)

> Documento vivo. Estas reglas son las **decisiones económicas FINALES** adoptadas por el
> propietario y **prevalece sobre cualquier bloque/checkpoint anterior de AGENTS.md y de la
> HOJA DE RUTA que describa reglas antiguas** (morosidad solo tras `fechaFin`, "cuarto día
> hábil", días hábiles/festivos, pago como entidad independiente, módulo económico del CLIENTE,
> movimientos "solo locales", movimientos no eliminables en Firestore, descuentos automáticos,
> `estado = MOROSO` persistido, servicios modificando movimientos ya creados). El histórico de
> `CONVERSACION_EXPORTADA.md` se conserva tal cual (no refleja estas decisiones salvo su propia fecha).

- **Fuente de verdad:** Room (Admin) sigue siendo la fuente de verdad económica del ADMIN;
  Firestore es la **réplica remota** de la economía. Todo movimiento debe existir en Room y en
  `movimientos/{movimientoId}`; la sincronización debe mantener ambos lados coherentes.
- **Movimiento = unidad económica principal**, multi-servicio. Campos: cliente, servicios,
  fechaInicio, fechaFin, precioFinal, estado, fechaPago, metodoPago, observaciones (si procede).
  **No existe entidad Pago independiente**: el pago se representa con `estado + fechaPago + metodoPago`.
- **Creación manual por el ADMIN** (no automática): el ADMIN decide fecha de inicio, fecha de fin,
  servicios, precio final, si está pagado y el método de pago cuando corresponda. La acción
  "Renovar" existente gestiona los casos ya contemplados; **no se inventan nuevas reglas de prorrateo**.
- **Estado:** si el ADMIN marca "Pagado" → `PAGADO` (con datos de pago); si no → `PENDIENTE`.
  Solo el ADMIN marca un movimiento como PAGADO; el CLIENTE no registra ni valida pagos.
- **Fechas del período:** las fija el ADMIN (fechaInicio/fechaFin), sin regla de mes natural.
- **Pago:** la cuota/movimiento debe pagarse **el día 1 del período**. **No existe "cuarto día
  hábil"** ni margen de días hábiles; no cuentan sábados, domingos ni festivos.
- **Deuda:** **suma de TODOS los movimientos PENDIENTES**. Un movimiento PENDIENTE ya representa
  deuda (no hay que esperar a `fechaFin`).
- **Morosidad (dos causas; modelo 2026-09-03):** un cliente es **MOROSO** si tiene deuda pendiente
  (**"moroso por deuda"** — uno o más movimientos `PENDIENTE`; no hay que esperar al vencimiento).
  Segunda causa: un cliente que permanece ACTIVO pasa a **"moroso por fecha"** el **día siguiente a la
  fechaFin** del período que le cubría si continúa sin cobertura económica (p. ej. fechaFin 15/09 →
  16/09), aunque ese día sea sábado, domingo o festivo. Sin reglas de días hábiles. La distinción entre
  ambas causas determina los avisos automáticos: solo la causa "por fecha" genera aviso/recordatorios
  (ver bloque «Decisiones de producto — Notificaciones, Cloud Functions y Storage», A.3–A.7).
- **Deuda (importe pendiente) ≠ MOROSO (situación del cliente).** Un PENDIENTE ya genera morosidad;
  es INCORRECTO documentar que la deuda solo genera morosidad después de `fechaFin`. Moroso por deuda y
  moroso por fecha son causas independientes: un cliente puede ser moroso por ambas, y cada causa entra
  y sale por separado (pagar la deuda no elimina la causa "por fecha" si sigue ACTIVO sin cobertura;
  renovar con un nuevo período elimina la causa "por fecha" aunque conserve deuda).
- **Salida de morosidad:** al marcar PAGADO el único pendiente y no existir otra causa →
  `moroso = false`; se actualizan `fechaPago`/`metodoPago`. Si quedan otros PENDIENTES sigue
  moroso. No se conserva historial de haber sido moroso (situación = estado ACTUAL).
- **`fechaEntradaMorosidad`:** representa la entrada en la morosidad ACTUAL; al dejar de ser
  moroso debe limpiarse/anularse. No conserva antecedente histórico.
- **BAJA + deuda:** la BAJA **no elimina** deudas (`estado = BAJA` con `deuda > 0` es válido). Los
  PENDIENTES siguen existiendo; el ADMIN puede gestionarlos y marcarlos PAGADO. La baja no perdona ni elimina deuda.
- **Servicios contratados:** afectan solo a movimientos **nuevos**; no modifican movimientos ya
  creados. Para corregir uno existente: el ADMIN lo elimina con confirmación y crea uno nuevo.
- **Eliminación:** el ADMIN puede eliminar **cualquier** movimiento (con confirmación) en
  **Room + Firestore**; al eliminar debe recalcularse la situación económica del cliente (deuda,
  morosidad, fechaEntradaMorosidad, período actual, resumen económico remoto).
- **Histórico remoto:** Firestore conserva el histórico completo (`movimientos/{id}`); no se
  elimina por antigüedad, solo cuando el ADMIN lo elimina.
- **Resumen económico remoto de `clientes/{id}`:** debe contemplar `moroso`, `deuda`,
  `fechaEntradaMorosidad`, `fechaInicioActual`, `fechaFinActual`, para que procesos futuros
  (incluidas Cloud Functions) conozcan la situación económica sin depender de la app Admin abierta.
- **App CLIENTE sin economía:** no verá movimientos, importes, deuda, método de pago ni histórico
  económico; solo lo ya decidido (estado, fecha de fin del período y funcionalidades generales).
- **Estados:** la morosidad NO es estado administrativo. Estados: ACTIVO, REGISTRADO, BAJA,
  ARCHIVADO (separados) + `moroso` como dato independiente. No persistir `estado = MOROSO`.
- **ACTIVO + moroso:** continúa usando actividades/reservas si cumple el resto de condiciones de
  acceso; la morosidad no implica BAJA ni bloqueo de actividades/reservas.
- **Descuentos:** no existe sistema automático (ni estudiante/familia/jubilado, ni categorías). El
  ADMIN decide el `precioFinal`; cualquier descuento queda reflejado indirectamente en él.
- **Precio final:** el sistema puede proponer un precio desde los servicios seleccionados, pero el
  ADMIN lo puede modificar antes de guardar; el movimiento conserva el precio decidido y cambios
  posteriores del precio de un servicio **no alteran** movimientos históricos.
- **Cloud Functions:** qué procesos automatizar con Functions sigue siendo **DECISIÓN PENDIENTE**
  de una futura sesión. Lo cerrado es que **los movimientos los crea manualmente el ADMIN**.

## Decisiones de producto — Notificaciones, Cloud Functions y Storage (2026-09-03, bloque documental)

> Documento vivo y **EXCLUSIVAMENTE DOCUMENTAL** (no implementado). Decisiones de **producto FINALES**
> del propietario sobre notificaciones, avisos automáticos de morosidad, Cloud Functions, Cloud
> Storage, logos y fotos de clientes. **Prevalece sobre cualquier bloque/checkpoint anterior** de
> AGENTS.md, de `CONTEXTO_PROYECTO.md` (p. ej. §12) y de la HOJA DE RUTA que describa flujos,
> frecuencias o comportamientos contrarios.
>
> **2.ª tanda documental (2026-09-03):** esta versión actualiza el bloque y **SUPERSEDE** el texto
> previo del propio bloque en: (a) **avisos de morosidad por movimiento PENDIENTE** — ya NO se avisa
> automáticamente por el simple hecho de existir un movimiento PENDIENTE; (b) **frecuencia de
> notificaciones programadas** — la precisión aproximada de **15 minutos es suficiente** (no cada
> minuto ni 2 minutos por precisión); (c) **límite de tamaño de fotos** — decidido en **10 MB** con
> compresión/redimensionado automático en la app. Las reglas/frecuencias preparadas en `functions/`
> local (programadas cada 2 min, recordatorio de morosidad cada 1 h) y en `storage.rules` preparadas
> (5 MB de referencia) eran **preparación provisional SIN desplegar** y quedan desactualizadas frente a
> estas decisiones; se rediseñarán/alinearán en la sesión de implementación (NADA se modifica en esta
> tarea documental). El histórico de `CONVERSACION_EXPORTADA.md` se conserva tal cual.

### A. Notificaciones — decisiones cerradas

1. **Notificaciones manuales del ADMIN (sin cambios).** Se mantiene la creación manual con destino
   **Individual / Grupo / Todos** y envío **inmediato o programado**. La pantalla actual ya contempla
   estas opciones.
2. **Notificación manual = PUSH + BUZÓN.** Cuando el ADMIN envía una notificación se debe (a) enviar
   push al cliente (FCM) y (b) registrar la notificación en el **buzón** del cliente. El buzón conserva
   el mensaje aunque el cliente no tenga activados los avisos push.
3. **Morosidad: dos causas con consecuencias distintas (distinción fundamental).**
   - **Moroso por deuda:** tiene uno o más movimientos en `PENDIENTE`. Es una situación de morosidad del
     cliente, pero **no genera por sí misma un aviso automático**: la deuda pendiente la gestiona el
     ADMIN. `PENDIENTE` es estado del **MOVIMIENTO**, no del cliente.
   - **Moroso por fecha:** su período pagado ha terminado, sigue **ACTIVO** y no existe un nuevo período
     que lo cubra. **Este sí genera aviso y recordatorios.**
4. **Morosidad — finalidad de la automatización.** La automatización de morosidad (caso "moroso por
   fecha") sirve para avisar al cliente cuando ha terminado su período y sigue ACTIVO, provocando que
   tome una decisión: **renovar o darse de baja**.
5. **Entrada en morosidad por fecha.** Si un cliente tiene un período pagado que termina
   (p. ej. `fechaFin = 15/09/2026`) y llega el 16/09/2026, continúa ACTIVO y no existe un nuevo período
   que lo cubra → **se considera MOROSO por fecha**. Da igual que el día siguiente sea sábado, domingo o
   festivo; **no existe regla de cuarto día hábil**.
6. **Transiciones entre causas y detención de avisos.**
   - Si paga la deuda pero **no renueva**: deja de ser moroso por deuda, pero **puede seguir siendo
     moroso por fecha** (sigue ACTIVO sin cobertura → se mantienen los avisos/recordatorios por fecha).
   - Si **renueva** y existe un nuevo período que lo cubre: deja de ser moroso por fecha (aunque conserve
     deuda pendiente seguiría siendo moroso por deuda, sin aviso automático por ello).
   - Si pasa a **BAJA**: **se detienen los avisos automáticos de morosidad**, aunque pueda conservar
     deuda.
7. **NO se avisa automáticamente por un movimiento PENDIENTE.** El simple hecho de que el ADMIN cree un
   movimiento/cuota en `PENDIENTE` **no genera notificación automática** al cliente (moroso por deuda):
   la existencia de la deuda pendiente la gestiona el ADMIN. La notificación automática de morosidad
   queda reservada al escenario "moroso por fecha" (los detalles de redacción y recordatorios siguen
   abiertos, ver bloque D).
8. **PAGADO no genera aviso.** Cuando el ADMIN marca un movimiento como PAGADO **NO** se envía
   notificación de confirmación de pago al cliente; el cambio de estado económico se actualiza con
   normalidad.
9. **BAJA CONFIRMADA (se mantiene).** Cuando el ADMIN acepta una solicitud de baja y el cliente pasa a
   BAJA, el cliente recibe la notificación de baja confirmada (comportamiento automático ya previsto).
10. **Sin notificación de rechazo de baja.** No se diseña flujo de notificación de rechazo: el ADMIN no
    va a rechazar solicitudes de baja.
11. **Notificaciones programadas (precisión ~15 min).** El ADMIN programa una notificación para
    fecha/hora futura; **Cloud Functions es la responsable** del envío automático al llegar el momento
    **aunque la app Admin no esté abierta**. Se ha decidido que una precisión aproximada de **15 minutos
    es suficiente**: NO se comprueba cada minuto y **no se usa una frecuencia de 2 minutos solo por ser
    más precisa**. La frecuencia de ejecución debe ser eficiente para evitar trabajo y coste
    innecesarios.
12. **Avisos desactivados ≠ sin buzón.** Si el cliente desactiva "Recibir avisos": no recibe push, pero
    las notificaciones **siguen apareciendo en el buzón** de la app. El switch controla el push; no
    elimina ni impide conservar el historial del buzón.
13. **Varios dispositivos por cliente.** Una notificación debe poder llegar a **todos** los dispositivos
    activos de un mismo cliente (móvil, móvil nuevo, tablet…). Se conserva la arquitectura actual de
    dispositivos/tokens.

### B. Cloud Functions — principios y decisiones

1. **Cloud Functions = automatización.** No sustituyen a Room como fuente de verdad económica del
   ADMIN. Arquitectura conceptual definitiva: **ROOM** → fuente de verdad económica del ADMIN;
   **FIRESTORE** → réplica remota de la información que deba existir en nube; **CLOUD FUNCTIONS** →
   automatizaciones y procesos en segundo plano; **FCM** → envío de notificaciones push; **CLOUD
   STORAGE** → almacenamiento remoto de logos y fotografías.
2. **Comprobación automática de morosidad (intención confirmada).** Se usará Cloud Functions para la
   comprobación económica porque el ADMIN puede tener la app cerrada: el paso del tiempo debe poder
   provocar la actualización de la situación económica aunque la app Admin no esté abierta.
3. **Frecuencia de comprobación de morosidad:** **una vez al día, aproximadamente a las 08:00**
   (suficiente: los cambios de día y de fecha de fin ocurren durante la noche y no es necesario
   reaccionar a las 00:00 exactas, ni comprobar cada minuto). La **lógica exacta de esa Function sigue
   PENDIENTE** (ver bloque D).
4. **Coste de Firebase.** Evitar ejecuciones innecesariamente frecuentes cuando una frecuencia menor sea
   suficiente; la frecuencia debe buscar el equilibrio entre precisión, funcionamiento correcto y
   coste. No ejecutar Functions cada minuto por una respuesta ligeramente más rápida. Referencias
   cerradas: precisión ~15 min para notificaciones programadas; comprobación de morosidad ~diaria
   ~08:00.
5. **Blaze.** La activación de Blaze queda asociada a la necesidad de usar infraestructura como Cloud
   Functions y Cloud Storage. Antes del despliegue definitivo deberán revisarse consumo, configuración
   de presupuesto/alertas, costes potenciales y las funciones que realmente se van a ejecutar. **No
   activar ni configurar Blaze durante la fase documental.**

### C. Cloud Storage — logos y fotos

1. **Nombre y logo del centro compartidos.** El ADMIN configura el negocio con nombre del centro y logo
   del centro; ADMIN y CLIENTE muestran la misma información (NOMBRE + LOGO). El logo **no** depende de
   un archivo local exclusivo del dispositivo Admin: se usa **almacenamiento remoto**.
2. **Actualización del logo.** Al cambiar el logo, el anterior se sustituye y el nuevo pasa a ser el
   logo vigente en ambas aplicaciones. No se deben acumular innecesariamente versiones antiguas del
   mismo logo en Storage.
3. **Fotos de clientes remotas y compartidas.** Las fotos de clientes pasan a almacenarse remotamente
   en Firebase Storage y se comparten entre ambas apps: ADMIN cambia foto → el CLIENTE la ve; CLIENTE
   cambia foto → el ADMIN la ve. **No** debe existir una copia local independiente como fuente de
   verdad de la fotografía: la foto remota es la referencia compartida.
4. **Permisos de foto.** ADMIN y CLIENTE pueden cambiar la foto del cliente: el ADMIN las de los
   clientes de su negocio; el CLIENTE solo la suya. Mantener las restricciones de seguridad; un cliente
   nunca modifica fotografías de otro cliente.
5. **Fotos de prueba actuales.** Las fotos locales que existen hoy en los dispositivos durante el
   desarrollo son fotos de prueba y **NO requieren migración**. No son la arquitectura definitiva: la
   solución de producción debe funcionar mediante Storage para cualquier negocio que utilice GestorPro.
6. **Tamaño de fotos (decidido): máximo 10 MB por fotografía** como límite de seguridad. **El usuario
   NO debe manipular manualmente una fotografía para cumplir el límite**: la aplicación debe poder
   recibir una foto de la galería o tomada con la cámara, **redimensionar/comprimir automáticamente
   cuando sea necesario** y subir el resultado a Storage. El objetivo es que el límite de 10 MB sea un
   límite técnico, no una tarea que el usuario deba gestionar. La implementación exacta de la
   compresión/redimensionado sigue **PENDIENTE** (bloque D). ⚠️ Las `storage.rules` preparadas actuales
   usan **5 MB de referencia**: quedan desactualizadas frente a esta decisión; NO se modifican en esta
   tarea y se alinearán al implementar.
7. **Sustitución de fotos (principio de seguridad).** Al cambiar una fotografía: la nueva pasa a ser la
   vigente, el archivo anterior debe eliminarse de Storage **cuando sea seguro hacerlo** y no deben
   quedar versiones antiguas acumuladas innecesariamente. La implementación debe priorizar **no dejar al
   cliente sin fotografía** si la subida o actualización falla. Orden del cambio:
   (1) subir la nueva fotografía; (2) actualizar la referencia; (3) confirmar que la nueva es válida;
   (4) eliminar la anterior. **No borrar la fotografía antigua antes de disponer correctamente de la
   nueva.**

### D. Decisiones que siguen ABIERTAS (NO cerrar, NO implementar todavía)

> Quedan pendientes de una sesión específica de diseño. No inventar estrategias ni documentarlas como
> definitivas.
>
> **3.ª aclaración (2026-09-03, modelo de morosidad):** las cuestiones 3, 4 y 5 quedan **RESUELTAS a
> nivel de modelo** (ver A.3–A.7: dos causas "moroso por deuda / moroso por fecha", transiciones y
> detención de avisos con BAJA). Se mantienen aquí numeradas solo por trazabilidad; su detalle
> operativo/redacción puede seguir abierto.

1. Redacción exacta de las notificaciones de morosidad.
2. Diferencia exacta entre aviso inicial de morosidad y recordatorio.
3. ~~Cuándo se detienen los recordatorios (intervalo de 24 h...)~~ → **RESUELTO en el modelo:** los
   recordatorios acompañan al estado "moroso por fecha" y cesan cuando el cliente deja de serlo (renueva
   con un nuevo período que lo cubre) o pasa a **BAJA** (los avisos automáticos de morosidad se detienen,
   aunque conserve deuda). Solo queda el detalle operativo del ciclo/redacción.
4. ~~Qué ocurre exactamente si se paga la deuda pero no existe un nuevo período~~ → **RESUELTO en el
   modelo:** deja de ser moroso por deuda, pero **puede seguir siendo moroso por fecha** (ACTIVO sin
   cobertura → continúan avisos/recordatorios por fecha).
5. ~~Qué comportamiento tendrán las notificaciones de morosidad para clientes BAJA con deuda~~ →
   **RESUELTO en el modelo:** al pasar a BAJA **se detienen los avisos automáticos de morosidad**, aunque
   el cliente pueda conservar deuda (los PENDIENTES siguen existiendo y los gestiona el ADMIN).
6. Estrategia exacta de idempotencia.
7. Estrategia exacta de reintentos (y comportamiento cuando una Function falla).
8. Lógica interna definitiva de la Function de morosidad (qué consulta, qué clientes/movimientos revisa,
   cómo determina moroso, cuándo genera notificación, cómo evita duplicados).
9. Lógica definitiva de actualización del resumen económico remoto (semántica exacta y sincronización de
   `moroso`, `deuda`, `fechaEntradaMorosidad`, `fechaInicioActual`, `fechaFinActual`).
10. Implementación concreta de compresión/redimensionado de fotos.
11. Configuración definitiva de Blaze/presupuesto (consumo, alertas, costes y funciones a ejecutar).

> **Relación con el modelo económico:** el bloque «Modelo económico definitivo» sigue vigente sin
> cambios (Room = fuente de verdad; movimientos replicados a Firestore; histórico remoto completo;
> crear/editar/eliminar manuales con confirmación y recálculo; PENDIENTE = deuda; morosidad por deuda y
> por fin de período con ACTIVO sin cobertura; deuda subsiste con BAJA; sin entidad Pago; el CLIENTE no
> tiene módulo económico, solo estado y fecha de fin de período; precio final decidido por el ADMIN; los
> cambios de servicios afectan solo a movimientos nuevos). Las decisiones de Cloud Functions **no
> modifican** el modelo económico.

## Estado actual y pendientes (2026-09-03)

> ACTUALIZACIÓN 2026-09-04 (F2 PRUEBAS REALES + CORRECCIONES MOVIMIENTOS/MOROSIDAD + DEPLOY RULES).
> HEAD del desarrollador: `c67cdbd "impplementando codigo para cuando contrate balze2"` (la F2 de
> economía queda **COMMITEADA** en HEAD: Room v17, réplica `movimientos/{id}` + resumen en `clientes/{id}`,
> `eliminacion_pendiente`, etc.). Working tree con cambios de esta sesión SIN commit (NO revertir).
> Este bloque SUPERSEDE a los checkpoints previos que afirmaban "F2 sin commit / sin deploy".
>
> **DEPLOY AUTORIZADO (único de esta tanda):** `firestore.rules` local, validado con
> `npm --prefix firestore-tests test` (**151/151**), desplegado en `gestorpro-50e83` → ruleset
> `projects/gestorpro-50e83/rulesets/cd36cbc9-dee0-47e1-b523-481b31fb6eb0` (release `cloud.firestore`,
> createTime 2026-09-03T21:32:40Z). **NO** se desplegó Storage Rules, Functions ni nada más.
>
> **Cambios de la sesión (working tree, sin commit):**
> 1. **Alta de movimientos sin servicios:** se elimina la obligatoriedad "Selecciona al menos un
>    servicio" (un movimiento puede tener 0..n servicios; `precioFinal` y fechas siguen obligatorios).
> 2. **Visual de estado de movimientos:** `ItemMovimientoPerfil` (perfil) e `ItemMovimiento`
>    (EconomíaScreen) pintan el card según `movimiento.estado` — PENDIENTE rojizo suave (`0xFFF44336`
>    @8 %) y PAGADO verde (`0xFF4CAF50` @8 %) — e icono `$` + importe del mismo color del estado.
>    `ui/components/MovimientoItem.kt` sigue SIN consumidores (no se toca).
> 3. **"Deuda total"** al inicio de la pestaña Economía del perfil (antes de "Nuevo movimiento"),
>    reutilizando `ResumenEconomiaCard` con `MovimientoMorosidad.deudaDe(movimientos)`, formato
>    moneda es_ES sin signo `+`, color rojo.
> 4. **Morosidad por fecha con ETAPAS (frontera = última `fechaBaja`):**
>    - El motor `MovimientoMorosidad` gana `inicioEtapa: Long? = null`; un PAGADO solo cuenta en la
>      causa por fecha si `fechaFin >= inicioEtapa`. `null` = sin corte (comportamiento histórico).
>    - La frontera se alimenta con **`cliente.fechaBaja`** (última fecha de baja), NO con `fechaAlta`.
>    - BAJA→ACTIVO: **conserva `fechaBaja`** (helpers puros `prepararReactivacion`/`aplicarBaja` en
>      `ClienteViewModel`) y renueva `fechaAlta` (dato informativo). `restaurarCliente` (ARCHIVADO) ya
>      no borra `fechaBaja`. Una nueva BAJA siempre fija `System.currentTimeMillis()` (formulario y
>      `darDeBaja`), sin reutilizar la anterior.
>    - `MovimientoRepository.calcularYPersistirMorosidad` y el `esMoroso` del perfil pasan
>      `inicioEtapa = cliente.fechaBaja`.
>    - `model/Cliente` y `toCliente()` exponen `fechaAlta`/`fechaBaja` (SIN migración Room ni cambios
>      de Rules/Functions/Storage).
>    - Corrige la regresión real detectada en producción (`clientes/1654697743`, ACTIVO, fechaAlta
>      2026-09-03T21:23Z > fechaFin del único PAGADO 2026-09-03T00:00Z): ahora ese movimiento cuenta y
>      el cliente es **MOROSO por fecha** con hoy posterior al fin. Se conserva el caso F2-14 (periodo
>      antiguo cerrado antes de la última baja no provoca morosidad) y la deuda PENDIENTE sigue
>      contando siempre.
>
> **Tests/verificación HOY:** `:app:testDebugUnitTest` → **85/85** (`MovimientoMorosidadTest` **35**,
> nuevo `ClienteTransicionEstadoTest` **4**, resto intacto); `:app:assembleDebug` BUILD SUCCESSFUL.
> Rules 151/151 (sin cambios). NO commit, NO deploy adicional.
>
> **Pendientes:** commit agrupado del working tree; retirar logs temporales (`[DIAG alta]`,
> `[DIAG sesiones]`, `ClasesDiagnostico`); confirmar fix del alta con BD limpia; conciliar/desplegar
> pendientes previos (VÍA 2/fecha nacimiento opcional, `fallbackToDestructiveMigration`, botones
> restantes, diagnóstico "Crear negocio"); Blaze/Functions/Storage pendientes de decisión.

> ACTUALIZACIÓN 2026-09-03 (F2 ECONOMÍA ROOM↔FIRESTORE IMPLEMENTADA + DIAGNÓSTICO "CREAR NEGOCIO").
> HEAD del desarrollador: `100c4eb "mejoras y correciones"`. Working tree con cambios SIN commit:
> toda la F2 (economía) + las tandas documentales + `RegistroScreen.kt` (del desarrollador) +
> `firestore-tests/firestore-debug.log` (basura del emulador). NO deploy, NO commit.
>
> **F2 cerrada en código (pendiente de prueba manual):**
> - **Motor `MovimientoMorosidad`:** deuda = suma de TODOS los PENDIENTES (sin filtrar `fechaFin`);
>   dos causas (por deuda / por fecha solo ACTIVO con cobertura PAGADA terminada); BAJA solo por deuda;
>   `fechaEntradaMorosidad` = `ahora` (detección), nunca `fechaFin`; `exentoMorosidad` (moroso=false
>   con la deuda real).
> - **IDs globales:** `util/IdMovimiento` (rango alto ≥1e9, patrón `IdCliente`) preasignados antes del
>   insert → sin colisiones `movimientos/{id}` entre dispositivos Admin. `MovimientoDao.insertarMovimiento`
>   sigue en `Unit` (no se usa autoincrement local como id remoto).
> - **Réplica cableada:** crear/editar/eliminar movimiento → Room → recálculo → `movimientos/{id}` +
>   resumen remoto en `clientes/{id}` (`moroso`, `deuda`, `fechaEntradaMorosidad`, `fechaInicioActual`,
>   `fechaFinActual`, `exentoMorosidad`). Baja/restauración publican resumen. Eliminaciones remotas
>   fallidas persistidas en `eliminacion_pendiente` (Room v17) con reintento al arranque
>   (`MainViewModel`), al entrar en gestión de clientes y desde el perfil.
> - **Ajustes:** editar datos personales NO dispara economía (solo si cambia `estado`); abrir un perfil NO
>   reescribe todos los movimientos (`sincronizarSiHayPendientes` solo si hay pendientes).
> - **Room v17** (`MIGRACION_16_17`: columna `exentoMorosidad` en `cliente` + tabla
>   `eliminacion_pendiente`). Switch "Exento de morosidad" en el perfil Admin.
> - **`firestore.rules` LOCAL:** update ADMIN de `clientes` admite `moroso`/`deuda`/
>   `fechaEntradaMorosidad`/`exentoMorosidad` (el CLIENTE nunca). Tests Rules **151/151** (PRUEBA 129–136);
>   unit `:app` **68/68**; `assembleDebug` OK (ambos módulos). NO desplegado.
>
> **Diagnóstico ABIERTO — "Crear negocio en la nube" PERMISSION_DENIED (producción):** el batch de
> `NegocioRepository.crearNegocio` (set `negocios/{uid}` + set `negocios_publicos/{uid}` + update
> `usuarios/{uid}.negocioId`) falla. Evidencia: servicios/sesiones sí se crean → `usuarios/{uid}.negocioId`
> ya está asignado (= UID). Hipótesis principal: estado de DATOS (negocioId asignado pero `negocios/{uid}`
> ausente) → las tres escrituras las bloquean las Rules LOCALES (`usuarioActual().negocioId == null` /
> `resource.data.negocioId == null`). Pendiente confirmar en consola y reconciliar datos (Admin SDK,
> autorizado). NO es un bug de F2.
>
> **Pendientes inmediatos:** prueba manual de F2; reconciliar/desplegar `firestore.rules` local (151 tests)
> solo con autorización (ruleset desplegado obsoleto, sin claves del resumen); retirar
> `fallbackToDestructiveMigration` antes de producción; `actualizarPeriodoActualRemoto` quedó sin
> consumidores (limpieza opcional); los fallos de create/update no se persisten (solo eliminaciones),
> riesgo residual documentado.

> ACTUALIZACIÓN 2026-09-03 (CIERRE — CORRECCIÓN ACCESO SOLO ACTIVO + NOTIFICACIÓN VINCULACIÓN + TEXTO
> "ACTIVIDADES"). HEAD del desarrollador: `f32a5c1 "impplementando codigo para cuando contrate balze2"`
> (2026-09-02 21:53). Working tree con **32 archivos modificados SIN commit** (incluye TODO lo anterior
> sin commitear + los cambios de esta tanda; ver git status). Este CHECKPOINT documenta lo cerrado en
> esta conversación para retomar con precisión. Detalle completo en `CONVERSACION_EXPORTADA.md`.
>
> **1) Acceso del CLIENTE a sesiones/reservas SOLO con estado == "ACTIVO" (regla definitiva, cerrada):**
> - `firestore.rules`: `clientePuedeAcceder` ahora exige `c.estado == "ACTIVO"` (antes `!= "BAJA"`);
>   `reservaCreaValida` exige `cliente.estado == "ACTIVO"`. La morosidad es independiente: un ACTIVO
>   con deuda (`moroso`) SÍ accede (es flag, no estado).
> - `appCliente` `ui/viewmodel/SesionesClienteViewModel.kt`: si `ficha.estado != ACTIVO` corta la
>   carga; BAJA → `dadoDeBaja`; cualquier otro no activo (REGISTRADO/ARCHIVADO…) → nuevo flag
>   `estadoNoActivo`.
> - `appCliente` `ui/home/ClasesScreen.kt`: rama visual `estadoNoActivo` ("Tu cuenta aún no está
>   activa… podrás ver y reservar actividades").
> - `appCliente` `ui/home/HomeScreen.kt`: la card de acceso solo se muestra con estado
>   ACTIVO/PAGO_VENCIDO (antes: != BAJA).
> - `appCliente` `data/firebase/ReservaRepository.kt` (`crearReserva`): bloquea si
>   `estado != "ACTIVO"` (BAJA → mensaje de baja; resto → "Tu cuenta no está activa para reservar").
> - Rules tests nuevos **PRUEBA 121–124**: REGISTRADO no lee sesiones (DENY), ARCHIVADO no lista
>   (DENY), REGISTRADO no reserva (DENY), ACTIVO+moroso lee y reserva (ALLOW). **Suite local: 143/143.**
>
> **2) Notificación al ADMIN cuando el CLIENTE se vincula (tipo VINCULACION, cerrada):**
> - `appCliente` `data/firebase/VinculacionRepository.kt`: tras vincularse con éxito (VÍA 1 en
>   `vincularFichaExistente` y VÍA 2 en `crearFicha`) llama a `notificarVinculacionAlAdmin(negocioId,
>   clienteId)`, que crea (solo si no existe) `notificaciones/vinculacion_{negocioId}_{clienteId}` con
>   `{negocioId, titulo, mensaje, tipo: "VINCULACION", origen: "AUTOMATICA", modoDestino: "INDIVIDUAL",
>   clienteId, fechaCreacion: Timestamp.now(), estado: "PENDIENTE"}`. Falla en silencio (nunca bloquea
>   la vinculación).
> - `firestore.rules` (`notificaciones`): rama `allow create` SOLO para CLIENTE vinculado (rol CLIENTE,
>   `clienteId is int`, `negocioId is string`), con `hasOnly` de esas 9 claves, `negocioId`/`clienteId`
>   iguales a los del usuario, `tipo == "VINCULACION"`, y `!exists(...)` (si el documento ya existe la
>   escritura es update y se deniega → sin duplicados). No abre escritura genérica al cliente.
> - `:app` `ui/notificaciones/GestionNotificacionesScreen.kt`: tipo "VINCULACION" → etiqueta
>   "Vinculación" en `nombreDeTipo`.
> - Rules tests nuevos **PRUEBA 125–128**: VINCULACION de su negocio ALLOW; de otro negocio DENY;
>   duplicado (doc existente) DENY; otro tipo (MOROSIDAD) DENY.
>
> **3) Término visible "Actividades" (solo UI/terminología):**
> - appCliente `ui/home/HomeScreen.kt` (card Home): "Clases" → "Actividades", descripción
>   "Consulta y reserva tus actividades" (card fija 168.dp, cabe en ~2 líneas; sin cambios de lógica).
> - `:app` `ui/home/HomeScreen.kt` (card Accesos rápidos): "Servicios" → "Actividades". La descripción
>   pasó por "Crea y gestiona tus actividades" (probó `MenuCard.descripcionMaxLines`) y quedó en
>   **"Crea tus actividades"** a 1 línea. `MenuCard.kt` volvió a su estado original (parámetro extra
>   eliminado, `maxLines = 1`); NO tiene cambios netos.
> - La sección/colecciones siguen llamándose internamente "Servicios"/`servicios` (sin renombrar); solo
>   cambió el texto visible de la card. Navegación intacta (`Routes.SERVICIOS`).
>
> **4) Identidad remota del negocio (revisada, sin cambios de código):** nombre ya viaja a
> `negocios_publicos` y appCliente lo refresca; el logo cross-device sigue bloqueado por el bucket de
> Storage/Blaze (si no hay URL remota el cliente no puede mostrar el logo del admin). No se fuerza URL.
>
> **VERIFICACIÓN REALIZADA (al cierre):** `:app` y `:appCliente` compilan (`compileDebugKotlin`) y sus
> unit tests pasan; `assembleDebug` de ambas OK; Rules **143/143** (PRUEBA 121–128 nuevas). **NO commit,
> NO deploy** (el ruleset DESPLEGADO sigue obsoleto respecto al local: sin acceso "ACTIVO" estricto ni
> rama VINCULACION).
>
> **PARA REANUDAR:**
> 1. Reconciliar `firestore.rules` local (143 tests) frente al desplegado; desplegar solo con
>    autorización (`deploy --only firestore:rules`). Hasta entonces, en producción un cliente
>    REGISTRADO/ARCHIVADO aún puede operar como antes y la notificación VINCULACION no se podrá crear.
> 2. Commit agrupado del working tree (32 archivos M; ver listado en `CONVERSACION_EXPORTADA.md`).
> 3. Seguir pendientes previos: conciliar ruleset movimientos/resumen, revisar VÍA 2 y fecha de
>    nacimiento opcional, terminar unificación de botones en pantallas activas restantes, decidir
>    `DetalleVisuales.kt`, regenerar sesiones y confirmar índices, diagnóstico alta `[DIAG alta]`,
>    limpieza (`firestore-debug.log`, basura versionada).

> ACTUALIZACIÓN (2026-09-0X — CONTINUACIÓN DESDE AUDITORÍA FINAL DE BOTONES). HEAD actual del
> desarrollador: `3b94164 "mejoras y correciones"` (hay cambios del desarrollador posteriores a la
> anterior ACTUALIZACIÓN de 2026-09-02). Working tree con 29 cambios SIN commit. Esta actualización
> es un CHECKPOINT: documenta lo realizado en esta conversación y las inconsistencias detectadas en
> el árbol para retomar con precisión.
>
> **VERIFICADO EN EL ÁRBOL ACTUAL (al cierre):**
> - Rules de Firestore: la colección de tests sigue con **135 tests** (`npm --prefix firestore-tests test`);
>   las PRUEBA 121–129 (economía/resumen, campos vacíos, fecha opcional) **NO están presentes** en el
>   ruleset local (parece revertido por commits del desarrollador). `firestore.rules` mantiene
>   `match /movimientos/...` pero NO contiene las claves `moroso`/`fechaEntradaMorosidad`/`deuda` en el
>   update ADMIN de `clientes`.
> - Economía FASE 6 (Room→Firestore): presente: `util/MovimientoFirestore.kt`,
>   `data/firebase/MovimientoRemotoRepository.kt`, DAO insert→Long, `MovimientoRepository` con
>   sincronización de movimiento+resumen, `ClienteRemotoRepository.actualizarResumenEconomicoRemoto`,
>   `AppModule`. **OJO:** si el ruleset desplegado/actual no autoriza las claves del resumen, reaparece
>   el aviso "Cliente X: No tienes permisos para sincronizar esta ficha" (en esta conversación se
>   DESPLEGARON las Rules una vez autorizado `deploy --only firestore:rules`; verificar estado desplegado
>   vs. local antes de continuar).
> - Alta/identidad: presente `util/IdCliente.kt` (ids altos en el alta del ADMIN) y la reconciliación
>   Firestore→Room (`buscarClienteEnNubePorDni`, `obtenerClientesRemotosDelNegocio`,
>   `incorporarClientesRemotos` en ClientesScreen). La reactivación de la VÍA 2 en appCliente
>   (`VinculacionRepository`) puede haber quedado afectada por el revert: revisar `ResultadoIndice.NoExiste`.
> - `fechaNacimiento` OPCIONAL: **NO está vigente** en el árbol actual: `AñadirClienteScreen` vuelve a
>   exigirla (`errorFechaNacimiento = fechaNacimiento == null`) y `CompletarPerfilScreen` la pide. Decidir
>   si se reintroduce (fue un cambio de esta conversación posteriormente revertido).
> - Botones App*: `Botones.kt` existe en `:app` y `:appCliente`. En `:app` se fijó `AppPrimaryButton` y
>   `AppDialogConfirmButton` al azul corporativo `#1E88E5` (`AzulPrimarioGestPro`) porque el tema usa
>   Material You (primary dinámico/verde). `:appCliente` mantiene `AppPrimaryButton` con color del tema
>   (no modificado por nosotros). `ui/components/DetalleVisuales.kt` NO existe actualmente (eliminado/revert).
> - Se re-unificaron a App* estas pantallas (última verificación: 0 botones Material 3 directos):
>   `:app` ClientesScreen, EditarServicioScreen, DetalleServicioScreen, EditarSesionScreen,
>   AñadirClienteScreen (principales), PerfilClienteAdministradorScreen (botones principales/diálogos;
>   quedan excepciones justificadas: WhatsApp, DatePickers, Archivar/Restaurar semántico, selector método).
>   `:appCliente` CompletarPerfilScreen, EditarPerfilScreen, InicioScreen, MiPerfilScreen,
>   ConfiguracionScreen, HomeScreen.
> - Política de privacidad implementada y accesible en ambas apps
>   (`PoliticaPrivacidadScreen.kt` en `:app` y `:appCliente`; rutas/enlaces OK).
>
> **PARA REANUDAR (próximos pasos sugeridos):**
> 1. Verificar/conciliar el ruleset: local (135 tests, sin PRUEBA 121–129 ni claves del resumen) frente al
>    desplegado en producción; decidir si se reintroduce la sección movimientos completa + `moroso`/`deuda`/
>    `fechaEntradaMorosidad` y sus tests (esta conversación llegó a 144 tests antes del revert).
> 2. Revisar si la VÍA 2 de appCliente sigue reactivada y el flujo de fecha de nacimiento opcional.
> 3. Completar la unificación de botones en las pantallas activas que aún usan Material 3 directo
>    (inventario previo: auth de `:app`, Perfil de cliente restante, EconomiaScreen y sus diálogos,
>    ProgramarSesionesScreen, MiNegocio/CrearNegocio/Cuenta/Datos/Preferencias, notificaciones, solicitudes,
>    y varias de `:appCliente` Cuenta/ListaNotificaciones/etc.). NO tocar `ui/clases/*` (legacy).
> 4. Decidir si `DetalleVisuales.kt` (kit de detalle) se recupera o se elimina definitivamente.
> 5. Commits agrupados del working tree y limpieza (`firestore-debug.log`, basura versionada).


> ACTUALIZACIÓN 2026-09-02 (ECONOMÍA FASES 1–5 + AJUSTES LLAVE/CARDS + SELECTOR NOTIFICACIÓN):
> HEAD = `3b113e6 "impplementando codigo para cuando contrate balze2"` (SIN commits nuevos; todo lo
> de esta tanda está en el working tree). Detalle cronológico en `CONVERSACION_EXPORTADA.md`
> (Sesiones XXVIII en adelante).
>
> **Cambios cerrados en esta tanda (todo compilando, sin commit ni deploy):**
> 1. **Llave como servicio normal:** eliminado `tieneLlave` (Room v13→14 con recreación de
>    `cliente` sin la columna), de `model/Cliente`, de la UI (AñadirCliente/Perfil), de la réplica a
>    Firestore y de las Rules (hasOnly). Sin migrar valores antiguos (decisión: descartar). La
>    "llave" se gestiona como un servicio normal contratado. appCliente solo conserva cambios
>    previos sin commitear (parser sin el campo).
> 2. **ServiciosScreen:** card COMPACTO (sin chip "ACTIVO/DE BAJA" dentro) y ahora muestra el
>    **precio del servicio** (`30 €` / `12,50 €`).
> 3. **FASE 1 — Modelos Room (Room v14):** `ServicioEntity.precio: Double = 0.0`;
>    `MovimientoEntity` pasa a `servicios: List<Int>` + `precioFinal: Double` + `metodoPago:
>    MetodoPago?` (desaparecen `servicio`/`precio`); nuevos `model/MetodoPago` (EFECTIVO/BIZUM/
>    TRANSFERENCIA) y `MetodoPagoConverter`. `MIGRACION_13_14` no destructiva (recrea `servicio`
>    con precio=0 y `movimiento` con precioFinal=precio, metodoPago=NULL y servicios mapeado por
>    nombre exacto único; si no hay correspondencia segura → `[]`).
> 4. **FASE 2 — Precio de servicios (UI + Firestore):** campo Precio en EditarServicioScreen
>    (≥0); ServiciosScreen muestra el precio; VM/Repo remoto envían `precio`; Rules `servicios`
>    aceptan `precio` (number) en create/update (en update solo se exige si se toca). Rules
>    **135/135** (PRUEBA 21B/21C/25B/25C nuevas).
> 5. **FASE 3 — Movimientos multi-servicio:** Nuevo movimiento con checkboxes de ACTIVOS
>    (nombre+precio) y propuesta `precioFinal` = suma (flag manual que NO se sobrescribe; botón
>    "Usar precio calculado"). Edición multi; inactivos históricos se conservan bloqueados.
>    Crear/editar NO modifica `serviciosContratados`. Helpers `util/MovimientoPrecio` (10 tests).
> 6. **FASE 4 — Pagos en el movimiento:** `util/MovimientoPago.resolver` (12 tests): PENDIENTE→
>    PAGADO fija fechaPago (hoy salvo fecha elegida); PAGADO→PENDIENTE limpia fechaPago+metodoPago;
>    método opcional; edición conserva fecha/método existentes (bug de pérdida corregido); detalle
>    de EconomiaScreen muestra fecha y método.
> 7. **FASE 5 — Deuda y morosidad en Room (Room v15):** `ClienteEntity`/`Cliente` con
>    `moroso: Boolean` y `fechaEntradaMorosidad: Long?`. Motor ÚNICO `util/MovimientoMorosidad`
>    (17 tests): deuda = PENDIENTES exigibles (`fechaFin <= ahora`); ACTIVO moroso si deuda>0 o
>    perdió continuidad PAGADA; BAJA moroso SOLO por deuda; fechaEntrada = fechaFin del periodo y
>    NO se reinicia al recalcular. `ClienteDao.obtenerIdsMorosos()` ahora lee `moroso=1`;
>    `MovimientoRepository` recalcula tras cada CRUD y expone `recalcularMorosidadDeCliente` (se
>    llama también en bajas/restauración). NO se eliminó `EstadoCliente.MOROSO` todavía; sin
>    `MOROSO_BAJA`, PagoEntity ni pagos parciales.
> 8. **Corrección notificaciones:** el selector INDIVIDUAL ya NO abre diálogo: reutiliza la
>    pantalla completa `SeleccionarClientesScreen` con `ModoSeleccion.UNO` (selección única;
>    "Continuar" fija `NotificacionesViewModel.seleccionIndividual`; volver atrás no lo modifica).
>    GRUPO sin cambios (ruta `seleccionar_clientes?modo=grupo|individual`). `DialogoSeleccionarClientes`
>    queda sin uso (no eliminado).
>
> **Verificación:** `:app`/`:appCliente` BUILD SUCCESSFUL; `:app:testDebugUnitTest` **45/45**
> (Example 1 + MovimientoPrecio 10 + MovimientoPago 12 + MovimientoMorosidad 17 +
> NotificacionConfig 5); Rules **135/135** (sin cambios tras Fase 2).
> **No tocado:** Firestore de movimientos, Functions, appCliente (salvo previos sin commitear),
> notificaciones de envío. Room v15 con migraciones 11→12,12→13,13→14,14→15 (fallback destructivo
> aún presente como respaldo, no usado en estas rutas).
> **Pendiente inmediato (decisión del propietario):** siguiente fase de economía (¿resumen/deuda
> en UI de Economía?, ¿sincronización Firestore de movimientos o resumen?, retirar
> `EstadoCliente.MOROSO`, alinear default de config app↔Functions) + revisión/commit del working
> tree y limpieza de basura (Rules desplegadas siguen obsoletas: sin `clientePuedeAcceder`,
> `solicitudes/delete` ni `SOLICITUD_BAJA`/`precio`).

> ACTUALIZACIÓN 2026-09-02 (CORRECCIONES/FUNCIONALIDADES + DIAGNÓSTICO ECONOMÍA): HEAD =
> `3b113e6 "impplementando codigo para cuando contrate balze2"`. Todo el trabajo
> posterior a `244db1e` sigue en el working tree SIN commit (incluidas las 2 fases de
> correcciones de esta sesión). Rules **131/131** (123 + PRUEBA 113–120). Unit `:app`
> con `NotificacionConfigTest` (5). Detalle en `CONVERSACION_EXPORTADA.md` (Sesiones XXV–XXVII).
>
> **Working tree actual (NO revertir, sin commit):**
> - `app/.../ui/viewmodel/SesionViewModel.kt` — fix regresión PERMISSION_DENIED al generar sesiones.
> - `app/.../ui/solicitudes/SolicitudesScreen.kt` — búsqueda por cliente + eliminar resueltas + scroll.
> - `app/.../ui/viewmodel/SolicitudesViewModel.kt` + `app/.../data/firebase/SolicitudRemotoRepository.kt` — eliminar solicitud resuelta; aviso SOLICITUD_BAJA al ADMIN.
> - `app/.../data/firebase/NotificacionRemotoRepository.kt` — `crearNotificacionSolicitudBaja`, `existeNotificacion`, config con default `bajaConfirmada.activa=true`.
> - `app/.../data/firebase/BajaClienteRemotoRepository.kt` — BAJA_CONFIRMADA con fecha + idempotencia + default activo.
> - `app/.../ui/notificaciones/GestionNotificacionesScreen.kt` — tipo "Solicitud de baja".
> - `appCliente/.../ui/home/HomeScreen.kt` — aviso de morosidad como texto (no Card), borde rojo en Card de estado.
> - `firestore.rules` — `solicitudes/delete` no PENDIENTE; tipo `SOLICITUD_BAJA` permitido al ADMIN.
> - `firestore-tests/firestore.rules.test.cjs` — PRUEBA 113–120.
> - `app/src/test/.../NotificacionConfigTest.kt` (nuevo, 5 tests).
> - Basura: `firestore-tests/firestore-debug.log`, `.idea/shelf/...`.
>
> **Correcciones y funcionalidades cerradas (working tree):**
> 1. **Regresión sesiones (PERMISSION_DENIED al generar):** causa = el servicio no estaba
>    replicado en Firestore (negocio actual `6YFNg1...` con `servicios` vacía); la regla
>    `sesiones/create` exige `servicioValidoParaSesion`. Fix: `SesionViewModel.generarSesiones`
>    replica el servicio (`crearServicioRemoto`, idempotente) antes de la cascada/réplica.
>    NO se abrieron permisos. PRUEBA 113–115.
> 2. **SolicitudesScreen:** búsqueda por datos REALES del cliente (nombre+apellidos/DNI/
>    teléfono/email/id); eliminar ACEPTADA/RECHAZADA con confirmación; PENDIENTE no
>    eliminable (UI y Rules); lista con `weight(1f)`. PRUEBA 116–117.
> 3. **Home appCliente:** aviso de morosidad como texto (color error, "aquí" en primario/
>    negrita/subrayado, solo "aquí" clicable → `Routes.CUENTA`); Card de estado con borde
>    `colorScheme.error` cuando PAGO_VENCIDO (contenido/fondo intactos).
> 4. **Notificación CLIENTE→ADMIN por solicitud de baja:** la crea el ADMIN al cargar
>    solicitudes PENDIENTES (`SolicitudesViewModel.generarAvisosDeSolicitudesPendientes` →
>    `crearNotificacionSolicitudBaja`, `notificaciones/solicitud_baja_{clienteId}_{fecha}`,
>    tipo SOLICITUD_BAJA, origen AUTOMATICA). Se RETIRÓ la creación desde el CLIENTE (las
>    Rules desplegadas solo permiten `esAdmin()` en `notificaciones`). Rules: `SOLICITUD_BAJA`
>    añadido al `tipo` del create ADMIN. PRUEBA 118–120.
> 5. **BAJA_CONFIRMADA:** mensaje con fecha ("Tu baja se ha realizado con fecha dd/MM/yyyy."),
>    **default `bajaConfirmada.activa = true`** (config inexistente/campo ausente → activa;
>    el ADMIN puede desactivarla en Configuración), idempotencia con `existeNotificacion` e
>    ID determinista `baja_confirmada_{clienteId}_{fechaBaja}` (igual que CF).
>
> **DIAGNÓSTICO ECONOMÍA (2026-09-02, SOLO LECTURA, nada implementado):** ver
> `CONVERSACION_EXPORTADA.md` Sesión XXVII. Resumen: NO existe circuito económico (no hay
> Cuota/Pago/Descuento/Tarifa/método de pago/prorrateo). Un movimiento Room es la "cuota"
> manual (servicio texto + precio manual + fechaInicio/fechaFin + estado + fechaPago solo en
> renovación). Solo se replica a Firestore `clientes/{id}.fechaInicioActual/fechaFinActual`
> (derivados del movimiento con mayor fechaFin). Morosidad derivada y NO persistida, con DOS
> fuentes (Room: ACTIVO fechaFin<ahora / BAJA PENDIENTE; appCliente/Functions: fechaFinActual).
> La regla del "cuarto día hábil" NO está implementada en ningún sitio. `movimientos/{id}` tiene
> reglas Firestore pero la colección no se usa. CF (sin deploy) depende solo de
> `clientes/{id}.fechaFinActual`. BUG confirmado: editar un movimiento resetea `fechaPago`
> (`PerfilClienteAdministradorScreen.kt:2094-2109`). Divergencia app/CF en default de config.
> Hay **10 decisiones de negocio pendientes** (sección 24 del informe / Sesión XXVII).
>
> **Pendiente INMEDIATO (decisión del propietario, NO programar hasta decidir):**
> 1. **ECONOMÍA — decisiones (Sesión XXVII §24):** cuota=movimiento o entidad; Pago
>    independiente vs fechaPago; tarifas en Servicio; descuentos (estudiante/familia/
>    jubilado + edad estudiante); altas/prorrateos (tramos, cargo, 0,25 €/día, llave-tarifa);
>    regla exacta de morosidad ("cuarto día hábil"); BAJA+deuda; replicación mínima a
>    Firestore; default config app↔CF; appCliente económico o no.
> 2. **Rules desplegadas OBSOLETAS (01/09 15:18):** no incluyen `clientePuedeAcceder`,
>    `solicitudes/delete` restringido ni `SOLICITUD_BAJA`. Pendiente de desplegar cuando se
>    autorice (hasta entonces, en producción el aviso SOLICITUD_BAJA no se puede crear).
> 3. **Alta Admin PERMISSION_DENIED (Sesión XVIII):** `[DIAG alta]` en `ClienteRemotoRepository`
>    (hipótesis: documento huérfano existente); retirar logging al cerrar.
> 4. **Regenerar sesiones en el negocio actual** y confirmar índice `READY`; retirar logs
>    `ClasesDiagnostico`/`[DIAG sesiones]`.
> 5. Blaze/Storage/Functions siguen pendientes (sin activar).

> ACTUALIZACIÓN 2026-09-01 (FASES D + E LOCAL + SOLICITUDES DE BAJA + CORRECCIÓN BAJA): HEAD sin
> cambios funcionales nuevos del desarrollador (todo el trabajo posterior a
> `244db1e` está en el working tree, SIN commit). Rules **123/123** (119 + 4 de
> bloqueo de BAJA). Detalle en `CONVERSACION_EXPORTADA.md` (Sesiones XII–XX) y en los
> informes de Fase D/E/Solicitudes/BAJA.
>
> **CLOUD FUNCTIONS PREPARADAS EN LOCAL, SIN DESPLEGAR (proyecto en plan Spark):
> `billingEnabled=false` comprobado por API (2026-09-01).** No activar Blaze sin
> decisión explícita. Hasta entonces no se despliega nada (Functions, Storage,
> FCM real).
>
> **Solicitud de baja del cliente (completada):** colección `solicitudes`
> (reutilizada, NO `solicitudes_baja`) con `idSolicitud, negocioId, idCliente,
> firebaseUid, fechaSolicitud, estado (PENDIENTE/ACEPTADA/RECHAZADA), tipo
> (ALTA/BAJA), fechaResolucion, resueltaPor, motivo`. DocumentId determinista
> `baja_{clienteId}_{fechaSolicitud}`. Rules reforzadas (create CLIENTE con
> hasOnly + `esClientePuedeSolicitarBaja` (no BAJA/ARCHIVADO); update solo ADMIN
> desde PENDIENTE y solo campos de resolución). Admin: `SolicitudesScreen` +
> card Home + `SolicitudesViewModel` + `SolicitudRemotoRepository` (acepta con
> Transaction: solicitud ACEPTADA + `clientes/{id}` BAJA + fechaBaja, y actualiza
> Room). appCliente: `CuentaScreen` ("Solicitar baja" + estado) +
> `SolicitudRepository` (sin duplicado PENDIENTE). Al aceptar, si
> `configuracion_notificaciones/{negocioId}.bajaConfirmada.activa` está activa,
> se crea la notificación **BAJA_CONFIRMADA** reutilizando
> `NotificacionRemotoRepository` con ID determinista
> `baja_confirmada_{clienteId}_{fechaBaja}` (el mismo que usará Cloud Functions).
> El FCM real queda para Cloud Functions. PRUEBA 99–108.
>
> **Corrección del flujo de BAJA (completada, auditada):** la auditoría confirmó
> que `CuentaScreen` era **inalcanzable** (nadie navegaba a `Routes.CUENTA`) y que
> un cliente BAJA **podía** leer sesiones y reservar (app y Rules). Corregido:
> (1) appCliente `ConfiguracionScreen` ahora enlaza a **"Mi cuenta"** →
> `Routes.CUENTA`; (2) **Rules**: helper `clientePuedeAcceder` (estado != "BAJA")
> en `sesiones get/list` CLIENTE y `reservaCreaValida` → PRUEBA 109–112, **123/123**;
> (3) appCliente: `SesionesClienteViewModel` no carga sesiones si BAJA,
> `ClasesScreen` muestra el aviso, `HomeScreen` oculta la card "Clases",
> `ReservaRepository.crearReserva` rechaza BAJA; (4) **baja efectiva UNIFICADA**
> en `BajaClienteRemotoRepository` (cancela reservas FUTURAS en Room y Firestore
> liberando plazas, conserva las pasadas y los `serviciosContratados`, y genera
> `BAJA_CONFIRMADA` con ID determinista): la **baja directa**
> (`ClienteViewModel.darDeBaja` + confirmación en `AñadirClienteScreen`) y la
> **aceptación de solicitud** (`SolicitudesViewModel`) convergen en la misma
> lógica. `fechaBaja` coherente en Room/Firestore.
>
> **Fase D — Notificaciones ADMIN (completada):** `GestionNotificacionesScreen`
> (lista `notificaciones/{id}`), `CrearNotificacionScreen` (individual/grupo/
> todos + programadas), `SeleccionarClientesScreen` (selección grupal con filtros
> reutilizando ClienteItem/FilterChipItem/FiltroClientes), `ConfigNotificaciones
> Screen` (morosidad, recordatorio 24h = 0/24, baja confirmada), `DialogoSeleccion
> arClientes` (individual). Modelo `NotificacionAdmin` + `ConfiguracionNotificaciones`
> + `ModoDestino` + `DestinatarioResuelto`. La creación inmediata crea buzones y
> deja `PENDIENTE`; el estado final (ENVIADA/ERROR) y el push los resuelve Cloud
> Functions.
>
> **Fase E — preparación LOCAL (pendiente Blaze):** Cloud Functions 2ª gen en
> `functions/` (`index.js` + `lib/{ids,tokens,firestore,destinatarios,envio,
> procesadores}.js` + `test/`): triggers de notificación inmediata, programadas
> (onSchedule 2 min, índice `notificaciones(estado, fechaProgramada)` pendiente),
> recordatorio de morosidad (onSchedule 1 h, campo `ultimoRecordatorioMorosidad`),
> morosidad (onUpdate clientes con `fechaFinActual`), baja confirmada. Claims
> atómicos PENDIENTE→ENVIADA / PROGRAMADA→ENVIADA, IDs deterministas, lotes de
> ≤500 tokens (`sendEachForMulticast`), tokens inválidos eliminados, respeto de
> `notificacionesActivadas` por dispositivo (appCliente: `DispositivoRepository`
> guarda el campo + `actualizarNotificacionesActivadas`). `storage.rules` local
> ampliada para fotos (`clientes/{clienteId}/foto.jpg`, image/* ≤5MB, ADMIN del
> negocio escribe, CLIENTE solo su propia foto); helper `rutaFotoClienteEnStorage`.
> **Sin deploy. Sin Blaze.**
>
> **Persistencia/Sincronización:** fuente de verdad = Room en Admin (Firestore =
> espejo write-through para clientes/servicios/sesiones/reservas/notificaciones/
> solicitudes). El CLIENTE usa Firestore directo (sin Room). Los MOVIMIENTOS solo
> se replican parcialmente (período `fechaInicioActual`/`fechaFinActual`): **la
> auditoría de economía es el siguiente bloque pendiente** (ver abajo).
>
> **Pendiente actual (2026-09-01):**
> 1. **AUDITORÍA DE ECONOMÍA (siguiente bloque):** verificar el circuito completo
>    cuota→movimiento→pago→morosidad→baja. Preguntas clave: ¿cuál es la fuente de
>    verdad del movimiento (Room/Firestore/ambas)? ¿Se replica lo suficiente para
>    que Cloud Functions calcule la morosidad desde Firestore? ¿Regla real de
>    entrada/salida de MOROSO ("cuarto día hábil")? ¿Qué ocurre con BAJA + deuda
>    pendiente y si hace falta `MOROSO_BAJA`? NO tocar código hasta decidirlo.
> 2. **Reservas del CLIENTE:** verificar que la cancelación está conectada a la UI
>    de appCliente (repositorio existe) y la consistencia atómica reserva+plazas.
> 3. **Blaze (cuando se decida):** activar facturación → crear índice
>    `notificaciones(estado, fechaProgramada)` → `npm install` en `functions/` →
>    desplegar Functions y `storage.rules` → probar FCM real.
> 4. **Habilitar bucket de Storage** (logo y futuras fotos) y probar `storage.rules`.
> 5. **Auditorías finales:** Admin (clientes/servicios/sesiones/reservas/
>    solicitudes/notificaciones), seguridad (aislamiento negocio/cliente),
>    persistencia/sincronización entidad por entidad.
> 6. **Pendientes heredados:** backfill de `indices_clientes` (solo con
>    aprobación), limpieza definitiva de `Clase`/`SesionClase`/`ServicioItem`,
>    retirar logging temporal `[DIAG alta]`/`ClasesDiagnostico`, commits agrupados
>    y limpieza de basura versionada (`build_*.txt`, `firestore-debug.log`).
>
> Histórico (2026-08-31, Sesión XIX) a continuación — se conserva como referencia.
>
> ---

> ACTUALIZACIÓN 2026-08-31 (SESIÓN XIX): HEAD = `244db1e "Conectando las sesiones"` (commit del
> desarrollador; incluye la fase `horaDesdeReserva` completa). Tests de Rules **99/99**. Detalle en
> `CONVERSACION_EXPORTADA.md` (Sesiones XII–XIX).
>
> **ÍNDICES COMPUESTOS CREADOS en `gestorpro-50e83`** (causa raíz de que `sesiones` estuviera VACÍA
> en Firestore y appCliente no viera clases): `sesiones(idServicio, negocioId)`,
> `reservas(clienteId, negocioId)`, `reservas(sesionId, negocioId)`. El emulador no exige índices,
> por eso los tests pasaban mientras la réplica Admin fallaba silenciosamente en producción.
> PENDIENTE: confirmar `READY` y **regenerar las sesiones desde el Admin** para que se repliquen.
>
> El working tree tiene **5 archivos sin commitear** (NO revertir): `PerfilClienteAdministradorScreen`
> (campo "Servicio" en detalle del movimiento), `DetalleServicioScreen` (fix fecha sesión de hoy),
> `EditarSesionScreen` (fix DatePicker UTC), `ProgramarSesionesScreen` (lista de sesiones +
> navegación a edición), `SesionesClienteViewModel` (logs `ClasesDiagnostico` + `esDeHoy()`).
>
> Diagnóstico en curso del **PERMISSION_DENIED en el alta Admin** (Sesión XVIII): el payload y la lógica de `crearClienteRemoto()` son correctos contra las Rules locales (test de aislamiento `firestore-tests/diagnostico_alta_cliente.test.cjs` = 7/7). La causa más probable es que **uno de los 3 documentos del batch ya existe** en Firestore (índice/ficha/privado de un intento anterior), lo que convierte `batch.set()` en `update` y las Rules lo deniegan. Hay logging temporal `[DIAG alta]` en `ClienteRemotoRepository.crearClienteRemoto()` (incluye `existencia previa`) para confirmarlo; retirarlo al cerrar la causa.

Implementado y compilado (`:app` y `:appCliente` BUILD SUCCESSFUL; `:app:compileDebugKotlin` EXITCODE 0; Rules **92/92 OK**):

- **Dos aplicaciones independientes:** `:app` (Admin) y `:appCliente` (Cliente) en el mismo proyecto Gradle, con el mismo Firebase (`gestorpro-50e83`) compartido.
- **Nuevo modelo SERVICIOS/SESIONES/RESERVAS (Fases 1–5C):** `Cliente → Servicio → Sesión → Reserva`, sin entidad Clase en el flujo nuevo.
  - **Room (Fase 1, 2, 3, 5B):** `ServicioEntity`, `SesionEntity`, `serviciosContratados: List<Int>` en `ClienteEntity`, reservas atómicas con `RoomDatabase.withTransaction` y cascadas.
  - **Firestore (Fases 4A, 4B, 5C):** réplica de `servicios/{id}`, `sesiones/{id}` y `reservas/{clienteId}_{sesionId}` con write-through; Transaction atómica reservar/cancelar (plazas±1). `negocioId` remoto = UID del ADMIN.
- **Sync `serviciosContratados` Admin → Firestore (Fase 6, 28-29/08):** `ClienteRemotoRepository.actualizarServiciosContratadosRemoto(idCliente, ids)` (write-through ints); `ClienteViewModel` con reintento manual (`reintentarSincronizacion`) y bandera de pendiente; `PerfilClienteAdministradorScreen` muestra banner + botón reintentar. `appCliente` `Cliente.kt` pasa a `List<Int>`; parser en `ClienteRepository` y `VinculacionRepository` corregidos. **PRUEBA 9B** (ADMIN update serviciosContratados) → 77/77.
- **Pantalla "Clases de hoy" del Cliente (Fase 7, 28-29/08):** modelo nuevo en `appCliente` (`model/Servicio.kt`, `model/Sesion.kt`), `data/firebase/SesionRepository.kt` (`obtenerServicioActivo`, `obtenerSesionesPorServicio`), `ui/viewmodel/SesionesClienteViewModel.kt` (filtra `fecha == inicioDeHoy()`, orden por hora, estados noVinculado/cargando/error/sinServicios/sinSesionesHoy) y `ui/home/ClasesScreen.kt` funcional. **Reservar/ver/cancelar reservas del CLIENTE aún NO implementado.**
- **Cascadas administrativas de reservas (Fase 8, 28-29/08):** el borrado masivo `batch.delete` anterior fallaba en Rules (`reservaEliminadaValida` exige plazas+1). Sustituido por `runTransaction` por sesión con reintento de query fresca (3 intentos, `MAX_RESERVAS_POR_SESION = 498`), idempotente.
  - **Fase 1 (Android):** `ReservaRemotoRepository.kt` (`eliminarSesionConReservasRemoto`, `eliminarSesionesFuturasConReservasRemoto`, `eliminarTodasLasSesionesConReservasRemoto`); `ServicioViewModel.replicarDesactivacionRemota`/`replicarEliminacionRemota` y `SesionViewModel.eliminarSesion`/`generarSesiones` las usan. `:app:assembleDebug` BUILD SUCCESSFUL.
  - **Fase 2 (Rules + tests):** helper `cascadaEliminaSesion(sesionId)` y rama OR en `reservas/delete` ADMIN. **PRUEBA 77-81** → 82/82 OK. Rules desplegadas verificadas idénticas al local.
- **Diagnóstico de Rules en producción (28-29/08):** se descubrió que el ruleset desplegado **no contenía `match /servicios/{servicioId}`** (Rules antiguas), causa de `PERMISSION_DENIED` en alta/baja de servicios. Tras redeploy, el ruleset coincide con el local. **Aclaración de UID:** el admin real en producción es `aSiZI8YWlLYOWhj2TXlznZWJP5O2` (minúscula `l` en posición 9 y 18: `WlLYO` y `TXlzn`); el `negocioId` de los servicios (`aSiZI8YWlLYOWhj2TXlznZWJP5O2`) es coherente con ese UID. La cadena `aSiZI8YWILYOWhj2TXIznZWJP5O2` (mayúscula `I`) que venía manejándose era un **typo I/l** del humano, no el UID real.
- **Resto validado:** flujo CLIENTE sin vínculo/VÍA 1/VÍA 2, sync nombre de negocio, logo con Storage (pendiente bucket), dos apps.
- **Sincronización de períodos Admin → Firestore (30/08):** `MovimientoRepository` persiste primero en Room, recalcula `fechaInicioActual`/`fechaFinActual` desde los movimientos persistidos y replica el período después de insertar, actualizar o eliminar. Los errores quedan pendientes para reintento manual y `ClienteRemotoRepository` registra la operación y el código de error Firebase. El alta Admin asigna `fechaAlta` a clientes creados como `ACTIVO` y `ClienteViewModel` solo confirma el alta/edición cuando la réplica remota termina correctamente.
- **Vía A código maestro + DNI (30/08):** corregida la regresión del commit `653f117de71a169dcb9f2f75e2dcdf6b6d4c44f5`. `VinculacionRepository` busca exactamente `indices_clientes/{negocioId}_{dni}`; si no existe devuelve `No existe ningún cliente registrado con ese DNI.` y no llama a `crearFicha()`. La vinculación de una ficha existente mantiene la Transaction de `clientes/{idCliente}` + `usuarios/{uid}`. El código de Vía 2 permanece conservado, pero no se ejecuta desde esta entrada Vía A. Añadidas PRUEBA 6B, PRUEBA 6C y test unitario de rechazo.
- **Card de estado del Home Cliente (validado 30/08):** no modificar. Un cliente `ACTIVO` sin movimientos muestra el estado sin fecha; después de crear un movimiento, `fechaInicioActual`/`fechaFinActual` llegan desde Firestore y el card muestra correctamente la fecha de fin del período. La lógica actual queda validada manualmente.
- **`ClaseEntity`/`SesionClaseEntity` y su UI/DAOs/repositorios/ViewModel siguen TRANSITORIOS** (Fase 5B desconectados); NO eliminar sin tarea específica.
- **`horaDesdeReserva` (Sesión XIX, Fase 3/3.1):** campo `String? = null` en `SesionEntity`/`Sesion`/`sesiones/{id}`; Room v12 con `MIGRACION_11_12`; Admin (ProgramarSesionesScreen por día + EditarSesionScreen con TimePicker); appCliente (`reservable`/`aperturaAlcanzada` + botón deshabilitado "Reservas abren a las HH:mm"); Rules `sesiones/create`/`sesiones/update` + capa C en `reservaCreaValida` (`reservaAbierta`/`minutosReserva` con `split(":")` + `int(...)`). `null` = reservas abiertas desde el inicio del día. Tests 99/99.

Pendiente para continuar:

0. **ÍNDICES DE `sesiones`/`reservas` en producción (Sesión XIX):** creados vía API REST en `gestorpro-50e83` (`sesiones(idServicio, negocioId)`, `reservas(clienteId, negocioId)`, `reservas(sesionId, negocioId)`). Confirmar `READY` en consola y **regenerar las sesiones desde el Admin** (Gestionar sesiones → Generar sesiones) para que se repliquen a Firestore. Tras esto, appCliente debe mostrar las clases. Retirar los logs `ClasesDiagnostico` de `SesionesClienteViewModel` al confirmarlo.
1. **PERMISSION_DENIED en alta Admin — EN DIAGNÓSTICO (Sesión XVIII):** el payload de `crearClienteRemoto()` pasa las Rules locales (test `diagnostico_alta_cliente.test.cjs` 7/7). La hipótesis principal es que uno de los 3 documentos del batch (`clientes/{id}`, `indices_clientes/{negocioId}_{dni}`, `clientes_privados/{id}`) **ya existe** en Firestore de un intento anterior, convirtiendo el `batch.set()` en `update` que las Rules deniegan. Siguiente paso: reproducir el alta con el logging temporal `[DIAG alta]` (incluye `existencia previa -> ...=true`) para confirmar cuál documento existe; después limpiar el documento huérfano (con aprobación) o ajustar la réplica, y retirar el logging temporal.
2. **Diagnóstico PERMISSION_DENIED en baja/eliminación de servicios — RESUELTO a nivel de Rules (Sesión XIII):** la causa era que las queries administrativas de cascada (`reservas` por `sesionId`, `sesiones` por `idServicio`) no incluían `negocioId`, así las reglas `sesiones/list` y `reservas/list` las negaban (rules-are-not-filters). Se corrigió en `ReservaRemotoRepository`/`SesionRemotoRepository` (filtro `negocioId` + fail-closed si la sesión no existe pero tiene reservas) y se añadieron 8 pruebas de regresión (PRUEBA 33A–33H). Tests **90/90**, `:app:assembleDebug` BUILD SUCCESSFUL. **Riesgo abierto:** el crash de la app en alta/reactivación no se aisló (no se aportó stacktrace/logcat); conviene validar en dispositivo con el build corregido y, si persiste, capturar el logcat.
3. **Reservas del CLIENTE (implementadas en commits previos a Sesión XIX, pendientes de validación real):** `Reserva` model + `ReservaRepository`/`ReservasClienteViewModel`/`MisReservasScreen` (Transaction `reservas/{clienteId}_{sesionId}`), reservar/cancelar en `ClasesScreen`. Requieren que `sesiones`/`reservas` tengan índices compuestos (creados en Sesión XIX) y que las sesiones se repliquen.
4. **Habilitar el bucket de Storage** y desplegar `storage.rules` (hasta entonces el logo falla).
5. **Backfill de `indices_clientes`** (DRY-RUN: 2 índices). NO ejecutar sin aprobación. Relacionado con el pendiente 1: un índice huérfano podría ser el que provoca el PERMISSION_DENIED del alta.
6. **Limpieza definitiva de `Clase`/`SesionClase`** y de `ServicioItem` (sin uso).
7. **Commits pendientes** (5 archivos del working tree de la Sesión XIX: PerfilClienteAdministradorScreen, DetalleServicioScreen, EditarSesionScreen, ProgramarSesionesScreen, SesionesClienteViewModel) y limpieza de basura versionada (`build_*.txt`, `firestore-tests/firestore-debug.log`).
8. Pendientes heredados: crear negocio con `PERMISSION_DENIED` (hipótesis token) y validar `rol == "ADMIN"` en el login de Admin (hoy solo exige doc existente + activo).

---

# HOJA DE RUTA DEL PROYECTO (2026-09-01)

> Documento vivo. Reconstrucción del estado funcional REAL a partir de arquitectura,
> entidades, DAOs, repositorios, ViewModels, pantallas, Rules, Functions locales y
> tests (no es una búsqueda de TODO/FIXME). Úsalo para tachar funcionalidad por
> funcionalidad sin reinventar ni tocar lo ya terminado. Actualizar al cerrar cada
> bloque.

## 1. Ya implementado y verificado (funciona y compila)

- Dos aplicaciones independientes (`:app` Admin, `:appCliente` Cliente) sobre el mismo Firebase (`gestorpro-50e83`).
- Autenticación real (Firebase Auth) en ambas; roles remotos `ADMIN`/`CLIENTE`.
- Vinculación CLIENTE por **código maestro + DNI** (VÍA 1 y VÍA 2) con `indices_clientes`, `perfiles_pendientes`, `clientes_privados`; sin Vía B/deep links.
- Negocio: crear/editar nombre y código maestro; logo (pendiente bucket).
- **Clientes Admin:** alta, edición, búsqueda, filtros (Todos/Activos/Bajas/Morosos/Archivados), archivar/restaurar, servicios contratados (Room + Firestore write-through), foto local, banners de sincronización con reintento.
- **Servicios/Sesiones/Reservas:** modelo `Cliente → Servicio → Sesión → Reserva`; Room atómico (`withTransaction`, plazas±1, cascadas) + réplica Firestore write-through; generación de sesiones con apertura global y edición individual; índices compuestos creados en producción.
- **Economía base:** movimientos **multi-servicio** con `precioFinal`, `estado` (PENDIENTE/PAGADO), `fechaPago` y `metodoPago` en Room (Fases 1–5 del modelo definitivo); `fechaInicioActual`/`fechaFinActual` replicados a `clientes/{id}`; morosidad persistida como flag (`moroso`/`fechaEntradaMorosidad`) recalculada por `MovimientoMorosidad`; `EconomiaScreen` (resumen + CRUD de gastos + lectura de movimientos); movimientos por cliente (crear/editar/eliminar/renovar). Ver "Modelo económico definitivo".
- **Notificaciones (Fases B/C/D):** Admin (lista, crear individual/grupo/todos/programadas, configuración de preconfiguradas, selección grupal), bandeja del cliente, leído/no leído, toggle por dispositivo, buzón `notificaciones_por_destinatario`.
- **Solicitudes de baja:** flujo completo Cliente → PENDIENTE → Admin (Aceptar=BAJA / Rechazar) + notificación `BAJA_CONFIRMADA` (si config activa).
- **Baja de cliente (corregida):** navegación a `CuentaScreen` ("Mi cuenta" desde Configuración); bloqueo de BAJA en app (SesionesCliente/ReservaRepository/Home) y en Rules (`clientePuedeAcceder` en sesiones get/list y `reservaCreaValida`); baja directa y aceptación de solicitud convergen en `BajaClienteRemotoRepository` (cancelación de reservas futuras Room+Firestore liberando plazas, conservación de pasadas y `serviciosContratados`, BAJA_CONFIRMADA con ID determinista).
- **Rules Firestore+Storage:** **123/123** (`npm --prefix firestore-tests test`).
- Tests helpers de Functions: `node --test functions/test/ids.test.js functions/test/tokens.test.js` → **13/13**.

## 2. Implementado pero pendiente de pruebas reales (dispositivo / producción)

- **Reservas del CLIENTE:** `ReservaRepository` (Transactions atómicas) y `ReservasClienteViewModel` cableados en `ClasesScreen` (reservar/cancelar con confirmación). Requieren índices `READY` y sesiones replicadas. **No hay pantalla "Mis reservas"** (`reservasVisibles`/`esProxima()` sin consumidor).
- **Réplica Room→Firestore de sesiones:** conectada (`sincronizarSesionesGeneradas`), pero hubo fallo silencioso por `idSesion=0` (fix local en working tree); validar regenerando sesiones y confirmando documentos en Firestore.
- **Alta Admin con `[DIAG alta]`:** pendiente de confirmar la causa (hipótesis: documento huérfano ya existente en el batch); retirar logging al cerrar.
- **Logo Storage:** implementado (`NegocioRepository.guardarLogoRemoto`), bloqueado por bucket (ver §5).

## 3. Implementado parcialmente

- **ECONOMÍA (modelo DEFINIDO — ver «Modelo económico definitivo»; la implementación Room en Fases 1–5 está cerrada y la réplica remota de movimientos/resumen es tarea de implementación pendiente):**
  - No existe entidad Pago/Cuota: el "pago" es `Movimiento.estado` (PENDIENTE/PAGADO) + `fechaPago` + `metodoPago`.
  - Movimientos multi-servicio: `servicios: List<Int>` + `precioFinal` + estado + fechaPago + metodoPago (EFECTIVO/BIZUM/TRANSFERENCIA). El `fechaPago` se conserva al editar (resuelto con `MovimientoPago`; validar en dispositivo).
  - Creación/edición/eliminación **manuales por el ADMIN** (eliminación con confirmación). La "Renovar" gestiona los casos actuales. **No** se generan movimientos automáticamente.
  - Morosidad = flag independiente persistido en Room (`moroso`/`fechaEntradaMorosidad`) con motor único `MovimientoMorosidad`: **un PENDIENTE ya es deuda y genera morosidad**; un ACTIVO pasa a moroso el **día siguiente a su `fechaFin`** si sigue sin cobertura. `EstadoCliente.MOROSO` no se persiste.
  - Deuda = suma de los movimientos PENDIENTES.
  - Firestore (decisión cerrada): espejo `movimientos/{id}` + resumen remoto en `clientes/{id}` (`moroso`, `deuda`, `fechaEntradaMorosidad`, `fechaInicioActual`, `fechaFinActual`). **Implementación pendiente en el árbol actual** (ver CONTEXTO_PROYECTO.md §8.5): hoy solo se replica el período.
  - `EconomiaScreen` es solo lectura para movimientos (gestión en el perfil del cliente).
- **Reservas ADMIN:** `SesionReservasScreen` es solo lectura; el ADMIN no puede cancelar la reserva de un cliente (solo cascadas de servicios/sesiones).
- **Sesiones:** `eliminarSesion` (ViewModel + remoto) existe pero **sin botón en la UI** (`EditarSesionScreen` solo guarda cambios).
- **Clientes:** `eliminarCliente` (Room) sin botón en UI (baja lógica por diseño). "Dar de baja" directo solo vía switch de edición o aceptar solicitud. Reactivación `BAJA→ACTIVO` solo vía switch (no hay método dedicado).
- **Cuenta Admin:** el diálogo "Cambiar contraseña" es un **placeholder** (no llama a `FirebaseAuth.updatePassword`).
- **appCliente economía:** solo indicador de estado en Home (`PAGO_VENCIDO` derivado de `fechaFinActual`); **sin** módulo de cuotas/movimientos/pagos (**decisión cerrada: el CLIENTE no tendrá módulo económico**).
- **Persistencia/sincronización:** entidad por entidad hay diferencias (ver §10): cliente/servicio/sesión/reserva write-through; movimiento parcial (decisión: replicar a `movimientos/{id}` + resumen; implementación pendiente); gasto solo Room; solicitud antigua Room inerte + nueva Firestore; notificación/dispositivo Firestore.

## 4. Pendiente de implementar (funcional)

- Implementar la réplica económica ya decidida (ver «Modelo económico definitivo»): `movimientos/{id}` + resumen remoto en `clientes/{id}`; decidir en una futura sesión qué automatizar con Cloud Functions.
- Pantalla **"Mis reservas"** del CLIENTE (si se aprueba).
- **Cancelar reserva desde ADMIN** (`SesionReservasScreen`).
- Botón **eliminar sesión** en `EditarSesionScreen`.
- **Cambiar contraseña real** en Cuenta Admin.
- ~~Módulo económico del CLIENTE~~: **descartado** (decisión cerrada: el CLIENTE no tendrá módulo económico).
- Auditorías finales: seguridad (aislamiento negocio/cliente) y persistencia/sincronización entidad por entidad.

## 5. Bloqueado por Blaze/Firebase (infraestructura real)

- **Cloud Functions 2ª gen** (código local listo en `functions/`): inmediatas, programadas (onSchedule 2 min), recordatorio morosidad (1 h), morosidad, baja confirmada. Requiere: Blaze → índice `notificaciones(estado ASC, fechaProgramada ASC)` → `npm install` en `functions/` → `firebase deploy --only functions`. ⚠️ Los `onSchedule` (2 min / 1 h) son **preparación provisional SIN desplegar**; quedan desactualizados frente a las decisiones (precisión ~15 min para programadas; comprobación de morosidad ~diaria ~08:00) y se rediseñarán en la sesión de diseño según el bloque «Decisiones de producto — Notificaciones, Cloud Functions y Storage» (2026-09-03).
- **FCM real** (push a dispositivo).
- **Firebase Storage:** bucket + `storage.rules` (logo; fotos de clientes preparadas en rules locales, sin desplegar). ⚠️ Las reglas de fotos preparadas usan **5 MB como referencia desactualizada**: el tamaño máximo decidido es **10 MB** con compresión/redimensionado automático en la app (bloque C/§D del documento de decisiones); se alinearán al implementar.
- **Backfill de `indices_clientes`** (solo con aprobación; DRY-RUN: 2 índices).
- ~~(Opcional) migración de fotos locales → Storage~~ — decisión 2026-09-03: las fotos de prueba locales **NO se migran**; en producción las fotos remotas (Storage) son la referencia compartida (ver bloque «Decisiones de producto — Notificaciones, Cloud Functions y Storage»).

## 6. Decisiones pendientes (el desarrollador debe decidir ANTES de programar)

1. **Economía:** **DECIDIDA** — ver «Modelo económico definitivo» (fuente de verdad Room + réplica Firestore `movimientos/{id}`, pago = `estado + fechaPago + metodoPago` sin entidad Pago, deuda = suma de PENDIENTES, morosidad si hay deuda o si el ACTIVO supera su período sin cobertura, BAJA + deuda válido, eliminación con confirmación en Room + Firestore, appCliente sin economía). Solo queda implementar la réplica remota y decidir en una sesión futura qué automatizar con Cloud Functions.
2. ~~BAJA + deuda~~: **decidido** — la BAJA no elimina la deuda (los PENDIENTES se gestionan y pueden marcarse PAGADO); no se necesita `MOROSO_BAJA`.
3. ~~appCliente economía~~: **decidido** — el CLIENTE no tendrá módulo económico.
4. **Reservas:** ¿pantalla "Mis reservas"? ¿el ADMIN puede cancelar reservas desde `SesionReservasScreen`?
5. **Sesiones:** ¿botón eliminar sesión en la UI?
6. ~~**Fotos:** ¿migrar a Storage cuando haya bucket?~~ → **DECIDIDO** (2026-09-03, bloque «Decisiones de producto — Notificaciones, Cloud Functions y Storage»): fotos remotas compartidas ADMIN↔CLIENTE; sin migración de las fotos de prueba; máximo **10 MB** por foto con compresión/redimensionado automático en la app. Pendiente: implementación de compresión y Storage Rules (bloque D del documento de decisiones).
7. **Backfill de índices** (aprobación explícita).

## 7. Orden recomendado de implementación

1. **ECONOMÍA:** decisiones ya tomadas (ver «Modelo económico definitivo») → cerrar la implementación de la réplica remota (`movimientos/{id}` + resumen en `clientes/{id}`) con Rules/tests; en sesión futura decidir qué automatizar con Cloud Functions.
2. **Reservas del CLIENTE:** validar índices/sesiones, pantalla "Mis reservas" (si aprobada), retirar logs `ClasesDiagnostico`.
3. **Cierres menores Admin:** eliminar sesión, cancelar reserva admin, cambiar contraseña, reactivar desde BAJA.
4. **Auditorías:** seguridad + persistencia/sincronización entidad por entidad + completitud funcional Admin.
5. **Blaze:** activar facturación → índice programadas → `npm install` → deploy Functions + `storage.rules` → FCM real → Storage (logo/fotos).
6. **Pruebas finales integradas** (admin+cliente+varios dispositivos, estados, morosidad, baja, reservas, notificaciones).

## 8. Dependencias entre funcionalidades

- Morosidad/recordatorio (Functions) dependen de datos en Firestore. **Decisión cerrada (ver «Modelo económico definitivo»):** replicar `movimientos/{id}` + resumen remoto en `clientes/{id}` (`moroso`, `deuda`, `fechaEntradaMorosidad`, fechas de período) para que Functions puedan calcular la situación sin app Admin; implementación pendiente.
- `BAJA_CONFIRMADA` depende de: config `bajaConfirmada.activa` + `clientes/{id}.estado == "BAJA"` (ya conectado por solicitud y preparado en Functions).
- Notificaciones programadas dependen del índice `notificaciones(estado, fechaProgramada)` + Blaze.
- Reservas del CLIENTE dependen de índices `sesiones`/`reservas` READY + sesiones replicadas.
- Solicitud de baja → BAJA → BAJA_CONFIRMADA ya conectado (ID determinista compartido con Functions).
- Fotos en Storage dependen del bucket; la app sigue funcionando con fotos locales hasta entonces.

## 9. Tests existentes y qué cubren

- **Rules Firestore+Storage — 123/123** (`npm --prefix firestore-tests test`):
  - PRUEBA 1–18: clientes, permisos, VÍA 1/VÍA 2, índices, `perfiles_pendientes`, `clientes_privados`, `negocios_publicos`, cambio de DNI.
  - PRUEBA 19–20: Storage logo + `negocios_publicos` logo.
  - PRUEBA 21–33: servicios (CRUD + aislamiento negocio).
  - PRUEBA 33A–33H: queries admin con `negocioId` (sesiones/reservas) + cascadas.
  - PRUEBA 34–53: sesiones (CRUD, servicio activo/contratado, apertura).
  - PRUEBA 54–76: reservas (crear/cancelar, plazas±1, transacciones atómicas, duplicado, sesión llena).
  - PRUEBA 77–81: cascadas administrativas de reservas/sesiones.
  - PRUEBA 82–88: `horaDesdeReserva`.
  - PRUEBA 89–98: notificaciones, `configuracion_notificaciones`, buzón, token FCM por dispositivo.
  - PRUEBA 99–108: solicitudes de baja (create/get/list/aceptar/rechazar, aislamiento, datos inválidos).
  - PRUEBA 109–112: bloqueo de BAJA (no lee/no lista sesiones, no crea reserva; ACTIVO sigue permitido).
- **Functions helpers:** `functions/test/ids.test.js` + `tokens.test.js` (13/13) — IDs deterministas y clasificación de tokens/lotes.
- **Unit appCliente:** rechazo de VÍA A cuando no existe el índice `negocioId_DNI`.
- **Diagnóstico (temporal):** `firestore-tests/diagnostico_alta_cliente.test.cjs` (7/7).
- Los tests de Android funcionales se reservan para la fase final (decisión del proyecto).

## 10. Problemas técnicos conocidos

- Editar un movimiento reseteaba `fechaPago` a `null` — **resuelto en código** con `MovimientoPago` (Fase 4); falta validar en dispositivo.
- Rules no pueden hacer cross-document query → el duplicado de solicitud PENDIENTE se controla en el repositorio (capa de negocio), no solo en la UI.
- Réplica de sesiones Room→Firestore estuvo rota por `idSesion=0` (fix en working tree; validar).
- Alta Admin `PERMISSION_DENIED` en diagnóstico (posible documento huérfano en el batch; logging `[DIAG alta]`).
- Crear negocio con `PERMISSION_DENIED` (hipótesis token) — histórico sin resolver.
- Login Admin no valida `rol == "ADMIN"` (solo exige doc existente + activo).
- `EstadoCliente.MOROSO` nunca se persiste; appCliente lo rechaza si llegara remoto.
- `Clase`/`SesionClase`/`ServicioItem` y las rutas legadas `ui/clases` siguen TRANSITORIOS (no eliminar sin tarea específica).
- `TipoSolicitud` (Room) sigue con `CLASE`/`BAJA` (deuda: adaptar a `ALTA`/`BAJA` cuando se decida sobre la tabla `solicitud` antigua, inerte).
- Fotos locales (`filesDir/fotos`) hasta decidir migración a Storage.
- Basura versionada: `firestore-tests/firestore-debug.log` (emulador) y posibles `build_*.txt`.
