/**
 * Cloud Functions de GestorPro (Fase E - envío real de notificaciones).
 *
 * Cloud Functions 2ª generación. Requiere plan Blaze (no desplegar hasta que
 * la facturación esté activa).
 *
 * Triggers:
 *   - notificacionInmediata  : onDocumentCreated("notificaciones/{id}").
 *   - procesarProgramadas    : onSchedule cada 2 minutos (PROGRAMADA vencidas).
 *   - entradaMorosidad       : onSchedule diario 08:00 Europe/Madrid (morosidad por fecha).
 *   - recordatorioMorosidad  : onSchedule diario 08:00 Europe/Madrid (recordatorio 24h).
 *   - bajaConfirmada         : onDocumentUpdated("clientes/{id}").
 *
 * Índices compuestos requeridos en Firestore:
 *   notificaciones(estado ASC, fechaProgramada ASC)
 *   clientes(estado ASC, fechaFinActual ASC)
 */
const { initializeApp } = require("firebase-admin/app");
initializeApp();

const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onCall } = require("firebase-functions/v2/https");
const { setGlobalOptions } = require("firebase-functions/v2");

const {
  procesarNotificacionInmediata,
  procesarProgramadas,
  procesarRecordatorioMorosidad,
  procesarEntradaMorosidad,
  procesarBajaConfirmada,
} = require("./lib/procesadores");

const { eliminarMiCuenta } = require("./lib/eliminacion");
const { reservar, cancelarReserva } = require("./lib/reservas");

// Región cercana a España y límites razonables para los barridos.
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

exports.notificacionInmediata = onDocumentCreated(
  "notificaciones/{notificacionId}",
  procesarNotificacionInmediata
);

exports.procesarProgramadas = onSchedule("every 2 minutes", procesarProgramadas);

// Barrido diario (~08:00 Europe/Madrid) de la morosidad por fecha: la entrada
// en morosidad se produce por el paso del tiempo, no por una escritura en
// clientes/{id}, por lo que no puede depender de onDocumentUpdated.
exports.entradaMorosidad = onSchedule(
  { schedule: "0 8 * * *", timeZone: "Europe/Madrid" },
  procesarEntradaMorosidad
);

exports.recordatorioMorosidad = onSchedule(
  { schedule: "0 8 * * *", timeZone: "Europe/Madrid" },
  procesarRecordatorioMorosidad
);

exports.bajaConfirmada = onDocumentUpdated(
  "clientes/{clienteId}",
  procesarBajaConfirmada
);

// Eliminación completa de cuenta (CLIENTE) o cuenta+negocio (ADMIN).
// El objetivo se deriva de context.auth.uid (nunca de parámetros de la app).
exports.eliminarMiCuenta = onCall((request) => eliminarMiCuenta(request));

// Backend de reservas del CLIENTE (FASE 1). La identidad se deriva de
// context.auth.uid; `sesionId` es el único dato que envía la app.
exports.reservar = onCall((request) => reservar(request));
exports.cancelarReserva = onCall((request) => cancelarReserva(request));
