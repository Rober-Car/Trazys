"use strict";

const { logger } = require("firebase-functions/v2");
const { FieldValue, Timestamp } = require("firebase-admin/firestore");
const { db, leerConfiguracion, timestampAms } = require("./firestore");
const {
  resolverDestinatariosDesdeDoc,
  obtenerVinculados,
  crearBuzones,
  esperarBuzones,
} = require("./destinatarios");
const { enviarFCMaClientes } = require("./envio");
const {
  debeNotificarMorosidadPorFecha,
  configMorosidadActiva,
  configRecordatorioActivo,
} = require("./plan_morosidad");
const { decidirCreacionNotificacion } = require("./idempotencia");
const {
  idNotificacionBaja,
  idNotificacionMorosidad,
  idNotificacionRecordatorioMorosidad,
  periodoDe24h,
} = require("./ids");

/**
 * procesadores.js
 * ---------------
 * Procesadores de las notificaciones (Fase E). Todos usan el mismo patrón:
 *   1) datos de Firestore (nunca Room ni UI);
 *   2) buzones con set() determinista (idempotente);
 *   3) CLAIM atómico en Transaction (solo la ejecución ganadora envía);
 *   4) envío FCM por cliente (máx. 500 tokens) + limpieza de tokens inválidos;
 *   5) escritura de diagnóstico (opcional, no cambia el estado).
 */

const HORAS_RECORDATORIO = 24;
const MILIS_HORA = 3600000;

/**
 * reclamarTransicion
 * ------------------
 * CLAIM atómico de una notificación: transita de `estadoEsperado` a ENVIADA
 * (con fechaEnvio) solo si el documento sigue en `estadoEsperado`. Devuelve:
 * 'reclamada' (esta ejecución es la ganadora), 'ya-procesada' o 'no-existe'.
 * Es la barrera de idempotencia del envío FCM.
 */
async function reclamarTransicion(notificacionId, estadoEsperado) {
  const ref = db().collection("notificaciones").doc(notificacionId);
  return db().runTransaction(async (t) => {
    const snap = await t.get(ref);
    if (!snap.exists) return "no-existe";
    if (snap.data().estado !== estadoEsperado) return "ya-procesada";
    t.update(ref, {
      estado: "ENVIADA",
      fechaEnvio: FieldValue.serverTimestamp(),
    });
    return "reclamada";
  });
}

/**
 * escribirDiagnostico
 * -------------------
 * Campos opcionales de diagnóstico en notificaciones/{id}. No cambia el
 * estado; si falla, solo se registra un aviso.
 */
async function escribirDiagnostico(notificacionId, res) {
  try {
    await db().collection("notificaciones").doc(notificacionId).update({
      dispositivosEnviados: res.enviados,
      dispositivosFallidos: res.fallidos,
      dispositivosEliminados: res.eliminados,
    });
  } catch (e) {
    logger.warn("No se pudo escribir el diagnóstico de envío", { notificacionId, error: e.message });
  }
}

/**
 * crearYEnviarAutomatica
 * ----------------------
 * Crea una notificación automática (MOROSIDAD / BAJA_CONFIRMADA /
 * recordatorio) con ID determinista, su buzón, el claim y el envío FCM.
 * Reutilizada por los triggers de clientes y el recordatorio.
 */
