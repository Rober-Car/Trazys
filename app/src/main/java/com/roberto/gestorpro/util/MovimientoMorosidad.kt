package com.roberto.gestorpro.util

import com.roberto.gestorpro.data.entity.MovimientoEntity
import com.roberto.gestorpro.model.EstadoCliente
import com.roberto.gestorpro.model.EstadoMovimiento

/**
 * ResultadoMorosidad
 * ------------------
 * Resultado de la detección de morosidad de un cliente en un instante dado,
 * con la distinción conceptual de las DOS causas:
 *  - morosoPorDeuda : existe al menos un movimiento PENDIENTE.
 *  - morosoPorFecha : ACTIVO cuyo período actual (de la etapa actual) venció.
 */
data class ResultadoMorosidad(
    val moroso: Boolean,
    val deuda: Double,
    val morosoPorDeuda: Boolean = false,
    val morosoPorFecha: Boolean = false
)

/**
 * EstadoFinalMorosidad
 * --------------------
 * Valores finales a persistir en ClienteEntity tras aplicar el recálculo
 * (conserva la fecha de entrada previa mientras el cliente siga moroso).
 */
data class EstadoFinalMorosidad(
    val moroso: Boolean,
    val fechaEntradaMorosidad: Long?
)

/**
 * MovimientoMorosidad
 * -------------------
 * ÚNICA fuente de lógica de morosidad y deuda del ADMIN. Funciones PURAS,
 * sin acceso a base de datos ni UI, para poder testearse fácilmente.
 *
 * Reglas (modelo económico vigente):
 *  - Deuda = suma de TODOS los movimientos PENDIENTES (sin filtrar por etapa ni
 *    por fechas). Un PENDIENTE ya es deuda.
 *  - ACTIVO: moroso si tiene deuda pendiente O si `fechaFinActual < ahora`.
 *  - BAJA: moroso SOLO si existe deuda pendiente (nunca por fecha).
 *  - REGISTRADO / ARCHIVADO / MOROSO (legacy): sin morosidad propia.
 *  - ETAPA ACTUAL: para la causa "por fecha" solo cuentan los movimientos
 *    CREADOS en la etapa actual, es decir, con `fechaRegistro >= inicioEtapa`
 *    (la última `fechaBaja`; `null` = sin corte). Así, al reactivar de BAJA a
 *    ACTIVO no se hereda la morosidad por fecha del período anterior. Un
 *    movimiento creado DESPUÉS de la reactivación sí cuenta. Los movimientos
 *    históricos sin `fechaRegistro` (0) se tratan como anteriores a la etapa.
 *  - La causa "por fecha" NO depende del estado de pago del movimiento (PAGADO o
 *    PENDIENTE) y NO usa `fechaPago`.
 *  - exentoMorosidad = true: moroso = false y fechaEntradaMorosidad = null,
 *    pero la DEUDA se sigue calculando (valor real). No toca los movimientos.
 *  - fechaEntradaMorosidad = fecha ACTUAL (ahora) de detección de la entrada en
 *    morosidad; se conserva mientras siga moroso; se limpia al salir; se
 *    renueva al volver a entrar.
 */
object MovimientoMorosidad {

    /**
     * Deuda actual: suma de precioFinal de TODOS los movimientos PENDIENTE.
     * No cuenta los PAGADOS. No depende de fechaFin ni de la etapa.
     */
    fun deudaDe(movimientos: List<MovimientoEntity>): Double =
        movimientos
            .filter { it.estado == EstadoMovimiento.PENDIENTE }
            .sumOf { it.precioFinal }

    /**
     * ¿Existe al menos un movimiento PENDIENTE? (morosidad por deuda).
     */
    fun morosidadPorDeuda(movimientos: List<MovimientoEntity>): Boolean =
        movimientos.any { it.estado == EstadoMovimiento.PENDIENTE }

