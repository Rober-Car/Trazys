package com.roberto.gestorpro.model

import java.time.DayOfWeek

/**
 * TramoHorario
 * ------------
 * Un tramo de apertura/cierre del centro en formato "HH:mm". Un día puede tener
 * 0, 1 o varios tramos (p. ej. 10:00-13:00 y 17:00-22:00).
 */
data class TramoHorario(
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
 * Admite varios tramos; una lista vacía significa CERRADO. Se guarda SEPARADA
 * del horario semanal y NUNCA lo modifica.
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
 * Horario configurable del negocio, INDEPENDIENTE de las sesiones:
 *  - centro: horario semanal general (varios tramos por día; lista vacía = cerrado);
 *  - actividades: horario semanal de actividades (varias por día permitidas);
 *  - excepciones: días concretos con prioridad sobre el horario semanal.
 * Si no hay configuración, todo está vacío (compatibilidad con negocios
 * existentes).
 */
data class HorarioNegocio(
    val centro: Map<DayOfWeek, List<TramoHorario>> = emptyMap(),
    val actividades: Map<DayOfWeek, List<ActividadHorario>> = emptyMap(),
    val excepciones: List<ExcepcionHorario> = emptyList()
)

/**
 * HorarioSerializacion
 * --------------------
 * Conversión entre el modelo de horario y los mapas que se guardan en
 * Firestore (negocios/{id} y negocios_publicos/{id}). Las claves de día son el
 * nombre del enum (MONDAY..SUNDAY), estables e independientes del idioma.
 *
 * Formato nuevo (varios tramos):
 *   horarioCentro: { "MONDAY": [ {apertura, cierre}, ... ], ... }  (lista vacía = cerrado)
 *   horarioExcepciones: [ {fecha, tramos:[{apertura,cierre},...]}, ... ]
 * Formato antiguo (un tramo): { "MONDAY": {cerrado, apertura, cierre}, ... } y
 * excepciones {fecha, cerrado, apertura, cierre}. El parseo admite AMBOS para
 * no perder datos existentes.
 */
object HorarioSerializacion {

    /** Convierte el horario del centro al mapa remoto (varios tramos por día). */
    fun centroAMapa(centro: Map<DayOfWeek, List<TramoHorario>>): Map<String, Any> =
        centro.entries.associate { (dia, tramos) ->
            dia.name to tramos.map { tramoAMapa(it) }
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
                "tramos" to excepcion.tramos.map { tramoAMapa(it) }
            )
        }

    /** Reconstruye el horario del centro desde el valor remoto (fail-open). */
    fun mapaACentro(valor: Any?): Map<DayOfWeek, List<TramoHorario>> {
        val mapa = valor as? Map<*, *> ?: return emptyMap()
        val resultado = mutableMapOf<DayOfWeek, List<TramoHorario>>()
        mapa.forEach { (clave, tramos) ->
            val dia = diaDe(clave?.toString()) ?: return@forEach
            resultado[dia] = tramosDe(tramos)
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
            val tramos = if (datos.containsKey("tramos")) {
                tramosDe(datos["tramos"])
            } else {
                // Formato antiguo: un único tramo o cerrado.
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

    /** Convierte un tramo a su mapa remoto. */
    private fun tramoAMapa(tramo: TramoHorario): Map<String, Any> =
        mapOf("apertura" to tramo.apertura, "cierre" to tramo.cierre)

    /**
     * Interpreta un valor remoto de día como lista de tramos. Admite el formato
     * nuevo (lista) y el antiguo (mapa cerrado/apertura/cierre).
     */
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
