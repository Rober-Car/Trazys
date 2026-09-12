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
const { enviarFCMaClientes, enviarFCMaAdmin } = require("./envio");
const {
  debeNotificarMorosidadPorFecha,
  configMorosidadActiva,
  configRecordatorioActivo,
} = require("./plan_morosidad");
const { decidirCreacionNotificacion, decidirEntregaAutomatica } = require("./idempotencia");
const { esNotificacionInmediataProcesable, esAvisoAlAdmin } = require("./plan_inmediata");
const { esProgramadaProcesable } = require("./plan_programadas");
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
      dispositivosSinDispositivos: res.sinDispositivos,
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

  // 1) Creación idempotente: nunca un set() ciego que resetee una notificación
  //    ya creada. Solo se crea si no existe (PENDIENTE).
  await db().runTransaction(async (t) => {
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
  });

  // 2) Destinatarios válidos (vinculados). Sin ninguno, la notificación NO se
  //    considera entregada: queda PENDIENTE y se reintenta cuando el cliente
  //    obtenga firebaseUid.
  const vinculados = await obtenerVinculados([clienteId]);
  if (vinculados.length === 0) {
    logger.info("Notificación automática sin destinatario válido; se reintentará", {
      notificacionId,
      tipo,
    });
    return;
  }

  // 3) Buzones que falten (idempotente). Devuelve los destinatarios cuyo buzón
  //    se acaba de crear (aún no servidos).
  const pendientes = await crearBuzones({
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

  // 4) Estado y diagnóstico actuales.
  const snap = await notifRef.get();
  const datosNotif = snap.exists ? snap.data() : {};
  const estadoActual = datosNotif.estado;
  const huboDispositivos =
    ((datosNotif.dispositivosEnviados || 0) + (datosNotif.dispositivosFallidos || 0)) > 0;

  // 5) Ya entregada (hubo algún dispositivo) y sin buzones pendientes -> omitir.
  if (
    decidirEntregaAutomatica({
      estado: estadoActual,
      pendientes: pendientes.length,
      huboDispositivos,
    }) === "omitir"
  ) {
    logger.info("Notificación automática ya entregada; se omite", { notificacionId, tipo });
    return;
  }

  // 6) Si sigue PENDIENTE, reclamar (barrera de envío). Si ya estaba ENVIADA
  //    pero faltaban destinatarios/dispositivos, NO se reclama de nuevo.
  if (estadoActual === "PENDIENTE") {
    const claim = await reclamarTransicion(notificacionId, "PENDIENTE");
    if (claim !== "reclamada") {
      logger.info("Notificación automática ya procesada", { notificacionId, claim });
      return;
    }
  }

  // 7) Enviar: si ya estaba ENVIADA, solo a los destinatarios pendientes; si no,
  //    a todos los vinculados. Las de morosidad (con subtipo) viajan DATA-ONLY.
  const idsEnvio =
    estadoActual === "ENVIADA" && pendientes.length > 0
      ? pendientes
      : vinculados.map((v) => v.idCliente);
  const soloDatos = typeof subtipo === "string" && subtipo.length > 0;
  const res = await enviarFCMaClientes({
    negocioId,
    notificacionId,
    titulo,
    mensaje,
    tipo,
    origen,
    clienteIds: idsEnvio,
    tituloEn,
    soloDatos,
  });
  await escribirDiagnostico(notificacionId, res);

  // 8) Sin ningún dispositivo válido -> NO entregada: se reabre a PENDIENTE para
  //    reintentar cuando el cliente registre un dispositivo.
  if (res.enviados === 0 && res.fallidos === 0) {
    try {
      await notifRef.update({ estado: "PENDIENTE" });
    } catch (e) {
      logger.warn("No se pudo reabrir la notificación automática", {
        notificacionId,
        error: e.message,
      });
    }
    logger.info("Notificación automática sin dispositivos; queda PENDIENTE", {
      notificacionId,
      tipo,
      ...res,
    });
  } else {
    logger.info("Notificación automática procesada", { notificacionId, tipo, ...res });
  }
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
  // MANUAL, las PRECONFIGURADAS de baja (BAJA_CONFIRMADA / SOLICITUD_RECHAZADA)
  // y los avisos al ADMIN (SOLICITUD_BAJA) se envían aquí. La morosidad la envía
  // su barrido programado (no se procesa).
  if (!esNotificacionInmediataProcesable(datos)) return;
  const negocioId = datos.negocioId;
  if (!negocioId) return;

  const avisoAdmin = esAvisoAlAdmin(datos);

  logger.info("Procesando notificación inmediata", {
    notificacionId,
    negocioId,
    tipo: datos.tipo,
    origen: datos.origen,
    destino: avisoAdmin ? "ADMIN" : "CLIENTE",
  });

  // Avisos al ADMIN: no hay buzones que resolver (los ve dentro de la app).
  let clienteIds = [];
  if (!avisoAdmin) {
    const buzones = await esperarBuzones(notificacionId);
    clienteIds =
      buzones.length > 0
        ? buzones
        : resolverDestinatariosDesdeDoc(datos);
  }

  const claim = await reclamarTransicion(notificacionId, "PENDIENTE");
  if (claim !== "reclamada") {
    logger.info("Notificación inmediata ya procesada", { notificacionId, claim });
    return;
  }

  let res;
  if (avisoAdmin) {
    // Canal EXCLUSIVO del ADMIN propietario del negocio; nunca al CLIENTE.
    res = await enviarFCMaAdmin({
      negocioId,
      notificacionId,
      titulo: datos.titulo,
      mensaje: datos.mensaje,
      tipo: datos.tipo,
      origen: datos.origen,
    });
  } else {
    // Las notificaciones con `subtipo` (baja confirmada / baja rechazada) viajan
    // como DATA-ONLY con el título localizado en `data.tituloEn`, igual que la
    // morosidad, para que FcmService pinte el idioma correcto en segundo plano.
    const soloDatos = typeof datos.subtipo === "string" && datos.subtipo.length > 0;
    res = await enviarFCMaClientes({
      negocioId,
      notificacionId,
      titulo: datos.titulo,
      mensaje: datos.mensaje,
      tipo: datos.tipo,
      origen: datos.origen,
      clienteIds,
      tituloEn: datos.tituloEn,
      mensajeEn: datos.mensajeEn,
      soloDatos,
    });
  }
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
    // Guard defensivo (la query ya filtra por estado/fecha): parte pura testeable.
    if (!esProgramadaProcesable(datos, ahora.getTime())) continue;
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
        titulo: "Pago vencido",
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
