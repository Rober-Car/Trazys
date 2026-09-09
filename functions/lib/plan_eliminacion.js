"use strict";

/**
 * plan_eliminacion.js
 * -------------------
 * MÓDULO PURO (sin Firebase) con la lógica de PLANIFICACIÓN del borrado de
 * cuenta/negocio. Se puede testear con node --test sin dependencias.
 *
 * La eliminación real (Admin SDK) vive en eliminacion.js y usa estos planes.
 * El objetivo se deriva SIEMPRE de usuarios/{uid} (nunca de parámetros que
 * envíe la app), para que un usuario no pueda pedir borrar datos ajenos.
 */

const USUARIOS = "usuarios";
const NEGOCIOS = "negocios";
const NEGOCIOS_PUBLICOS = "negocios_publicos";
const CODIGOS_MAESTROS = "codigos_maestros";
const CONFIG_NOTIFICACIONES = "configuracion_notificaciones";
const CLIENTES = "clientes";
const CLIENTES_PRIVADOS = "clientes_privados";
const AGENDA = "agenda";
const INDICES_CLIENTES = "indices_clientes";
const PERFILES_PENDIENTES = "perfiles_pendientes";
const SERVICIOS = "servicios";
const SESIONES = "sesiones";
const RESERVAS = "reservas";
const MOVIMIENTOS = "movimientos";
const SOLICITUDES = "solicitudes";
const NOTIFICACIONES = "notificaciones";
const NOTIFICACIONES_BUZON = "notificaciones_por_destinatario";
const DISPOSITIVOS = "dispositivos";

function esAdmin(data) {
  return data != null && data.rol === "ADMIN";
}

function esCliente(data) {
  return data != null && data.rol === "CLIENTE";
}

/**
 * Devuelve el objetivo de borrado EXCLUSIVAMENTE a partir del documento
 * usuarios/{uid}: ADMIN -> { tipo: "ADMIN", negocioId };
 * CLIENTE -> { tipo: "CLIENTE", clienteId|null, negocioId|null };
 * Devuelve null si no hay objetivo válido.
 */
function objetivoDesdeUsuario(data) {
  if (!data) return null;
  if (esAdmin(data)) {
    const negocioId = data.negocioId;
    return typeof negocioId === "string" && negocioId.length > 0
      ? { tipo: "ADMIN", negocioId }
      : null;
  }
  if (esCliente(data)) {
    return {
      tipo: "CLIENTE",
      clienteId: typeof data.clienteId === "number" ? data.clienteId : null,
      negocioId: typeof data.negocioId === "string" && data.negocioId.length > 0
        ? data.negocioId
        : null,
    };
  }
  return null;
}

/** Un CLIENTE solo puede borrar su propia ficha (firebaseUid coincide). */
function fichaPerteneceAlUsuario(ficha, uid) {
  return ficha != null && ficha.firebaseUid === uid;
}

/** Rutas fijas (string) a borrar para un CLIENTE. */
function rutasFijasCliente(uid, clienteId) {
  const rutas = [`${USUARIOS}/${uid}`, `${PERFILES_PENDIENTES}/${uid}`];
  if (clienteId != null) rutas.push(`${CLIENTES}/${clienteId}`, `${CLIENTES_PRIVADOS}/${clienteId}`);
  return rutas;
}

function rutaIndice(negocioId, dni) {
  return `${INDICES_CLIENTES}/${negocioId}_${dni}`;
}

/** Rutas fijas a borrar para un ADMIN/negocio (codigo puede ser null). */
function rutasFijasAdmin(negocioId, codigo) {
  const rutas = [
    `${NEGOCIOS}/${negocioId}`,
    `${NEGOCIOS_PUBLICOS}/${negocioId}`,
    `${CONFIG_NOTIFICACIONES}/${negocioId}`,
    `${USUARIOS}/${negocioId}`,
  ];
  if (codigo != null && String(codigo).length > 0) {
    rutas.push(`${CODIGOS_MAESTROS}/${codigo}`);
  }
  return rutas;
}

/** Colecciones cuyo documento contiene negocioId (para borrado por query). */
function coleccionesConNegocioId() {
  return [
    SERVICIOS,
    SESIONES,
    RESERVAS,
    MOVIMIENTOS,
    SOLICITUDES,
    NOTIFICACIONES,
    NOTIFICACIONES_BUZON,
    CLIENTES_PRIVADOS,
  ];
}

/** Rutas de Storage que deben eliminarse. */
function rutasStorageCliente(clienteId) {
  return [`${CLIENTES}/${clienteId}/foto.jpg`];
}

function rutaStorageLogo(negocioId) {
  return `${NEGOCIOS}/${negocioId}/logo.jpg`;
}

function rutaDispositivos(coleccion, clienteId) {
  return `${coleccion}/${clienteId}/${DISPOSITIVOS}`;
}

/** Ruta de la subcolección de AGENDA derivada de un cliente. */
function rutaAgenda(clienteId) {
  return `${CLIENTES}/${clienteId}/${AGENDA}`;
}

module.exports = {
  USUARIOS,
  NEGOCIOS,
  NEGOCIOS_PUBLICOS,
  CODIGOS_MAESTROS,
  CONFIG_NOTIFICACIONES,
  CLIENTES,
  CLIENTES_PRIVADOS,
  AGENDA,
  INDICES_CLIENTES,
  PERFILES_PENDIENTES,
  SERVICIOS,
  SESIONES,
  RESERVAS,
  MOVIMIENTOS,
  SOLICITUDES,
  NOTIFICACIONES,
  NOTIFICACIONES_BUZON,
  DISPOSITIVOS,
  esAdmin,
  esCliente,
  objetivoDesdeUsuario,
  fichaPerteneceAlUsuario,
  rutasFijasCliente,
  rutaIndice,
  rutasFijasAdmin,
  coleccionesConNegocioId,
  rutasStorageCliente,
  rutaStorageLogo,
  rutaDispositivos,
  rutaAgenda,
};
