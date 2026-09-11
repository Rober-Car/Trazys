"use strict";

/**
 * idempotencia.js
 * ---------------
 * Decide cómo tratar una notificación automática a partir de su estado actual,
 * sin tocar Firestore. Permite que `crearYEnviarAutomatica` sea idempotente en
 * ejecuciones repetidas del barrido:
 *
 *  - "crear"    : el documento no existe -> se crea en PENDIENTE.
 *  - "continuar": existe y está PENDIENTE -> se reanuda el envío.
 *  - "omitir"   : existe y ya no está PENDIENTE (p. ej. ENVIADA) -> no se toca.
 *
 * Módulo sin dependencias: testeable sin Firebase.
 */

function decidirCreacionNotificacion(existente) {
  if (!existente) return "crear";
  if (existente.estado === "PENDIENTE") return "continuar";
  return "omitir";
}

module.exports = { decidirCreacionNotificacion };