    /**
     * periodoActualDe
     * ---------------
     * Movimiento de la ETAPA ACTUAL con mayor `fechaFin` (el período más
     * reciente). Un movimiento pertenece a la etapa actual si
     * `fechaRegistro >= inicioEtapa` (última `fechaBaja`); `inicioEtapa == null`
     * = sin corte (todos los movimientos). null si no hay movimientos de la etapa.
     */
    fun periodoActualDe(
        movimientos: List<MovimientoEntity>,
        inicioEtapa: Long? = null
    ): MovimientoEntity? {
        val corte = inicioEtapa ?: Long.MIN_VALUE
        return movimientos
            .filter { it.fechaRegistro >= corte }
            .maxByOrNull { it.fechaFin }
    }

    /**
     * `fechaFinActual`: fin del período más reciente de la etapa actual
     * (`periodoActualDe`). null si no hay movimientos en la etapa.
     */
    fun fechaFinActualDe(
        movimientos: List<MovimientoEntity>,
        inicioEtapa: Long? = null
    ): Long? = periodoActualDe(movimientos, inicioEtapa)?.fechaFin

    /**
     * Morosidad por FECHA: el período más reciente de la etapa actual ya venció
     * (`fechaFinActual < ahora`). NO depende del estado de pago del movimiento
     * (los PENDIENTES no la impiden) y NO usa `fechaPago`.
     */
    fun morosidadPorFecha(
        movimientos: List<MovimientoEntity>,
        ahora: Long,
        inicioEtapa: Long? = null
    ): Boolean {
        val fin = fechaFinActualDe(movimientos, inicioEtapa) ?: return false
        return fin < ahora
    }

    /**
     * Detección de morosidad según el estado administrativo del cliente, la
     * frontera de etapa (`inicioEtapa` = última `fechaBaja`) y la excepción
     * manual `exentoMorosidad`. Devuelve también la deuda real.
     */
    fun resultadoDe(
        estado: EstadoCliente,
        movimientos: List<MovimientoEntity>,
        ahora: Long,
        exentoMorosidad: Boolean = false,
        inicioEtapa: Long? = null
    ): ResultadoMorosidad {
        val deuda = deudaDe(movimientos)
        val porDeuda = morosidadPorDeuda(movimientos)
        val porFecha = estado == EstadoCliente.ACTIVO &&
            morosidadPorFecha(movimientos, ahora, inicioEtapa)
        val morosoCalculado = when (estado) {
            EstadoCliente.ACTIVO -> porDeuda || porFecha
            EstadoCliente.BAJA -> porDeuda
            // REGISTRADO / ARCHIVADO / MOROSO (legacy): sin morosidad propia.
            else -> false
        }
        val moroso = if (exentoMorosidad) false else morosoCalculado
        return ResultadoMorosidad(
            moroso = moroso,
            deuda = deuda,
            morosoPorDeuda = porDeuda,
            morosoPorFecha = porFecha
        )
    }

    /**
     * Valores finales a persistir. fechaEntradaMorosidad:
     *  - Si el cliente YA era moroso: se conserva su fecha de entrada previa.
     *  - Si pasa de no moroso a moroso (o vuelve a entrar): `ahora` (fecha de
     *    detección del inicio de la situación). NO se usa fechaFin.
     *  - Si deja de ser moroso: null.
     *  - Si exentoMorosidad = true: moroso = false y fechaEntradaMorosidad = null.
     */
    fun resultadoFinal(
        estado: EstadoCliente,
        movimientos: List<MovimientoEntity>,
        morosoPrevio: Boolean,
        fechaEntradaPrevia: Long?,
        ahora: Long,
        exentoMorosidad: Boolean = false,
        inicioEtapa: Long? = null
    ): EstadoFinalMorosidad {
        val resultado = resultadoDe(
            estado, movimientos, ahora, exentoMorosidad, inicioEtapa
        )
        val fechaFinal = when {
            exentoMorosidad -> null
            !resultado.moroso -> null
            morosoPrevio && fechaEntradaPrevia != null -> fechaEntradaPrevia
            else -> ahora
        }
        return EstadoFinalMorosidad(resultado.moroso, fechaFinal)
    }
}
