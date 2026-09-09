package com.roberto.gestorpro.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ServicioEntity
 * --------------
 * Entidad Room de la tabla "servicio".
 * Representa un servicio del negocio (Sala de máquinas, CrossFit, Yoga, ...).
 * Un servicio puede tener muchas sesiones; el CLIENTE los contrata (varios a la vez).
 * Un servicio activo es el único que puede recibir nuevas sesiones y contratarse.
 */
@Entity(tableName = "servicio")
data class ServicioEntity(
    @PrimaryKey(autoGenerate = true)
    val idServicio: Int = 0,
    val negocioId: String,
    val nombre: String,
    val descripcion: String,
    val activo: Boolean = true,
    val precio: Double = 0.0,
    val permiteCombinarDia: Boolean = true
)
