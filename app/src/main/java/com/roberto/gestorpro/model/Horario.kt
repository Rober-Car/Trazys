package com.roberto.gestorpro.model

import java.time.DayOfWeek

/**
 * TramoHorario
 * ------------
 * Horario de un día para el centro: abierto/cerrado y, si está abierto, la
 * hora de apertura y de cierre en formato "HH:mm". Un único tramo por día.
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
 * hora habitual "HH:mm". El nombre NO se guarda aquí: se resuelve en vivo
 * contra las actividades actuales para reflejar renombrados.
 */
data class ActividadHorario(
    val idServicio: Int,
    val hora: String
)

/**
 * ExcepcionHorario
 * ----------------
 * Excepción para una FECHA concreta (festivo, cierre especial, horario
 * especial...). Tiene PRIORIDAD sobre el horario semanal del día que
 * corresponda. `fecha` es la medianoche local del día concreto en epoch millis.
 * Se guarda SEPARADA del horario semanal y NUNCA lo modifica.
 */
data class ExcepcionHorario(
    val fecha: Long,
    val cerrado: Boolean = false,
    val apertura: String = "",
    val cierre: String = ""
)

/**
 * HorarioNegocio
 * --------------
 * Horario configurable del negocio, INDEPENDIENTE de las sesiones:
 *  - centro: horario semanal general (un tramo por día);
 *  - actividades: horario semanal de actividades (varias por día permitidas);
 *  - excepciones: días concretos con prioridad sobre el horario semanal.
 * Si no hay configuración, todo está vacío (compatibilidad con negocios
 * existentes).
 */
data class HorarioNegocio(
    val centro: Map<DayOfWeek, TramoHorario> = emptyMap(),
    val actividades: Map<DayOfWeek, List<ActividadHorario>> = emptyMap(),
    val excepciones: List<ExcepcionHorario> = emptyList()
)

/**
 * HorarioSerializacion
 * --------------------
 * Conversión entre el modelo de horario y los mapas que se guardan en
 * Firestore (negocios/{id} y negocios_publicos/{id}). Las claves de día son el
 * nombre del enum (MONDAY..SUNDAY), estables e independientes del idioma.
 */
object HorarioSerializacion {

    /** Convierte el horario del centro al mapa remoto. */
    fun centroAMapa(centro: Map<DayOfWeek, TramoHorario>): Map<String, Any> =
        centro.entries.associate { (dia, tramo) ->
            dia.name to mapOf(
                "cerrado" to tramo.cerrado,
                "apertura" to tramo.apertura,
                "cierre" to tramo.cierre
            )
        }

    /** Convierte el horario de actividades al mapa remoto. */
    fun actividadesAMapa(
        actividades: Map<DayOfWeek, List<ActividadHorario>>
    ): Map<String, Any> =
        actividades.entries.associate { (dia, lista) ->
            dia.name to lista.map { entrada ->
                mapOf(
                    "idServicio" to entrada.idServicio,
                    "hora" to entrada.hora
                )
            }
        }

    /** Convierte las excepciones (días concretos) a la lista remota. */
    fun excepcionesALista(excepciones: List<ExcepcionHorario>): List<Map<String, Any>> =
        excepciones.sortedBy { it.fecha }.map { excepcion ->
            mapOf(
                "fecha" to excepcion.fecha,
                "cerrado" to excepcion.cerrado,
                "apertura" to excepcion.apertura,
                "cierre" to excepcion.cierre
            )
        }

    /** Reconstruye el horario del centro desde el valor remoto (fail-open). */
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

    /** Reconstruye el horario de actividades desde el valor remoto (fail-open). */
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

    /** Reconstruye las excepciones desde el valor remoto (fail-open). */
    fun listaAExcepciones(valor: Any?): List<ExcepcionHorario> {
        val lista = valor as? List<*> ?: return emptyList()
        return lista.mapNotNull { entrada ->
            val datos = entrada as? Map<*, *> ?: return@mapNotNull null
            val fecha = (datos["fecha"] as? Number)?.toLong() ?: return@mapNotNull null
            ExcepcionHorario(
                fecha = fecha,
                cerrado = (datos["cerrado"] as? Boolean) ?: false,
                apertura = datos["apertura"] as? String ?: "",
                cierre = datos["cierre"] as? String ?: ""
            )
        }.sortedBy { it.fecha }
    }

    private fun diaDe(nombre: String?): DayOfWeek? =
        nombre?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
}
