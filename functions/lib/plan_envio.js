"use strict";

/**
 * plan_envio.js
 * -------------
 * Construcción PURA del mensaje FCM multicast (tokens + data/notification).
 *
 * - Notificaciones normales: incluyen `notification` (el sistema las muestra
 *   en segundo plano) y `data` sin el texto.
 * - Notificaciones de morosidad (`soloDatos`): se envían como DATA-ONLY con
 *   prioridad alta; incluyen `titulo`/`tituloEn`/`mensaje` en `data` para que
 *   el CLIENTE construya la notificación local con el título localizado, tanto
 *   en primer como en segundo plano.
 *
 * Módulo sin dependencias: testeable sin Firebase.
 */
function construirMensajeMulticast({
  tokens,
  notificacionId,
  clienteId,
  tipo,
  negocioId,
  origen,
  titulo,
  mensaje,
  tituloEn,
  mensajeEn,
  soloDatos = false,
}) {
  const data = {
    notificacionId: String(notificacionId),
    clienteId: String(clienteId),
    tipo: String(tipo),
    negocioId: String(negocioId),
    origen: String(origen || ""),
  };
  if (typeof tituloEn === "string" && tituloEn.length > 0) {
    data.tituloEn = tituloEn;
  }
  if (typeof mensajeEn === "string" && mensajeEn.length > 0) {
    data.mensajeEn = mensajeEn;
  }

  const multicast = { tokens, data };
  if (soloDatos) {
    // Data-only: el CLIENTE pinta la notificación con el texto localizado.
    data.titulo = String(titulo);
    data.mensaje = String(mensaje);
    multicast.android = { priority: "high" };
  } else {
    multicast.notification = { title: titulo, body: mensaje };
  }
  return multicast;
}

module.exports = { construirMensajeMulticast };
