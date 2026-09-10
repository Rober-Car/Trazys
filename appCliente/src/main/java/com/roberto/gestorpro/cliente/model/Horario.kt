package com.roberto.gestorpro.cliente.model

import java.time.DayOfWeek

/**
 * TramoHorario
 * ------------
 * Un tramo de apertura/cierre del centro en formato "HH:mm". Un día puede tener
 * varios tramos (p. ej. 10:00-13:00 y 17:00-22:00).
 */
data class TramoHorario(
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
 * ExcepcionHorario
 * ----------------
 * Excepción para una FECHA concreta (festivo, cierre especial, horario
 * especial). Tiene prioridad sobre el horario semanal. Lista de tramos vacía =
 * CERRADO. `fecha` es la medianoche local del día en epoch millis.
 */
data class ExcepcionHorario(
    val fecha: Long,
    val tramos: List<TramoHorario> = emptyList()
) {
    val cerrado: Boolean get() = tramos.isEmpty()
}

/**
 * HorarioNegocio
 * --------------
 * Horario configurable del negocio, independiente de las sesiones. Vacío si el
 * negocio no lo ha configurado (compatibilidad con negocios existentes).
 */
data class HorarioNegocio(
    val centro: Map<DayOfWeek, List<TramoHorario>> = emptyMap(),
    val actividades: Map<DayOfWeek, List<ActividadHorario>> = emptyMap(),
    val excepciones: List<ExcepcionHorario> = emptyList()
) {
    val vacio: Boolean get() = centro.isEmpty() && actividades.isEmpty()
}

/**
 * HorarioParseo
 * -------------
 * Reconstruye el modelo de horario desde los mapas remotos de
 * negocios_publicos/{id}. Fail-open: valores ausentes o inválidos se ignoran.
 * Admite el formato nuevo (varios tramos) y el antiguo (un tramo).
 */
object HorarioParseo {

    fun mapaACentro(valor: Any?): Map<DayOfWeek, List<TramoHorario>> {
        val mapa = valor as? Map<*, *> ?: return emptyMap()
        val resultado = mutableMapOf<DayOfWeek, List<TramoHorario>>()
        mapa.forEach { (clave, tramos) ->
            val dia = diaDe(clave?.toString()) ?: return@forEach
            resultado[dia] = tramosDe(tramos)
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

    fun listaAExcepciones(valor: Any?): List<ExcepcionHorario> {
        val lista = valor as? List<*> ?: return emptyList()
        return lista.mapNotNull { entrada ->
            val datos = entrada as? Map<*, *> ?: return@mapNotNull null
            val fecha = (datos["fecha"] as? Number)?.toLong() ?: return@mapNotNull null
            val tramos = if (datos.containsKey("tramos")) {
                tramosDe(datos["tramos"])
            } else {
                val cerrado = (datos["cerrado"] as? Boolean) ?: false
                if (cerrado) {
                    emptyList()
                } else {
                    listOf(
                        TramoHorario(
                            apertura = datos["apertura"] as? String ?: "",
                            cierre = datos["cierre"] as? String ?: ""
                        )
                    )
                }
            }
            ExcepcionHorario(fecha = fecha, tramos = tramos)
        }.sortedBy { it.fecha }
    }

    private fun tramosDe(valor: Any?): List<TramoHorario> {
        return when (valor) {
            is List<*> -> valor.mapNotNull { entrada ->
                val datos = entrada as? Map<*, *> ?: return@mapNotNull null
                TramoHorario(
                    apertura = datos["apertura"] as? String ?: "",
                    cierre = datos["cierre"] as? String ?: ""
                )
            }
            is Map<*, *> -> {
                val cerrado = (valor["cerrado"] as? Boolean) ?: false
                if (cerrado) {
                    emptyList()
                } else {
                    listOf(
                        TramoHorario(
                            apertura = valor["apertura"] as? String ?: "",
                            cierre = valor["cierre"] as? String ?: ""
                        )
                    )
                }
            }
            else -> emptyList()
        }
    }

    private fun diaDe(nombre: String?): DayOfWeek? =
        nombre?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
}
