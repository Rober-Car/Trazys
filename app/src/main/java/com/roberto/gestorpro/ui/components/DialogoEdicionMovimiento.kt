package com.roberto.gestorpro.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.roberto.gestorpro.data.entity.MovimientoEntity
import com.roberto.gestorpro.data.entity.ServicioEntity
import com.roberto.gestorpro.model.EstadoMovimiento
import com.roberto.gestorpro.model.MetodoPago
import com.roberto.gestorpro.util.MovimientoPago
import com.roberto.gestorpro.util.MovimientoPrecio
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * DialogoEdicionMovimiento
 * ------------------------
 * ÚNICA implementación del editor de un movimiento existente (diálogo de
 * detalle + edición). Es una extracción del editor que antes vivía embebido en
 * PerfilClienteAdministradorScreen; lo usan tanto el perfil del cliente como la
 * pantalla Economía para NO duplicar el editor.
 *
 * Mantiene intacto el comportamiento original:
 *  - precarga de los campos desde el movimiento;
 *  - edición de servicios activos (los históricos dados de baja se conservan);
 *  - precio final, fechas de inicio/fin, "Pago realizado", fecha y método de pago;
 *  - validaciones y guardado vía `MovimientoPago.resolver` (F4);
 *  - confirmación de eliminación (la eliminación la ejecuta el llamador).
 *
 * @param movimiento movimiento que se edita (nunca null; quien lo invoca
 *        decide cuándo mostrarlo).
 * @param serviciosActivos servicios ACTIVOS del catálogo para editar selección.
 * @param serviciosMap nombre de los servicios (id → nombre) para mostrar los
 *        históricos dados de baja.
 * @param onDismiss cierra el diálogo sin guardar.
 * @param onGuardar recibe el MovimientoEntity ya construido/validado para que
 *        el llamador lo persista por ViewModel → Repository (nunca DAO directo).
 * @param onEliminar se invoca tras la confirmación de eliminar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoEdicionMovimiento(
    movimiento: MovimientoEntity,
    serviciosActivos: List<ServicioEntity>,
    serviciosMap: Map<Int, String>,
    onDismiss: () -> Unit,
    onGuardar: (MovimientoEntity) -> Unit,
    onEliminar: (MovimientoEntity) -> Unit
) {
    val idsActivos = remember(movimiento, serviciosActivos) {
        serviciosActivos.map { it.idServicio }.toSet()
    }

    // Estado local del editor (precarga equivalente al LaunchedEffect anterior).
    var idsServiciosEditados by remember(movimiento) {
        mutableStateOf(movimiento.servicios.filter { it in idsActivos })
    }
    var idsServiciosEditadosFijos by remember(movimiento) {
        mutableStateOf(MovimientoPrecio.idsFijosHistoricos(movimiento.servicios, idsActivos))
    }
    var precioEditado by remember(movimiento) {
        mutableStateOf(MovimientoPrecio.precioCampo(movimiento.precioFinal))
    }
    var fechaInicioEditada by remember(movimiento) {
        mutableStateOf<Long?>(movimiento.fechaInicio)
    }
    var fechaFinEditada by remember(movimiento) {
        mutableStateOf<Long?>(movimiento.fechaFin)
    }
    var pagadoEditado by remember(movimiento) {
        mutableStateOf(movimiento.estado == EstadoMovimiento.PAGADO)
    }
    var fechaPagoEditada by remember(movimiento) {
        mutableStateOf(movimiento.fechaPago)
    }
    var metodoPagoEditadoNombre by remember(movimiento) {
        mutableStateOf<String?>(movimiento.metodoPago?.name)
    }
    var observacionesEditadas by remember(movimiento) {
        mutableStateOf(movimiento.observaciones ?: "")
    }
    var errorPrecioEditado by remember { mutableStateOf(false) }
    var errorFechaInicioEditada by remember { mutableStateOf(false) }
    var errorFechaFinEditada by remember { mutableStateOf(false) }
    var mostrarConfirmarEliminar by remember { mutableStateOf(false) }
    var mostrarDatePickerInicioDetalle by remember { mutableStateOf(false) }
    var mostrarDatePickerFinDetalle by remember { mutableStateOf(false) }
    var mostrarDatePickerPagoDetalle by remember { mutableStateOf(false) }

    fun alternarServicioEditado(idServicio: Int) {
        idsServiciosEditados = if (idServicio in idsServiciosEditados) {
            idsServiciosEditados - idServicio
        } else {
            (idsServiciosEditados + idServicio).distinct()
        }
    }

    val fechaInicioFormateadaDetalle =
        fechaInicioEditada?.let { formatearFechaEditor(it) } ?: ""
    val fechaFinFormateadaDetalle =
        fechaFinEditada?.let { formatearFechaEditor(it) } ?: ""

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Detalle",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF1E88E5),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Servicios del movimiento",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val nombresMovimientoDetalle = movimiento.servicios
                        .mapNotNull { serviciosMap[it] }
                        .joinToString(" + ")
                    Text(
                        text = if (nombresMovimientoDetalle.isBlank()) {
                            "Sin servicio asociado"
                        } else {
                            nombresMovimientoDetalle
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                if (serviciosActivos.isEmpty() && idsServiciosEditadosFijos.isEmpty()) {
                    Text(
                        text = "No hay servicios activos para añadir",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
                serviciosActivos.forEach { servicio ->
                    val marcado = servicio.idServicio in idsServiciosEditados
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                alternarServicioEditado(servicio.idServicio)
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = marcado,
                            onCheckedChange = {
                                alternarServicioEditado(servicio.idServicio)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = servicio.nombre,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = MovimientoPrecio.importeLegible(servicio.precio),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (idsServiciosEditadosFijos.isNotEmpty()) {
                    Text(
                        text = "Servicios dados de baja (se conservan)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    idsServiciosEditadosFijos.forEach { idServicio ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = true,
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = serviciosMap[idServicio]
                                    ?: "Servicio $idServicio",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = precioEditado,
                    onValueChange = {
                        precioEditado = it
                        errorPrecioEditado = false
                    },
                    label = { Text("Precio final (€)") },
                    isError = errorPrecioEditado,
                    supportingText = {
                        if (errorPrecioEditado) {
                            Text("Introduce un precio válido (0 o mayor)")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = fechaInicioFormateadaDetalle,
                    onValueChange = { },
                    readOnly = true,
                    enabled = false,
                    label = { Text("Fecha de inicio") },
                    placeholder = { Text("dd/MM/aaaa") },
                    isError = errorFechaInicioEditada,
                    supportingText = {
                        if (errorFechaInicioEditada) {
                            Text("La fecha de inicio es obligatoria")
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = Color.Transparent,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Seleccionar fecha de inicio"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            mostrarDatePickerInicioDetalle = true
                        }
                )

                OutlinedTextField(
                    value = fechaFinFormateadaDetalle,
                    onValueChange = { },
                    readOnly = true,
                    enabled = false,
                    label = { Text("Fecha de fin") },
                    placeholder = { Text("dd/MM/aaaa") },
                    isError = errorFechaFinEditada,
                    supportingText = {
                        if (errorFechaFinEditada) {
                            Text("La fecha de fin es obligatoria")
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = Color.Transparent,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Seleccionar fecha de fin"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            mostrarDatePickerFinDetalle = true
                        }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Pago realizado")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = pagadoEditado,
                        onCheckedChange = { activar ->
                            pagadoEditado = activar
                            // Al pasar a PAGADO se propone hoy como fecha de
                            // pago (el ADMIN puede modificarla después).
                            if (activar) {
                                fechaPagoEditada = System.currentTimeMillis()
                            }
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF1E88E5)
                        )
                    )
                }

                if (pagadoEditado) {
                    OutlinedTextField(
                        value = fechaPagoEditada?.let { formatearFechaEditor(it) } ?: "",
                        onValueChange = { },
                        readOnly = true,
                        enabled = false,
                        label = { Text("Fecha de pago") },
                        placeholder = { Text("dd/MM/aaaa") },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = Color.Transparent,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Seleccionar fecha de pago"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                mostrarDatePickerPagoDetalle = true
                            }
                    )

                    SelectorMetodoPago(
                        nombre = metodoPagoEditadoNombre,
                        onCambio = { metodoPagoEditadoNombre = it }
                    )
                }

                OutlinedTextField(
                    value = observacionesEditadas,
                    onValueChange = {
                        observacionesEditadas = it
                    },
                    label = { Text("Observaciones") },
                    modifier = Modifier.fillMaxWidth()
                )

                AppPrimaryButton(
                    onClick = {
                        val precioValido = precioEditado
                            .replace(",", ".")
                            .toDoubleOrNull()

                        errorPrecioEditado =
                            precioValido == null || precioValido < 0

                        errorFechaInicioEditada =
                            fechaInicioEditada == null

                        errorFechaFinEditada =
                            fechaFinEditada == null

                        val fechasValidas =
                            fechaInicioEditada != null &&
                                    fechaFinEditada != null &&
                                    fechaFinEditada!! >= fechaInicioEditada!!

                        if (!fechasValidas) {
                            errorFechaFinEditada = true
                        }

                        if (
                            !errorPrecioEditado &&
                            !errorFechaInicioEditada &&
                            !errorFechaFinEditada &&
                            fechasValidas
                        ) {
                            val pagoEditadoResuelto = MovimientoPago.resolver(
                                nuevoPagado = pagadoEditado,
                                eraPagado = movimiento.estado ==
                                    EstadoMovimiento.PAGADO,
                                fechaPagoElegida = fechaPagoEditada,
                                metodoPago = MovimientoPago.metodoPagoDe(
                                    metodoPagoEditadoNombre
                                ),
                                ahora = System.currentTimeMillis()
                            )

                            val movimientoActualizado = MovimientoEntity(
                                idMovimiento = movimiento.idMovimiento,
                                idCliente = movimiento.idCliente,
                                // Identidad histórica INMUTABLE: se conserva la
                                // del movimiento original (no se edita aquí).
                                nombreCliente = movimiento.nombreCliente,
                                apellidosCliente = movimiento.apellidosCliente,
                                dniCliente = movimiento.dniCliente,
                                // Servicios: activos marcados + fijos históricos
                                // (de baja/eliminados) que se conservan. Si el
                                // movimiento histórico no tenía servicios, se
                                // mantiene la lista vacía.
                                servicios = (idsServiciosEditados +
                                        idsServiciosEditadosFijos).distinct(),
                                fechaInicio = fechaInicioEditada!!,
                                fechaFin = fechaFinEditada!!,
                                precioFinal = precioValido!!,
                                estado = pagoEditadoResuelto.estado,
                                fechaPago = pagoEditadoResuelto.fechaPago,
                                metodoPago = pagoEditadoResuelto.metodoPago,
                                observaciones = observacionesEditadas.ifBlank {
                                    null
                                }
                            )

                            onGuardar(movimientoActualizado)
                        }
                    },
                    text = "Guardar cambios"
                )

                AppDangerOutlinedButton(
                    text = "Eliminar movimiento",
                    onClick = {
                        mostrarConfirmarEliminar = true
                    }
                )
            }
        }
    }

    if (mostrarConfirmarEliminar) {
        AlertDialog(
            onDismissRequest = {
                mostrarConfirmarEliminar = false
            },
            title = {
                Text(
                    text = "Eliminar movimiento",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text("¿Seguro que quieres eliminar este movimiento? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                AppDialogDangerConfirmButton(
                    text = "Eliminar",
                    onClick = {
                        mostrarConfirmarEliminar = false
                        onEliminar(movimiento)
                    }
                )
            },
            dismissButton = {
                AppDialogTextButton(
                    text = "Cancelar",
                    onClick = { mostrarConfirmarEliminar = false }
                )
            }
        )
    }

    if (mostrarDatePickerInicioDetalle) {
        val selectableDatesInicioDetalle = remember {
            val hoy = LocalDate.now()
            val fechaMinimaUtc = hoy.minusYears(120)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= fechaMinimaUtc

                override fun isSelectableYear(year: Int): Boolean =
                    year >= hoy.minusYears(120).year
            }
        }

        val datePickerStateInicioDetalle = rememberDatePickerState(
            selectableDates = selectableDatesInicioDetalle
        )

        DatePickerDialog(
            onDismissRequest = {
                mostrarDatePickerInicioDetalle = false
            },
            confirmButton = {
                TextButton(
                    enabled = datePickerStateInicioDetalle.selectedDateMillis != null,
                    onClick = {
                        fechaInicioEditada =
                            datePickerStateInicioDetalle.selectedDateMillis
                        mostrarDatePickerInicioDetalle = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        mostrarDatePickerInicioDetalle = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(
                state = datePickerStateInicioDetalle
            )
        }
    }

    if (mostrarDatePickerFinDetalle) {
        val selectableDatesFinDetalle = remember {
            val hoy = LocalDate.now()
            val fechaInicioUtcDetalle = fechaInicioEditada
                ?: hoy.minusYears(120).atStartOfDay(ZoneOffset.UTC)
                    .toInstant().toEpochMilli()
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= fechaInicioUtcDetalle

                override fun isSelectableYear(year: Int): Boolean =
                    year >= hoy.minusYears(120).year
            }
        }

        val datePickerStateFinDetalle = rememberDatePickerState(
            selectableDates = selectableDatesFinDetalle
        )

        DatePickerDialog(
            onDismissRequest = {
                mostrarDatePickerFinDetalle = false
            },
            confirmButton = {
                TextButton(
                    enabled = datePickerStateFinDetalle.selectedDateMillis != null,
                    onClick = {
                        fechaFinEditada =
                            datePickerStateFinDetalle.selectedDateMillis
                        mostrarDatePickerFinDetalle = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        mostrarDatePickerFinDetalle = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(
                state = datePickerStateFinDetalle
            )
        }
    }

    if (mostrarDatePickerPagoDetalle) {
        val selectableDatesPagoDetalle = remember {
            val hoy = LocalDate.now()
            val fechaMinimaUtc = hoy.minusYears(120).atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli()
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= fechaMinimaUtc

                override fun isSelectableYear(year: Int): Boolean =
                    year >= hoy.minusYears(120).year
            }
        }

        val datePickerStatePagoDetalle = rememberDatePickerState(
            selectableDates = selectableDatesPagoDetalle
        )

        DatePickerDialog(
            onDismissRequest = {
                mostrarDatePickerPagoDetalle = false
            },
            confirmButton = {
                TextButton(
                    enabled = datePickerStatePagoDetalle.selectedDateMillis != null,
                    onClick = {
                        fechaPagoEditada =
                            datePickerStatePagoDetalle.selectedDateMillis
                        mostrarDatePickerPagoDetalle = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        mostrarDatePickerPagoDetalle = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(
                state = datePickerStatePagoDetalle
            )
        }
    }
}

/**
 * SelectorMetodoPago (copia local del patrón usado en el perfil para el editor
 * compartido)
 * ------------------
 * Selector sencillo de método de pago opcional (FASE 4): botón con menú
 * desplegable con "Sin especificar" (null) y EFECTIVO/BIZUM/TRANSFERENCIA.
 * No obliga a seleccionar nada (el método es opcional).
 */
@Composable
private fun SelectorMetodoPago(
    nombre: String?,
    onCambio: (String?) -> Unit
) {
    var desplegado by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Método de pago",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { desplegado = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = MovimientoPago.metodoPagoLabel(
                        MovimientoPago.metodoPagoDe(nombre)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            DropdownMenu(
                expanded = desplegado,
                onDismissRequest = { desplegado = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Sin especificar") },
                    onClick = {
                        onCambio(null)
                        desplegado = false
                    }
                )
                MetodoPago.entries.forEach { metodo ->
                    DropdownMenuItem(
                        text = { Text(metodo.name) },
                        onClick = {
                            onCambio(metodo.name)
                            desplegado = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Formatea un timestamp en milisegundos a texto dd/MM/yyyy (equivalente al
 * helper del perfil, privado para no acoplarse a él).
 */
private fun formatearFechaEditor(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}
