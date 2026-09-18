# conversacionEstilo — Contexto de estilo (GestorPro)

> Archivo de continuidad. La IA debe leerlo para saber exactamente dónde quedamos
> en el diseño visual de GestorPro (Admin y Cliente).
> Última actualización: 2026-09-18.

## 1. Resumen rápido — dónde estamos

Se trabaja **solo en el estilo visual** de GestorPro (dos apps Android en un mismo
proyecto Gradle: `:app` = Admin, `:appCliente` = Cliente, Firebase compartido `gestorpro-50e83`).

**Regla fundamental:** SOLO presentación. Nunca se toca lógica, ViewModels, repositorios,
navegación, rutas, entidades, Firebase ni Room. Si compila y el aspecto es el pedido, no se
hacen cambios adicionales.

**Estado actual (2026-09-18):**
- **Homes ADMIN y CLIENTE rediseñados** con **cards de imagen** + **cabecera azul corporativa**
  `#1E88E5`. Detalle en §2.
- **Pantallas de tema/preferencias** (`ConfiguracionScreen` Cliente = "Ajustes",
  `PreferenciasScreen` Admin = "Preferencias") con el patrón de filas `TemaOption`. Sigue vigente (§3).
- Convención de color: **azul `#1E88E5`** = selección / acción principal; **rojo** = destructivo /
  cerrar sesión; **grises** = secundario.

## 2. Homes (estado actual)

### Home ADMIN (`:app`)
- **Cabecera azul `#1E88E5`** extendida hasta el borde superior (respetando insets), con logo (~50 dp)
  + nombre del centro en blanco. **Se retiraron** los rótulos "Panel principal" y "Accesos directos".
- **6 cards con imagen** (`HomeAdminImageCard`, alto **175 dp**) con icono + título sobre imagen:
  - Centro → `img_centro`
  - Clientes → `img_clinetes`
  - Actividades → `img_actividades_admind`
  - Rutinas → `img_rutinas_admind`
  - Economía → `img_econimia`
  - Solicitudes → `img_solicitudes_baja`
- **2 cards compactas azules** (`HomeAdminCompactCard`): **Notificaciones** (con badge de no leídas) y
  **Ajustes**. Orden: las 6 de imagen + el par compacto al final.

### Home CLIENTE (`:appCliente`)
- **Cabecera azul `#1E88E5`**: logo + nombre del centro en blanco.
- **Cards con imagen** (misma familia visual que Admin). **Ajustes** y **Notificaciones** en azul con
  texto/icono blanco; Notificaciones con **badge** de no leídas.
- **Indicador de estado al FINAL** de la pantalla (último ítem *full-span* del `LazyVerticalGrid`, tras
  Notificaciones): sin card; **círculo + texto**. Con estado ACTIVO se muestra en **una línea**
  ("Activo hasta [fecha]"). **Ya usa datos reales** de la ficha/período (no valores mock).
- El aviso de morosidad se muestra como **texto** (no como Card).
- El menú ⋮ de la cabecera **NO** tiene "Denunciar el centro"; el acceso general a
  **"Denunciar contenido o usuario"** está en **Ajustes**.

### Histórico (superado, se conserva por trazabilidad)
- El diseño de 2026-08-28 del Home ADMIN era un **dashboard 2×2** con `MenuCard` cuadradas de icono
  coloreado (Clientes `#2196F3`, Clases `#43A047`, Economía `#FB8C00`, Ajustes `#78909C`) y cabecera
  gris `surfaceContainerLow`. **Queda SUPERSEDIDO** por el rediseño con cards de imagen y cabecera azul.
- El Home del CLIENTE estaba pendiente de rediseño y usaba valores mock en el indicador de estado; ambos
  aspectos **ya están resueltos** (rediseñado + datos reales).

## 3. Patrón reutilizable — filas de opción de tema (Ajustes/Preferencias) [VIGENTE]

Ambas pantallas de tema usan el mismo patrón. Composable privado `TemaOption`:

- **Una sola Card** agrupa las 3 opciones (Claro / Oscuro / Sistema). No se crean 3 cards.
  Card: `shape = 16.dp`, `containerColor = surface`, `elevation = 0.dp`,
  `border = BorderStroke(1.dp, outlineVariant)`.
- Fila `TemaOption` (`Row`, `verticalAlignment = CenterVertically`,
  `horizontalArrangement = Arrangement.spacedBy(16.dp)`, `clickable(onClick)`):
  - **Icono** a la izquierda (`24.dp`): `LightMode` / `DarkMode` / `SettingsBrightness`
    (vía `material-icons-extended`, presente en ambas apps).
  - **Columna** con `titleMedium` (título) + `bodyMedium` (descripción secundaria):
    "Claro"→"Tema claro", "Oscuro"→"Tema oscuro", "Seguir configuración del sistema"→"Según el dispositivo".
  - **Indicador** `RadioButton` a la derecha (`selectedColor = azul #1E88E5`).
