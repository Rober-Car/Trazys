# CONTEXTO_PROYECTO.md — Documento de traspaso a nueva IA

> **🟢 ACTUALIZACIÓN 2026-09-11 — RESERVAS (FASES 1–4) + MOROSIDAD AUTOMÁTICA + LOCALIZACIÓN DE NOTIFICACIONES + DEPLOYS (estado vigente):**
> verificado contra el árbol real. **HEAD del desarrollador: `7cf9a7d "Nueva funcionalidad horario"` (rama
> `master`).** El horario (bloque 2026-09-10 II) quedó **commiteado** en `e11ebe4`/`7cf9a7d`. El working
> tree conserva **SIN commit** todo lo de esta tanda (**NO revertir**; ~72 archivos). Este bloque SUSTITUYE
> a la actualización 2026-09-10 (II), que queda como histórica.
> - **RESERVAS DEL CLIENTE (Fases 1–4):** callables `reservar`/`cancelarReserva` (Admin SDK) que escriben
>   `reservas/{clienteId}_{sesionId}`, `sesiones/{id}.asistentes.{clienteId}=nombre` y la agenda derivada
>   `clientes/{clienteId}/agenda/{fecha}`; respetan `servicios/{id}.permiteCombinarDia` (default true).
>   `appCliente` usa las callables y tiene `AsistentesSesionScreen` independiente; el ADMIN añade
>   `permiteCombinarDia` (**Room v19**) y cascadas de agenda. Rules de FASE 3/4 (agenda + cierre del
>   acceso directo del CLIENTE). **Desplegadas** `reservar`/`cancelarReserva`.
> - **NOTIFICACIONES AUTOMÁTICAS DE MOROSIDAD:** `entradaMorosidad` y `recordatorioMorosidad` son ahora
>   **`onSchedule` diario 08:00 Europe/Madrid** (antes `onDocumentUpdated`, incapaz de detectar el paso del
>   tiempo). Regla **definitiva**: notificar si `clientes/{id}.estado == "ACTIVO"` y `fechaFinActual < ahora`
>   y `exentoMorosidad != true` y `configuracion_notificaciones.morosidad.activa == true`; **no** se
>   inspeccionan movimientos (PAGADO/PENDIENTE). Módulos puros `plan_morosidad.js` e `idempotencia.js`.
>   `crearYEnviarAutomatica` es idempotente (crear/reanudar/omitir + claim PENDIENTE→ENVIADA) con IDs
>   deterministas. Índice `clientes(estado ASC, fechaFinActual ASC)`. **Desplegadas** ambas Functions + índice.
> - **LOCALIZACIÓN ES/EN + DATA-ONLY (solo morosidad):** `notificaciones/{id}` y buzón incluyen `titulo`
>   (ES), `tituloEn` (EN) y `subtipo` (`ENTRADA`/`RECORDATORIO`); se mantiene `tipo = "MOROSIDAD"`. El
>   Cliente (`IdiomaAplicacion.textoLocalizado`, `ListaNotificacionesScreen`, `FcmService`) muestra el
>   título según el idioma. Las de morosidad viajan **data-only** (`plan_envio.js`, `android.priority:"high"`,
>   texto en `data`) para localizar también en segundo plano; el resto conserva `notification`. Compatible
>   con notificaciones antiguas sin `tituloEn`/`subtipo`.
> - **FIX de Rules en `configuracion_notificaciones`:** la regla `get` usa el ID del documento
>   (`configId == usuarioActual().negocioId`) para permitir leer un documento inexistente (el repositorio
>   hace `get()` antes de crear/actualizar). **Deploy** solo `firestore:rules` → ruleset activo
>   `49e46140-2c39-4121-bf1a-9f2ed06ac3a8` (updateTime `2026-09-11T09:52:17Z`), idéntico al local.
> - **OTROS:** ayuda contextual `AyudaContextual` (ⓘ) en Admin y Cliente; reorganización visual de las
>   pantallas de horario en secciones; nomenclatura Admin "negocio/gimnasio" → "centro" (cards Centro/Rutinas).
> - **Estado de producción:** Functions desplegadas = `reservar`, `cancelarReserva`, `eliminarMiCuenta`,
>   `notificacionInmediata`, `entradaMorosidad` (scheduled), `recordatorioMorosidad` (scheduled)
>   (`europe-west1`, nodejs20). Rules `49e46140`. Índices: los 4 previos + `clientes(estado,fechaFinActual)`,
>   todos **READY**. **Las Functions de morosidad se desplegaron a las 10:16Z del 11/09, después de las 08:00
>   Madrid → no se ejecutaron ese día; la 1ª ejecución real es el día siguiente (06:00Z).**
> - **Verificación:** Functions puras **66/66**; Rules **211/211**; `:app`/`:appCliente` `testDebugUnitTest`
>   + `assembleDebug` OK; `git diff --check` limpio salvo avisos CRLF y el log del emulador.
> - **Pendiente:** probar reserva/cancelación y morosidad en dispositivo (forzar el job de Scheduler),
>   commit agrupado del working tree, y pendientes heredados (logs de diagnóstico,
>   `fallbackToDestructiveMigration`, Storage/bucket, Node 20 / `firebase-functions`, `.gitignore`).

> **🟢 ACTUALIZACIÓN 2026-09-10 (II) — HORARIO MULTI-TRAMO + "DÍAS ESPECIALES" + RULES DESPLEGADAS (estado vigente):**
> verificado contra el árbol real. **HEAD del desarrollador: `75d0eb2 "otros"` (rama `master`).** El working
> tree conserva **SIN commit** los cambios (**NO revertir**; ver CHECKPOINT superior de AGENTS.md y `git status`).
> Este bloque SUSTITUYE a la actualización 2026-09-10 (I), que queda como histórica.
> - **HORARIO (independiente de sesiones):** en `negocios_publicos/{negocioId}` → `horarioCentro`
>   (semanal con **varios tramos por día**; lista vacía = cerrado), `horarioActividades` (`idServicio`+hora) y
>   `horarioExcepciones` (fechas concretas con **varios tramos**; prioridad sobre el semanal). No se guarda en `negocios`.
>   El parseo admite también el formato antiguo (un tramo con `cerrado`), sin pérdida de datos.
> - **ADMIN:** Ajustes → NEGOCIO agrupa "Mi negocio" + "Horario del centro" + "Horario de actividades"
>   (rutas `HORARIO_CENTRO`/`HORARIO_ACTIVIDADES`). Centro: aplicar a toda la semana + edición por día
>   (añadir/editar/eliminar tramos) + validación (apertura<cierre, sin solapes) + **"Días especiales"**
>   (terminología visible; antes "Excepciones", funcionalidad idéntica). Actividades: día (scroll horizontal) +
>   chips de actividades + hora + CRUD (guarda `idServicio`).
> - **CLIENTE:** Home con **exactamente 6 cards** en 3 filas: Reservas, Rutinas, Horario del centro,
>   Actividades, Ajustes, Notificaciones. Dos pantallas SEPARADAS: `HorarioCentroClienteScreen` y
>   `ActividadesClienteScreen` (sin imágenes). En el centro, la configuración especial de la fecha se muestra
>   directamente ("Cerrado" o tramos), sin mencionar "excepción". Card "Actividades" anterior → **"Reservas"** (solo texto).
> - **Notificación `CAMBIO_HORARIO`:** switch en `configuracion_notificaciones`; al guardar el horario del
>   CENTRO se crea `notificaciones` tipo `CAMBIO_HORARIO` (TODOS) si está activo. Actividades NO notifican.
> - **Persistencia rotación** (`rememberSaveable` + savers) y **estilo azul corporativo** `#1E88E5`.
> - **FIX permisos:** `guardarHorario` escribe **solo** en `negocios_publicos` (antes también `negocios`).
> - **Rules locales 204/204**; `:app` (190 tests) y `:appCliente` (33 tests) compile/assemble/test OK;
>   `git diff --check` limpio salvo el log del emulador.
> - **✅ DEPLOY HECHO:** `firebase.json` ya incluye `"firestore": { "rules": "firestore.rules" }`; ruleset
>   desplegado en `gestorpro-50e83` = `078c7b83-528b-48e6-8183-ede6d4a5dc29` (updateTime `2026-09-10T15:15:05Z`),
>   verificado por API (SHA-256 remoto == local). Los bloques inferiores son históricos.

> **🗄️ ACTUALIZACIÓN 2026-09-10 (I) — FUNCIONALIDAD HORARIO (modelo antiguo) + DEPLOY PENDIENTE (HISTÓRICO):**
> verificado contra el árbol real. **HEAD del desarrollador: `75d0eb2 "otros"` (rama `master`).** El working
> tree conserva **SIN commit** 25 cambios (**NO revertir**; ver CHECKPOINT superior de AGENTS.md y `git status`).
> - **HORARIO (nuevo, independiente de sesiones):** en `negocios_publicos/{negocioId}` → `horarioCentro`
>   (semanal), `horarioActividades` (`idServicio`+hora) y `horarioExcepciones` (fechas concretas, con prioridad
>   sobre el semanal). No se guarda en `negocios`.
> - **ADMIN:** Ajustes → NEGOCIO agrupa "Mi negocio" + "Horario del centro" + "Horario de actividades"
>   (rutas `HORARIO_CENTRO`/`HORARIO_ACTIVIDADES`). Centro: aplicar a toda la semana + edición individual +
>   excepciones. Actividades: día (scroll horizontal) + chips de actividades + hora + CRUD (guarda `idServicio`).
> - **CLIENTE:** card "Horario" en Home (solo vinculados+activos) → `HorarioScreen` (centro + actividades, sin
>   imágenes). Card "Actividades" renombrado a **"Reservas"** (solo texto).
> - **Notificación `CAMBIO_HORARIO`:** switch en `configuracion_notificaciones`; al guardar el horario del
>   CENTRO se crea `notificaciones` tipo `CAMBIO_HORARIO` (TODOS) si está activo. Actividades NO notifican.
> - **Persistencia rotación** (`rememberSaveable` + savers) y **estilo azul corporativo** `#1E88E5`.
> - **FIX permisos:** `guardarHorario` escribe **solo** en `negocios_publicos` (antes también `negocios`, cuya
>   regla exige `adminUid` → "No tienes permisos").
> - **Rules locales 204/204**; `:app`/`:appCliente` compile/assemble/test OK; `git diff --check` limpio.
> - **⚠️ DEPLOY PENDIENTE:** el ruleset desplegado (`17c46034-…`, 2026-09-09) no incluye horario y
>   `firebase.json` **no tiene sección `firestore`** → `firebase deploy --only firestore:rules` falla con
>   "No targets…". Añadir `"firestore": { "rules": "firestore.rules" }` antes de desplegar (el diff es solo
>   HORARIO, aditivo → seguro desplegar únicamente rules). Los bloques inferiores son históricos.

> **🔴 ACTUALIZACIÓN 2026-09-09 — SISTEMA DE RESERVAS + ASISTENTES (estado vigente):** verificado contra
> el árbol real. **HEAD del desarrollador: `e31e701`** (i18n, en `origin/master`). El working tree
> conserva SIN commit el sistema de reservas (FASES 1–4) y sus correcciones (**NO revertir**; ver
> CHECKPOINT superior de AGENTS.md). En producción `gestorpro-50e83` están desplegadas las callable
> `reservar`/`cancelarReserva` (europe-west1, v2) y el ruleset de Firestore de FASE 4.
> - **Backend (Functions):** las reservas del CLIENTE ya NO las crea/borra la app por Rules: lo hacen las
>   callable `reservar`/`cancelarReserva` (Admin SDK). La callable también mantiene `sesiones/{id}
>   .asistentes` (map clienteId→nombre, solo nombre) y la agenda derivada `clientes/{clienteId}/agenda/
>   {fecha}` = `{negocioId, fecha, sesiones:{sesionId:idServicio}}`. Regla de negocio `permiteCombinarDia`
>   en `servicios/{id}` (default true si falta).
> - **Rules (FASE 4):** el CLIENTE puede leer reservas/sesiones/servicios y su propia agenda, pero NO
>   escribir reservas ni modificar plazas/asistentes/agenda. El ADMIN gestiona sus sesiones/servicios,
>   la agenda de sus clientes y `permiteCombinarDia`.
> - **`permiteCombinarDia` en `:app`:** añadido en Room (v19 + `MIGRACION_18_19`), en la réplica remota
>   y en el switch "Permitir combinar con otras actividades el mismo día" de alta/edición.
> - **Asistentes en `:appCliente`:** pantalla independiente `AsistentesSesionScreen` (ruta
>   `asistentes_sesion/{idSesion}`), accesible solo desde una sesión RESERVADA; lista solo con nombres.
> - **Fix `cancelarReserva`:** se reordenó la Transaction (todas las lecturas antes de las escrituras);
>   desplegada en producción.
> - Los bloques inferiores de este documento son histórico y pueden estar superados por AGENTS.md.

> **🔴 ACTUALIZACIÓN 2026-09-09 — I18N ES/EN POR BLOQUES (estado vigente):** verificado contra el árbol
> real. **HEAD del desarrollador: `64de014`** ("Ignorar archivos Shelf"). El working tree conserva SIN
> commit TODO el trabajo de i18n de esta tanda (**NO revertir**; lista en `git status`; ver CHECKPOINT
> 2026-09-09 (I18N) de AGENTS.md). Resumen de esta tanda:
> - **Infraestructura previa (del desarrollador, ya commiteada):** preferencia `idioma` en DataStore
>   (es/en, default es), `util/IdiomaAplicacion` (locale + contexto override), precarga en Application y
>   `attachBaseContext`+`recreate` en MainActivity. **Nosotros añadimos** en `IdiomaAplicacion` de ambos
>   módulos los helpers `textoDe(base, recurso[, vararg])` para resolver recursos en el idioma elegido
>   desde repositorios/ViewModels (`@ApplicationContext`).
> - **`values-en/` nuevo en `:app` y `:appCliente`** (carpeta sin versionar aún). Contadores finales:
>   `:app` 48+48 (solo AUTH del ADMIN), `:appCliente` 237+237 (todos los bloques del CLIENTE).
> - **`:app` (ADMIN):** internacionalizado únicamente el bloque AUTENTICACIÓN (Login/Registro/Recuperar +
>   mensajes del `AutenticacionRepository` + validaciones visibles del `MainViewModel`). El resto de
>   pantallas ADMIN sigue hardcodeado en español (bloques i18n futuros).
> - **`:appCliente` (CLIENTE):** completados AUTENTICACIÓN, INCORPORACIÓN (Elección/Inicio código+DNI/
>   CompletarPerfil/BotonSelectorFoto), MI PERFIL y EDITAR PERFIL, MI CUENTA (CuentaScreen y diálogos),
>   HOME (incluido `AvisoMorosidad` sin `indexOf("aquí")` y `DialogoDenuncia` + motivos), CONFIGURACIÓN
>   (incluida `EliminarCuentaScreen`) y NOTIFICACIONES (buzón + preferencias de avisos). Errores visibles
>   de repos/ViewModels localizados; funciones puras testadas (`validarCambioContrasena`,
>   `validarDatosDenuncia`, `MotivosDenuncia.etiqueta`) intactas con sus tests.
> - **Excluido del alcance (ver AGENTS.md):** cuerpo legal de `PoliticaPrivacidadScreen` y
>   `TerminosDeUsoScreen` (solo interfaz/cabeceras/versión traducidas), `ClasesScreen`/Actividades y
>   Rutinas del CLIENTE, pantallas ADMIN fuera de AUTH, `InformacionLegalScreen` (sin ruta), web.
> - Verificación: `:app` y `:appCliente` compilan y sus `testDebugUnitTest`/`assembleDebug` pasan;
>   claves ES/EN idénticas y sin `R.string` huérfanos; `git diff --check` limpio. Sin commit/push/deploy.

> **🔴 ACTUALIZACIÓN 2026-09-09 — DEPLOY `notificacionInmediata` + LECTURA ADMIN (estado vigente):**
> verificado contra el árbol real. **HEAD del desarrollador: `a0bc03b`** (en `origin/master`; `77e3641`
> ya commiteó denuncias 2C-2, Login Cliente, web `/terminos` y la doc previa). El working tree mezcla
> SIN commit trabajo en curso del desarrollador (internacionalización/idioma, ver AGENTS.md) y cambios
> nuestros (NO revertir). Resumen de esta tanda:
> - **Decisión de estado cerrada:** `notificaciones/{id}.estado` = SOLO estado de ENVÍO
>   (PENDIENTE/ENVIADA/PROGRAMADA/CANCELADA/ERROR); la lectura es independiente en el buzón
>   `notificaciones_por_destinatario` (`leida`/`fechaLeida`). NO existe estado LEIDA.
> - **Indicador de lectura del ADMIN implementado (sin commit):** `LecturaNotificacion`,
>   `obtenerLecturaBuzones` (una consulta `notificaciones_por_destinatario` por negocio),
>   `NotificacionesViewModel.lecturaPorNotificacion` (best-effort) y `GestionNotificacionesScreen`
>   ("Leída"/"Sin leer"/"X/Y leídas"; sin indicador si no hay buzones; textos temporales en español).
> - **Cloud Function `notificacionInmediata` DESPLEGADA en producción** (v2, `onDocumentCreated`,
>   `europe-west1`, nodejs20): hará `PENDIENTE→ENVIADA` y FCM para manuales inmediatas nuevas.
>   Hubo que añadir `"functions": { "source": "functions" }` a `firebase.json` (aprobado, sin commit);
>   el primer deploy falló por el Eventarc Service Agent y el reintento funcionó. Avisos: Node 20
>   deprecado (2026-10-31), `firebase-functions` desactualizada, posibles imágenes de build sin limpiar.
>   **PENDIENTE:** prueba funcional manual de envío real (estado + push).
> - **Home CLIENTE:** reorden solo visual de cards (Fila 1: Actividades | Rutinas; Fila 2: Ajustes |
>   Notificaciones) manteniendo el badge en "Notificaciones". Sin commit.
> - `firestore.rules` (denuncias) sigue SIN desplegar. Detalle en el CHECKPOINT 2026-09-09 de AGENTS.md
>   y en el último bloque de CONVERSACION_EXPORTADA.md.

> **🔴 ACTUALIZACIÓN 2026-09-08 (2) — FASE 2C-3 + FOTOS SELECTOR + DIAGNÓSTICO LEÍDA (estado vigente):**
> verificado contra el árbol real. **HEAD del desarrollador: `77e3641 "seguridad de google play"`**.
> El working tree conserva cambios SIN commit de esta tanda (ver `git status`; NO revertir):
> FASE 2C-3 (retirada de notificaciones), la corrección de fotos del selector de clientes y el borrado
> de basura `.idea/shelf/…` + log del emulador. Detalle en el CHECKPOINT 2026-09-08 (2) de AGENTS.md y
> en el último bloque de CONVERSACION_EXPORTADA.md. Resumen:
> - **FASE 2C-3 — Retirar notificaciones MANUALES publicadas (moderación UGC):** regla pura
>   `util/RetiradaNotificacionReglas.kt` (retirable = `origen MANUAL` con estado `PENDIENTE`/`ENVIADA`);
>   `NotificacionRemotoRepository.retirarNotificacionManual(id)` (idempotente; borra `notificaciones/{id}`
>   + buzones deterministas `{clienteId}_{notificacionId}` de `idsClientes` en lotes ≤500; helper
>   companion `idDeBuzon` reutilizado en `crearBuzones`); `NotificacionesViewModel.retirarNotificacion`
>   con guard anti doble pulsación; UI "Retirar" (roja) en `GestionNotificacionesScreen` con spinner y
>   diálogo de confirmación permanente. Automáticas/preconfiguradas y programadas sin publicar NUNCA se
>   retiran (programadas → cancelación existente). Denuncias no se borran al retirar. **Rules NO
>   modificadas** (ya permiten `delete` ADMIN). Tests `RetiradaNotificacionReglasTest` (14).
> - **Fotos vacías en el selector de clientes (corregido):** `SeleccionarClientesScreen` llamaba a
>   `ClienteItem` SIN `idCliente`/`obtenerFotoCacheada` (a diferencia de `ClientesScreen`); con foto
>   remota (URL de Storage) se usaba la URL cruda. Corregido pasando `idCliente = cliente.idCliente` y
>   `obtenerFotoCacheada = clienteViewModel::cargarFotoLocal`. Sin tocar Storage/FotoCliente* /Rules/
>   modelo.
> - **Diagnóstico "marcar como leída" (SIN cambios):** el flujo existe y funciona (evidencia real en
>   Firestore: buzón `1716402750_n_1788893959120_6989` → `leida=true` + `fechaLeida` 19:00:01; buzón
>   `1100806408_n_…8107` → sigue `leida=false` con `updateTime` intacto). Rules desplegadas (ruleset
>   `b4559665`) permiten el `update` CLIENTE (`leida`/`fechaLeida`, `firebaseUid == uid`). Causa raíz no
>   cerrada; corrección recomendada (optimista + recarga) NO implementada.
> - Verificación de la tanda: `:app:testDebugUnitTest` y `:app:assembleDebug` OK; Rules 182/182;
>   `git diff --check` limpio salvo avisos CRLF/whitespace preexistentes. Sin commit/push/deploy.

