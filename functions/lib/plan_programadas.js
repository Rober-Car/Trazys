"use strict";

/**
 * plan_programadas.js
 * -------------------
 * Módulo PURO (sin Firebase) con la regla que decide si una notificación
 * `PROGRAMADA` debe procesarse (crear buzones + enviar FCM) en el barrido
 * `procesarProgramadas`.
 *
 * Una notificación es procesable si:
 *  - su `estado` es exactamente "PROGRAMADA";
 *  - tiene `fechaProgramada` válida;
 *  - `fechaProgramada <= ahora`.
 *
 * La idempotencia real (claim `PROGRAMADA -> ENVIADA`) vive en `procesadores.js`
 * (Transaction). Este módulo solo expone la parte pura y testeable.
 */

/** Convierte `fechaProgramada` (Timestamp/number/string/Date) a milisegundos. */
function fechaProgramadaMs(valor) {
  if (valor === null || valor === undefined) return null;
  if (typeof valor === "number") return Number.isFinite(valor) ? valor : null;
  if (typeof valor === "string") {
    const t = Date.parse(valor);
    return Number.isNaN(t) ? null : t;
  }
  if (typeof valor.toMillis === "function") return valor.toMillis();
  if (typeof valor.toDate === "function") return valor.toDate().getTime();
  if (typeof valor.seconds === "number") return valor.seconds * 1000;
  return null;
}

/** ¿La notificación `datos` debe procesarse en el barrido a `ahoraMillis`? */
function esProgramadaProcesable(datos, ahoraMillis) {
  if (!datos) return false;
  if (datos.estado !== "PROGRAMADA") return false;
  const fecha = fechaProgramadaMs(datos.fechaProgramada);
  if (fecha === null) return false;
  return fecha <= ahoraMillis;
}

module.exports = { esProgramadaProcesable, fechaProgramadaMs };
