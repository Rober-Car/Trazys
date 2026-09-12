package com.roberto.gestorpro.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.roberto.gestorpro.model.EstadoMovimiento
import com.roberto.gestorpro.model.MetodoPago

/**
 * MovimientoEntity
 * ----------------
 * Entidad Room de la tabla "movimiento". Es el núcleo económico del negocio:
 * cada movimiento representa el cargo/cobro de un PERIODO para un cliente.
 *
 * A partir de la fase de ECONOMÍA (v14) un movimiento puede contener VARIOS
 * servicios: se guardan los IDs de ServicioEntity en `servicios`. El importe
 * que se cobra es ÚNICAMENTE `precioFinal` (lo que el ADMIN decidió cobrar,
 * ya sea la suma de los precios de los servicios o un importe manual).
 *
 * No se guardan nombres de servicios como estructura principal (los nombres
 * se resuelven contra la tabla "servicio"), ni existe "precioBase".
 */
@Entity(tableName = "movimiento")
data class MovimientoEntity(

    /**
     * idMovimiento: identificador único del movimiento (PK autogenerada).
     */
    @PrimaryKey(autoGenerate = true)
    val idMovimiento: Int = 0,

    /**
     * idCliente: cliente al que pertenece el movimiento. Es una REFERENCIA
     * HISTÓRICA interna: el movimiento es histórico autónomo del centro y su
     * visualización NO depende de que `clientes/{idCliente}` siga existiendo.
     */
    val idCliente: Int,

    /**
     * nombreCliente: FOTOGRAFÍA histórica del nombre del cliente en el momento
     * de crear el movimiento. No se actualiza al editar ni cuando cambia la ficha.
     * El defaultValue debe coincidir con la migración 20→21 (`DEFAULT ''`).
     */
    @ColumnInfo(defaultValue = "")
    val nombreCliente: String = "",

    /**
     * apellidosCliente: FOTOGRAFÍA histórica de los apellidos del cliente en el
     * momento de crear el movimiento. Inmutable al editar.
     */
    @ColumnInfo(defaultValue = "")
    val apellidosCliente: String = "",

    /**
     * dniCliente: FOTOGRAFÍA histórica del DNI del cliente en el momento de
     * crear el movimiento. Inmutable al editar.
     */
    @ColumnInfo(defaultValue = "")
    val dniCliente: String = "",

    /**
     * servicios: IDs de ServicioEntity incluidos en este movimiento.
     * Lista de enteros guardada como texto separado por comas (IntListConverter).
     * Un movimiento antiguo sin correspondencia segura con el catálogo
     * conserva esta lista vacía (no se inventan relaciones).
     */
    val servicios: List<Int> = emptyList(),

    /**
     * fechaInicio: inicio del periodo facturado (milisegundos).
     */
    val fechaInicio: Long,

    /**
     * fechaFin: fin del periodo facturado (milisegundos).
     */
    val fechaFin: Long,

    /**
     * precioFinal: importe final cobrado por el movimiento.
     * Es el ÚNICO importe persistido; los movimientos históricos conservan
     * el valor que tenían cuando se crearon y nunca se recalculan.
     */
    val precioFinal: Double = 0.0,

    /**
     * estado: PENDIENTE o PAGADO.
     */
    val estado: EstadoMovimiento,

    /**
     * fechaPago: fecha (milisegundos) en que se cobró; null si sigue pendiente.
     */
    val fechaPago: Long? = null,

    /**
     * metodoPago: método de pago opcional (EFECTIVO/BIZUM/TRANSFERENCIA).
     * Los movimientos históricos no tienen método asignado (null).
     */
    val metodoPago: MetodoPago? = null,

    /**
     * observaciones: notas opcionales del movimiento.
     */
    val observaciones: String? = null,

    /**
     * fechaRegistro: momento (milisegundos) en que el movimiento se CREÓ en el
     * sistema. Sirve como frontera de etapa económica: los movimientos creados
     * antes de la última baja (`fechaBaja`) no cuentan para la morosidad por
     * fecha tras reactivar (ver MovimientoMorosidad). NO es una fecha del
     * período (eso es fechaInicio/fechaFin) ni la fecha de pago. Los movimientos
     * históricos sin este dato valen 0 y se tratan como anteriores a la etapa.
     */
    @ColumnInfo(defaultValue = "0")
    val fechaRegistro: Long = 0L

)