> **🔴 ACTUALIZACIÓN 2026-09-08 — CONTRASEÑA REAL + GATE 2B-1 + LOGIN CLIENTE + WEB /TERMINOS +
> DENUNCIAS 2C-2 (estado vigente):** verificado contra el árbol real. **HEAD del desarrollador:
> `63d74f2` "correcion contraseñas"** (ahead 1 de `origin/master`, sin push). El working tree tiene
> cambios SIN commit de esta tanda (ver `git status`; NO revertir). Resumen:
> - **Cambio de contraseña REAL** (ADMIN y CLIENTE): `cambiarContrasena(actual,nueva)` en cada
>   `AutenticacionRepository` (reauth `EmailAuthProvider` → `updatePassword`; sin guardar contraseñas)
>   + validador puro `validarCambioContrasena(actual,nueva,repetida)`; `CuentaScreen` cableada en ambas
>   apps (la de Admin dejó de ser placeholder). Ya COMMITEADO por el desarrollador en `63d74f2`.
> - **FASE 2B-1** (gate de Términos a notificaciones MANUALES del Admin) implementado y COMMITEADO en
>   `63d74f2` (`GateUgcNotificaciones`, `NotificacionesViewModel`, `CrearNotificacionScreen`).
> - **Login Cliente estético** = Login Admin (sin tarjeta gris, logo, cabecera, footer) — SIN commit.
> - **FASE 2B-2** (gate motivo de baja): NO implementado (no existe campo Motivo en la UI del Cliente).
> - **FASE 2C-1**: `web/terminos/index.html` + enlaces `/terminos` en portada/privacidad/eliminar-cuenta;
>   **hosting desplegado** (`https://trazys.web.app/terminos` y resto 200) — SIN commit.
> - **FASE 2C-2 denuncias UGC** (ADMIN denuncia foto/usuario; CLIENTE denuncia notificación MANUAL y
>   logo/negocio): bloque `denuncias` en `firestore.rules` + tests PRUEBA 151–163 (**182/182**);
>   `DenunciaRepository`/`DialogoDenuncia`/`GestionDenunciasScreen`/`DenunciasViewModel` en `:app` y
>   `DenunciaRepository`/`DialogoDenuncia` en `:appCliente` + puntos ⋮ (perfil Admin, notificaciones y
>   Home del Cliente). **Rules SIN desplegar; SIN commit.** Sin bloqueo de usuarios (no hay P2P).
> - Verificación de la tanda: unit + assemble de `:app`/`:appCliente` OK; Rules 182/182; `git diff
>   --check` limpio. Detalle en el CHECKPOINT 2026-09-08 de AGENTS.md y en el último bloque de
>   CONVERSACION_EXPORTADA.md.

> **🔴 ACTUALIZACIÓN 2026-09-07 — MARCA TRAZYS + PRE-BETA (estado vigente):** el proyecto pasa a
> llamarse **Trazys** de forma visible (apps, web, textos), sin tocar identificadores técnicos
> (applicationId `com.roberto.gestorpro[.cliente]`, Firebase Project ID `gestorpro-50e83`, paquetes).
> En Firebase: Rules doc-first desplegadas (firestore ruleset `b4559665…`, storage `c68b395b…`),
> Function callable `eliminarMiCuenta` (europe-west1, Node 20) desplegada, y Hosting site `trazys`
> desplegado en `https://trazys.web.app` (`/`, `/privacidad`, `/eliminar-cuenta`).
> Eliminación de cuenta: CLIENTE borra su cuenta y datos personales conservando movimientos + índice
> + ficha mínima; ADMIN borra todo su negocio. Fase 1 de Términos de uso: núcleo de aceptación
> (versión 1.0 en DataStore) y pantallas reales en ambas apps; **pendiente el checkbox de Registro**.
> Detalle completo y pendientes en el CHECKPOINT 2026-09-07 de AGENTS.md. El working tree tiene
> cambios SIN commit (NO revertir).

> **⚠️ ACTUALIZACIÓN (post-recuperación `0b1d370`, tandas UGC/Términos/identidad) — complementa el
> bloque anterior y al CHECKPOINT "REANUDAR AQUÍ" de AGENTS.md:**
> - **Registro con Términos (hecho en el working tree):** checkbox obligatorio + enlaces a Términos y
>   Política en :app y :appCliente, con alineación en la misma columna (checkbox en la primera línea).
> - **Pantalla de Términos** refleja la aceptación persistida (si ya aceptó la vigente no repite botón
>   y muestra "Términos aceptados — versión 1.0").
> - **Configuración Admin:** Política y Términos como entradas de lista (sin cards), estilo Cliente.
> - **Gates de UGC (fotos y logo) activos (sin deploy):** foto CLIENTE (`MainViewModel.
>   actualizarMisDatosPersonales`), foto pendiente en VÍA 2 (`VinculacionRepository.
>   transferirFotoPendienteSiProcede`), fotos ADMIN (`ClienteViewModel`: alta/edición/reintento, sin
>   escribir rutas locales como foto remota) y **logo ADMIN** (`MainViewModel.sincronizarLogoNegocio`
>   antes de `guardarLogoRemoto`). Avisos con enlace a Términos.
> - **Aislamiento de identidad Admin por propietario:** `refrescarIdentidadLocal()` solo usa la caché
>   si `uid == uid_propietario_datos_locales`; la adopción silenciosa limpia la identidad de DataStore.
> - **UI Home Cliente:** sin "Fecha no disponible"/"Hasta el Fecha no disponible" cuando no hay fecha
>   real (muestra solo el estado).
> - **Pendiente de decisión/análisis:** no se aplicó "sin movimientos → REGISTRADO en VÍA 1"
>   (conflicto con Rules/activación manual). Diagnóstico del email de Registro (carácter invisible /
>   mapeo de error) sin corrección.
> - Verificación: unit + assemble de :app y :appCliente BUILD SUCCESSFUL; `git diff --check` limpio.
>   Sin commit/push/deploy.


> **🟡 ACTUALIZACIÓN 2026-09-06 (continuación; cambios SIN commit en el árbol):** HEAD sigue en
> `cb44d1e`. El working tree contiene cambios SIN commit (NO revertir) de una tanda final de UX:
> orden de clientes por apellido→nombre (`model/Cliente` expone `apellidos`), selectores INDIVIDUAL y
> GRUPO de CrearNotificacionScreen como Surface tonal clicable, y regla visual de Switches
> (ON azul `#1E88E5`, excepciones morosidad/baja en rojo). Detalle y pendientes en AGENTS.md
> (CHECKPOINT 2026-09-06) y en el último bloque de CONVERSACION_EXPORTADA.md.

> **🔴 ACTUALIZACIÓN 2026-09-05 (CIERRE — HEAD `cb44d1e`, Room 18, working tree limpio):**
> verificado contra el árbol real. HEAD del desarrollador `cb44d1e` (2026-09-05 18:27). Este bloque
> SUPERSEDE las cifras "HEAD limpio `60cf834` / Room v15-17 / working tree sin commit" citadas abajo.
> - **Room v18** (`MIGRACION_17_18` crea `servicio_desactivacion_pendiente`) — **migración 17→18 PROBADA
>   en teléfono físico**: desde `5184af9` (esquema 17, datos 8 clientes/1 actividad/2 sesiones/2
>   movimientos) a `cb44d1e` (esquema 18): `user_version` 18, tabla nueva presente, filas conservadas,
>   sin errores ni recreación destructiva (detalle y artefactos en `%TEMP%\opencode\roomtest`).
> - **Nuevas funcionalidades ya en HEAD** (detalle en AGENTS.md CHECKPOINT 2026-09-05): Economía como
>   pantalla de gestión (selección/acciones masivas, editor compartido `DialogoEdicionMovimiento`,
>   `MovimientoRepository.actualizarMovimientos/eliminarMovimientos`, `MovimientoPago.resolverLote`,
>   filtro de fechas por `fechaInicio`), barra contextual común `BarraSeleccionContextual`, selección
>   múltiple de clientes, estado y filtro de VINCULACIÓN (`firebaseUid`; `model/Cliente` lo expone),
>   protecciones UX de notificaciones, arreglos de cancelación de reservas (appCliente + capacidad Admin
>   con `CapacidadSesion`) y baja durable de actividades (`DesactivacionServicioSincronizador`).
> - **Verificación:** `:app:testDebugUnitTest` **146/146**, `:appCliente:testDebugUnitTest` **17/17**,
>   assembleDebug OK ambos, `git diff --check` limpio. Sin cambios de Rules/Functions/Storage.
> - Para los pendientes de esta sesión (migraciones Room sin fallback, logs de diagnóstico, Storage/Blaze,
>   etc.) consultar AGENTS.md y el nuevo bloque final de CONVERSACION_EXPORTADA.md.

> **Fecha del informe:** 2026-09-03 (verificado contra el árbol real).
> **Objetivo:** que una IA que no conoce ninguna conversación previa pueda continuar el desarrollo con precisión.
> **Regla de uso:** este documento es SOLO lectura/contexto. No refleja decisiones nuevas.
> **Fuentes:** inspección directa del código, Gradle, Manifiestos, `firestore.rules`, `storage.rules`, `functions/`, `firestore-tests/`, tests ejecutados, `AGENTS.md` (documentación viva; este informe la complementa y corrige donde está desactualizada) y **`CONVERSACION_EXPORTADA.md`** (crónica completa Sesiones I–XXXV, 2026-08-24 → 2026-09-03; ver Anexo A con índice por sesión y los matices de reverts del desarrollador). Si una afirmación de este informe contradice a AGENTS.md o a un checkpoint de la conversación, **prevalece lo verificado en el árbol actual** (los checkpoints describen working trees intermedios que el desarrollador ya commiteó).

> **⚠️ ACTUALIZACIÓN 2026-09-03 (F2 ECONOMÍA ROOM↔FIRESTORE + DIAGNÓSTICO CREAR NEGOCIO):** verificado contra el árbol real (HEAD `100c4eb "mejoras y correciones"`). Este documento y AGENTS.md han quedado desactualizados en varios puntos por la F2, que **sí está implementada en el working tree (SIN commit y SIN deploy)**:
> - **Room v17** (no v15/16): `cliente` con `exentoMorosidad` + tabla `eliminacion_pendiente` (`MIGRACION_16_17`).
> - **Economía cableada** (ya NO "sin cablear", §8.5/§14-G/§24): réplica `movimientos/{id}` + resumen remoto en `clientes/{id}` (`moroso`, `deuda`, `fechaEntradaMorosidad`, fechas, `exentoMorosidad`); motor de morosidad con deuda = TODOS los PENDIENTES, dos causas y `fechaEntradaMorosidad`=detección; `IdMovimiento` (ids altos); eliminaciones pendientes persistidas con reintento al arranque (`MainViewModel`) y en gestión de clientes/perfil.
> - **Rules locales** ampliadas (update ADMIN de `clientes` con claves económicas); suite Rules **151/151** (PRUEBA 129–136) y unit `:app` **68/68**; `assembleDebug` OK en ambos módulos. **NO desplegado.**
> - **Abierto:** diagnóstico "Crear negocio en la nube" PERMISSION_DENIED en producción (hipótesis principal: `usuarios/{uid}.negocioId` asignado pero `negocios/{uid}` ausente → lo bloquean también las Rules LOCALES); pendiente de confirmar en consola y reconciliar con Admin SDK.
> - Algunas cifras y frases de las secciones siguientes (HEAD limpio `60cf834`, Room v15, "réplica no cableada", 143 tests) quedan **superadas por este bloque**; prevalecerá lo verificado arriba hasta el próximo traspaso.

> **⚠️ ACTUALIZACIÓN 2026-09-04 (IDENTIDAD / BACKUP v1 / UNICIDAD CÓDIGO MAESTRO — estado del working tree):**
> verificado contra el árbol real. **HEAD del desarrollador: `500bae3`** (commits recientes del
> desarrollador sobre estilos de perfil, visibilidad de contraseña y aislamiento de ADMIN). El working
> tree contiene **cambios SIN commit** (ver `git status`; lista autoritativa en el CHECKPOINT de
> AGENTS.md 2026-09-04). Este bloque SUPERSEDE afirmaciones anteriores desactualizadas:
> - **Backup v1 (`data/export/ExportManager.kt`, reescrito):** ZIP `manifest.json` + `media/`; validación
>   estricta de `negocioId` vs `usuarios/{uid}`; merge (importar) y replace completo atómico
>   (restaurar, `clearAllTables()`); normalización de filas al negocio actual; DNI duplicado → aborto;
>   legacy JSON rechazado; recálculo + publicación de resumen económico de clientes afectados; fotos en
>   ZIP; logo NO empaquetado. `DatosScreen`/`DatosViewModel` reescritos.
> - **Identidad única del centro:** fuente remota común `negocios_publicos/{negocioId}` (nombre+logo);
>   Admin refresca al arrancar/login y tras WIPE (DataStore = caché, fallback offline); cabeceras Admin y
>   Cliente con `[LOGO] Nombre`, logo URL-aware (Coil) y ellipsis; el nombre se actualiza inmediatamente
>   en la UI tras guardar (MainViewModel Activity-scoped compartido entre Home/MiNegocio/Login).
> - **Aislamiento local de cuenta/propietario** (`data/local/PreparadorLocalCuenta.kt`, clave
>   `uid_propietario_datos_locales`): WIPE en cambio de cuenta, bloques indeterminado/pendientes; crash de
>   arranque corregido (orden de `init` de identidad).
> - **Unicidad GLOBAL del código maestro (SIN deploy):** colección `codigos_maestros/{codigo}`
>   (`{negocioId}`), creación/cambio en `runTransaction`, VÍA 1 del Cliente resuelve solo por
>   `codigos_maestros` (sin `whereEqualTo/limit(1)`); Rules LOCALES con bloque `codigos_maestros` +
>   validación cruzada. **Rules desplegadas en producción siguen SIN `codigos_maestros`.**
> - **Tests/verificación:** `npm --prefix firestore-tests test` → **165/165** (PRUEBA 137–150 nuevas);
>   unit `:app`/`:appCliente` BUILD SUCCESSFUL; `assembleDebug` ambos módulos OK. **Sin deploy; sin
>   migración de datos** (el duplicado real Coliseo/prueba = `123456` sigue en producción, verificado
>   solo-lectura; plan de migración en AGENTS.md 2026-09-04).
> - El resto de secciones de este informe (esquemas, decisiones, §14-§25) sigue vigente salvo lo aquí
>   corregido.

