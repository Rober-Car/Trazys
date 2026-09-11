"use strict";

const { Timestamp } = require("firebase-admin/firestore");
const { db, leerCliente } = require("./firestore");
const { idBuzon } = require("./ids");
const { dividirEnLotes } = require("./tokens");

/**
 * destinatarios.js
 * ----------------
 * Resolución y creación de destinatarios/buzones a partir de los datos de
 * Firestore. La Function nunca depende de Room ni de la UI: los destinatarios
 * salen de notificaciones/{id} (idsClientes / clienteId) o del buzón ya creado.
 */

/**
 * resolverDestinatariosDesdeDoc
 * -----------------------------
 * Devuelve los ids de cliente indicados en el documento de una notificación
 * según su modo de destino. Solo usa datos del propio documento.
 */
function resolverDestinatariosDesdeDoc(datos) {
  if (Array.isArray(datos.idsClientes) && datos.idsClientes.length > 0) {
    return datos.idsClientes
      .map((v) => Number(v))
      .filter((n) => Number.isInteger(n));
  }
  if (typeof datos.clienteId === "number") return [datos.clienteId];
  return [];
}

/**
 * obtenerVinculados
 * -----------------
 * Filtra los destinatarios y devuelve [{ idCliente, firebaseUid }] para los
 * clientes que están vinculados (firebaseUid string no vacío). Los clientes
 * sin vínculo no pueden recibir buzón.
 */
async function obtenerVinculados(clienteIds) {
  const vinculados = [];
  for (const id of clienteIds) {
    const cliente = await leerCliente(id);
    if (
      cliente &&
      typeof cliente.data.firebaseUid === "string" &&
      cliente.data.firebaseUid.length > 0
    ) {
      vinculados.push({ idCliente: Number(id), firebaseUid: cliente.data.firebaseUid });
    }
  }
  return vinculados;
}

/**
 * crearBuzones
 * ------------
 * Crea (set idempotente) notificaciones_por_destinatario/{clienteId}_{id}
 * para los vinculados, en lotes de máximo 500 escrituras por WriteBatch.
 */
async function crearBuzones({
  negocioId,
  notificacionId,
  titulo,
  mensaje,
  tipo,
  origen,
  vinculados,
  tituloEn,
  subtipo,
}) {
  const fechaEnvio = Timestamp.now();
  for (const lote of dividirEnLotes(vinculados, 500)) {
    const batch = db().batch();
    for (const v of lote) {
      const datos = {
        negocioId,
        notificacionId,
        clienteId: v.idCliente,
        firebaseUid: v.firebaseUid,
        titulo,
        mensaje,
        tipo,
        origen,
        fechaEnvio,
        leida: false,
      };
      // Localización opcional (solo notificaciones de morosidad). Las
      // notificaciones sin estos campos siguen siendo válidas.
      if (typeof tituloEn === "string" && tituloEn.length > 0) datos.tituloEn = tituloEn;
      if (typeof subtipo === "string" && subtipo.length > 0) datos.subtipo = subtipo;
      batch.set(
        db().collection("notificaciones_por_destinatario").doc(idBuzon(v.idCliente, notificacionId)),
        datos
      );
    }
    await batch.commit();
  }
}

/**
 * esperarBuzones
 * --------------
 * Para notificaciones inmediatas: la app crea el documento principal (PENDIENTE)
 * y justo después los buzones. La Function consulta el buzón por notificacionId
 * y, si aún no está listo, espera un breve intervalo hasta un máximo de
 * intentos. Devuelve los ids de cliente encontrados ([] si no aparecen).
 */
async function esperarBuzones(notificacionId, intentos = 5, esperaMs = 1200) {
  for (let i = 0; i < intentos; i++) {
    const snap = await db()
      .collection("notificaciones_por_destinatario")
      .where("notificacionId", "==", notificacionId)
      .get();
    const ids = snap.docs
      .map((d) => Number(d.data().clienteId))
      .filter((n) => Number.isInteger(n));
    if (ids.length > 0) return ids;
    await new Promise((resolve) => setTimeout(resolve, esperaMs));
  }
  return [];
}

module.exports = {
  resolverDestinatariosDesdeDoc,
  obtenerVinculados,
  crearBuzones,
  esperarBuzones,
};
