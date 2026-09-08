package com.roberto.gestorpro.navigation

/**
 * Routes.kt
 * ---------
 * ✔ TIPO: archivo de código fuente Kotlin (navegación)
 * Es el archivo que centraliza las rutas de navegación de GestorPro Admin.
 * Sirve para que NavHost y todas las pantallas usen las mismas rutas y la
 * navegación sea consistente. (Las rutas de CLIENTE y Vía B quedaron fuera:
 * la app Admin es exclusiva del administrador.)
 */
object Routes {

    /**
     * LOGIN
     * -----
     * Ruta de la pantalla de inicio de sesión.
     */
    const val LOGIN = "login"

    /**
     * REGISTRO
     * --------
     * Ruta de la pantalla de creación de cuenta real con Firebase.
     */
    const val REGISTRO = "registro"

    /**
     * RECUPERAR_PASSWORD
     * ------------------
     * Ruta de la pantalla de recuperación de contraseña.
     */
    const val RECUPERAR_PASSWORD = "recuperar_password"

    /**
     * HOME
     * ----
     * Ruta de la pantalla de inicio o menú principal del administrador.
     */
    const val HOME = "home"

    /**
     * CLIENTES
     * --------
     * Ruta de la pantalla de la lista de clientes.
     */
    const val CLIENTES = "clientes"

    /**
     * PERFILCLIENTE
     * -------------
     * Ruta de la pantalla de perfil de un cliente.
     */
    const val PERFILCLIENTE = "perfil_cliente"

    /**
     * perfilCliente
     * -------------
     * Ruta dinámica del perfil de un cliente concreto: perfil_cliente/{id}.
     */
    fun perfilCliente(idCliente: Int): String {
        return "$PERFILCLIENTE/$idCliente"
    }

    /**
     * AÑADIRCLIENTE
     * -------------
     * Ruta del formulario para añadir un nuevo cliente.
     */
    const val AÑADIRCLIENTE = "añadir_cliente"

    /**
     * MODIFICARCLIENTE
     * ----------------
     * Ruta base del formulario para modificar un cliente existente.
     */
    const val MODIFICARCLIENTE = "modificar_cliente"

    const val ECONOMIA = "economia"
    const val CONFIGURACION = "configuracion"
    const val PREFERENCIAS = "preferencias"
    const val DATOS = "datos"
    const val CUENTA = "cuenta"
    const val CLASES = "clases"
    const val CREAR_CLASE = "crear_clase"

    /**
     * SERVICIOS
     * ---------
     * Ruta de la pantalla principal de gestión de servicios del ADMIN.
     */
    const val SERVICIOS = "servicios"

    /**
     * CREAR_SERVICIO
     * --------------
     * Ruta del formulario de creación de un servicio nuevo.
     */
    const val CREAR_SERVICIO = "crear_servicio"

    /**
     * EDITAR_SERVICIO
     * ---------------
     * Ruta base del formulario de edición de un servicio existente.
     */
    const val EDITAR_SERVICIO = "editar_servicio"

    /**
     * editarServicio
     * --------------
     * Ruta dinámica del formulario de edición: editar_servicio/{idServicio}.
     */
    fun editarServicio(idServicio: Int): String {
        return "$EDITAR_SERVICIO/$idServicio"
    }

    /**
     * DETALLE_SERVICIO
     * ----------------
     * Ruta base del detalle de un servicio con sus sesiones.
     */
    const val DETALLE_SERVICIO = "detalle_servicio"

    /**
     * detalleServicio
     * ---------------
     * Ruta dinámica del detalle: detalle_servicio/{idServicio}.
     */
    fun detalleServicio(idServicio: Int): String {
        return "$DETALLE_SERVICIO/$idServicio"
    }

    /**
     * PROGRAMAR_SESIONES
     * ------------------
     * Ruta base de la programación de sesiones de un servicio.
     */
    const val PROGRAMAR_SESIONES = "programar_sesiones"

    /**
     * programarSesiones
     * -----------------
     * Ruta dinámica de la programación: programar_sesiones/{idServicio}.
     */
    fun programarSesiones(idServicio: Int): String {
        return "$PROGRAMAR_SESIONES/$idServicio"
    }

