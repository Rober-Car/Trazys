"use strict";

const { getMessaging } = require("firebase-admin/messaging");
const { logger } = require("firebase-functions/v2");
const { db } = require("./firestore");
const { esTokenInvalido, dividirEnLotes, MAX_TOKENS_POR_LOTE } = require("./tokens");
const { construirMensajeMulticast } = require("./plan_envio");

/**
 * envio.js
 * --------
 * Envío real de FCM con el Admin SDK. Respeta el campo por dispositivo
 * `notificacionesActivadas` (ausente => activadas), envía como máximo 500
 * tokens por llamada (sendEachForMulticast), elimina automáticamente los
 * tokens inválidos y no deja que un token fallido rompa el envío global.
 */

/**
 * enviarFCMaClientes
 * ------------------
 * Envía a todos los dispositivos habilitados de cada cliente destino.
 * Devuelve un resumen: enviados, fallidos, eliminados, sinDispositivos y
 * errorGlobal (solo si una llamada a FCM falló de forma global por red/infra).
 */
async function enviarFCMaClientes({
  negocioId,
  notificacionId,
  titulo,
  mensaje,
  tipo,
  origen,
  clienteIds,
  tituloEn,
  soloDatos = false,
}) {
  const messaging = getMessaging();
  const resultado = {
    enviados: 0,
    fallidos: 0,
    eliminados: 0,
    sinDispositivos: 0,
    errorGlobal: null,
  };

  for (const clienteId of clienteIds) {
    const clienteRef = db().collection("clientes").doc(String(clienteId));
    const clienteSnap = await clienteRef.get();
    if (!clienteSnap.exists) {
      resultado.sinDispositivos += 1;
      continue;
    }
    const cliente = clienteSnap.data();
    if (typeof cliente.firebaseUid !== "string" || cliente.firebaseUid.length === 0) {
      resultado.sinDispositivos += 1;
      continue;
    }

    const devsSnap = await clienteRef.collection("dispositivos").get();
    const tokens = devsSnap.docs
      .map((d) => ({ token: d.data().token, ref: d.ref, activadas: d.data().notificacionesActivadas }))
      .filter((t) => typeof t.token === "string" && t.token.length > 0)
      .filter((t) => t.activadas !== false);

    if (tokens.length === 0) {
      resultado.sinDispositivos += 1;
      continue;
    }

    for (const lote of dividirEnLotes(tokens, MAX_TOKENS_POR_LOTE)) {
      try {
        const respuesta = await messaging.sendEachForMulticast(
          construirMensajeMulticast({
            tokens: lote.map((t) => t.token),
            notificacionId,
            clienteId,
            tipo,
            negocioId,
            origen,
            titulo,
            mensaje,
            tituloEn,
            soloDatos,
          })
        );
        respuesta.responses.forEach((r, i) => {
          if (r.success) {
            resultado.enviados += 1;
          } else {
            resultado.fallidos += 1;
            const codigo = r.error && r.error.code;
            if (esTokenInvalido(codigo)) {
              resultado.eliminados += 1;
              const token = lote[i];
              token.ref.delete().catch((e) => {
                logger.warn("No se pudo eliminar token FCM inválido", { clienteId, error: e.message });
              });
            } else {
              logger.warn("Push no entregado (token válido)", { notificacionId, clienteId, codigo });
            }
          }
        });
      } catch (e) {
        // Fallo global de red/infra de la llamada: no impide seguir con el resto.
        resultado.fallidos += lote.length;
        if (!resultado.errorGlobal) resultado.errorGlobal = e.message;
        logger.error("Fallo global al enviar lote FCM", { notificacionId, clienteId, error: e.message });
      }
    }
  }

  return resultado;
}

module.exports = { enviarFCMaClientes };
