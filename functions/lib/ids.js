"use strict";

/**
 * ids.js
 * ------
 * Helpers puros para construir los IDs deterministas de notificaciones y
 * buzones. Los IDs deterministas garantizan que un reintento de la misma
 * operación (baja confirmada, morosidad, recordatorio) sobrescriba el mismo
 * documento (set()) en lugar de crear duplicados.
 *
 * Módulo sin dependencias: puede ejecutarse y testearse sin firebase.
 */

/**
 * idNotificacionBaja
 * ------------------
 * ID determinista de la notificación de baja confirmada. La fecha de baja
 * (epoch) distingue una baja de una reactivación + nueva baja.
 */
function idNotificacionBaja(clienteId, fechaBajaMillis) {
  return `baja_confirmada_${clienteId}_${fechaBajaMillis}`;
}

/**
 * idNotificacionMorosidad
 * -----------------------
 * ID determinista de la entrada en morosidad. La fechaFinActual (epoch)
 * identifica el periodo vencido concreto: el mismo periodo nunca genera dos
 * notificaciones.
 */
function idNotificacionMorosidad(clienteId, fechaFinActualMillis) {
  return `morosidad_${clienteId}_${fechaFinActualMillis}`;
}

/**
 * idNotificacionRecordatorioMorosidad
 * -----------------------------------
 * ID determinista del recordatorio de morosidad para una ventana de 24h
 * (periodo = índice del día). El claim sobre ultimoRecordatorioMorosidad
 * evita el doble envío; este ID evita duplicar el buzón en el mismo periodo.
 */
function idNotificacionRecordatorioMorosidad(clienteId, periodo24h) {
  return `morosidad_recordatorio_${clienteId}_${periodo24h}`;
}

/**
 * idNotificacionApertura
 * ----------------------
 * ID determinista de la notificación de apertura de reservas: un documento por
 * CLIENTE y OLEADA (bucket de 5 min). Reprocesar el mismo bucket no duplica el
 * envío (mismo ID).
 */
function idNotificacionApertura(clienteId, bucketStart) {
  return `apertura_${clienteId}_${bucketStart}`;
}

/**
 * idBuzon
 * -------
 * DocumentId determinista del buzón de un cliente:
 * notificaciones_por_destinatario/{clienteId}_{notificacionId}.
 */
function idBuzon(clienteId, notificacionId) {
  return `${clienteId}_${notificacionId}`;
}

/**
 * periodoDe24h
 * ------------
 * Índice de la ventana de 24h a la que pertenece un instante. Estable para
 * todos los reintentos que ocurran dentro de la misma ventana.
 */
function periodoDe24h(epochMillis) {
  return Math.floor(epochMillis / 86400000);
}

module.exports = {
  idNotificacionBaja,
  idNotificacionMorosidad,
  idNotificacionRecordatorioMorosidad,
  idNotificacionApertura,
  idBuzon,
  periodoDe24h,
};