async function crearYEnviarAutomatica({
  notificacionId,
  negocioId,
  clienteId,
  titulo,
  mensaje,
  tipo,
  origen,
  tituloEn,
  subtipo,
}) {
  const ahora = Timestamp.now();
  const notifRef = db().collection("notificaciones").doc(notificacionId);

  // Creación idempotente: nunca un set() ciego que resetee una notificación ya
  // ENVIADA. En transacción:
  //  - no existe -> se crea PENDIENTE;
  //  - existe y PENDIENTE -> se reanuda el proceso;
  //  - existe y ya procesada (ENVIADA...) -> se omite.
  const decision = await db().runTransaction(async (t) => {
    const snap = await t.get(notifRef);
    const accion = decidirCreacionNotificacion(snap.exists ? snap.data() : null);
    if (accion === "crear") {
      const datosNotif = {
        negocioId,
        titulo,
        mensaje,
        tipo,
        origen,
        modoDestino: "INDIVIDUAL",
        idsClientes: [clienteId],
        clienteId,
        fechaCreacion: ahora,
        programada: false,
        estado: "PENDIENTE",
      };
      // Localización opcional (solo notificaciones de morosidad). Las
      // notificaciones sin estos campos siguen siendo válidas.
      if (typeof tituloEn === "string" && tituloEn.length > 0) datosNotif.tituloEn = tituloEn;
      if (typeof subtipo === "string" && subtipo.length > 0) datosNotif.subtipo = subtipo;
      t.set(notifRef, datosNotif);
    }
    return accion;
  });
  if (decision === "omitir") {
    logger.info("Notificación automática ya enviada; se omite", { notificacionId, tipo });
    return;
  }

  const vinculados = await obtenerVinculados([clienteId]);
  if (vinculados.length > 0) {
    await crearBuzones({
      negocioId,
      notificacionId,
      titulo,
      mensaje,
      tipo,
      origen,
      vinculados,
      tituloEn,
      subtipo,
    });
  }

  const claim = await reclamarTransicion(notificacionId, "PENDIENTE");
  if (claim !== "reclamada") {
    logger.info("Notificación automática ya procesada", { notificacionId, claim });
    return;
  }

  // Las notificaciones de morosidad (con subtipo) viajan como DATA-ONLY para
  // que el CLIENTE construya la notificación con el título localizado tanto en
  // primer como en segundo plano. El resto conserva el payload `notification`.
  const soloDatos = typeof subtipo === "string" && subtipo.length > 0;
  const res = await enviarFCMaClientes({
    negocioId,
    notificacionId,
    titulo,
    mensaje,
    tipo,
    origen,
    clienteIds: [clienteId],
    tituloEn,
    soloDatos,
  });
  await escribirDiagnostico(notificacionId, res);
  logger.info("Notificación automática procesada", { notificacionId, tipo, ...res });
}

/**
 * procesarNotificacionInmediata
 * -----------------------------
 * Trigger: onDocumentCreated("notificaciones/{notificacionId}").
 * Solo actúa sobre inmediatas MANUAL (PENDIENTE, programada=false, origen
 * MANUAL). Los buzones ya los creó la app; se esperan brevemente y se envían.
 */
async function procesarNotificacionInmediata(event) {
  const notificacionId = event.params.notificacionId;
  const datos = event.data && event.data.data();
  if (!datos) return;
  if (datos.estado !== "PENDIENTE" || datos.programada !== false || datos.origen !== "MANUAL") {
    return;
  }
  const negocioId = datos.negocioId;
  if (!negocioId) return;

  logger.info("Procesando notificación inmediata", { notificacionId, negocioId });

  const buzones = await esperarBuzones(notificacionId);
  const clienteIds =
    buzones.length > 0
      ? buzones
      : resolverDestinatariosDesdeDoc(datos);

  const claim = await reclamarTransicion(notificacionId, "PENDIENTE");
  if (claim !== "reclamada") {
    logger.info("Notificación inmediata ya procesada", { notificacionId, claim });
    return;
  }

  const res = await enviarFCMaClientes({
    negocioId,
    notificacionId,
    titulo: datos.titulo,
    mensaje: datos.mensaje,
    tipo: datos.tipo,
    origen: datos.origen,
    clienteIds,
  });
  await escribirDiagnostico(notificacionId, res);
  logger.info("Notificación inmediata procesada", { notificacionId, ...res });
}

/**
 * procesarProgramadas
 * -------------------
 * Trigger: onSchedule("every 2 minutes"). Barrido de notificaciones
 * PROGRAMADA con fechaProgramada <= ahora. Requiere el índice compuesto
 * notificaciones(estado ASC, fechaProgramada ASC).
 */