    /**
     * SESION_RESERVAS
     * ---------------
     * Ruta base del detalle de reservas de una sesión.
     */
    const val SESION_RESERVAS = "sesion_reservas"

    /**
     * sesionReservas
     * --------------
     * Ruta dinámica del detalle de reservas: sesion_reservas/{idSesion}.
     */
    fun sesionReservas(idSesion: Int): String {
        return "$SESION_RESERVAS/$idSesion"
    }

    /**
     * EDITAR_SESION
     * -------------
     * Ruta base de la pantalla "Ver / editar sesión" de una sesión concreta.
     */
    const val EDITAR_SESION = "editar_sesion"

    /**
     * editarSesion
     * ------------
     * Ruta dinámica de la edición de una sesión: editar_sesion/{idSesion}.
     */
    fun editarSesion(idSesion: Int): String {
        return "$EDITAR_SESION/$idSesion"
    }

    /**
     * MINEGOCIO
     * ---------
     * Ruta de la pantalla de personalización del negocio (nombre y logo).
     */
    const val MINEGOCIO = "mi_negocio"

    /**
     * CREAR_NEGOCIO
     * -------------
     * Ruta del alta remota del negocio (negocios + negocios_publicos +
     * usuarios/{uid}) con su código maestro.
     */
    const val CREAR_NEGOCIO = "crear_negocio"

    fun detalleClase(idClase: Int): String {
        return "detalle_clase/$idClase"
    }

    const val DETALLE_CLASE = "detalle_clase"

    const val DETALLE_SESION_RESERVAS = "detalle_sesion_reservas"

    fun detalleSesionReservas(idSesion: Int): String {
        return "detalle_sesion_reservas/$idSesion"
    }

    /**
     * modificarCliente
     * ----------------
     * Ruta dinámica del formulario de modificación de un cliente concreto.
     */
    fun modificarCliente(idCliente: Int): String {
        return "$MODIFICARCLIENTE/$idCliente"
    }

    /**
     * NOTIFICACIONES
     * --------------
     * Ruta de la pantalla principal de gestión de notificaciones del ADMIN.
     */
    const val NOTIFICACIONES = "notificaciones"

    /**
     * CREAR_NOTIFICACION
     * ------------------
     * Ruta del formulario de creación de una notificación del ADMIN.
     */
    const val CREAR_NOTIFICACION = "crear_notificacion"

    /**
     * CONFIG_NOTIFICACIONES
     * ---------------------
     * Ruta de la configuración de notificaciones preconfiguradas del negocio.
     */
    const val CONFIG_NOTIFICACIONES = "config_notificaciones"

    /**
     * SELECCIONAR_CLIENTES
     * --------------------
     * Ruta de la pantalla de selección de clientes para una notificación GRUPAL.
     */
    const val SELECCIONAR_CLIENTES = "seleccionar_clientes"

    /**
     * seleccionarClientes
     * -------------------
     * Ruta con parámetro de modo: "individual" o "grupo". La pantalla de
     * selección es la misma; solo cambia el máximo de selección.
     */
    fun seleccionarClientes(modo: String): String =
        "$SELECCIONAR_CLIENTES?modo=$modo"

    /**
     * SOLICITUDES
     * -----------
     * Ruta de la pantalla de gestión de solicitudes de baja del ADMIN.
     */
    const val SOLICITUDES = "solicitudes"

    /**
     * POLITICA_PRIVACIDAD
     * -------------------
     * Ruta de la política de privacidad de GestPro (app Admin).
     */
    const val POLITICA_PRIVACIDAD = "politica_privacidad"

    /**
     * TERMINOS_CONDICIONES
     * --------------------
     * Ruta de los términos y condiciones de uso de Trazys (app Admin).
     */
    const val TERMINOS_CONDICIONES = "terminos_condiciones"

    /**
     * DENUNCIAS
     * ---------
     * Ruta de la gestión de denuncias UGC del ADMIN.
     */
    const val DENUNCIAS = "denuncias"

    /**
     * ELIMINAR_CUENTA
     * ---------------
     * Ruta de la eliminación permanente de la cuenta y del negocio (app Admin).
     */
    const val ELIMINAR_CUENTA = "eliminar_cuenta"

}
