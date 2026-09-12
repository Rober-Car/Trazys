package com.roberto.gestorpro.ui.notificaciones

/**
 * ModoSeleccion
 * -------------
 * Cómo se seleccionan los clientes: uno solo (INDIVIDUAL) o varios (GRUPO).
 * Lo consume `SeleccionarClientesScreen` (ruta `seleccionar_clientes?modo=`).
 */
enum class ModoSeleccion {
    UNO,
    MUCHOS
}
