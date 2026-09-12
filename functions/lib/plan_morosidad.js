"use strict";

/**
 * plan_morosidad.js
 * -----------------
 * Regla de negocio de la NOTIFICACIÓN AUTOMÁTICA de morosidad por FECHA.
 *
 * La entrada en morosidad notificable depende EXCLUSIVAMENTE de que el período
 * haya terminado por fecha:
 *   - cliente ACTIVO;
 *   - `fechaFinActual` conocida y ya vencida (`< ahora`);
 *   - no exento de morosidad (`exentoMorosidad != true`).
 *
 * NO se inspeccionan los movimientos (PAGADO/PENDIENTE): la deuda/impagos los
 * gestiona el ADMIN y no provocan por sí mismos esta notificación automática.
 * La fuente temporal principal es `clientes/{id}.fechaFinActual`.
 *
 * Módulo puro, sin dependencias: puede ejecutarse y testearse sin Firebase.
 */

const ESTADO_ACTIVO = "ACTIVO";

/**
 * ¿Procede notificar la entrada en morosidad por fecha?
 * La configuración (`morosidad.activa`) se evalúa aparte con
 * `configMorosidadActiva`.
 *
 * `movimientos` (si se aporta) se ignora a propósito: el estado de pago de los
 * movimientos NO interviene en esta decisión.
 */
function debeNotificarMorosidadPorFecha({
  estado,
  fechaFinActual,
  exentoMorosidad = false,
  ahora,
}) {
  if (estado !== ESTADO_ACTIVO) return false;
  if (exentoMorosidad === true) return false;
  if (fechaFinActual == null) return false;
  return fechaFinActual < ahora;
}

/** `configuracion_notificaciones/{negocioId}.morosidad.activa === true`. */
function configMorosidadActiva(config) {
  return !!config && !!config.morosidad && config.morosidad.activa === true;
}

/** Recordatorio de 24h activo: morosidad activa y `recordatorioHoras === 24`. */
function configRecordatorioActivo(config) {
  return configMorosidadActiva(config) && config.morosidad.recordatorioHoras === 24;
}

module.exports = {
  debeNotificarMorosidadPorFecha,
  configMorosidadActiva,
  configRecordatorioActivo,
};
