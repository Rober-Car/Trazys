package com.roberto.gestorpro.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * FechaBajaEfectiva
 * -----------------
 * Lógica PURA (sin Android ni estado) para resolver la FECHA EFECTIVA de baja
 * a partir del día elegido en el selector. La usan las tres vías DIRECTAS de
 * baja (perfil, edición y baja masiva) para que la fecha elegida se propague de
 * forma coherente hasta `fechaBaja` (y desde ahí a la frontera de etapa de
 * `MovimientoMorosidad`).
 *
 * Reglas:
 *  - HOY (día actual) -> instante actual (`ahora`), igual que el comportamiento
 *    histórico de la baja.
 *  - Fecha PASADA -> medianoche LOCAL de ese día.
 *  - `null` -> HOY.
 *  - No se admiten fechas futuras (`esDiaPermitido`).
 *
 * NO decide nada sobre reservas ni morosidad: solo calcula el instante de
 * `fechaBaja`. Las reservas futuras se siguen cancelando con el momento actual.
 */
object FechaBajaEfectiva {

    /** Día local (epochDay) correspondiente a `millis` en `zone`. */
    fun diaLocalDe(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    /** Día local (epochDay) de HOY en `zone`. */
    fun hoyEpochDay(ahora: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        diaLocalDe(ahora, zone)

    /**
     * Instante (millis) que se guarda como `fechaBaja` para el día elegido:
     * HOY (o null) -> `ahora`; una fecha pasada -> medianoche local de ese día.
     */
    fun millis(
        diaEpochDay: Long?,
        ahora: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val hoy = hoyEpochDay(ahora, zone)
        val dia = diaEpochDay ?: hoy
        return if (dia == hoy) {
            ahora
        } else {
            LocalDate.ofEpochDay(dia).atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }

    /**
     * ¿Es un día permitido para la baja (HOY o anterior)? No se admiten futuros.
     */
    fun esDiaPermitido(
        diaEpochDay: Long,
        ahora: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean = diaEpochDay <= hoyEpochDay(ahora, zone)
}
