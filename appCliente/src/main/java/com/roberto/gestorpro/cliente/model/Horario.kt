package com.roberto.gestorpro.cliente.model

import java.time.DayOfWeek

/**
 * TramoHorario
 * ------------
 * Horario de un día del centro: abierto/cerrado y, si está abierto, apertura y
 * cierre en formato "HH:mm". Un único tramo por día.
 */
data class TramoHorario(
    val cerrado: Boolean = false,
    val apertura: String = "",
    val cierre: String = ""
)

/**
 * ActividadHorario
 * ----------------
 * Entrada del horario semanal de actividades: la actividad (idServicio) y su
 * hora habitual. El nombre se resuelve en vivo contra las actividades actuales.
 */
data class ActividadHorario(
    val idServicio: Int,
    val hora: String
)

/**
 * HorarioNegocio
 * --------------
 * Horario configurable del negocio, independiente de las sesiones. Vacío si el
 * negocio no lo ha configurado (compatibilidad con negocios existentes).
 */
data class HorarioNegocio(
    val centro: Map<DayOfWeek, TramoHorario> = emptyMap(),
    val actividades: Map<DayOfWeek, List<ActividadHorario>> = emptyMap()
) {
    val vacio: Boolean get() = centro.isEmpty() && actividades.isEmpty()
}

/**
 * HorarioParseo
 * -------------
 * Reconstruye el modelo de horario desde los mapas remotos de
 * negocios_publicos/{id}. Fail-open: valores ausentes o inválidos se ignoran.
 */
object HorarioParseo {

    fun mapaACentro(valor: Any?): Map<DayOfWeek, TramoHorario> {
        val mapa = valor as? Map<*, *> ?: return emptyMap()
        val resultado = mutableMapOf<DayOfWeek, TramoHorario>()
        mapa.forEach { (clave, tramo) ->
            val dia = diaDe(clave?.toString()) ?: return@forEach
            val datos = tramo as? Map<*, *>
            resultado[dia] = TramoHorario(
                cerrado = (datos?.get("cerrado") as? Boolean) ?: false,
                apertura = datos?.get("apertura") as? String ?: "",
                cierre = datos?.get("cierre") as? String ?: ""
            )
        }
        return resultado
    }

    fun mapaAActividades(valor: Any?): Map<DayOfWeek, List<ActividadHorario>> {
        val mapa = valor as? Map<*, *> ?: return emptyMap()
        val resultado = mutableMapOf<DayOfWeek, List<ActividadHorario>>()
        mapa.forEach { (clave, lista) ->
            val dia = diaDe(clave?.toString()) ?: return@forEach
            val entradas = (lista as? List<*>)
                ?.mapNotNull { entrada ->
                    val datos = entrada as? Map<*, *> ?: return@mapNotNull null
                    val idServicio = (datos["idServicio"] as? Number)?.toInt()
                        ?: return@mapNotNull null
                    val hora = datos["hora"] as? String ?: return@mapNotNull null
                    ActividadHorario(idServicio = idServicio, hora = hora)
                } ?: emptyList()
            if (entradas.isNotEmpty()) {
                resultado[dia] = entradas
            }
        }
        return resultado
    }

    private fun diaDe(nombre: String?): DayOfWeek? =
        nombre?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
}
