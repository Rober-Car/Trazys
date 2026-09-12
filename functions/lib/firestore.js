"use strict";

const { getFirestore } = require("firebase-admin/firestore");

/**
 * firestore.js
 * ------------
 * Acceso perezoso a Firestore y utilidades comunes. Todas las lecturas se
 * hacen bajo demanda (getFirestore() dentro de cada función) para no depender
 * del orden de inicialización de firebase-admin.
 */

function db() {
  return getFirestore();
}

/**
 * leerCliente
 * -----------
 * Lee la ficha clientes/{clienteId}. Devuelve null si no existe.
 */
async function leerCliente(clienteId) {
  const ref = db().collection("clientes").doc(String(clienteId));
  const snap = await ref.get();
  return snap.exists ? { ref, data: snap.data() } : null;
}

/**
 * leerConfiguracion
 * -----------------
 * Lee configuracion_notificaciones/{negocioId}. Devuelve null si no existe
 * (en ese caso se trata como "todo desactivado").
 */
async function leerConfiguracion(negocioId) {
  const snap = await db()
    .collection("configuracion_notificaciones")
    .doc(negocioId)
    .get();
  return snap.exists ? snap.data() : null;
}

/**
 * timestampAms
 * ------------
 * Convierte un valor a milisegundos epoch cuando es posible (Timestamp de
 * Firestore, número, Date). Devuelve null si no es interpretable.
 */
function timestampAms(valor) {
  if (!valor) return null;
  if (typeof valor.toDate === "function") return valor.toDate().getTime();
  if (typeof valor === "number") return valor;
  if (valor instanceof Date) return valor.getTime();
  return null;
}

module.exports = { db, leerCliente, leerConfiguracion, timestampAms };