- **Opción seleccionada:** `background = azul.copy(alpha = 0.08f)`, icono y título en azul `#1E88E5`
  con `FontWeight.Bold`; las no seleccionadas en `onSurfaceVariant` (gris).
- **Divisores:** `HorizontalDivider` con `outlineVariant` entre filas (con `padding horizontal 16.dp`).
- **No usar `Modifier.weight`** en este patrón: en la versión de Compose del proyecto el import
  `androidx.compose.foundation.layout.weight` resuelve a un símbolo `internal` y falla la compilación.
  Se usa `Arrangement.spacedBy(16.dp)` en su lugar (funciona sin overflow).

### 3a. Pantalla de Preferencias del Admin (`PreferenciasScreen.kt`)
- Cabecera idéntica a la del resto, título "Preferencias", `IconButton` con `ArrowBack` →
  `navController.popBackStack()`.
- Título de sección "Apariencia".
- "Cerrar sesión": `TextButton` rojo `#F44336` con icono `Logout`
  (`Icons.AutoMirrored.Filled.Logout`), ancho completo, separado 32.dp de la card.
- Diálogo `AlertDialog` intacto.

### 3b. Pantalla de Ajustes del Cliente (`ConfiguracionScreen.kt`)
- Misma cabecera pero título visible "Ajustes" (el nombre interno `ConfiguracionScreen`/
  `Routes.CONFIGURACION` NO se cambia).
- Misma Card y patrón `TemaOption`. Contiene además el acceso **"Denunciar contenido o usuario"**.

## 4. Estado de archivos

### Admin (`:app`)
- `ui/home/HomeScreen.kt` — Home con cabecera azul + 6 cards de imagen (`HomeAdminImageCard`, 175 dp) +
  2 compactas (`HomeAdminCompactCard`) + badge (`HomeAdminBadge`).
- `ui/components/MenuCard.kt` — usado por el Home; el rol principal del Home pasó a las cards de imagen.
- `ui/configuracion/PreferenciasScreen.kt` — patrón `TemaOption` + "Cerrar sesión".
- `ui/viewmodel/MainViewModel.kt` — expone `nombreNegocio` y `logoNegocio` (sin cambios de lógica).
- Recursos: `res/drawable/img_centro`, `img_clinetes`, `img_actividades_admind`, `img_rutinas_admind`,
  `img_econimia`, `img_solicitudes_baja` (imágenes de las cards).

### Cliente (`:appCliente`)
- `ui/home/HomeScreen.kt` — cabecera azul, cards con imagen, Ajustes/Notificaciones azules, indicador de
  estado al final con datos reales.
- `ui/configuracion/ConfiguracionScreen.kt` — patrón `TemaOption`, título "Ajustes" + "Denunciar…".
- `ui/components/MenuCard.kt` — estilo propio del Cliente (no confundir con el del Admin).
- `ui/viewmodel/MainViewModel.kt` — expone `logoNegocio`/nombre del centro.

## 5. Pendientes / siguientes pasos (visual)

- No hay rediseños visuales pendientes conocidos en los Homes.
- Posibles ajustes finos si el usuario los pide (alpha de fondos, tamaños, elevación).
- La i18n del ADMIN sigue parcial (solo Home + Centro + Rutinas); **no** continuar sin instrucción.

## 6. Notas técnicas importantes

- `TakePicture` no requiere permiso de cámara en el manifest (usa la app de cámara del sistema).
- Cliente tiene `FileProvider` + `res/xml/file_paths.xml` (cache-path `fotos_camara`).
- `material-icons-extended` está en ambos `build.gradle.kts` (necesario para
  `LightMode`/`DarkMode`/`SettingsBrightness`/`Logout`).
- `tonalElevation` NO existe como parámetro de `CardDefaults.cardElevation()` ni
  `surfaceColorAtElevation` en la versión de M3 del proyecto (usar `Surface(tonalElevation=...)` si hace falta).
- Comando de build usado: `.\gradlew.bat :app:assembleDebug` (Admin) /
  `.\gradlew.bat :appCliente:assembleDebug` (Cliente). `BUILD SUCCESSFUL` en ambos.

## 7. Archivos clave para retomar

- `app/src/main/java/com/roberto/gestorpro/ui/home/HomeScreen.kt` (Admin)
- `app/src/main/java/com/roberto/gestorpro/ui/configuracion/PreferenciasScreen.kt` (Admin)
- `appCliente/src/main/java/com/roberto/gestorpro/cliente/ui/home/HomeScreen.kt` (Cliente)
- `appCliente/src/main/java/com/roberto/gestorpro/cliente/ui/configuracion/ConfiguracionScreen.kt` (Cliente)
- Recursos de imagen de las cards en `app/src/main/res/drawable/` y `appCliente/src/main/res/drawable/`.