async function procesarProgramadas() {
  const ahora = new Date();
  const snapshot = await db()
    .collection("notificaciones")
    .where("estado", "==", "PROGRAMADA")
    .where("fechaProgramada", "<=", ahora)
    .get();

  logger.info("Barrido de programadas", { encontradas: snapshot.size });

  for (const doc of snapshot.docs) {
    const notificacionId = doc.id;
    const datos = doc.data();
    const negocioId = datos.negocioId;
    if (!negocioId) continue;
    try {
      const clienteIds = resolverDestinatariosDesdeDoc(datos);
      const vinculados = await obtenerVinculados(clienteIds);

      if (vinculados.length > 0) {
        await crearBuzones({
          negocioId,
          notificacionId,
          titulo: datos.titulo,
          mensaje: datos.mensaje,
          tipo: datos.tipo,
          origen: datos.origen,
          vinculados,
        });
      }

      const claim = await reclamarTransicion(notificacionId, "PROGRAMADA");
      if (claim !== "reclamada") continue;

      const res = await enviarFCMaClientes({
        negocioId,
        notificacionId,
        titulo: datos.titulo,
        mensaje: datos.mensaje,
        tipo: datos.tipo,
        origen: datos.origen,
        clienteIds: vinculados.map((v) => v.idCliente),
      });
      await escribirDiagnostico(notificacionId, res);
      logger.info("Programada procesada", { notificacionId, ...res });
    } catch (e) {
      logger.error("Error procesando programada", { notificacionId, error: e.message });
    }
  }
}

/**
 * procesarEntradaMorosidad
 * ------------------------
 * Trigger: onSchedule (diario, ~08:00 Europe/Madrid). Barrido de clientes
 * ACTIVO cuyo `fechaFinActual` ya venció, SIN depender de que el documento
 * `clientes/{id}` se actualice.
 *
 * Regla DEFINITIVA: la notificación depende EXCLUSIVAMENTE de que el período
 * haya terminado por fecha (`fechaFinActual < ahora`), del estado ACTIVO y de
 * que el cliente no esté exento. NO se inspeccionan los movimientos
 * (PAGADO/PENDIENTE): la deuda/impagos los gestiona el ADMIN y no generan por
 * sí mismos esta notificación. Respeta `morosidad.activa`.
 *
 * Idempotencia: ID determinista `morosidad_{clienteId}_{fechaFinActual}` y
 * creación sin set() ciego (ver crearYEnviarAutomatica).
 */
async function procesarEntradaMorosidad() {
  const ahora = Date.now();
  const snapshot = await db()
    .collection("clientes")
    .where("estado", "==", "ACTIVO")
    .where("fechaFinActual", "<", Timestamp.fromMillis(ahora))
    .get();

  logger.info("Barrido de entrada en morosidad", { candidatos: snapshot.size });

  for (const doc of snapshot.docs) {
    const clienteId = doc.id;
    const cliente = doc.data();
    try {
      if (!cliente || !cliente.negocioId) continue;

      const fechaFinActual = timestampAms(cliente.fechaFinActual);
      if (
        !debeNotificarMorosidadPorFecha({
          estado: cliente.estado,
          fechaFinActual,
          exentoMorosidad: cliente.exentoMorosidad === true,
          ahora,
        })
      ) {
        continue;
      }

      const negocioId = cliente.negocioId;
      const config = await leerConfiguracion(negocioId);
      if (!configMorosidadActiva(config)) continue;

      const notificacionId = idNotificacionMorosidad(clienteId, fechaFinActual);
      await crearYEnviarAutomatica({
        notificacionId,
        negocioId,
        clienteId: Number(clienteId),
        titulo: "Alerta de pago vencido",
        tituloEn: "Payment overdue",
        subtipo: "ENTRADA",
        mensaje: "Se ha detectado un periodo de pago vencido en tu cuenta.",
        tipo: "MOROSIDAD",
        origen: "PRECONFIGURADA",
      });
    } catch (e) {
      logger.error("Error procesando entrada en morosidad", { clienteId, error: e.message });
    }
  }
}

