"use strict";

/**
 * plan_inmediata.js
 * -----------------
 * Regla PURA que decide si una notificación creada en `notificaciones/{id}`
 * debe procesarse por el trigger inmediato (`notificacionInmediata`: buzón +
 * push FCM).
 *
 * Casos procesables:
 *  - origen MANUAL: notificaciones creadas por el ADMIN (inmediatas; las
 *    programadas ya vienen marcadas con `programada === true`).
 *  - origen PRECONFIGURADA de BAJA (BAJA_CONFIRMADA / SOLICITUD_RECHAZADA): las
 *    crea la app al confirmar o rechazar una baja; también deben enviarse.
 *
 * Casos NO procesables aquí:
 *  - PRECONFIGURADA de MOROSIDAD: la crea y la envía su propio barrido
 *    programado (`entradaMorosidad`/`recordatorioMorosidad`). Procesarla aquí
 *    provocaría un doble envío.
 *  - Cualquier notificación que no esté PENDIENTE o que esté programada.
 *
 * Módulo sin dependencias: testeable sin firebase.
 */

const TIPOS_BAJA_PRECONFIGURADA = ["BAJA_CONFIRMADA", "SOLICITUD_RECHAZADA"];

/**
 * Tipos de notificación dirigidos EXCLUSIVAMENTE al ADMIN (no al CLIENTE). Se
 * envían al token del dispositivo del administrador propietario del negocio.
 */
const TIPOS_AVISO_ADMIN = ["SOLICITUD_BAJA"];

function esAvisoAlAdmin(datos) {
  return !!datos && TIPOS_AVISO_ADMIN.includes(datos.tipo);
}

function esNotificacionInmediataProcesable(datos) {
  if (!datos) return false;
  if (datos.estado !== "PENDIENTE") return false;
  if (datos.programada !== false) return false;
  if (datos.origen === "MANUAL") return true;
  // Avisos al ADMIN (p. ej. SOLICITUD_BAJA): origen AUTOMATICA.
  if (esAvisoAlAdmin(datos)) return true;
  return (
    datos.origen === "PRECONFIGURADA" &&
    TIPOS_BAJA_PRECONFIGURADA.includes(datos.tipo)
  );
}

module.exports = {
  esNotificacionInmediataProcesable,
  esAvisoAlAdmin,
  TIPOS_BAJA_PRECONFIGURADA,
  TIPOS_AVISO_ADMIN,
};