> **🔴 ACTUALIZACIÓN 2026-09-05 (DEPLOY `codigos_maestros` + IDENTIDAD/LOGIN + HIDRATACIÓN CENTRAL Room + misc — estado real):**
> verificado contra el árbol real. **HEAD del desarrollador: `f616891`** (ya commiteados los estilos de
> perfil/contraseña/aislamiento de ADMIN). Working tree con cambios SIN commit de esta tanda (NO revertir;
> lista autoritativa en el CHECKPOINT de AGENTS.md 2026-09-05). Este bloque SUPERSEDE lo anterior donde
> contradiga:
> - **DEPLOY AUTORIZADO de Firestore Rules (único):** `firestore.rules` local validado **165/165** se
>   desplegó a `gestorpro-50e83` → ruleset **`9d38a26c-0dae-41bf-b691-7f3f55138dbc`** (createTime
>   2026-09-04T21:24:26Z), verificado por API: contiene `match /codigos_maestros` y la validación cruzada
>   en `negocios`/`negocios_publicos`. Causa del fallo de "crear negocio" con ADMIN nuevo: la APK ya
>   escribe `codigos_maestros/{codigo}`, pero el ruleset anterior (`cd36cbc9`) no tenía esa colección. Un
>   ADMIN con `usuarios/{uid}.negocioId == null` también recibía PERMISSION_DENIED en Notificaciones y
>   Solicitudes (rules-are-not-filters en listados de negocio).
> - **Identidad/aislamiento Admin1→Admin2 (`NegocioRepository`, `MainViewModel`,
>   `PreferencesRepository`):** `estadoNegocioDeCuenta(): EstadoNegocioDeCuenta`
>   (`SinSesion/Error/SinNegocio/ConNegocio`) para distinguir "sin negocio CONFIRMADO" de "no comprobable".
>   `refrescarIdentidadLocal()` suspend y esperada antes de `Listo`; `refrescarIdentidadRemota()` vacía
>   nombre/logo (DataStore+memoria) solo con negocio confirmado `null` y conserva caché ante error/offline;
>   `cerrarSesion()` limpia identidad en memoria y DataStore (Room/owner/ficheros se conservan);
>   `decidirPropietarioIndeterminado(conservar=true)` aplica la verdad remota tras adoptar.
> - **Solicitudes sin negocio (`SolicitudesScreen`):** guard `negocioOk`; sin negocio NO consulta
>   Firestore y muestra `SinNegocioContenido` (textos propios + botón "Crear mi negocio" → `MINEGOCIO`);
>   con negocio el flujo es idéntico. Se elimina el PERMISSION_DENIED de esta situación.
> - **Hidratación CENTRAL de Room tras WIPE (nuevo):** `data/repository/HidratadorCacheLocal.kt`
>   (coordinador; transacción Room por fase; insert-if-missing; best-effort con marcador
>   `cache_hidratada_uid` en DataStore) + `util/HidratacionMapeadores.kt` (mapeos puros) +
>   `obtenerXRemotosDelNegocio()` en Servicio/Sesion/Reserva/Movimiento repos (todas filtran por
>   `negocioId == uid` del auth y propagan errores) y `ClienteRemotoRepository` ya no traga el error de
>   lista. Orden: clientes → servicios → sesiones → reservas (solo con cliente+sesión locales) →
>   movimientos → recálculo de morosidad/deuda por cliente afectado (MovimientoMorosidad). Solo se ejecuta
>   con negocio confirmado y `negocioId == uid`, tras WIPE/adopción; NUNCA para cuenta sin negocio. Los
>   **GASTOS** no tienen espejo remoto → no recuperables tras WIPE (limitación documentada).
> - **Fix teclado en Mi negocio (`MiNegocioScreen`):** `verticalScroll(rememberScrollState())` +
>   `imePadding()` (patrón de AñadirClienteScreen); diseño idéntico con teclado oculto.
> - **Diagnóstico "Restaurar copia": FALSA ALARMA.** El backup real contenía `clientes=2`,
>   `movimientos=0`; la transacción hace COMMIT y Room queda con el contenido exacto del backup. Se
>   retiró la instrumentación temporal sin cambios funcionales.
> - **Verificación:** Rules **165/165** (antes del deploy); unit `:app` **98/98** (85 + 13
>   `HidratacionMapeadoresTest`); `:app:assembleDebug` BUILD SUCCESSFUL. Sin commit, sin otros deploys.
> - Producción (solo-lectura): ruleset `9d38a26c`; `usuarios`=5 (ADMIN `rdKOD…` con "prueba de
>   negocio"/654321, ADMIN `BW8a…` sin negocio, 1 CLIENTE sin negocio, 2 CLIENTES vinculados a `rdKOD…`);
>   `negocios`/`negocios_publicos`/`codigos_maestros`=1; `clientes`=2; `notificaciones`/`solicitudes`=0.
> - El resto de secciones de este informe (esquemas, decisiones, §14–§25) sigue vigente salvo lo aquí
>   corregido.

Convención de evidencia usada en todo el informe:
- **[CONFIRMADO]** — comprobado directamente en el código/configuración/ejecución de hoy.
- **[INFERIDO]** — conclusión razonable por el comportamiento del código, no documentada explícitamente.
- **[DESCONOCIDO / NO DETERMINADO]** — no hay información suficiente en el repositorio.
- Cuando se indica "según AGENTS.md" es información heredada de la documentación del proyecto, no verificada hoy.

---

# 1. Descripción general de la aplicación

## Nombre del proyecto
**GestorPro** ([CONFIRMADO]: `rootProject.name = "GestorPro"` en `settings.gradle.kts`).

## Qué es
Sistema Android para la gestión de un **negocio deportivo (gimnasio)**, formado por **dos aplicaciones independientes** que comparten el **mismo proyecto Firebase/Firestore** (`gestorpro-50e83`, [CONFIRMADO] en `.firebaserc`):

1. **GestorPro Admin** — módulo `:app`, paquete `com.roberto.gestorpro`, rol remoto `ADMIN`. Es la app del gestor del negocio.
2. **GestorPro Cliente** — módulo `:appCliente`, paquete `com.roberto.gestorpro.cliente`, rol remoto `CLIENTE`. Es la app del socio/cliente del gimnasio.

## Tipo de aplicación
- Aplicaciones Android nativas en **Kotlin + Jetpack Compose (Material 3)**, 100% UI Compose, una sola Activity por app.
- Backend: **Firebase** (Authentication + Firestore como base de datos principal remota + Storage para logo/fotos futuro). Solo el Admin usa además **Room** como base de datos local. El Cliente usa **Firestore como fuente de verdad** y **DataStore** solo como caché/preferencias.

## Objetivo / problema que resuelve
Digitalizar la gestión de un gimnasio pequeño/mediano: altas y bajas de clientes, cobro de cuotas (representadas como "movimientos"), reservas de actividades/servicios por sesiones, avisos/notificaciones, solicitudes de baja, y datos económicos básicos, con dos perfiles diferenciados (gestor y cliente) sobre los mismos datos remotos y con control de acceso por Security Rules de Firestore.

## Usuario objetivo
- El **administrador/gestor** del gimnasio (Admin).
- Los **clientes/socios** del gimnasio (Cliente), que se vinculan a la ficha que crea el Admin mediante **código maestro + DNI**.

## Funcionalidades principales (resumen)
- Autenticación real con Firebase en ambas apps (roles `ADMIN`/`CLIENTE`).
- **Admin:** alta/edición/consulta/archivado/restauración de clientes con réplica write-through a Firestore; cambio de DNI atómico; **servicios** (catálogo con precio), **sesiones** (programación, apertura de reservas), **reservas** (contingente, atómicas), **economía** (movimientos por cliente con multi-servicio, precio, método de pago, morosidad; gastos; resumen), **solicitudes de baja**, **notificaciones** (creación, configuración, listado), configuración del negocio (nombre/código maestro/logo con Storage).
- **Cliente:** registro/login; perfil pendiente si no está vinculado ("No tengo vinculación"); **vinculación por código maestro + DNI (VÍA 1)** a la ficha creada por el Admin; ver/editar sus datos personales (el DNI queda bloqueado tras vincularse); Home con estado de su cuota; **"Clases/Actividades de hoy"** con reservar/cancelar reservas; buzón de notificaciones; **solicitar baja**; configuración visual.

## Estado actual general
[CONFIRMADO] El árbol está **limpio y en git** (working tree sin cambios; HEAD `60cf834 "mejoras y correciones"`, 2026-09-03 01:18). Compila y los tests pasan (ver §13). El proyecto NO está publicado en Google Play y tiene varios pendientes de producción (migraciones Room, Rules desplegadas, Functions/Blaze, Storage). Términos comerciales de interfaz: el texto visible dice **"Actividades"** (antes "Servicios"/"Clases"), pero el nombre interno de modelos/colecciones sigue siendo `Servicio`/`servicios` (decisión deliberada, ver §11).

> ⚠️ **Los checkpoints de AGENTS.md y de `CONVERSACION_EXPORTADA.md` que citan "working tree con N archivos SIN commit" están DESACTUALIZADOS:** el desarrollador commiteó todo ese trabajo en los commits `60cf834`, `f32a5c1`, `3b94164`, `91f9e94`, `d42fa75`, `0838adb` y anteriores ("mejoras y correcciones" / "impplementando codigo para cuando contrate balze*"). El árbol de HOY está limpio y contiene TODO (incluidas las correcciones que la conversación describe a veces como "solo en working tree"). No revertir nada "para recuperar un working tree perdido".

> ⚠️ **El archivo `AGENTS.md` está desactualizado en algunos puntos** (afirma "32 archivos sin commitear", cifras de tests antiguas, descripciones de Fase 6 que no coinciden con el estado final, etc.). Este informe recoge el estado REAL verificado. No borrar `AGENTS.md`: es la documentación de referencia con las reglas del proyecto y el histórico.

---

# 2. Tecnologías y versiones

[CONFIRMADO] en `gradle/libs.versions.toml`, `build.gradle.kts` de cada módulo y `gradle-wrapper.properties`.

| Tecnología | Versión | Uso |
|---|---|---|
| Kotlin | 2.2.10 | Lenguaje principal (ambas apps) |
| Android Gradle Plugin (AGP) | 9.1.1 | Build Android |
| KSP | 2.3.6 | Compilador de Room y Hilt |
| Gradle (wrapper) | 9.3.1 | Sistema de build |
| compileSdk | 36 (minor API level 1 → 36.1) | SDK de compilación |
| targetSdk | 36 | SDK objetivo |
| minSdk | 26 | SDK mínimo |
| Java | source/target `VERSION_11` | Compilación Java/Kotlin |
| Jetpack Compose | BOM `2026.02.01` (Material 3) | UI |
| Navigation Compose | 2.9.3 | Navegación |
| Hilt | 2.60.1 (+ `hilt-navigation-compose` 1.2.0) | Inyección de dependencias |
| Room | 2.8.4 | BD local SOLO Admin (`:app`) |
| DataStore Preferences | 1.1.7 | Preferencias/caché (ambas) |
| Coil | 3.3.0 (`io.coil-kt.coil3:coil-compose`) | Carga de imágenes (logo/fotos) |
| Gson | 2.11.0 | Export/import de datos SOLO Admin |
| Firebase | BOM `34.16.0` (declarado inline en cada `build.gradle.kts`, no en el catálogo) | auth, firestore, messaging (ambas); storage (solo `:app`) |
| google-services plugin | 4.5.0 | Config Firebase por app |
| androidx core/lifecycle/activity | core-ktx 1.18.0, lifecycle 2.10.0, activity-compose 1.13.0 | Base AndroidX |
| material-icons-extended | (BOM Compose) | Iconos Compose |
| JUnit | 4.13.2 (unit) / androidx junit 1.3.0, espresso 3.7.0 (instrumental) | Tests |

**Observaciones de dependencias [CONFIRMADO]:**
- `libs.firebase.storage` y `libs.firebase.messaging` existen en el catálogo **sin versión** (la resuelve el BOM inline).
- `:app` NO declara `firebase-messaging`; `:appCliente` SÍ (tiene `FcmService`). El Admin no recibe push.
- `:appCliente` NO usa Room ni Gson ([CONFIRMADO] en sus dependencias).
- `appCliente/` NO tiene `.gitignore` propio; lo cubre el `.gitignore` raíz con `**/google-services.json`. [CONFIRMADO] `app/google-services.json` y `appCliente/google-services.json` existen localmente y están ignorados por git.

---

# 3. Arquitectura

## Arquitectura real (MVVM con repositorios; NO Clean Architecture estricta)

```
UI Compose (screens)
      │  observa StateFlow (collectAsState / collectAsStateWithLifecycle)
      ▼
ViewModel (@HiltViewModel, viewModelScope)
      │  llama a métodos del repositorio
      ▼
Repository (lógica de negocio + orquestación)
      │
      ├──► Room DAO (SOLO :app) ───► ClientesDatabase (fuente de verdad local ADMIN)
      │
      └──► data/firebase/*RemotoRepository ───► Firestore / Storage / Auth
```

- **Admin (`:app`):** Room es la fuente de verdad local y Firestore el **espejo remoto write-through** (réplica inmediata sin cola offline). Cuando la réplica falla se conserva el dato local, se informa al ADMIN y queda una operación de reintento manual.
- **Cliente (`:appCliente`):** Firestore es la fuente de verdad; DataStore solo cachea (tema, idCliente, negocioId, dniPendiente, nombre/logo del negocio, `notificacionesActivadas`).
- No existe capa `domain/usecase`. No usar ViewModels como dependencia directa de composables hijos cuando se pueda evitar (se pasan datos + lambdas).

## Flujo de una acción (ejemplo reserva del CLIENTE)
`ClasesScreen` → botón → `ReservasClienteViewModel.reservar()` → `ReservaRepository.crearReserva()` (Firestore `runTransaction`: lee cliente/servicio/sesión/reserva, valida estado ACTIVO + servicio activo + contratado + plazas + no duplicado + apertura, escribe `reservas/{clienteId}_{sesionId}` y `sesiones.plazasDisponibles-1`) → el VM expone estado → la pantalla recarga.

## Flujo en ADMIN (ejemplo alta cliente)
`AñadirClienteScreen` → `ClienteViewModel` → `ClienteRepository` (Room insert) → `ClienteRemotoRepository.crearClienteRemoto()` (WriteBatch `clientes/{id}` + `indices_clientes/{negocioId}_{DNI}` + `clientes_privados/{id}`). La confirmación de alta/edición solo ocurre cuando la réplica remota termina.

## Gestión de estado
- `StateFlow`/`MutableStateFlow` en ViewModels, expuestos como `StateFlow`; varias pantallas usan `collectAsStateWithLifecycle()`, pero existen usos heredados de `collectAsState()` (deuda aceptada, ver §22).
- `MainViewModel` suele compartirse a nivel de `AppNavigation` (p. ej. `NotificacionesViewModel` en Admin se obtiene una vez en `AppNavigation` y se pasa a sus pantallas).

## Inyección de dependencias
- **Hilt.** `@HiltAndroidApp` en `GestorProApplication` y `GestorProClienteApplication`; `@AndroidEntryPoint` en `MainActivity`.
- Cada app tiene un único `di/AppModule.kt` que provee singletons (FirebaseAuth, Firestore, Storage en Admin, DB Room + DAOs + repositorios en Admin; FirebaseAuth/Firestore/Context en Cliente).
- Repositorios de Firestore del Cliente y algunos de Admin usan `@Singleton @Inject constructor`.

## Navegación
- Una sola Activity + **Navigation Compose**. Cada app tiene su pareja `navigation/Routes.kt` + `navigation/AppNavigation.kt`.
- `AppNavigation` decide el destino inicial según sesión (`destinoInicialSegunSesion` en Admin; `destinoInicial()` en Cliente). No hay deep links. Rutas con placeholder se construyen sustituyendo, nunca concatenando (regla del proyecto).

## Persistencia
- **Admin:** Room (`ClientesDatabase`, versión 17) + DataStore (`preferencias`) + Firestore/Storage.
- **Cliente:** Firestore + DataStore (`preferencias_cliente`) + fotos locales en `filesDir` (no subidas a Storage todavía).
- El ID de cliente en el Admin usa **rango alto aleatorio** (≥ 1.000.000.000, `util/IdCliente.kt`) para evitar colisiones entre instalaciones de un mismo negocio en Firestore.

---

# 4. Estructura del proyecto

## Raíz
```
build.gradle.kts / settings.gradle.kts / gradle.properties
gradle/libs.versions.toml            -> catálogo de versiones
gradlew(.bat), gradle/wrapper/       -> wrapper Gradle 9.3.1
firebase.json                        -> rules (firestore.rules, storage.rules) + functions
.firebaserc                          -> proyecto por defecto: gestorpro-50e83
firestore.rules (1338 líneas)        -> Security Rules de Firestore (LOCAL)
storage.rules                        -> Security Rules de Storage
functions/                           -> Cloud Functions 2ª gen (preparadas, SIN deploy)
firestore-tests/                     -> tests de Rules + emulador
AGENTS.md                            -> documentación viva y reglas (leer SIEMPRE)
AI_RULES.md, AUDITORIA_PROYECTO_MIGRACION_KMP.md, CONVERSACION_EXPORTADA.md,
EXPLICACION_BASE_DE_DATOS.html, build_*.txt, files.txt, structure.txt,
app_kt_files.txt, conversacionEstilo.md, firestore-debug.log  -> basura/históricos versionados (ver §17)
```

## Módulo `:app` — GestorPro Admin (`app/src/main/java/com/roberto/gestorpro`)
```
GestorProApplication.kt, MainActivity.kt
navigation/        Routes.kt + AppNavigation.kt
model/             Cliente, EstadoCliente, EstadoMovimiento, MetodoPago, EstadoSolicitud,
                   TipoSolicitud(CLASE/BAJA, legacy), FiltroClientes, NotificacionAdmin,
                   SolicitudBaja, ReservaConCliente, ReservaClienteDetalle, SesionConClase(legacy),
                   TipoUsuario
data/
  entity/          10 entidades Room (Cliente, Movimiento, Gasto, Servicio, Sesion, Reserva,
                   Solicitud(legacy), Clase(legacy), SesionClase(legacy), EliminacionPendiente)
  dao/             10 DAOs (incluye queries de cascadas de reservas/sesiones + EliminacionPendiente)
  database/        ClientesDatabase (v17)
  converter/       7 conversores Room (enum/lista a texto)
  firebase/        AutenticacionRepository, ClienteRemotoRepository, BajaClienteRemotoRepository,
                   MovimientoRemotoRepository, NegocioRepository, NotificacionRemotoRepository,
                   ReservaRemotoRepository, ServicioRemotoRepository, SesionRemotoRepository,
                   SolicitudRemotoRepository
  repository/      ClienteRepository, MovimientoRepository, GastoRepository, ServicioRepository,
                   SesionRepository, ReservaRepository, PreferencesRepository + legacy
                   (Clase/SesionClase/Solicitud)
  export/          ExportManager (export/import/restore JSON)
di/AppModule.kt    Hilt + Room + migraciones 11→15
util/              IdCliente, MovimientoFirestore, MovimientoMorosidad, MovimientoPrecio(incluye MovimientoPago)
ui/
  auth/            Login, Registro, RecuperarPassword
  home/            HomeScreen (panel con 6 accesos: Clientes, Actividades, Economía, Ajustes, Notificaciones, Solicitudes)
  clientes/        ClientesScreen (lista+filtros+búsqueda), PerfilClienteAdministradorScreen (2733 líneas;
                   composable PerfilClienteScreen), AñadirClienteScreen (archivo con "ñ" en el nombre),
                   DialogoEditarServiciosContratados
  servicios/       ServiciosScreen, EditarServicioScreen, DetalleServicioScreen, ProgramarSesionesScreen,
                   EditarSesionScreen, SesionReservasScreen (MODELO NUEVO)
  economia/        EconomiaScreen (resumen + CRUD gastos + detalle solo lectura de movimientos)
  solicitudes/     SolicitudesScreen
  notificaciones/  GestionNotificacionesScreen, CrearNotificacionScreen, ConfigNotificacionesScreen,
                   SeleccionarClientesScreen, DialogoSeleccionarClientes (sin uso)
  configuracion/   ConfiguracionScreen, MiNegocioScreen, CrearNegocioScreen, PreferenciasScreen,
                   DatosScreen, CuentaScreen, PoliticaPrivacidadScreen
  componentes/     Botones.kt (kit App*), MenuCard, ClienteItem, BotonSelectorFoto + sin uso:
                   ServicioItem, MovimientoItem, ResumenCard
  clases/          Código LEGACY (ClasesScreen, CrearClaseScreen, DetalleClaseScreen,
                   DetalleSesionReservasScreen) — rutas registradas pero INALCANZABLES desde la UI activa
  viewmodel/       MainViewModel, ClienteViewModel, MovimientoViewModel, EconomiaViewModel,
                   GastoViewModel, ServicioViewModel, SesionViewModel, SolicitudesViewModel,
                   NotificacionesViewModel, PreferenciasViewModel, DatosViewModel, ClaseViewModel(legacy)
  theme/           Color/Theme/Type
  utils/           FotoUtils
```

## Módulo `:appCliente` — GestorPro Cliente (`appCliente/src/main/java/com/roberto/gestorpro/cliente`)
```
GestorProClienteApplication.kt, MainActivity.kt
navigation/        Routes.kt + AppNavigation.kt
model/             Cliente, EstadoCliente, EstadoHomeCliente, EstadoSolicitud, Servicio, Sesion,
                   Reserva, Notificacion, SolicitudBaja
data/
  firebase/        AutenticacionRepository, ClienteRepository, NegocioRepository, PerfilPendienteRepository,
                   VinculacionRepository, SesionRepository, ReservaRepository, SolicitudRepository,
                   NotificacionRepository, DispositivoRepository
  repository/      PreferencesRepository (DataStore "preferencias_cliente")
di/AppModule.kt    Hilt (Auth, Firestore, Context)
ui/
  auth/            InicioScreen (código+DNI), Login, Registro, RecuperarPassword, CompletarPerfil,
                   MiPerfil, EditarPerfil, CuentaScreen (solicitar baja + cerrar sesión)
  home/            HomeScreen, ClasesScreen ("Clases/Actividades de hoy", reservar/cancelar)
  configuracion/   ConfiguracionScreen, NotificacionesScreen (switch avisos), InformacionLegalScreen
                   (genérica, "Contenido próximamente"), PoliticaPrivacidadScreen
  notificaciones/  ListaNotificacionesScreen (buzón)
  rutinas/         RutinasScreen (placeholder visual)
  service/         FcmService (FCM)
  viewmodel/       MainViewModel, SesionesClienteViewModel, ReservasClienteViewModel, NotificacionesClienteViewModel
  theme/ utils/    Tema + FotoUtils
```

**Nota de localización:** `app/src/main/java/com/roberto/gestorpro/ui/clientes/AñadirClienteScreen.kt` tiene un carácter `ñ` en el nombre de archivo [CONFIRMADO]. Compila en Windows pero es un riesgo de portabilidad/git; no renombrar sin avisar (rompería imports).

---

# 5. Estado del repositorio y de compilación (verificado hoy)

- [CONFIRMADO] `git status`: working tree **limpio**, rama `master`, sincronizada con `origin/master`.
- [CONFIRMADO] HEAD del desarrollador: `100c4eb "mejoras y correciones"`; working tree con la F2 de economía + tandas documentales SIN commit (ver ACTUALIZACIÓN al inicio).
- [CONFIRMADO] Ejecutado hoy:
  - `.\gradlew.bat :app:testDebugUnitTest` → **BUILD SUCCESSFUL**.
  - Unit tests `:app` = **68/68 OK**; `:appCliente` = **9/9 OK**.
  - `npm --prefix firestore-tests test` → **151/151 OK** (emuladores firestore+storage, proyecto `gestorpro-rules-test`; PRUEBA 129–136 nuevas).
  - `node --test functions/test/ids.test.js functions/test/tokens.test.js` → **13/13 OK**.
- `:app:assembleDebug` y `assembleDebug` (ambos módulos) → BUILD SUCCESSFUL.

---

# 6. Autenticación y roles

## Cómo funciona (común a ambas apps)
- **Firebase Authentication** con email/contraseña. Sesión persistida por el SDK (restauración automática al arrancar).
- Al **registrar**, se crea la cuenta Auth y después el documento `usuarios/{uid}` = `{ rol, activo: true, clienteId: null, negocioId: null }`. Si la escritura falla, se borra la cuenta para no dejar huérfanos.
- Al **iniciar sesión**, se lee `usuarios/{uid}`: si no existe o `activo != true` se cierra sesión y se informa.
- **Roles remotos exactos:** `ADMIN` y `CLIENTE` (constantes en cada `AutenticacionRepository`).
- **Logout:** `FirebaseAuth.signOut()`. No borra DataStore.
- **Recuperación de contraseña:** únicamente `FirebaseAuth.sendPasswordResetEmail`. Mensaje de éxito SIEMPRE genérico ("Si el email existe, recibirás un enlace…") para no revelar cuentas existentes; ante errores de autenticación se responde el mismo mensaje genérico; solo se comunican fallos reales (p. ej. sin conexión). Validación previa de email con `Patterns.EMAIL_ADDRESS`.

## Diferencias por app
- **Admin (`:app`):** registro siempre con rol `ADMIN`. `destinoInicialSegunSesion()` → si hay sesión restaurada va a `HOME`, si no a `LOGIN`. **[CONFIRMADO - PROBLEMA]**: `AutenticacionRepository.iniciarSesion` (Admin) NO valida que `rol == "ADMIN"`; solo comprueba que el documento exista y `activo == true`. Un usuario con rol CLIENTE que conozca las credenciales podría entrar en la app Admin (riesgo de seguridad conocido, ver §15 y §19). En la UI, si el ADMIN no tiene negocio creado se muestra el guard "crea tu negocio".
- **Cliente (`:appCliente`):** registro siempre con rol `CLIENTE`. El login valida `usuarios/{uid}` con rol CLIENTE. `destinoInicial()`: autenticado → si existe ficha vinculada o perfil pendiente va a `HOME`; si no, a `INICIO` (pantalla "¿Tu gimnasio ya te ha registrado?").

## Vinculación del CLIENTE (flujo clave, ver también §7)
- **VÍA 1 (única activa hoy [CONFIRMADO]):** El Admin crea la ficha (replica `clientes/{id}` con `firebaseUid: null` + `indices_clientes/{negocioId}_{DNI}` + `clientes_privados/{id}`). El cliente, desde `InicioScreen`, introduce **código maestro + DNI**:
  1. Resuelve `negocioId` con `negocios_publicos` por código maestro.
  2. Escribe en `perfiles_pendientes/{uid}` una **declaración mínima** `{ dni, negocioId }` con `SetOptions.merge()` (para que las Rules validen el acceso al índice/ficha).
  3. Busca `indices_clientes/{negocioId}_{dni}`.
     - **Si existe y `firebaseUid == null`** → Transaction: `clientes/{id}.firebaseUid = uid` + `usuarios/{uid} = {clienteId, negocioId}` (VÍA 1).
     - **Si existe y `firebaseUid != null`** → error "Ese DNI ya está vinculado a otra cuenta".
     - **Si NO existe** → error fijo **"No existe ningún cliente registrado con ese DNI."** ([CONFIRMADO] el código de VÍA 2 `crearFicha()` está CONSERVADO en `VinculacionRepository` pero NO se ejecuta desde esta entrada; hay test unitario que verifica ese rechazo).
  4. Tras éxito: borra `perfiles_pendientes/{uid}` y llama `notificarVinculacionAlAdmin(...)` (crea `notificaciones/vinculacion_{negocioId}_{clienteId}` tipo `VINCULACION`, falla en silencio).
- **"No tengo vinculación":** el cliente completa un perfil completo que se guarda en `perfiles_pendientes/{uid}` (VÍA 2 de datos, sin negocio) y entra al Home sin vincular (aviso visible). Más tarde solo puede VINCULARSE por VÍA 1 (código + DNI con ficha existente).
- **DNI:** editable por el cliente SOLO mientras no está vinculado (se guarda en `perfiles_pendientes`); una vez vinculado, la ficha vive en `clientes/{id}` y el DNI queda **bloqueado para el cliente** (solo el ADMIN lo cambia manteniendo el índice atómico).
- El documento `perfiles_pendientes/{uid}` admite dos modos (mínimo VÍA 1 o completo VÍA 2) y **solo se borra cuando la vinculación se completa con éxito**.

---

# 7. Base de datos / Firebase

## 7.1 Room (solo Admin) — [CONFIRMADO]

`ClientesDatabase` **versión 17**, 10 entidades. Migraciones registradas en `di/AppModule.kt`: `11→12` (columna `horaDesdeReserva` en sesion), `12→13` (elimina `tieneLlave` del cliente), `13→14` (economía: `servicio.precio`; movimiento pasa a `servicios List<Int>`, `precioFinal`, `metodoPago`), `14→15` (cliente con `moroso` y `fechaEntradaMorosidad`), `15→16` (`fechaNacimiento` nullable) y **`16→17` (F2: `cliente.exentoMorosidad` + tabla `eliminacion_pendiente`)**. **Aún hay `.fallbackToDestructiveMigration()`** con TODO(PRODUCCION) para sustituirlo por migraciones reales antes de publicar.

Tablas y campos principales:
- **`cliente`**: idCliente PK autoincremental, nombre, apellidos, dni (índice único), telefono, email?, foto, fechaNacimiento Long, fechaRegistro, fechaAlta?, fechaBaja?, estado (enum), observaciones?, negocioId?, serviciosContratados (CSV Int), firebaseUid?, **moroso Boolean**, **fechaEntradaMorosidad Long?**. NO tiene `tieneLlave`.
- **`servicio`**: idServicio PK, negocioId, nombre, descripcion, activo, **precio Double**.
- **`sesion`**: idSesion PK, negocioId, idServicio, fecha Long, hora "HH:mm", duracionMinutos, capacidad, plazasDisponibles, **horaDesdeReserva String?**.
- **`reserva`**: idReserva PK, negocioId, idSesion, idCliente, fechaReserva; índice único `(idSesion, idCliente)`.
- **`movimiento`**: idMovimiento PK, idCliente, **servicios List<Int>**, fechaInicio, fechaFin, **precioFinal Double**, estado (PENDIENTE/PAGADO), fechaPago Long?, **metodoPago?**, observaciones?.
- **`gasto`**: idGasto PK, concepto, importe, fecha, observaciones?.
- **`solicitud`** (LEGACY/inerte): tipo TipoSolicitud **CLASE/BAJA** (deuda: el contrato remoto es ALTA/BAJA), estado, detalle, fechas.
- **`clase` / `sesion_clase`** (LEGACY transitorio): modelo antiguo Clase→SesionClase→Reserva, sin uso funcional.

## 7.2 Firestore — colecciones y operaciones [CONFIRMADO, extraído de `firestore.rules`]

Regla general: **bloqueo por defecto** salvo rutas declaradas. Roles: `ADMIN` y `CLIENTE`. Identidad: `usuarios/{uid}` es la fuente de roles; no hay Custom Claims.

| Colección | Documento / ID | Operaciones y quién |
|---|---|---|
| `usuarios/{uid}` | `{ rol, activo, clienteId, negocioId }` | get (propio), create autenticado, update ADMIN (negocioId), update CLIENTE VÍA 1/VÍA 2 (clienteId/negocioId). delete prohibido. |
| `negocios/{negocioId}` | `{ adminUid, nombre, codigoMaestro, logo }` | get/list/create/update ADMIN propietario. delete no. `negocioId` = UID del ADMIN. |
| `negocios_publicos/{negocioId}` | `{ nombre, codigoMaestro, logo }` | get/list cualquier autenticado; create (negocio nuevo) y update ADMIN (solo codigoMaestro/nombre/logo). |
| `clientes/{idCliente}` | `{ idCliente, negocioId, firebaseUid, nombre, apellidos, dni, telefono, email, foto, fechaNacimiento, fechaRegistro, fechaAlta, fechaBaja, estado, serviciosContratados:[int], fechaInicioActual, fechaFinActual }` | get: ADMIN su negocio; CLIENTE su propia ficha; CLIENTE sin vínculo VÍA 1 (declaración en perfiles_pendientes) y VÍA 2 (`resource==null`). list solo ADMIN. create ADMIN (con índice en el mismo batch) / CLIENTE VÍA 2. update CLIENTE solo campos personales (DNI y campos admin bloqueados) y update VÍA 1 (firebaseUid); update ADMIN con cambio de DNI atómico (índice viejo→nuevo). **delete prohibido**. Los valores de `estado` son los nombres del enum (ACTIVO/BAJA/ARCHIVADO/REGISTRADO). MOROSO se calcula, no se almacena. |
| `clientes_privados/{idCliente}` | `{ negocioId, observaciones }` | Solo ADMIN del negocio. delete prohibido. |
| `indices_clientes/{negocioId}_{dni}` | `{ negocioId, dni, clienteId }` | get: ADMIN su negocio, o CLIENTE cuyo DNI+negocioId coinciden con su perfiles_pendientes. create solo en el mismo batch que su ficha. **update prohibido**. delete solo ADMIN al cambiar DNI. list prohibido. |
| `perfiles_pendientes/{uid}` | VÍA 1 `{ dni, negocioId }` o VÍA 2 completo `{ nombre, apellidos, dni, telefono, email, foto, fechaNacimiento }` | create/get/update/delete solo del propio uid. list prohibido. |
| `servicios/{idServicio}` | `{ idServicio, negocioId, nombre, descripcion, activo, precio }` | get/list/create/update/delete ADMIN; CLIENTE vinculado puede `get` servicios ACTIVOS de su negocio (necesario en la Transaction de reserva). |
| `sesiones/{idSesion}` | `{ idSesion, negocioId, idServicio, fecha, hora, duracionMinutos, capacidad, plazasDisponibles, horaDesdeReserva }` | get/list/create/update/delete ADMIN (create exige servicio existente, del negocio y ACTIVO). CLIENTE vinculado: get/list solo sesiones de servicios **contratados + ACTIVOS + estado cliente ACTIVO** (`clientePuedeAcceder`). CLIENTE puede `update` SOLO plazas ±1 en Transaction con su reserva. |
| `reservas/{clienteId}_{sesionId}` | `{ idReserva, negocioId, sesionId, clienteId, fechaReserva }` | ID determinista. create/cancelar ATÓMICOS por Transaction (CLIENTE): `reservaCreaValida` exige cliente ACTIVO, servicio contratado+activo, plazas = anterior−1 y ≥0; cancelar = delete + plazas+1 (sin superar capacidad). ADMIN consulta y elimina con cascadas/ajuste de plazas. CLIENTE update: false. |
| `movimientos/{movimientoId}` | documento de movimiento (réplica Admin) | **Solo ADMIN** del negocio: get/list/create/update/delete. [CONFIRMADO] existe `match /movimientos/` (líneas ~795-811). |
| `solicitudes/{solicitudId}` | `{ idSolicitud, negocioId, idCliente, firebaseUid, fechaSolicitud, estado(PENDIENTE/ACEPTADA/RECHAZADA), tipo(ALTA/BAJA), fechaResolucion, resueltaPor, motivo }` | create CLIENTE (PENDIENTE, tipo ALTA/BAJA, no BAJA/ARCHIVADO); update ADMIN solo resolución desde PENDIENTE; delete ADMIN solo si no está PENDIENTE. ID determinista `baja_{clienteId}_{fechaSolicitud}`. |
| `notificaciones/{id}` | `{ negocioId, titulo, mensaje, tipo, origen, modoDestino, clienteId?, idsClientes?, fechaCreacion, fechaEnvio?, fechaProgramada?, estado(PENDIENTE/ENVIADA/PROGRAMADA/CANCELADA/ERROR), programada }` | get/list/delete ADMIN. create ADMIN (tipos MANUAL/MOROSIDAD/BAJA_CONFIRMADA/PROGRAMADA/SOLICITUD_BAJA). **create CLIENTE solo tipo VINCULACION** con `hasOnly` y `!exists` anti-duplicado. update ADMIN (estado/fechaEnvio/idsClientes). |
| `notificaciones_por_destinatario/{clienteId}_{notificacionId}` | buzón del CLIENTE (`leida`, `fechaLeida`, etc.) | create ADMIN; get/list CLIENTE (solo suyas) o ADMIN; update CLIENTE solo marcar leída; delete ADMIN. |
| `configuracion_notificaciones/{negocioId}` | `{ morosidad, bajaConfirmada }` | Solo ADMIN. delete prohibido. |
| `clientes/{idCliente}/dispositivos/{token}` (subcolección) | `{ token, plataforma, notificacionesActivadas, updatedAt }` | create/update/delete CLIENTE dueño de la ficha (token FCM); get/list ADMIN del negocio. |
| `clases/{claseId}` y relacionadas | — | **LEGACY transitorio** (reglas presentes para no romper históricos). |

**Notas importantes de Rules [CONFIRMADO]:**
- El helper `clientePuedeAcceder` exige `c.estado == "ACTIVO"` (regla definitiva cerrada en esta línea de trabajo). Un cliente **moroso (flag) con estado ACTIVO SÍ accede**; REGISTRADO/ARCHIVADO/BAJA NO.
- El ruleset local SÍ contiene las claves económicas `moroso`/`deuda`/`fechaEntradaMorosidad`/`exentoMorosidad` en el `update` de `clientes` tras F2 (la sincronización de resumen económico está CABLEADA; ver §24 y la ACTUALIZACIÓN al inicio).
- Índices compuestos necesarios en producción (creados según AGENTS.md en `gestorpro-50e83`): `sesiones(idServicio, negocioId)`, `reservas(clienteId, negocioId)`, `reservas(sesionId, negocioId)`. El índice `notificaciones(estado, fechaProgramada)` para Functions programadas está **documentado pero pendiente de crear** [INFERIDO según AGENTS.md].

## 7.3 Firebase Storage — [CONFIRMADO en `storage.rules`]
- `negocios/{negocioId}/logo.jpg`: lectura cualquier autenticado; escritura solo ADMIN propietario (`usuarios/{uid}.rol == "ADMIN"` y `negocioId`). El Cliente descarga por URL con Coil.
- `clientes/{clienteId}/foto.jpg`: reglas preparadas (ADMIN del negocio escribe, CLIENTE solo su propia foto, image/* ≤ 5 MB) para una migración futura de fotos; la app hoy guarda fotos en local. **El límite decidido es 10 MB** (§25-C6) con compresión/redimensionado automático en la app; las reglas preparadas con 5 MB de referencia quedan **desactualizadas** y se alinearán al implementar (no se modifican en la fase documental; la implementación de compresión sigue PENDIENTE, §25-D10). Las decisiones de producto sobre logos y fotos remotas están en **§25**.
- Bloqueo del resto del bucket.

## 7.4 Firestore como espejo (Admin) vs fuente de verdad (Cliente)
- En Admin, la réplica a Firestore es **write-through por entidad**: cliente (con `clientes_privados` e índice), servicios, sesiones, reservas (transacciones atómicas con plazas), movimientos (colección `movimientos/{id}`), períodos económicos (`fechaInicioActual`/`fechaFinActual` en `clientes/{id}`), solicitudes y notificaciones.
- **La replicación del resumen económico (moroso/deuda) NO está implementada** ([CONFIRMADO] `util/MovimientoFirestore.resumenDeCliente` no tiene consumidores; `ClienteRemotoRepository.actualizarResumenEconomicoRemoto` no existe en el árbol actual). Solo se replica el período. Esto limita lo que Cloud Functions puede calcular desde Firestore. **Decisión de negocio ya cerrada en §24** (replicar `movimientos/{id}` + resumen); la implementación está pendiente.

---

# 8. Funcionalidades implementadas (estado por feature)

> Leyenda de estado: **COMPLETA** = funciona y cableada; **PARCIAL** = hay parte; **PROBLEMÁTICA** = tiene bug/riesgo conocido; **NO TERMINADA** = a medias.

## 8.1 Autenticación (Admin y Cliente) — COMPLETA
Registro, login, logout, recuperación (genérica) reales con Firebase Auth + documento `usuarios/{uid}`.
Archivos: `AutenticacionRepository.kt` en cada app; `MainViewModel.kt`; screens `ui/auth/*`.
Problemas: login Admin sin validar rol == ADMIN (§15-E).

## 8.2 Negocio (Admin) — COMPLETA
Crear negocio (Batch `negocios` + `negocios_publicos` + `usuarios.negocioId`), editar nombre y código maestro, logo a Storage con WriteBatch de URLs (`negocios/{uid}/logo.jpg`).
Archivos: `NegocioRepository.kt`, `ui/configuracion/{MiNegocioScreen,CrearNegocioScreen}`.
Pendiente real: bucket habilitado (Storage). Histórico: "crear negocio PERMISSION_DENIED" no cerrado (§15).

## 8.3 Clientes (Admin) — COMPLETA con avisos
Lista con búsqueda/filtros/estados; alta/edición con foto; servicios contratados (multi-select de ACTIVOS); archivar/restaurar; baja (ver §8.10); sincronización Room↔Firestore write-through con banner y reintento; incorporación de clientes remotos a Room al entrar en ClientesScreen; ids de rango alto.
Archivos: `ClienteRemotoRepository.kt`, `ClienteViewModel.kt`, `ClientesScreen.kt`, `PerfilClienteAdministradorScreen.kt` (archivo grande), `AñadirClienteScreen.kt`, `DialogoEditarServiciosContratados.kt`, `util/IdCliente.kt`.
Problemas: la causa raíz del `PERMISSION_DENIED` del alta (seed Room con ids 1–20) ya se eliminó; queda verificar en dispositivo con BD limpia y retirar los logs `[DIAG alta]` (§14-A); `AñadirClienteScreen` exige fechaNacimiento obligatoria (decisión actual); uso de `!!` y strings hardcodeados.

## 8.4 Servicios, Sesiones, Reservas (modelo nuevo, Admin) — COMPLETA
- `ServiciosScreen`: listado ACTIVOS/DE BAJA mostrando **precio**; crear/editar (con campo precio ≥ 0), dar de baja/reactivar/eliminar.
- `DetalleServicioScreen`: detalle + sesión del día con plazas reales (refresco en `onResume`).
- `ProgramarSesionesScreen`: generar sesiones desde/hasta por día con hora propia, apertura global de reservas, duración y capacidad.
- `EditarSesionScreen`: ver/editar sesión (fecha/hora/apertura/duración/capacidad). **Sin botón eliminar sesión en UI** aunque el ViewModel tiene `eliminarSesion`.
- `SesionReservasScreen`: clientes reservados (solo lectura; al pulsar va al perfil). **El ADMIN no puede cancelar reservas desde aquí.**
- Reservas atómicas Room (`withTransaction`, plazas±1, duplicado por `(idSesion,idCliente)`) + Firestore (`runTransaction`), cascadas al regenerar/desactivar/eliminar servicios y al eliminar sesiones (Room + remoto con reintentos por sesión).
Archivos: `data/repository/{Servicio,Sesion,Reserva}Repository.kt`, `data/firebase/{Servicio,Sesion,Reserva}RemotoRepository.kt`, `ui/servicios/*`, `ui/viewmodel/{Servicio,Sesion}ViewModel.kt`.
Problemas: logs `[DIAG sesiones]` temporales; botón de eliminar sesión y cancelación admin sin UI (§9-8/§9-9).

## 8.5 Economía (Admin) — PARCIAL en su extremo remoto (modelo DEFINIDO — ver §24)
- **Room (Fases 1-5):** modelo movimiento multi-servicio con `precioFinal` + `metodoPago` + estado PENDIENTE/PAGADO + `fechaPago`; servicios con `precio`; motor puro de morosidad `MovimientoMorosidad` (un único punto donde se aplica la regla de deuda/morosidad; debe aplicarse según la decisión §24: un PENDIENTE ya es deuda y genera morosidad); `ClienteDao.obtenerIdsMorosos` lee `moroso=1`. **`EstadoCliente.MOROSO` sigue en el enum pero ya no se persiste como estado.**
- **UI:** `EconomiaScreen` (resumen ingresos/gastos/balance + CRUD de gastos + movimientos en modo lectura); CRUD completo de movimientos en el perfil del cliente.
- **Firestore (Fase 6 → F2 cableada, 2026-09-03):** la réplica remota quedó **CABLEADA** en el working tree: `MovimientoRepository` persiste Room → recalcula morosidad → replica `movimientos/{id}` y publica el resumen `moroso`/`deuda`/`fechaEntradaMorosidad`/`exentoMorosidad` + fechas en `clientes/{id}`; `MovimientoFirestore.resumenDeCliente` tiene consumidor (`ClienteRemotoRepository.actualizarResumenEconomicoRemoto`); eliminaciones fallidas persistidas en `eliminacion_pendiente`; Rules locales admiten esas claves (151/151). `MovimientoDao.insertarMovimiento` sigue en `Unit` porque los ids nuevos son globales preasignados (`IdMovimiento`), no autoincrement. (Histórico: la Fase 6 original fue revertida por el desarrollador; F2 la reconstruyó y cableó.)
Archivos: `util/{MovimientoMorosidad,MovimientoPrecio,MovimientoFirestore}.kt`, `MovimientoRepository.kt`, `MovimientoRemotoRepository.kt`, `EconomiaScreen.kt`, `PerfilClienteAdministradorScreen.kt`.
Problemas: la réplica remota (`movimientos/{id}` + resumen en `clientes/{id}`) quedó **CABLEADA en F2 (2026-09-03)** — ver ACTUALIZACIÓN al inicio; pendiente solo la prueba manual y el deploy autorizado de Rules. Véase bug histórico de `fechaPago` (resuelto en código, §15-8) y su matiz.

## 8.6 Solicitudes de baja (Cliente → Admin) — COMPLETA
El CLIENTE solicita baja desde `CuentaScreen`; el ADMIN acepta (Transaction: solicitud ACEPTADA + cliente BAJA + fechaBaja, y aplica consecuencias de baja) o rechaza; puede eliminar resueltas (no PENDIENTES). Búsqueda por datos reales. Aviso SOLICITUD_BAJA al ADMIN al cargar PENDIENTES.
Archivos: `SolicitudRemotoRepository.kt`, `BajaClienteRemotoRepository.kt`, `ui/solicitudes/SolicitudesScreen.kt`, `ui/viewmodel/SolicitudesViewModel.kt`; appCliente: `SolicitudRepository.kt`, `CuentaScreen.kt`.

## 8.7 Baja de cliente unificada — COMPLETA
`BajaClienteRemotoRepository.bajaEfectiva`: cancela reservas futuras (Room y Firestore, liberando plazas), conserva pasadas y `serviciosContratados`, genera `BAJA_CONFIRMADA` con ID determinista si la config lo permite. La usan la baja directa y la aceptación de solicitud.
Acceso del CLIENTE: BAJA no lee sesiones ni reserva (app y Rules); Home oculta la card de Actividades a no-ACTIVOS.

## 8.8 Notificaciones (Admin) y buzón (Cliente) — COMPLETA (envío real pendiente)
- Admin: `GestionNotificacionesScreen` (lista, tipos con etiqueta "Vinculación"), `CrearNotificacionScreen` (individual/grupo/todos; inmediata o programada; selección individual reutiliza `SeleccionarClientesScreen` modo UNO), `ConfigNotificacionesScreen` (morosidad, recordatorio 24h, baja confirmada), `SeleccionarClientesScreen`.
- Cliente: buzón `ListaNotificacionesScreen` (`notificaciones_por_destinatario`), switch "Recibir avisos" (`notificacionesActivadas`, DataStore + doc de dispositivo), `FcmService`.
- El **envío real (FCM)** lo harían las Cloud Functions (Fase E), que NO están desplegadas. Hoy las notificaciones quedan como documentos `PENDIENTE`/`PROGRAMADA`; el push no se envía. La política de privacidad declara que el push "no está operativo en esta versión".
Archivos: `data/firebase/NotificacionRemotoRepository.kt`, `ui/notificaciones/*`, `ui/viewmodel/NotificacionesViewModel.kt`; appCliente: `NotificacionRepository.kt`, `DispositivoRepository.kt`, `FcmService.kt`, `ListaNotificacionesScreen.kt`.

## 8.9 Clases/Actividades de hoy del CLIENTE + reservas — COMPLETA
`ClasesScreen` (appCliente): carga solo sesiones de HOY de servicios contratados y activos; estados (noVinculado/cargando/error/dadoDeBaja/estadoNoActivo/sinServicios/sinSesionesHoy/lista); respeta `horaDesdeReserva`; **reservar y cancelar** con Transactions; botón "Reservas abren a las HH:mm" deshabilitado si la apertura no ha llegado.
Archivos: `SesionesClienteViewModel.kt` (contiene logs `ClasesDiagnostico`), `ReservasClienteViewModel.kt`, `ClasesScreen.kt`, `SesionRepository.kt`, `ReservaRepository.kt` (appCliente).
Problemas: sin pantalla "Mis reservas" (el VM tiene `reservasVisibles` sin consumidor).

## 8.10 Perfil y configuración del CLIENTE — COMPLETA
Ver/editar datos personales (sin vínculo → `perfiles_pendientes`, DNI editable; vinculado → `clientes/{id}`, DNI bloqueado). `CompletarPerfilScreen` exige fechaNacimiento (obligatoria). `EditarPerfilScreen` deja la fecha de nacimiento en texto libre sin validar (inconsistencia de UI menor).
`HomeScreen`: nombre/logo del negocio, banner "No estás vinculado", indicador de estado (ACTIVO/PAGO_VENCIDO con borde rojo/BAJA/REGISTRADO/ARCHIVADO), aviso de morosidad con enlace "aquí" a `CUENTA`.
`ConfiguracionScreen` (Cliente): Mi perfil, **Mi cuenta** (→ `Routes.CUENTA`, donde está "Solicitar baja"), Notificaciones (switch), tema claro/oscuro/sistema, política de privacidad y términos.
Archivos: `ui/auth/{MiPerfil,EditarPerfil,CompletarPerfil,Cuenta}Screen.kt`, `ui/home/{HomeScreen,ClasesScreen}.kt`, `ui/configuracion/*`, `MainViewModel.kt` (appCliente).

## 8.11 Utilidades varias — COMPLETA/PARCIAL
- `data/export/ExportManager.kt` (Admin): exporta/importa/restaura JSON de clientes+movimientos+gastos **solo** (no servicios/sesiones/reservas/solicitudes/negocio).
- `ui/components/Botones.kt` en ambas apps: kit `AppPrimaryButton`/`AppSecondaryButton`/`AppDialogConfirmButton`/etc. En Admin fijados al azul corporativo `#1E88E5`.
- Fotos: `FotoUtils` local (galería + cámara con `TakePicture` + `FileProvider`); el guardado se hace en el callback del resultado.
- Política de privacidad con contenido real en ambas apps.

## 8.12 Legacy / transitorio (NO eliminar sin tarea específica)
- Admin: tablas/entidades/DAOs/repositorios/ViewModel de `Clase`/`SesionClase`, `ui/clases/*` (inalcanzable desde la UI activa), `SolicitudEntity` Room inerte, `ui/components/{ServicioItem,MovimientoItem,ResumenCard}` y `DialogoSeleccionarClientes` (sin uso), modelo `SesionConClase`.
- Rules: colección `clases/{claseId}` legacy.

---

# 9. Funcionalidades pendientes (por prioridad)

## CRÍTICO (para que el sistema funcione correctamente en producción)
1. **Conciliar y desplegar `firestore.rules`** (local = 143 tests) frente a lo desplegado en producción. [DESCONOCIDO] el estado exacto del ruleset desplegado (requiere consola/CLI). Riesgo: hasta el deploy, en producción un cliente REGISTRADO/ARCHIVADO podría operar y la notificación VINCULACION podría no poder crearse. Depende de: revisar `firebase deploy --only firestore:rules` (requiere autorización). Historial de deploys en §14-B, §15-17 y Anexo A.
2. **Verificación final del alta ADMIN (PERMISSION_DENIED)** — la causa raíz (seed Room con ids 1–20) ya se eliminó y está commiteada; queda **confirmar en dispositivo con BD limpia** que el primer cliente real obtiene `idCliente=1` y se replica (logs `[DIAG alta] existencia previa -> false,false,false`), limpiar un posible documento huérfano con aprobación si persistiera, y **retirar el logging temporal `[DIAG alta]`**. Relacionado con el backfill de índices (DRY-RUN existe).
3. **Cerrar la implementación de la réplica económica** (decisión de negocio ya tomada, §24): la morosidad que muestra Firebase no puede calcularse con precisión porque no se replican `moroso`/deuda; hay que **implementar** lo decidido en §24 (campos en `clientes/{id}` + `movimientos/{id}`, compatibilidad con Rules/Functions).
4. **Migraciones Room sin `fallbackToDestructiveMigration`** antes de publicar (TODO(PRODUCCION) en `ClientesDatabase.kt` y `AppModule.kt`). Riesgo de pérdida de datos si se publica con el fallback.
5. **Cloud Functions + FCM + Storage en producción** (bloqueado por plan Blaze/decisión del propietario): crear bucket, índice `notificaciones(estado, fechaProgramada)`, `npm install` en `functions/`, `deploy`, pruebas FCM reales.

## IMPORTANTE (antes de considerar el proyecto terminado)
6. **Login Admin sin validar `rol == "ADMIN"`** (riesgo de seguridad confirmado).
7. **Cambiar contraseña real en Cuenta Admin** (hoy placeholder sin `updatePassword`).
8. **Botón "eliminar sesión"** en `EditarSesionScreen` (VM ya tiene `eliminarSesion`).
9. **Cancelar reserva desde el ADMIN** en `SesionReservasScreen` (hoy solo lectura).
10. **Pantalla "Mis reservas" del CLIENTE** (VM preparado, sin UI) — si el propietario la aprueba.
11. **Retirar logs de diagnóstico temporales**: `[DIAG alta]` (`ClienteRemotoRepository`), `[DIAG sesiones]` (`SesionViewModel`, `SesionRemotoRepository`, `ReservaRemotoRepository`), `ClasesDiagnostico` (`SesionesClienteViewModel` en appCliente).
12. **Reactivación/decisión sobre VÍA 2** de vinculación (código conservado pero inactivo; hoy "si el índice no existe → error"). Decidir si se reactiva con la regla de fecha de nacimiento opcional o se elimina el código.
13. **Implementar la sincronización del resumen económico** a `clientes/{id}` (campos `moroso`/`deuda`/`fechaEntradaMorosidad` + Rules + tests) conforme a la decisión §24 (no se trata de decidir si se reintroduce: está decidido replicar; el helper `resumenDeCliente` debe cablearse).

## MEJORA
14. Unificar a `Botones.kt` los botones Material 3 directos que quedan en pantallas activas (FABs, TextButton de DatePickers, `MiNegocioScreen`, `PerfilClienteAdministradorScreen`, `EconomiaScreen`, `DetalleServicioScreen`, `ProgramarSesionesScreen`, notificaciones, auth de `:app`, etc.). NO tocar `ui/clases/*` (legacy).
15. Mover textos hardcodeados a `strings.xml` (hoy 0 uso de recursos de string; solo `app_name`). Es deuda grande.
16. Migrar fotos de perfil locales → Firebase Storage (reglas ya preparadas en `storage.rules`) cuando haya bucket.
17. `AñadirClienteScreen.kt` con "ñ" en el nombre de archivo: evaluar renombrado (riesgo: imports).
18. `DetalleVisuales.kt` (kit de detalle): decidir si se recupera o se elimina definitivamente (hoy no existe).
19. Limpieza de basura versionada: `build_*.txt`, `files.txt`, `structure.txt`, `app_kt_files.txt`, `conversacionEstilo.md`, `EXPLICACION_BASE_DE_DATOS.html`, `AI_RULES.md`, `AUDITORIA_PROYECTO_MIGRACION_KMP.md`, `firestore-debug.log` (raíz y `firestore-tests/`). (Algunos pueden ser documentos deliberados del propietario; confirmar antes de borrar.)

## OPCIONAL (no son tareas pendientes obligatorias)
20. ~~Módulo económico completo en appCliente (cuotas/movimientos/pagos)~~ — **descartado** (decisión cerrada §24: el CLIENTE no tiene economía).
21. Auditoría de migración KMP (existe `AUDITORIA_PROYECTO_MIGRACION_KMP.md`).
22. Tests instrumentados de Compose (hoy solo `ExampleInstrumentedTest`).
23. Página de estado (Home Admin) con resumen económico/morosidad.

---

# 10. Decisiones de diseño ya tomadas

| # | DECISIÓN | POR QUÉ | NO MODIFICAR SIN CONSULTAR |
|---|---|---|---|
| D1 | **Dos apps independientes** (`:app` + `:appCliente`) sobre el mismo proyecto Firebase | Separar roles y superficies UI; comparten datos vía Firestore | No fusionar en una app "multirol" |
| D2 | **Room = fuente de verdad local del Admin**; Firestore = espejo write-through; el Cliente usa Firestore directo sin Room | Trabajo offline local + réplica remota controlada | No cambiar la fuente de verdad sin tarea explícita |
| D3 | **Modelo `Cliente → Servicio → Sesión → Reserva`** (sin entidad Clase en el flujo nuevo) | Simplificar la programación de actividades | No reintroducir Clase en el flujo nuevo |
| D4 | **`Clase`/`SesionClase` y su UI/DAOs/ViewModel legacy TRANSITORIOS** (no eliminados) | Evitar pérdidas antes de una tarea de limpieza dedicada | NO ELIMINAR ni conectar sin tarea específica |
| D5 | **Vía B (enlace/deep link de vinculación) DESCARTADA**; sin `vinculaciones` ni `codigoVinculacion` | Decisión de producto | NO reintroducir nunca |
| D6 | **Vinculación por código maestro + DNI** con `indices_clientes/{negocioId}_{dni}` y documentId determinista | Unicidad negocio+DNI con Transaction | Mantener unicidad y el ID determinista |
| D7 | **`perfiles_pendientes/{uid}` admite DOS modos** (VÍA 1 mínima con merge y VÍA 2 completa) y solo se borra al completar la vinculación | No perder datos del usuario ante fallos | No borrar el perfil pendiente ante errores |
| D8 | **DNI**: lo cambia solo el ADMIN manteniendo el índice atómico; el CLIENTE lo edita solo pre-vinculación; bloqueado después | Integridad de la identidad | No permitir al cliente cambiar DNI vinculado |
| D9 | **Eliminación de `tieneLlave`**: la llave se gestiona como un **servicio normal** | Simplificar el modelo | No reintroducir el campo especial |
| D10 | **Reservas atómicas** con documentId `reservas/{clienteId}_{sesionId}`, plazas ±1 en Transaction (Room y Firestore), sin update directo de CLIENTE | Evitar sobre-reserva y duplicados | No abrir update de reservas al cliente |
| D11 | **Acceso CLIENTE solo con estado `ACTIVO`** (`clientePuedeAcceder`); morosidad es flag, no estado | Regla de negocio definitiva | No volver a `!= "BAJA"` |
| D12 | **`EstadoCliente.MOROSO` ya no se persiste** como estado (es flag `moroso`), aunque el enum conserva el valor | Evitar estados duplicados | Antes de eliminar el enum, tarea explícita (riesgo de referencias) |
| D13 | **`TipoSolicitud` Room (CLASE/BAJA) y `SolicitudEntity` Room legacy inertes** | Pendiente de decisión sobre la tabla antigua | Adaptar a ALTA/BAJA solo con tarea específica + migración |
| D14 | **Economía (modelo definitivo, §24)**: movimiento multi-servicio (lista ids + precioFinal + método pago), precio en servicio, morosidad por motor puro `MovimientoMorosidad` | Modelo económico rico decidido por el propietario | No revertir el modelo sin consultar (decisiones §24) |
| D15 | **Notificaciones**: buzones `notificaciones_por_destinatario`, docs `notificaciones/{id}` con estados, `configuracion_notificaciones` por negocio, IDs deterministas | Preparar Cloud Functions | No cambiar IDs deterministas (los comparte CF) |
| D16 | **`BajaClienteRemotoRepository` = lógica de baja UNIFICADA** (directa y por solicitud) | Convergencia y coherencia | No duplicar lógica de baja en otros sitios |
| D17 | **Recuperación de contraseña con mensaje genérico** siempre | No revelar qué emails existen | No mostrar errores específicos |
| D18 | **Terminología visible "Actividades"** en cards (Admin y Cliente) mientras el código interno sigue `Servicio`/`servicios` | Decisión de producto/UI | No renombrar colecciones/modelos por el cambio de etiqueta |
| D19 | **`android:allowBackup="false"`** en ambas apps + TODO(PRODUCCION) para decidir al publicar | Evitar que Google restaure BDs viejas en desarrollo | Decidir reactivación y reglas antes de publicar |
| D20 | **Botones App* con azul corporativo `#1E88E5` en Admin** (el tema usa Material You/dinámico) | Identidad visual | No reemplazar por colores dinámicos sin consultar |
| D21 | **`compileSdk 36 minor 36.1`, target 36, min 26; Java 11; sin minify en release** | Configuración actual | No cambiar versiones por iniciativa propia |
| D22 | **El `negocioId` del ADMIN es su UID** y viaja como String; ids de cliente/servicio/sesión/reserva son enteros | Contrato remoto | Mantener tipos del contrato |
| D23 | **Strings hardcodeados en español** (deuda conocida, no migración general sin pedirla) | Convención actual | No iniciar migración masiva a strings.xml sin orden |
| D24 | **No hay capa use-case/domain**; MVVM + repositorios | Decisión de arquitectura | No exigir Clean Architecture como requisito automático |

---

# 11. Reglas del proyecto (comportamientos que no deben cambiar)

Extraídas de AGENTS.md y verificadas en el código. Resumen operativo:
- Roles remotos exactos `ADMIN`/`CLIENTE`. `usuarios/{uid}` gobierna permisos; **sin Custom Claims**.
- Un ADMIN solo accede a su negocio; un CLIENTE solo a sus datos/reservas/solicitudes/sesiones de servicios contratados y activos. **Los clientes nunca acceden a `movimientos` ni a `clientes_privados`.**
- `clientePuedeAcceder` exige **estado `ACTIVO`** para leer sesiones y reservar.
- La morosidad es un **flag independiente** (deuda), no un estado administrativo.
- Creación del negocio, `negocios_publicos` y `usuarios/{uid}.negocioId` en el **mismo Batch**.
- Réplica de clientes en un único Batch con `indices_clientes` y `clientes_privados`. Cambio de DNI = índice atómico (borrar viejo + crear nuevo en el mismo Batch).
- `delete` prohibido en `clientes`; el borrado local es baja lógica remota. Delete de solicitudes PENDIENTES prohibido.
- Consultas Firestore **compatibles con Security Rules** (las Rules no filtran después; incluir `negocioId` en queries).
- Valores de `clientes.estado` remotos: `ACTIVO`, `BAJA`, `ARCHIVADO`, `REGISTRADO` (MOROSO nunca se almacena).
- Los valores remotos de `solicitudes.tipo` son `ALTA` y `BAJA`.
- Reglas de código: español en identificadores/comentarios; PascalCase clases, camelCase funciones; no `!!` en código nuevo; no operaciones Room/Firestore/DataStore/red en main thread; no secretos en texto plano ni en archivos versionados; no añadir dependencias ni cambiar versiones sin avisar; usar `libs.versions.toml`; avisar antes de tocar `build.gradle.kts`; usar `StateFlow` + `collectAsStateWithLifecycle()` en lo nuevo; strings nuevos en `strings.xml` cuando se pueda; respetar una sola Activity por app; no introducir Vía B/deep links; los tests de Android funcionales se reservan para la fase final salvo petición expresa.
- Fotos: usar `FotoUtils`/`BotonSelectorFoto`; guardar la foto de cámara solo en el callback.
- Los ViewModels acceden a datos mediante repositorios, no por DAOs directamente.

---

# 12. Notificaciones

- **Estado:** preparación completa en cliente; envío real PENDIENTE (Functions no desplegadas).
- **Modelo de datos:** `notificaciones/{id}` (registro), `notificaciones_por_destinatario/{clienteId}_{notificacionId}` (buzón, campo `leida`), `configuracion_notificaciones/{negocioId}` (switches), `clientes/{id}/dispositivos/{token}` (tokens FCM, con `notificacionesActivadas`).
- **Cliente:** `FcmService` (`onNewToken` registra token; `onMessageReceived` dibuja notificación local si `notificacionesActivadas`; canal `"notificaciones"` IMPORTANCE_HIGH creado al mostrar). Permiso `POST_NOTIFICATIONS` solicitado en `MainActivity` (Android 13+). Toggle en `ui/configuracion/NotificacionesScreen.kt`. Buzón en `ListaNotificacionesScreen.kt` (marcar leída).
- **Envío (Fase E, sin desplegar):** Cloud Functions 2ª gen en `functions/` — triggers `notificacionInmediata` (onDocumentCreated), `procesarProgramadas` (onSchedule 2 min), `recordatorioMorosidad` (1 h), `entradaMorosidad` (onUpdate clientes), `bajaConfirmada` (onUpdate clientes). Patrón: buzones deterministas → CLAIM atómico en Transaction (PENDIENTE→ENVIADA / PROGRAMADA→ENVIADA) → envío FCM por lotes ≤500 (`sendEachForMulticast`) → limpieza de tokens inválidos. Config global `europe-west1`, `maxInstances: 10`.
- **Problemas/limitaciones:** no desplegadas (sin Blaze); índice compuesto `notificaciones(estado, fechaProgramada)` pendiente de crear en producción; no hay deep links (al pulsar la notificación se abre `MainActivity` genérica). [DESCONOCIDO] estado real del proyecto Firebase respecto a FCM/sender (requiere consola).
- **⚠️ Frecuencias de `functions/` local superadas por decisiones (2026-09-03):** los `onSchedule` preparados en `functions/` (programadas cada 2 min, recordatorio de morosidad cada 1 h) eran **preparación provisional SIN desplegar** y quedan pendientes de la sesión de diseño de Cloud Functions. Las **decisiones de producto finales** están en **§25** (que prevalece): notificación manual = push + buzón; **NO** hay aviso automático por el simple hecho de existir un movimiento PENDIENTE (la deuda la gestiona el ADMIN); la notificación automática de morosidad se reserva al **moroso por fecha** (fin de período con cliente ACTIVO sin nueva cobertura); sin aviso de pago; BAJA_CONFIRMADA; sin rechazo de baja; avisos desactivados = sin push pero con buzón; push a todos los dispositivos activos; **programadas con precisión ~15 min** (no cada minuto, no 2 min); comprobación de morosidad ~diaria ~08:00; coste de Functions en equilibrio. La distinción "moroso por deuda / moroso por fecha" y sus transiciones (pago sin renovar, renovación, BAJA) están en §25-A.

---

# 13. Testing (estado real, ejecutado HOY)

### Probado y funciona [CONFIRMADO hoy]
- **Rules Firestore + Storage: 151/151** (`npm --prefix firestore-tests test`), emuladores `firestore,storage`, proyecto `gestorpro-rules-test`. Cubren: PRUEBA 1-18 (clientes/permisos/VÍA 1/VÍA 2/índices/perfiles pendientes/privados/negocios públicos/cambio DNI), 19-20 (Storage logo), 21-33 + 33A-33H (servicios + queries admin con negocioId), 34-53 (sesiones), 54-76 (reservas/transacciones/plazas/duplicado), 77-81 (cascadas admin), 82-88 (horaDesdeReserva), 89-98 (notificaciones), 99-108 (solicitudes de baja), 109-112 (bloqueo BAJA), 113-120 (regresión sesiones + borrado solicitudes + SOLICITUD_BAJA), 121-128 (acceso solo ACTIVO + VINCULACION), **129-136 (resumen económico: ADMIN ALLOW, otro negocio/CLIENTE DENY)**.
- **Unit `:app` (Admin): 68/68** (`:app:testDebugUnitTest`). Archivos: `MovimientoMorosidadTest` (22, regla deuda = TODOS los PENDIENTES + dos causas + exento + fechaEntrada=detección), `MovimientoFirestoreTest` (15, resumen con `exentoMorosidad`), `IdMovimientoTest` (3), `MovimientoPrecioTest` (10), `MovimientoPagoTest` (12), `NotificacionConfigTest` (5), `ExampleUnitTest` (1).
- **Unit `:appCliente`: 9/9** (`VinculacionRepositoryTest` 1 — rechazo de Vía A sin índice; `ReservaTest` 8).
- **Helpers de Functions: 13/13** (`node --test functions/test/ids.test.js functions/test/tokens.test.js`), solo módulos puros `ids.js`/`tokens.js`; `functions/` no tiene `node_modules` (no requiere instalación para estos tests).
- **Compilación:** ambos módulos compilan (los unit tests compilaron todo el main + tests).

### Probado y no se ejecuta (diagnósticos temporales)
- `firestore-tests/diagnostico_alta_cliente.test.cjs` (7 tests): no forma parte del suite; reproduce el payload real del alta contra Rules locales. Se ejecuta con su propio comando (ver AGENTS.md).
- `sessions-query-compatibility.test.cjs` (4 tests): runner que parchea reglas temporalmente.

### NO probado / pendiente
- Tests instrumentados reales (Compose) — decisión del proyecto: solo `ExampleInstrumentedTest`.
- Pruebas en dispositivo físico recientes de los flujos de reservas del CLIENTE, de la baja unificada, notificaciones push reales y regeneración de sesiones en producción (dependen de índices/despliegue).

---

# 14. Errores conocidos / pendientes

| # | ERROR | GRAVEDAD | CUÁNDO / REPRODUCIR | CAUSA CONOCIDA | POSIBLE SOLUCIÓN | ESTADO |
|---|---|---|---|---|---|---|
| A | Alta de cliente Admin → `PERMISSION_DENIED` | ALTA | Al crear un cliente (producción) en ciertos casos | **Causa principal identificada y eliminada (Sesión XX, 2026-09-01):** el seed automático de Room (`AppModule.insertarDatosPrueba`, vía `RoomDatabase.Callback.onCreate`) insertaba 20 clientes ficticios con ids 1–20; en una instalación nueva el primer cliente real recibía `idCliente=21/22` → colisión con documentos ya existentes en Firestore → `batch.set()` evaluado como `update` → Rules lo deniegan. **El seed fue ELIMINADO de `AppModule.kt`** (verificado: no existe hoy). Causa secundaria posible: documento huérfano previo (índice/ficha/privado) en el batch. El payload actual pasa las Rules locales (test de aislamiento 7/7) | Confirmar en dispositivo con BD limpia (primer cliente real → `idCliente=1` y réplica OK, logs `[DIAG alta] existencia previa -> false,false,false`); si persiste, limpiar el huérfano con aprobación; retirar los logs `[DIAG alta]` | CASI CERRADO (fix commiteado; falta verificación en dispositivo y retirar logging temporal) |
| B | Rules desplegadas vs local | ALTA | Producción | Ruleset desplegado puede estar desactualizado (históricamente quedó obsoleto varias veces; hubo deploys autorizados verificados byte-idénticos y un deploy en el checkpoint 2026-09-0X con las claves de resumen económico que luego el desarrollador revirtió en local; el CIERRE 2026-09-03 indica que el ruleset desplegado NO tiene el acceso estricto "ACTIVO" ni la rama VINCULACION) | Reconciliar y `deploy --only firestore:rules` tras validar 143 tests | [DESCONOCIDO] estado desplegado actual; PENDIENTE |
| C | Login Admin no valida `rol == ADMIN` | ALTA (seguridad) | Un usuario CLIENTE podría iniciar sesión en la app Admin | `iniciarSesion` (Admin) solo exige doc existente + `activo` | Validar `rol == "ADMIN"` en repositorio/login | ABIERTO |
| D | Cambiar contraseña (Admin) es placeholder | MEDIA | CuentaScreen → "Cambiar contraseña" | Diálogo sin llamada a `FirebaseAuth.updatePassword` | Implementar updatePassword con reautenticación | ABIERTO |
| E | Sin "eliminar sesión" ni "cancelar reserva ADMIN" en UI | MEDIA | EditarSesionScreen / SesionReservasScreen | VM tiene `eliminarSesion`; no cableado | Añadir botones/acciones | ABIERTO |
| F | Sin pantalla "Mis reservas" del CLIENTE | MEDIA (funcional) | — | `reservasVisibles` sin consumidor | Añadir pantalla (si se aprueba) | ABIERTO |
| G | Sincronización de resumen económico no implementada | MEDIA | Admin → Firestore solo replica período | `resumenDeCliente` sin consumidores; Rules sin claves moroso/deuda | **Decisión cerrada (§24): replicar** `movimientos/{id}` + resumen; implementar | ABIERTO (implementación, no decisión) |
| H | Regeneración de sesiones requirió replicar el servicio antes (regresión histórica) | RESUELTO | Generar sesiones daba PERMISSION_DENIED si el servicio no estaba en Firestore | `sesiones/create` exige servicio replicado | `SesionViewModel.generarSesiones` replica el servicio (idempotente) primero | CERRADO (PRUEBA 113-115) |
| I | Bug de edición de movimiento que reseteaba `fechaPago` | RESUELTO en código | Editar un movimiento en el perfil | Reconstrucción del objeto sin `fechaPago` | Ahora pasa por `MovimientoPago.resolver` que conserva fecha/método | CERRADO en código (falta validar en dispositivo). Matiz: apagar/encender "PAGADO" reescribe `fechaPago` a hoy |
| J | Login Admin: sesión restaurada sin negocio | MEDIA | — | Tras registrarse, el Admin debe crear su negocio | Guard de "crea tu negocio" en la UI | Gestionado en UI (flujo obligatorio) |
| K | `!!`, `collectAsState()`, strings hardcodeados, archivos enormes | DEUDA | En todo el código activo | Convenciones históricas | Migrar con tareas dedicadas | ABIERTO (deuda documentada; ver §21/§22) |
| L | `EstadoCliente.MOROSO` remoto rechazado por appCliente | BAJA/consistencia | Si llegara un estado MOROSO remoto | El enum lo conserva pero no se persiste | — | Documentado |
| M | Excepción de reservas: `reservas` update CLIENTE = false; borrado masivo batch fallaba en Rules | RESUELTO | Cascadas admin | `batch.delete` masivo fallaba (`reservaEliminadaValida` exige plazas+1) | Transacciones por sesión con reintentos (`MAX_RESERVAS_POR_SESION=498`) | CERRADO |
| N | Aviso de "No tienes permisos para sincronizar esta ficha" | HISTÓRICO | Apareció al sincronizar ficha con el resumen económico (ruleset desplegado del 01/09 sin las claves) | En el checkpoint 2026-09-0X se DESPLEGARON las Rules con esas claves (autorizado); luego el desarrollador revirtió el ruleset local sin ellas → hoy el aviso podría reaparecer si se intentara replicar el resumen | Ver A/G; conciliar ruleset | Documentado / estado actual del ruleset desplegado [DESCONOCIDO] |
| O | Sesiones no visibles en producción (colección vacía) | RESUELTO | appCliente no veía clases | Índices compuestos inexistentes (`sesiones(idServicio,negocioId)`, `reservas(clienteId,negocioId)`, `reservas(sesionId,negocioId)`) + bug `idSesion=0` (todas las sesiones se escribían en `sesiones/0`) | Índices creados vía API REST + fix `idSesion=0` (releer de Room con `obtenerSesionesFuturasPorServicioSync`) COMMITEADO | CERRADO en código; falta confirmar índices `READY` y regenerar sesiones en producción (logs `[DIAG sesiones]` para ello) |
| P | Crear negocio (Admin) con `PERMISSION_DENIED` | MEDIA | Alta del negocio en producción (histórico, Sesión VI en adelante) | Hipótesis: token de sesión caducado en el momento del Batch (`esAdmin()` falla) u orfandad de docs | Probar re-login (renovar token), diff de reglas, `project_id` de la APK | ABIERTO (histórico, sin reproducir recientemente) |
| Q | Cliente BAJA podía leer sesiones y reservar | RESUELTO | Antes de Sesión XXIV | No había bloqueo en app ni Rules (`estado != "BAJA"` era insuficiente o inexistente) | Bloqueo en app (`dadoDeBaja`, `estadoNoActivo`, ocultar card) + Rules `clientePuedeAcceder` exige `estado == "ACTIVO"` | CERRADO (PRUEBA 121-124) |

---

# 15. Cosas que YA se solucionaron (no reintroducir)

1. **Servicios no aparecían en Firestore / alta-baja de servicios PERMISSION_DENIED** → el ruleset desplegado no tenía `match /servicios`. Se redeployó y quedó coherente. Archivos: `firestore.rules`. (No reintroducir rulesets parciales.)
2. **`PERMISSION_DENIED` al generar sesiones** → el servicio no estaba replicado en Firestore; `SesionViewModel.generarSesiones` ahora replica el servicio antes (idempotente). PRUEBA 113-115.
3. **Cascadas de reservas con batch fallaban** → se sustituyó por `runTransaction` por sesión con reintentos. PRUEBA 77-81.
4. **Query admin sin `negocioId` denegada por Rules** (rules-are-not-filters) → se añadió `negocioId` a las queries de sesiones/reservas. PRUEBA 33A-33H.
5. **`idSesion=0` rompía la réplica de sesiones** → fix local en el flujo de generación.
6. **Baja de cliente: `CuentaScreen` inalcanzable y cliente BAJA podía reservar** → Configuración enlaza a "Mi cuenta"; bloqueo de BAJA en app (SesionesCliente/ReservaRepository/Home) y en Rules (`clientePuedeAcceder`). PRUEBA 109-112.
7. **Acceso de REGISTRADO/ARCHIVADO** → regla definitiva solo `ACTIVO`. PRUEBA 121-124.
8. **Edición de movimiento perdía `fechaPago`** → `MovimientoPago.resolver` (Fase 4).
9. **Notificación duplicada SOLICITUD_BAJA/BAJA_CONFIRMADA** → IDs deterministas + `existeNotificacion` + `!exists` en Rules.
10. **`tieneLlave` eliminado del modelo** (llave como servicio normal) con migración 12→13.
11. **Vía B descartada y regresión de Vía A corregida** (`653f117...`): `VinculacionRepository` busca `indices_clientes/{negocioId}_{dni}` exacto y no llama a `crearFicha()` desde la entrada código+DNI. PRUEBA 6B/6C + test unitario.
12. **Errores de sincronización de nombre/logo del negocio** resueltos con Batch en `negocios` + `negocios_publicos`.
13. **Seed automático de Room ELIMINADO** (`AppModule.insertarDatosPrueba`, 20 clientes/18 movimientos/4 gastos vía `RoomDatabase.Callback.onCreate`): ocupaba los ids 1–20 y era la causa raíz del PERMISSION_DENIED del alta en instalaciones nuevas (colisión de `idCliente` con Firestore). Eliminado en Sesión XX. No reintroducir seeds de prueba en `onCreate`.
14. **Límite de "1000 expresiones" en Firestore Rules** (histórico, 2026-08-24): se refactorizó todo el ruleset para eliminar `get()`/`getAfter()` redundantes sobre `clientes/{clienteId}` (caché, `resource.data`, `affectedKeys().hasOnly`). Commit histórico `63e88d7`. Al tocar Rules, evitar multiplicar lecturas por operación.
15. **Evolución del modelo de vinculación**: "Modelo A" (`vinculaciones/{codigo}`, códigos por ficha + deep link / Vía B) fue sustituido por el modelo actual **código maestro + DNI** con `indices_clientes`/`perfiles_pendientes` (Sesiones IV–IX). No reintroducir `vinculaciones`, `codigoVinculacion`, `EnlacePendiente` ni deep links.
16. **`PERMISSION_DENIED` al crear/generar sesiones** (Sesión XXV): causa = servicio sin replicar en Firestore (el negocio tenía `servicios` vacía); fix = `SesionViewModel.generarSesiones` replica el servicio antes (idempotente) + regresión asegurada. Ya commiteado.
17. **Deploy autorizado de `firestore:rules`** durante el checkpoint 2026-09-0X (con las claves de resumen económico) — el ÚNICO deploy de esa tanda; nada más se desplegó (ni Functions, ni storage, ni otros).
18. **Fecha de nacimiento "opcional" y VÍA 2 "reactivada" (checkpoint 2026-09-0X) fueron REVERTIDAS** por commits del desarrollador: hoy `fechaNacimiento` vuelve a ser obligatoria en los formularios (Admin y `CompletarPerfil`) y la VÍA 2 (`crearFicha` desde la entrada código+DNI) NO se ejecuta (índice inexistente → error fijo). Verificar antes de reintroducir.
19. **`MenuCard.kt` sin cambios netos** (la prueba de "descripción a 2 líneas" se revirtió); `DetalleVisuales.kt` NO existe en el árbol (decidir recuperar/eliminar); `DialogoSeleccionarClientes` quedó sin uso pero NO eliminado.
20. **Pantalla "Mis reservas" del CLIENTE fue creada y luego ELIMINADA** (working tree intermedio, Sesión XX): no existe hoy; no "recuperarla" sin decisión del propietario.

---

# 16. Configuración especial

- **JDK/Android Studio:** usar el JDK que configura Android Studio (toolchain resolver foojay en `settings.gradle.kts`). [DESCONOCIDO] ruta JDK concreta (en `local.properties`, ignorado).
- **`google-services.json`:** NO versionado. Presente localmente en `app/` y `appCliente/`. Si se clona el repo hay que añadirlos desde Firebase Console.
- **Emuladores de Rules:** `firestore-tests` con proyecto de emulador `gestorpro-rules-test`. Requiere Java y `node_modules` (ya instalado).
- **Comandos útiles:** (ver AGENTS.md, sección "Comandos del proyecto"):
  - `.\gradlew.bat :app:assembleDebug` / `:appCliente:assembleDebug` / `assembleDebug`.
  - `npm --prefix firestore-tests test` (Rules).
  - `node --test functions/test/ids.test.js functions/test/tokens.test.js`.
  - Deploy de rules: `& ".\firestore-tests\node_modules\.bin\firebase.cmd" deploy --only firestore:rules` (o storage:rules). **Requiere autorización**.
  - `node firestore-tests/auditoria_backfill_indices.cjs` (DRY-RUN índices; no ejecutar backfill sin aprobación).
- **Consola Firebase:** proyecto `gestorpro-50e83`. [DESCONOCIDO] estado de: bucket de Storage habilitado, plan de facturación (AGENTS indica Spark/Blaze sin activar), índices READY, Rules desplegadas, proveedores de Auth.
- **Datos de prueba/identificadores reales:** no documentar identificadores concretos; usar placeholders.

---

# 17. Limitaciones del proyecto

- **Cloud Functions 2ª gen sin desplegar** (requiere Blaze): notificaciones push reales, programadas, morosidad y baja confirmada automáticas NO operan.
- **Storage:** el bucket por defecto debe habilitarse en Firebase Console para el logo (subida `putFile`); hasta entonces el logo remoto falla y el Cliente no ve el logo del Admin si no hay URL.
- **Sesiones/reservas en producción:** dependen de índices compuestos READY y de regenerar las sesiones desde el Admin. [DESCONOCIDO] estado actual en producción.
- **Economía:** sin reglas de negocio avanzadas (descuentos, tarifas, prorrateos, "cuarto día hábil", entidad Pago propia). El "pago" es estado+fecha en el movimiento.
- **Fotos** de clientes locales (sin migrar a Storage).
- **Exportación JSON** parcial (sin servicios/sesiones/reservas/solicitudes/negocio).
- **Dos apps** comparten Auth; un email puede existir en ambos roles (riesgo de cruce si no se valida rol en login Admin).
- **`allowBackup=false`** + migraciones destructivas pendientes de endurecer para producción.
- **Sin minify/R8** en release; sin configuración de firmado de release diferenciada (usa el debug por defecto). [INFERIDO]
- **Idioma:** toda la UI en español y sin `strings.xml` (solo `app_name`).

---

# 18. Google Play / Producción

- **targetSdk 36**, minSdk 26: [CONFIRMADO]. Compatible con los requisitos actuales de Google Play en cuanto a targetSdk.
- **Release build:** `release { isMinifyEnabled = false; proguardFiles(...) }` sin R8. NO hay configuración de `signingConfig` de release → **PENDIENTE**.
- **AAB:** sin configuración específica de bundle; generable por Gradle. **NO COMPROBADO** en consola.
- **Privacy Policy:** implementada y accesible en ambas apps (pantallas con contenido real; appCliente declara el push como no operativo). **PENDIENTE** publicarla/URL.
- **Data Safety:** **NO COMPROBADO** (requiere consola).
- **Account deletion:** no implementada como flujo específico (solo baja lógica del cliente y `FirebaseAuth`). **PENDIENTE/NO COMPROBADO** para requisitos de Google.
- **Permisos:** `POST_NOTIFICATIONS` en appCliente; FileProvider interno. **LISTO** en manifiestos.
- **Notificaciones (FCM):** preparadas, envío pendiente (Functions).
- **Preparación previa a publicación (resumen):** migraciones Room sin fallback destructivo; decidir `allowBackup`; conciliar/desplegar Rules; cerrar alta PERMISSION_DENIED; validar login por rol; firmar release; completar Data Safety/eliminación de cuenta si aplica.

---

# 19. Plan de trabajo pendiente (orden lógico de ejecución)

> Orden propuesto según dependencias; el propietario debe validar el punto 1 (decisiones de negocio) antes de programar economía.

1. **Implementación económica (§24 ya decidido):** cerrar la réplica remota `movimientos/{id}` + resumen en `clientes/{id}` (fuente de verdad Room, morosidad por deuda/cobertura, BAJA+deuda, appCliente sin economía). Siguen como decisión de producto: pantalla "Mis reservas", botones eliminar sesión / cancelar reserva ADMIN, VÍA 2 y fecha de nacimiento opcional, `EstadoCliente.MOROSO`.
2. **Cerrar la verificación del alta ADMIN (`PERMISSION_DENIED`)**: el fix (eliminación del seed Room) ya está commiteado; probar en dispositivo con BD limpia (`[DIAG alta]` debe mostrar `existencia previa -> false,false,false`), limpiar huérfano si procede (aprobación) y **retirar los logs temporales `[DIAG alta]`**.
3. **Auditoría/backfill de `indices_clientes`** con aprobación (DRY-RUN ya preparado).
4. **Conciliar ruleset local vs desplegado y desplegar** tras los 143 tests (solo con autorización). Añadir a las Rules lo que se decida de economía y desplegar junto.
5. **Endurecer Room para producción**: quitar `fallbackToDestructiveMigration`, revisar migraciones y `allowBackup`.
6. **Completar funciones de ADMIN sin UI/seguridad**: validar rol ADMIN en login, cambiar contraseña real, eliminar sesión, cancelar reserva admin, y retirar logs `[DIAG sesiones]`/`ClasesDiagnostico` al confirmar regeneración.
7. **Confirmar producción de sesiones**: índices `READY`, regenerar sesiones, verificar que appCliente las ve; comprobar reservas reales de cliente (reservar/cancelar en dispositivo).
8. **Decidir y, si procede, cerrar la sincronización económica** (Fase 6) replicando lo necesario a `clientes/{id}` (o colección) con Rules + tests, o descartar el helper sin uso.
9. **Blaze + despliegue de infraestructura** (cuando el propietario active facturación): crear bucket Storage y probar logo; crear índice `notificaciones(estado, fechaProgramada)`; `npm install` en `functions/`; `deploy --only functions`; desplegar `storage.rules`; probar FCM real y notificaciones automáticas.
10. **Limpieza y calidad:** retirar logs, borrar basura versionada (con confirmación), opcionalmente mover strings a `strings.xml`, unificar botones restantes, decidir `DetalleVisuales.kt`.
11. **Limpieza legacy** (tarea dedicada): decidir eliminación de `Clase`/`SesionClase`/`ServicioItem`/`MovimientoItem`/`ResumenCard`/`DialogoSeleccionarClientes` y de la tabla Room `solicitud`.
12. **Preparación de publicación:** firmar release, pruebas integrales admin+cliente (estados, morosidad, baja, reservas, notificaciones), Data Safety/eliminación de cuenta, generar AAB y publicar.

---

# 20. Preguntas abiertas / decisiones pendientes que bloquean programación

> Las decisiones **económicas ya están tomadas** (ver **§24 — Modelo económico definitivo**), junto
> con BAJA+deuda y la ausencia de módulo económico en el Cliente. Lo que sigue abierto es de otra
> índole (reservas, VÍA 2, fotos, backfill, estado de la consola):

1. ~~Economía (fuente de verdad del movimiento; réplica de `moroso`/`deuda`/`fechaEntradaMorosidad` y `movimientos`; regla exacta de morosidad / "cuarto día hábil"; pago como entidad; tarifas/descuentos/altas/prorrateos)~~ → **DECIDIDO**: ver §24. Queda solo cerrar la implementación de la réplica remota (`movimientos/{id}` + resumen en `clientes/{id}`) con Rules y tests.
2. ~~BAJA + deuda (estado especial / impacto en notificaciones)~~ → **DECIDIDO**: la BAJA no elimina la deuda; los PENDIENTES se gestionan y pueden pagarse; sin `MOROSO_BAJA` ni estado especial.
3. ~~appCliente economía (módulo completo o solo estado derivado)~~ → **DECIDIDO**: el CLIENTE no tendrá módulo económico.
4. **Reservas:** pantalla "Mis reservas" y cancelación admin.
5. **VÍA 2 de vinculación:** reactivar (con fecha de nacimiento opcional) o eliminar el código conservado.
6. ~~**Fotos:** migrar a Storage~~ → **DECIDIDO** (bloque documental 2026-09-03, §25): fotos remotas compartidas ADMIN↔CLIENTE; sin migración de las fotos de prueba; máximo **10 MB** por foto con compresión/redimensionado automático en la app. Pendiente: implementación de compresión y Storage Rules (§25-D10).
7. **Backfill de índices** (requiere aprobación explícita).
8. **Estado actual del proyecto Firebase en consola** (plan, bucket, índices, rules desplegadas, FCM): [DESCONOCIDO] desde el repositorio.

---

# 21. Instrucciones para la nueva IA

1. **Lee primero este `CONTEXTO_PROYECTO.md` y después `AGENTS.md`** (ambos son complementarios; AGENTS.md tiene el histórico y las reglas, este informe el estado verificado). Si necesitas el "por qué" de una decisión o corrección antigua, consulta el **Anexo A** (índice de `CONVERSACION_EXPORTADA.md`) y salta a la sesión indicada.
2. **No asumas que las decisiones existentes son errores.** Consulta §10 (decisiones) y §11 (reglas) antes de "mejorar" algo.
3. **No hagas refactorizaciones masivas ni cambios de arquitectura** (MVVM + repositorios, dos apps, sin capa use-case).
4. **Verifica siempre contra el árbol actual** (haz `git status`/`git log` y comprueba AGENTS.md, que puede estar desactualizado).
5. **Antes de tocar `build.gradle.kts`, `gradle/libs.versions.toml`, `firestore.rules`, `storage.rules`, `functions/` o dependencias: avisa y justifica.**
6. **Respeta los identificadores reales:** colecciones, campos, tipos, IDs deterministas (`baja_{...}`, `baja_confirmada_{...}`, `solicitud_baja_{...}`, `vinculacion_{...}`, `reservas/{clienteId}_{sesionId}`, `indices_clientes/{negocioId}_{dni}`). No renombrar.
7. **No reintroduzcas** la Vía B/deep links, ni `tieneLlave`, ni `clientesPermitidos`, ni borrados masivos en batch de reservas, ni el acceso `!= "BAJA"`.
8. **No elimines** `ui/clases/*`, entidades `Clase`/`SesionClase`, la tabla Room `solicitud`, `TipoSolicitud.CLASE`, ni componentes "sin uso" sin tarea específica.
9. **No despliegues** Rules/Functions/Storage sin autorización; valida siempre antes con los tests (`npm --prefix firestore-tests test`).
10. **Tras cambios importantes, ejecuta**: unit tests de ambos módulos, tests de Rules, y compila `assembleDebug`.
11. **No documentes identificadores reales** (UIDs, códigos) en el repositorio.
12. **Respeta el español** en código/comentarios/mensajes.
13. **Si vas a corregir el login Admin por rol, o a desplegar Rules, o a activar Blaze/Storage, para y confirma con el propietario.**
14. **Para tareas de economía aplica el modelo DEFINITIVO del §24** (decisiones cerradas: Room + réplica `movimientos/{id}`, pago = estado+fechaPago+metodoPago, morosidad por deuda/cobertura, BAJA+deuda, eliminación Room+Firestore, Cliente sin economía). No programes automatizaciones nuevas de Cloud Functions sin una sesión específica (los movimientos los crea manualmente el ADMIN).

---

# 22. RESUMEN PARA PEGAR AL INICIO DE UNA NUEVA CONVERSACIÓN

> Copia este bloque como contexto inicial mínimo.

```
PROYECTO: GestorPro (Android, español). Sistema de gestión de gimnasio con DOS apps
independientes sobre el MISMO Firebase (gestorpro-50e83):
  :app        = GestorPro Admin  (com.roberto.gestorpro, rol ADMIN,  Room + Firestore write-through + Storage)
  :appCliente = GestorPro Cliente (com.roberto.gestorpro.cliente, rol CLIENTE, Firestore directo + DataStore caché)

STACK: Kotlin 2.2.10 · AGP 9.1.1 · Gradle 9.3.1 · Compose BOM 2026.02.01 (M3) · Nav 2.9.3 ·
Hilt 2.60.1 · Room 2.8.4 (Admin) · DataStore 1.1.7 · Coil 3.3.0 · Gson 2.11.0 (Admin) ·
Firebase BOM 34.16.0 · compile/target 36 · min 26 · Java 11. Versiones en gradle/libs.versions.toml.

ARQUITECTURA: MVVM + repositorios (sin capa use-case). UI Compose -> ViewModel (StateFlow) ->
Repository -> (Room DAO [Admin] y/o data/firebase/*RemotoRepository -> Firestore). 1 sola Activity por app.
Navegación Compose centralizada en navigation/{Routes,AppNavigation}.kt por app. Hilt en di/AppModule.kt.

ESTADO REAL (2026-09-03, tras F2): HEAD del desarrollador = 100c4eb "mejoras y correcciones". Working tree con la F2 de economía (Room v17, réplica movimientos+resumen cableada) SIN commit/deploy.
Compila. Tests HOY: :app unit 68/68, :appCliente 9/9, Rules Firestore/Storage 151/151,
Functions helpers 13/13. AGENTS.md puede estar desactualizado; contrastar con el árbol y con la ACTUALIZACIÓN al inicio de este documento.

MODELO DE NEGOCIO: Cliente -> Servicio (catálogo con precio) -> Sesión (programación, horaDesdeReserva)
-> Reserva (documentId reservas/{clienteId}_{sesionId}, plazas±1 atómicas). Vinculación del cliente por
CÓDIGO MAESTRO + DNI con indices_clientes/{negocioId}_{dni} (VÍA 1 activa; VÍA 2 conservada sin ejecutar;
Vía B/deep links DESCARTADA). Acceso del cliente SOLO con estado ACTIVO; morosidad = flag, no estado.
Room Admin v17 (migraciones 11->12->13->14->15->16->17, con exentoMorosidad + eliminacion_pendiente en 16->17; fallbackToDestructiveMigration PENDIENTE de retirar).
Legacy TRANSITORIO no eliminar: ui/clases/*, Clase/SesionClase, tabla Room solicitud, TipoSolicitud.CLASE.

IMPORTANTE: login Admin NO valida rol==ADMIN (riesgo abierto); alta Admin PERMISSION_DENIED: causa raíz
(seed Room ids 1-20) ELIMINADA y commiteada, falta verificación en dispositivo + retirar logs [DIAG alta]
(logs temporales también: [DIAG sesiones]/ClasesDiagnostico); Cloud Functions 2ª gen en
functions/ SIN desplegar (requiere Blaze) => FCM real y notificaciones automáticas NO operan; Storage
bucket pendiente; Economía: modelo DECIDIDO (ver §24) — la réplica remota `movimientos/{id}` +
resumen (`moroso`/`deuda`/`fechaEntradaMorosidad` en `clientes/{id}` + `exentoMorosidad`) ya está
CABLEADA en F2 (working tree, Room v17), pendiente prueba manual y deploy autorizado de Rules;
cambiar contraseña Admin es placeholder; sin botón eliminar sesión ni
cancelar reserva admin; sin pantalla "Mis reservas" del cliente (VM preparado).

DECISIONES 2026-09-03 (NOTIFICACIONES / CLOUD FUNCTIONS / STORAGE, §25): notificación manual = PUSH +
BUZÓN; **morosidad con dos causas** — "moroso por deuda" (movimientos PENDIENTE, sin aviso automático;
lo gestiona el ADMIN) y "moroso por fecha" (período terminado + ACTIVO sin cobertura; sí genera aviso y
recordatorios), con transiciones: pagar la deuda sin renovar deja de ser moroso por deuda pero puede
seguir siendo moroso por fecha; renovar con nuevo período elimina la causa por fecha; pasar a BAJA
detiene los avisos automáticos de morosidad aunque conserve deuda; entrada en morosidad por fecha al día
siguiente de `fechaFin` (da igual festivo/fin de semana); sin aviso al registrar un pago; BAJA_CONFIRMADA
sí, sin flujo de rechazo de baja; avisos desactivados = sin push pero con buzón; push a todos los
dispositivos activos; notificaciones programadas con precisión ~15 min (Cloud Functions, aunque la app
Admin esté cerrada); nombre + logo del centro compartidos entre apps vía Storage (reemplazo del archivo
anterior); fotos de cliente remotas compartidas ADMIN↔CLIENTE (sin migrar las fotos de prueba); máximo
10 MB por foto con compresión/redimensionado automático en la app; comprobación de morosidad ~diaria
~08:00 con coste de Functions en equilibrio; Blaze vinculado a CF/Storage (presupuesto/alertas antes de
desplegar). PENDIENTES (§25-D): redacción y detalles de las notificaciones de morosidad y recordatorios,
idempotencia/reintentos, lógica definitiva de la Function de morosidad y del resumen económico remoto,
implementación de compresión de fotos, configuración de Blaze. Todo EXCLUSIVAMENTE documentado, NO
implementado.

PRÓXIMO PASO RECOMENDADO: 1) cerrar la implementación de la réplica económica decidida (§24) +
revisar VÍA 2 y reservas (§20); 2) verificar fix del alta en dispositivo y retirar logs [DIAG alta]; 3) reconciliar y desplegar
firestore.rules local (143 tests) tras autorización; 4) endurecer Room (sin fallback destructivo) y
allowBackup; 5) activar Blaze -> bucket/índice/Functions/FCM. NO hacer refactorizaciones masivas ni
cambiar decisiones marcadas en CONTEXTO_PROYECTO.md §10/§11/§24/§25 sin consultar. Responder SIEMPRE en español.
Histórico completo por sesiones: CONVERSACION_EXPORTADA.md (índice en Anexo A de CONTEXTO_PROYECTO.md).
```

---

# 23. Verificación del informe frente a las preguntas objetivo

| Pregunta | ¿Respondida? |
|---|---|
| ¿Qué estamos construyendo? | Sí (§1) |
| ¿Para quién? | Sí (§1) |
| ¿Qué funcionalidades tiene? | Sí (§8) |
| ¿Qué está terminado / falta? | Sí (§8, §9) |
| ¿Cómo está construido? | Sí (§3, §4) |
| ¿Qué tecnologías? | Sí (§2) |
| ¿Cómo funcionan los datos? | Sí (§7) |
| ¿Cómo funciona Firebase? | Sí (§7, §12) |
| ¿Cómo funciona la autenticación? | Sí (§6) |
| ¿Cómo funcionan las notificaciones? | Sí (§12) |
| ¿Qué errores existen? | Sí (§14) |
| ¿Qué problemas ya se solucionaron? | Sí (§15) |
| ¿Qué decisiones no deben modificarse? | Sí (§10, §11) |
| ¿Qué pruebas se han realizado / faltan? | Sí (§13) |
| ¿Qué falta antes de producción/Google Play? | Sí (§18, §19) |
| ¿Cuál es el siguiente paso exacto? | Sí (§19, §22) |

---

*Anexo de hallazgos menores de higiene (documentados, sin acción en este informe): archivos raíz `build_*.txt`, `files.txt`, `structure.txt`, `app_kt_files.txt`, `conversacionEstilo.md`, `EXPLICACION_BASE_DE_DATOS.html`, `AI_RULES.md`, `AUDITORIA_PROYECTO_MIGRACION_KMP.md`, `firestore-debug.log` y `firestore-tests/firestore-debug.log` están versionados o presentes como basura histórica; `AñadirClienteScreen.kt` contiene "ñ" en el nombre de archivo; solo hay `app_name` en los `strings.xml`; hay 0 llamadas a `stringResource`.*

---

# 24. Modelo económico definitivo (decisiones del propietario)

> **DECISIONES ECONÓMICAS FINALES** (documento vivo). Prevalece sobre cualquier apartado anterior de
> este informe y de AGENTS.md/HOJA DE RUTA que describa reglas antiguas o "decisiones abiertas"
> (morosidad solo tras `fechaFin`, "cuarto día hábil", días hábiles/festivos, pago como entidad
> independiente, módulo económico del CLIENTE, movimientos "solo locales", movimientos no eliminables
> en Firestore, descuentos automáticos, `estado = MOROSO` persistido, servicios que modifican
> movimientos ya creados). El histórico de `CONVERSACION_EXPORTADA.md` se conserva tal cual.

- **Fuente de verdad:** Room (Admin) es la fuente de verdad económica del ADMIN; Firestore es la
  **réplica remota** de la economía. Todo movimiento debe existir en Room y en `movimientos/{movimientoId}`;
  la sincronización debe mantener ambos lados coherentes.
- **Movimiento = unidad económica principal**, multi-servicio. Campos: cliente, servicios, fechaInicio,
  fechaFin, precioFinal, estado, fechaPago, metodoPago, observaciones (si procede). **No existe entidad
  Pago independiente**: el pago se representa con `estado + fechaPago + metodoPago`.
- **Creación manual por el ADMIN** (no automática): el ADMIN decide fecha de inicio, fecha de fin,
  servicios, precio final, si está pagado y el método de pago cuando corresponda. La acción "Renovar"
  gestiona los casos ya contemplados; **no se inventan nuevas reglas de prorrateo**.
- **Estado:** si el ADMIN marca "Pagado" → `PAGADO` (con datos de pago); si no → `PENDIENTE`. Solo el
  ADMIN marca un movimiento como PAGADO; el CLIENTE no registra ni valida pagos.
- **Fechas del período:** las fija el ADMIN (fechaInicio/fechaFin), sin regla de mes natural.
- **Pago:** la cuota/movimiento debe pagarse **el día 1 del período**. **No existe "cuarto día hábil"**
  ni margen de días hábiles; no cuentan sábados, domingos ni festivos.
- **Deuda:** **suma de TODOS los movimientos PENDIENTES**. Un movimiento PENDIENTE ya representa deuda
  (no hay que esperar a `fechaFin`).
- **Morosidad (dos causas; modelo 2026-09-03):** un cliente es **MOROSO** si tiene deuda pendiente
  (**"moroso por deuda"** — uno o más movimientos `PENDIENTE`; sin esperar al vencimiento). Segunda
  causa: un cliente que permanece ACTIVO pasa a **"moroso por fecha"** el **día siguiente a la
  fechaFin** del período que le cubría si continúa sin cobertura económica (p. ej. fechaFin 15/09 →
  16/09), aunque ese día sea sábado, domingo o festivo. Sin reglas de días hábiles. La distinción entre
  ambas causas determina los avisos automáticos: solo la causa "por fecha" genera aviso/recordatorios
  (ver §25-A).
- **Deuda (importe pendiente) ≠ MOROSO (situación del cliente).** Un PENDIENTE ya genera morosidad; es
  INCORRECTO documentar que la deuda solo genera morosidad después de `fechaFin`. Moroso por deuda y
  moroso por fecha son causas independientes: un cliente puede ser moroso por ambas, y cada causa entra
  y sale por separado (pagar la deuda no elimina la causa "por fecha" si sigue ACTIVO sin cobertura;
  renovar con un nuevo período elimina la causa "por fecha" aunque conserve deuda).
- **Salida de morosidad:** al marcar PAGADO el único pendiente y no existir otra causa → `moroso = false`;
  se actualizan `fechaPago`/`metodoPago`. Si quedan otros PENDIENTES sigue moroso. No se conserva
  historial de haber sido moroso (situación = estado ACTUAL).
- **`fechaEntradaMorosidad`:** representa la entrada en la morosidad ACTUAL; al dejar de ser moroso
  debe limpiarse/anularse. No conserva antecedente histórico.
- **BAJA + deuda:** la BAJA **no elimina** deudas (`estado = BAJA` con `deuda > 0` es válido). Los
  PENDIENTES siguen existiendo; el ADMIN puede gestionarlos y marcarlos PAGADO. La baja no perdona ni
  elimina deuda.
- **Servicios contratados:** afectan solo a movimientos **nuevos**; no modifican movimientos ya creados.
  Para corregir uno existente: el ADMIN lo elimina con confirmación y crea uno nuevo.
- **Eliminación:** el ADMIN puede eliminar **cualquier** movimiento (con confirmación) en **Room +
  Firestore**; al eliminar debe recalcularse la situación económica del cliente (deuda, morosidad,
  fechaEntradaMorosidad, período actual, resumen económico remoto).
- **Histórico remoto:** Firestore conserva el histórico completo (`movimientos/{id}`); no se elimina por
  antigüedad, solo cuando el ADMIN lo elimina.
- **Resumen económico remoto de `clientes/{id}`:** debe contemplar `moroso`, `deuda`,
  `fechaEntradaMorosidad`, `fechaInicioActual`, `fechaFinActual`, para que procesos futuros (incluidas
  Cloud Functions) conozcan la situación económica sin depender de la app Admin abierta.
- **App CLIENTE sin economía:** no verá movimientos, importes, deuda, método de pago ni histórico
  económico; solo lo ya decidido (estado, fecha de fin del período y funcionalidades generales).
- **Estados:** la morosidad NO es estado administrativo. Estados: ACTIVO, REGISTRADO, BAJA, ARCHIVADO
  (separados) + `moroso` como dato independiente. No persistir `estado = MOROSO`.
- **ACTIVO + moroso:** continúa usando actividades/reservas si cumple el resto de condiciones de acceso;
  la morosidad no implica BAJA ni bloqueo de actividades/reservas.
- **Descuentos:** no existe sistema automático (ni estudiante/familia/jubilado, ni categorías). El ADMIN
  decide el `precioFinal`; cualquier descuento queda reflejado indirectamente en él.
- **Precio final:** el sistema puede proponer un precio desde los servicios seleccionados, pero el ADMIN
  lo puede modificar antes de guardar; el movimiento conserva el precio decidido y cambios posteriores
  del precio de un servicio **no alteran** movimientos históricos.
- **Cloud Functions:** qué procesos automatizar con Functions sigue siendo **DECISIÓN PENDIENTE** de una
  futura sesión. Lo cerrado es que **los movimientos los crea manualmente el ADMIN**.

> **Nota de implementación (resuelta en F2, 2026-09-03):** la sincronización remota de la economía
> (`movimientos/{id}` + resumen `moroso`/`deuda`/`fechaEntradaMorosidad`/`exentoMorosidad` en
> `clientes/{id}` + Rules/tests) está **CABLEADA** en el working tree (Room v17): crear/editar/eliminar
> movimiento replican a Firestore, se publica el resumen y las eliminaciones fallidas se persisten en
> `eliminacion_pendiente`. Pendiente: prueba manual y deploy autorizado de Rules (ver ACTUALIZACIÓN al inicio).

---

# 25. Decisiones de producto — Notificaciones, Cloud Functions y Storage (2026-09-03, bloque documental)

> Documento vivo y **EXCLUSIVAMENTE DOCUMENTAL** (no implementado). Decisiones de producto **FINALES**
> del propietario sobre notificaciones, avisos automáticos de morosidad, Cloud Functions, Cloud
> Storage, logos y fotos de clientes. **Prevalece sobre cualquier apartado anterior** de este informe
> (p. ej. §12, §19, §20) y de AGENTS.md / HOJA DE RUTA que describa flujos, frecuencias o
> comportamientos contrarios. NADA de este bloque está implementado todavía; no es una orden de
> codificación. El histórico de `CONVERSACION_EXPORTADA.md` se conserva tal cual.
>
> **2.ª tanda documental (2026-09-03):** esta versión actualiza el bloque y **SUPERSEDE** el texto
> previo del propio §25 en: (a) **avisos de morosidad por movimiento PENDIENTE** — ya NO se avisa
> automáticamente por el simple hecho de existir un movimiento PENDIENTE; (b) **frecuencia de
> notificaciones programadas** — la precisión aproximada de **15 minutos es suficiente** (no cada
> minuto ni 2 minutos por precisión); (c) **límite de tamaño de fotos** — decidido en **10 MB** con
> compresión/redimensionado automático en la app. Las reglas/frecuencias preparadas en `functions/`
> local (programadas cada 2 min, recordatorio de morosidad cada 1 h) y en `storage.rules` preparadas
> (5 MB de referencia) eran **preparación provisional SIN desplegar** y quedan desactualizadas frente a
> estas decisiones; se rediseñarán/alinearán en la sesión de implementación (NADA se modifica en esta
> tarea documental).

### A. Notificaciones — decisiones cerradas

1. **Notificaciones manuales del ADMIN (sin cambios):** destino **Individual / Grupo / Todos** y
   envío **inmediato o programado**. La pantalla actual ya contempla estas opciones.
2. **Notificación manual = PUSH + BUZÓN.** Cuando el ADMIN envía una notificación se debe (a) enviar
   push al cliente (FCM) y (b) registrar la notificación en el buzón del cliente. El buzón conserva el
   mensaje aunque el cliente no tenga activados los avisos push.
3. **Morosidad: dos causas con consecuencias distintas (distinción fundamental).**
   - **Moroso por deuda:** tiene uno o más movimientos en `PENDIENTE`. Es una situación de morosidad del
     cliente, pero **no genera por sí misma un aviso automático**: la deuda pendiente la gestiona el
     ADMIN. `PENDIENTE` es estado del **MOVIMIENTO**, no del cliente.
   - **Moroso por fecha:** su período pagado ha terminado, sigue **ACTIVO** y no existe un nuevo período
     que lo cubra. **Este sí genera aviso y recordatorios.**
4. **Morosidad — finalidad de la automatización.** La automatización de morosidad (caso "moroso por
   fecha") avisa al cliente cuando ha terminado su período y sigue ACTIVO, provocando que tome una
   decisión: **renovar o darse de baja**.
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
   movimiento/cuota en `PENDIENTE` **no genera notificación automática** (moroso por deuda): la deuda la
   gestiona el ADMIN. La notificación automática de morosidad queda reservada al escenario "moroso por
   fecha" (los detalles de redacción y recordatorios siguen abiertos, ver bloque D).
8. **PAGADO no genera aviso:** al marcar el ADMIN un movimiento como PAGADO **NO** se notifica al
   cliente; el estado económico se actualiza con normalidad.
9. **BAJA CONFIRMADA (se mantiene):** al aceptar una solicitud de baja y pasar el cliente a BAJA,
   recibe la notificación de baja confirmada.
10. **Sin notificación de rechazo de baja:** el ADMIN no rechaza solicitudes de baja; no se diseña ese
    flujo.
11. **Notificaciones programadas (precisión ~15 min).** El ADMIN programa una notificación para
    fecha/hora futura; **Cloud Functions es la responsable** del envío automático al llegar el momento
    **aunque la app Admin no esté abierta**. Se ha decidido que una precisión aproximada de **15 minutos
    es suficiente**: NO se comprueba cada minuto y **no se usa una frecuencia de 2 minutos solo por ser
    más precisa**. La frecuencia de ejecución debe ser eficiente para evitar trabajo y coste
    innecesarios.
12. **Avisos desactivados ≠ sin buzón:** si el cliente desactiva "Recibir avisos" no recibe push, pero
    las notificaciones **siguen apareciendo en el buzón**. El switch controla solo el push; no elimina
    ni impide conservar el historial del buzón.
13. **Varios dispositivos por cliente:** una notificación llega a **todos** los dispositivos activos de
    un mismo cliente (móvil, móvil nuevo, tablet…). Se conserva la arquitectura actual de
    dispositivos/tokens.

### B. Cloud Functions — principios y decisiones

1. **CF = automatización.** No sustituyen a Room como fuente de verdad económica del ADMIN.
   Arquitectura conceptual definitiva: **ROOM** → fuente de verdad económica del ADMIN; **FIRESTORE** →
   réplica remota de la información que deba existir en nube; **CLOUD FUNCTIONS** → automatizaciones y
   procesos en segundo plano; **FCM** → envío de notificaciones push; **CLOUD STORAGE** →
   almacenamiento remoto de logos y fotografías.
2. **Comprobación automática de morosidad (intención confirmada).** Se usará Cloud Functions porque el
   ADMIN puede tener la app cerrada: el paso del tiempo debe poder provocar la actualización de la
   situación económica aunque la app Admin no esté abierta.
3. **Frecuencia de comprobación de morosidad:** **una vez al día, aproximadamente a las 08:00**
   (suficiente: los cambios de día/fechaFin ocurren de noche; no es necesario reaccionar a las 00:00
   exactas, ni comprobar cada minuto). La **lógica exacta de la Function sigue PENDIENTE** (ver bloque
   D).
4. **Coste de Firebase.** Evitar ejecuciones innecesariamente frecuentes cuando una frecuencia menor
   sea suficiente; la frecuencia debe buscar el equilibrio entre precisión, funcionamiento correcto y
   coste. No ejecutar Functions cada minuto por una respuesta ligeramente más rápida. Referencias
   cerradas: precisión ~15 min para notificaciones programadas; comprobación de morosidad ~diaria
   ~08:00.
5. **Blaze.** La activación de Blaze queda asociada a la necesidad de usar infraestructura como Cloud
   Functions y Cloud Storage. Antes del despliegue definitivo deberán revisarse consumo, configuración
   de presupuesto/alertas, costes potenciales y las funciones que realmente se van a ejecutar. **No
   activar ni configurar Blaze durante la fase documental.**

### C. Cloud Storage — logos y fotos

1. **Nombre y logo del centro compartidos.** El ADMIN configura el negocio con nombre y logo del
   centro; ADMIN y CLIENTE muestran la misma información (NOMBRE + LOGO). El logo **no** depende de un
   archivo local exclusivo del dispositivo Admin: se usa **almacenamiento remoto**.
2. **Actualización del logo.** Al cambiar el logo, el anterior se sustituye y el nuevo pasa a ser el
   vigente en ambas aplicaciones. No se deben acumular innecesariamente versiones antiguas del mismo
   logo en Storage.
3. **Fotos de clientes remotas y compartidas.** Las fotos de clientes se almacenan remotamente en
   Firebase Storage y se comparten entre ambas apps: ADMIN cambia foto → el CLIENTE la ve; CLIENTE
   cambia foto → el ADMIN la ve. **No** debe existir una copia local independiente como fuente de
   verdad de la fotografía: la foto remota es la referencia compartida.
4. **Permisos de foto.** ADMIN y CLIENTE pueden cambiar la foto del cliente: el ADMIN las de los
   clientes de su negocio; el CLIENTE solo la suya. Mantener las restricciones de seguridad; un
   cliente nunca modifica fotografías de otro cliente.
5. **Fotos de prueba actuales.** Las fotos locales actuales de desarrollo son fotos de prueba y **NO
   requieren migración**. No son la arquitectura definitiva: la solución de producción debe funcionar
   mediante Storage para cualquier negocio que utilice GestorPro.
6. **Tamaño de fotos (decidido): máximo 10 MB por fotografía** como límite de seguridad. **El usuario
   NO debe manipular manualmente una fotografía para cumplir el límite**: la aplicación debe poder
   recibir una foto de la galería o tomada con la cámara, **redimensionar/comprimir automáticamente
   cuando sea necesario** y subir el resultado a Storage. El límite de 10 MB es un límite técnico, no
   una tarea que el usuario deba gestionar. La implementación exacta de la compresión/redimensionado
   sigue **PENDIENTE** (bloque D). ⚠️ Las `storage.rules` preparadas actuales usan **5 MB de
   referencia**: quedan desactualizadas frente a esta decisión; NO se modifican en esta tarea y se
   alinearán al implementar.
7. **Sustitución de fotos (principio de seguridad).** Al cambiar una fotografía: la nueva pasa a ser la
   vigente, el archivo anterior debe eliminarse de Storage **cuando sea seguro hacerlo** y no deben
   quedar versiones antiguas acumuladas innecesariamente. La implementación debe priorizar **no dejar al
   cliente sin fotografía** si la subida o actualización falla. Orden del cambio: (1) subir la nueva
   fotografía; (2) actualizar la referencia; (3) confirmar que la nueva es válida; (4) eliminar la
   anterior. **No borrar la fotografía antigua antes de disponer correctamente de la nueva.**

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

> **Relación con el modelo económico:** el modelo económico definitivo (§24) sigue vigente sin cambios
> (Room = fuente de verdad; movimientos replicados a Firestore; histórico remoto completo; crear/editar/
> eliminar manuales con confirmación y recálculo; PENDIENTE = deuda; morosidad por deuda y por fin de
> período con ACTIVO sin cobertura; deuda subsiste con BAJA; sin entidad Pago; el CLIENTE no tiene
> módulo económico, solo estado y fecha de fin de período; precio final decidido por el ADMIN; los
> cambios de servicios afectan solo a movimientos nuevos). Las decisiones de Cloud Functions **no
> modifican** el modelo económico.

---

# Anexo A — Índice cronológico de `CONVERSACION_EXPORTADA.md` (Sesiones I–XXXV)

> La crónica completa (2443 líneas) está en `CONVERSACION_EXPORTADA.md`. Este índice permite a la nueva IA saltar al detalle de una decisión/corrección concreta sin releerlo todo. **Los checkpoints citan working trees intermedios; hoy todo está commiteado en `master` (HEAD `60cf834`).** La numeración de sesiones no es estrictamente correlativa (hay dos bloques "Sesión XX" y saltos).

| Sesión | Fecha | Contenido clave (línea inicial aprox. en el archivo) |
|---|---|---|
| (I) | 2026-09-01* | Análisis "límite 1000 expresiones" del ruleset del Modelo A de vinculación (histórico) — l.1 |
| II | 2026-08-24 | Refactor de `firestore.rules` que eliminó el límite de 1000 expresiones; 9/9; deploy; creación de `firebase.json`/`.firebaserc` — l.181 |
| III | 2026-08-24 | Autenticación Firebase real implementada y probada en dispositivo (Xiaomi/MIUI) — l.258 |
| IV | 2026-08-25 | Diseño definitivo de vinculación (código maestro + enlace individual / Vía A + Vía B) — l.345 |
| V | 2026-08-25 | Vinculación implementada y desplegada; Vía B (deep link `gestorpro://vincular/{token}`) — l.526 |
| VI | 2026-08-26 | Cambio de PC; fotos galería/cámara; recuperación de contraseña; fix de ruta de Vía B (doble query); diagnóstico "crear negocio PERMISSION_DENIED" — l.630 |
| VII | 2026-08-27 | **Vía B DESCARTADA**; rediseño flujo código maestro + DNI; colección `indices_clientes`; auditoría DRY-RUN de backfill (2 índices) — l.739 |
| VIII | 2026-08-27 | **Split en dos apps** (`:app` Admin + `:appCliente`); Rules reescritas (16/16) — l.894 |
| IX | 2026-08-27 | VÍA 1 funcional: declaración temporal `{dni, negocioId}` en `perfiles_pendientes` + lectura de ficha (18/18) — l.983 |
| X | 2026-08-28 | Flujo cliente sin vínculo + VÍA 2; sync nombre de negocio; logo con Storage (bucket pendiente) — l.1078 |
| XI | 2026-08-28 | **Modelo Servicios/Sesiones/Reservas** (Room + Firestore + Rules; tests 33→76) — l.1207 |
| XII | 2026-08-29 | Sync `serviciosContratados`; pantalla "Clases de hoy"; redeploy Rules (byte-idénticas); cascadas admin; crash no aislado — l.1334 |
| XIII | 2026-08-29 | Fix PERMISSION_DENIED en cascadas: queries admin SIN `negocioId` (rules-are-not-filters); PRUEBA 33A–33H (90/90) — l.1406 |
| XIV | 2026-08-29 | Auditoría de solo lectura de appCliente (estado cliente, fechas, plan de reservas) — l.1485 |
| XV–XVII | 2026-08-30 | Sync de períodos (`fechaInicioActual`/`fechaFinActual`); card Home validado; corrección Vía A (regresión `653f117`) — l.1558 |
| XVIII | 2026-08-31 | Estabilización: diagnóstico alta Admin PERMISSION_DENIED (test de aislamiento `diagnostico_alta_cliente.test.cjs` 7/7, logs `[DIAG alta]`); selector de servicio en movimiento; DatePicker CompletarPerfil — l.1624 |
| XIX | 2026-08-31 | `horaDesdeReserva` (Room v12, Rules, appCliente); **índices compuestos creados** en producción; "Gestionar sesiones" navega a edición — l.1730 |
| XX-a | 2026-09-01 | Apertura GLOBAL de reservas (antes por día) + **bug `idSesion=0`** corregido + logging `[DIAG sesiones]` — l.1827 |
| XX-b | 2026-09-01 | **Eliminación del seed Room** (causa raíz del alta); diagnóstico de réplica de sesiones (los fixes NO estaban commiteados) — l.1923 |
| XXI–XXIV | 2026-09-01 | Fase D notificaciones ADMIN; Fase E Cloud Functions local (sin Blaze); solicitudes de baja; auditoría y corrección del flujo de BAJA (123/123) — l.1977 |
| XXV–XXVII | 2026-09-02 | Fix PERMISSION_DENIED al generar sesiones (replicar servicio antes); SolicitudesScreen (búsqueda/borrado); aviso morosidad Home; SOLICITUD_BAJA y BAJA_CONFIRMADA corregidas (131/131); **diagnóstico ECONOMÍA** (10 decisiones pendientes) — l.2028 |
| XXVIII–XXXV | 2026-09-02 | **Economía Fases 1–5**: llave como servicio normal; precio en servicios; movimientos multi-servicio (`MovimientoPrecio`); pagos (`MovimientoPago`, fix de `fechaPago`); morosidad en Room v15 (`MovimientoMorosidad`); selector individual de notificación (135/135; unit 45/45) — l.2175 |
| Checkpoint 2026-09-0X | (sept. 2026) | Economía Fase 6 (movimientos/resumen a Firestore, 144 tests) + correcciones alta/VÍAs + política de privacidad + unificación de botones — **parcialmente REVERTIDO por el desarrollador** — l.2286 |
| CIERRE 2026-09-03 | 2026-09-03 | **Acceso solo ACTIVO** + notificación VINCULACION + texto "Actividades" (143/143) — l.2337 |

\* La cabecera del archivo fecha la Sesión I como "2026-09-01" (typo; por el orden corresponde al 2026-08-24).

**Matices importantes que la crónica aporta y este informe incorpora:**
1. El root cause del alta Admin (`PERMISSION_DENIED`) fue el **seed Room** (ids 1–20), no solo "documentos huérfanos"; el seed se eliminó.
2. La **Fase 6 de economía** se implementó completa (resumen `moroso`/`deuda` en `clientes/{id}` + 144 tests + deploy autorizado de Rules) y luego el desarrollador la **revirtió parcialmente**; la **F2 (2026-09-03)** la reconstruyó y **cableó** de nuevo en el working tree (movimientos + resumen + eliminaciones pendientes, Room v17, Rules locales con las claves, 151/151). Pendiente solo deploy autorizado.
3. El ruleset desplegado pasó por varios deploys históricos (algunos verificados byte-idénticos); el último deploy conocido fue en el checkpoint 2026-09-0X. El estado DESPLEGADO actual frente al local (143 tests) es **[DESCONOCIDO]** y debe reconciliarse antes de producción.
4. Varias tandas (fecha de nacimiento opcional, VÍA 2 reactivada, botones, `DetalleVisuales.kt`, pantalla "Mis reservas") fueron creadas y luego **revertidas por commits del desarrollador**; el árbol actual es la única verdad.