/**
 * procesarRecordatorioMorosidad
 * -----------------------------
 * Trigger: onSchedule (diario, ~08:00 Europe/Madrid). Misma semántica que la
 * entrada: recuerda que el período ha terminado por fecha (`fechaFinActual <
 * ahora`) para clientes ACTIVO no exentos, independientemente de que existan
 * movimientos pendientes. Requiere morosidad.activa y recordatorioHoras == 24.
 * Usa ultimoRecordatorioMorosidad como claim atómico para enviar como mucho uno
 * cada 24 horas.
 */
async function procesarRecordatorioMorosidad() {
  const ahora = Date.now();
  const snapshot = await db()
    .collection("clientes")
    .where("estado", "==", "ACTIVO")
    .where("fechaFinActual", "<", Timestamp.fromMillis(ahora))
    .get();

  const candidatos = snapshot.docs
    .map((d) => ({ id: Number(d.id), data: d.data() }))
    .filter((c) => Number.isInteger(c.id));

  logger.info("Barrido de recordatorios de morosidad", { candidatos: candidatos.length });

  for (const c of candidatos) {
    if (c.data.exentoMorosidad === true) continue;
    const negocioId = c.data.negocioId;
    if (!negocioId) continue;
    const config = await leerConfiguracion(negocioId);
    if (!configRecordatorioActivo(config)) {
      continue;
    }

    const ref = db().collection("clientes").doc(String(c.id));
    const ganador = await db().runTransaction(async (t) => {
      const snap = await t.get(ref);
      if (!snap.exists) return false;
      const ultimo = timestampAms(snap.data().ultimoRecordatorioMorosidad);
      if (ultimo !== null && ahora - ultimo < HORAS_RECORDATORIO * MILIS_HORA) return false;
      t.update(ref, { ultimoRecordatorioMorosidad: Timestamp.now() });
      return true;
    });
    if (!ganador) continue;

    const notificacionId = idNotificacionRecordatorioMorosidad(c.id, periodoDe24h(ahora));
    await crearYEnviarAutomatica({
      notificacionId,
      negocioId,
      clienteId: c.id,
      titulo: "Recordatorio de pago vencido",
      tituloEn: "Overdue payment reminder",
      subtipo: "RECORDATORIO",
      mensaje: "Sigue pendiente tu pago. Recuerda regularizar tu situación.",
      tipo: "MOROSIDAD",
      origen: "PRECONFIGURADA",
    });
  }
}

/**
 * procesarBajaConfirmada
 * ----------------------
 * Trigger: onDocumentUpdated("clientes/{clienteId}"). Detecta la transición
 * a BAJA (estado anterior != BAJA y estado nuevo == BAJA) y, si la
 * configuración lo permite, crea la notificación de baja confirmada.
 */
async function procesarBajaConfirmada(event) {
  const clienteId = event.params.clienteId;
  const before = event.data.before.data();
  const after = event.data.after.data();
  if (!before || !after) return;
  if (before.estado === "BAJA" || after.estado !== "BAJA") return;

  const negocioId = after.negocioId;
  if (!negocioId) return;

  const config = await leerConfiguracion(negocioId);
  if (!config || config.bajaConfirmada?.activa !== true) return;

  const fechaBajaMillis = timestampAms(after.fechaBaja);
  const base = fechaBajaMillis !== null ? fechaBajaMillis : Date.now();
  const notificacionId = idNotificacionBaja(clienteId, base);

  await crearYEnviarAutomatica({
    notificacionId,
    negocioId,
    clienteId: Number(clienteId),
    titulo: "Baja confirmada",
    mensaje: "Tu baja en el gimnasio ha sido confirmada.",
    tipo: "BAJA_CONFIRMADA",
    origen: "PRECONFIGURADA",
  });
}

module.exports = {
  procesarNotificacionInmediata,
  procesarProgramadas,
  procesarRecordatorioMorosidad,
  procesarEntradaMorosidad,
  procesarBajaConfirmada,
};
