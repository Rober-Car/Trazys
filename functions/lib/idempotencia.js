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

/**
 * decidirEntregaAutomatica
 * ------------------------
 * Decide si una notificación automática ya está ENTREGADA (se omite) o si hay
 * que (re)enviarla. La entrega NO debe considerarse hecha cuando no había
 * ningún dispositivo válido en el intento previo, de modo que el barrido pueda
 * reintentarla cuando el destinatario esté disponible.
 *
 * @param estado           estado de envío actual del documento (PENDIENTE/ENVIADA/...).
 * @param pendientes       nº de destinatarios cuyo buzón aún no existía (recién creados).
 * @param huboDispositivos true si en el envío previo se intentó al menos un dispositivo.
 * @returns "omitir" (ya entregada, sin pendientes) | "enviar" (hay que enviar).
 */
function decidirEntregaAutomatica({ estado, pendientes = 0, huboDispositivos = false }) {
  if (estado === "ENVIADA" && pendientes === 0 && huboDispositivos) return "omitir";
  return "enviar";
}

module.exports = { decidirCreacionNotificacion, decidirEntregaAutomatica };
