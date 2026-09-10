package com.roberto.gestorpro.cliente.navigation

/**
 * Routes
 * ------
 * ✔ TIPO: object
 * Centraliza las rutas de navegación de GestorPro Cliente.
 */
object Routes {

    const val LOGIN = "login"
    const val REGISTRO = "registro"
    const val RECUPERAR_PASSWORD = "recuperar_password"

    /**
     * INICIO
     * ------
     * Pantalla de vinculación (código maestro + DNI). Se llega desde la
     * elección inicial, desde "Vincular gimnasio" del Home o tras completar
     * el registro.
     */
    const val INICIO = "inicio"

    /**
     * ELECCION
     * --------
     * Primera pantalla del CLIENTE autenticado sin ficha ni perfil pendiente:
     * decide entre "Vincularme al centro" (código + DNI) o "Registrarme".
     */
    const val ELECCION = "eleccion_inicio"

    /**
     * COMPLETAR_PERFIL
     * ----------------
     * Formulario de datos personales del CLIENTE sin negocio (VÍA 2).
     */
    const val COMPLETAR_PERFIL = "completar_perfil"

    /**
     * HOME
     * ----
     * Menú principal del cliente (vinculado o sin vincular).
     */
    const val HOME = "home"

    /**
     * CLASES
     * ------
     * Apartado de clases/sesiones del cliente.
     */
    const val CLASES = "clases"

    /**
     * ASISTENTES_SESION
     * -----------------
     * Pantalla independiente con los asistentes confirmados de una sesión que el
     * CLIENTE ya tiene reservada. Ruta con argumento: asistentes_sesion/{idSesion}.
     * Solo nombres (sin apellidos, fotos, teléfonos ni emails).
     */
    const val ASISTENTES_SESION = "asistentes_sesion/{idSesion}"

    /**
     * MI_PERFIL
     * ---------
     * Ver y editar los datos personales de la propia ficha.
     */
    const val MI_PERFIL = "mi_perfil"

    /**
     * EDITAR_PERFIL
     * -------------
     * Formulario de edición de los datos personales de la propia ficha.
     */
    const val EDITAR_PERFIL = "editar_perfil"

    /**
     * CUENTA
     * ------
     * Cerrar sesión / recuperar contraseña.
     */
    const val CUENTA = "cuenta"

    /**
     * CONFIGURACION
     * -------------
     * Ajustes de la aplicación (tema claro/oscuro).
     */
    const val CONFIGURACION = "configuracion"

    /**
     * NOTIFICACIONES
     * --------------
     * Configuración visual temporal de avisos del cliente.
     */
    const val NOTIFICACIONES = "notificaciones"

    /**
     * CONFIGURACION_NOTIFICACIONES
     * -----------------------------
     * Configuración visual temporal de los avisos del cliente.
     */
    const val CONFIGURACION_NOTIFICACIONES = "configuracion_notificaciones"

    /**
     * RUTINAS
     * -------
     * Apartado visual de rutinas de entrenamiento.
     */
    const val RUTINAS = "rutinas"

    /**
     * HORARIO_CENTRO
     * --------------
     * Horario semanal de apertura/cierre del centro con sus excepciones
     * (solo clientes vinculados y activos).
     */
    const val HORARIO_CENTRO = "horario_centro"

    /**
     * HORARIO_ACTIVIDADES
     * -------------------
     * Horario semanal de las actividades del centro (independiente de las
     * sesiones; solo clientes vinculados y activos).
     */
    const val HORARIO_ACTIVIDADES = "horario_actividades"

    /** Destino visual provisional de la política de privacidad. */
    const val POLITICA_PRIVACIDAD = "politica_privacidad"

    /** Destino visual provisional de los términos y condiciones. */
    const val TERMINOS_CONDICIONES = "terminos_condiciones"

    /** Destino de eliminación permanente de la cuenta. */
    const val ELIMINAR_CUENTA = "eliminar_cuenta"
}
