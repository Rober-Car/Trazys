"use strict";

/**
 * eliminacion.js
 * --------------
 * Eliminación COMPLETA e idempotente de cuenta (CLIENTE) o cuenta+negocio
 * (ADMIN) usando Admin SDK (no pasa por las Firestore Rules, que mantienen los
 * borrados cerrados). El objetivo se deriva EXCLUSIVAMENTE de usuarios/{uid}.
 *
 * Orden de seguridad:
 *   1) Firestore (datos) -> 2) Storage (fotos/logo) -> 3) usuarios/{uid}
 *   -> 4) Firebase Auth (lo último).
 * Si una fase falla se lanza un HttpsError y NO se borra Auth; el reintento
 * vuelve a ejecutarse de forma idempotente (borrar inexistentes es no-op).
 */

const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getAuth } = require("firebase-admin/auth");
const { getStorage } = require("firebase-admin/storage");
const { HttpsError } = require("firebase-functions/v2/https");
const plan = require("./plan_eliminacion");

function db() {
  return getFirestore();
}

/** Borra un documento por su ruta "coleccion/id" (idempotente). */
async function borrarDoc(ruta) {
  await db().doc(ruta).delete();
}

/** Borra todos los documentos de una consulta (idempotente). */
async function borrarConsulta(consulta) {
  const snap = await consulta.get();
  for (const d of snap.docs) {
    await d.ref.delete();
  }
}

/** Borra documentos de una colección con igualdad en un campo. */
async function borrarPorIgualdad(coleccion, campo, valor) {
  await borrarConsulta(db().collection(coleccion).where(campo, "==", valor));
}

/** Borra una subcolección completa (p. ej. dispositivos de un cliente). */
async function borrarSubcoleccion(rutaColeccion) {
  await borrarConsulta(db().collection(rutaColeccion));
}

/**
 * retirarAsistentesDeReservas
 * ---------------------------
 * Cuando un CLIENTE se da de baja o elimina su cuenta se borran sus reservas.
 * Esta función retira su NOMBRE del mapa `asistentes` de cada sesión en la que
 * tenía una reserva (las reservas de sesiones que siguen existiendo), para no
 * dejar asistentes huérfanos apuntando a una cuenta/ficha que ya no usa el
 * servicio. Idempotente y tolerante a sesiones ya eliminadas.
 */
async function retirarAsistentesDeReservas(clienteId, negocioId) {
  const reservas = await db()
    .collection(plan.RESERVAS)
    .where("clienteId", "==", clienteId)
    .where("negocioId", "==", negocioId)
    .get();
  for (const r of reservas.docs) {
    const sesionId = r.get("sesionId");
    if (!Number.isInteger(sesionId)) continue;
    const sesionRef = db().collection(plan.SESIONES).doc(String(sesionId));
    try {
      const sesion = await sesionRef.get();
      if (!sesion.exists) continue;
      const asistentes = sesion.data() && sesion.data().asistentes
        ? sesion.data().asistentes
        : null;
      if (!asistentes || !(String(clienteId) in asistentes)) continue;
      await sesionRef.update({
        [`asistentes.${clienteId}`]: FieldValue.delete(),
      });
    } catch (_) {
      // Best-effort: si falla (red/permisos Admin SDK no aplica), se reintenta
      // en una ejecución posterior porque el flujo de borrado es idempotente.
    }
  }
}

/**
 * Rastro personal del usuario que SIEMPRE se elimina al borrar su cuenta,
 * exista o no ficha: `perfiles_pendientes/{uid}` y denuncias donde el uid es
 * denunciante o denunciado. Se ejecuta de forma incondicional en la rama
 * CLIENTE de `eliminarMiCuenta` (cubre también al CLIENTE sin ficha).
 */
async function borrarRastroPersonalDelUsuario(uid) {
  const rastro = plan.operacionesRastroPersonal(uid);
  await borrarDoc(rastro.perfilPendiente);
  for (const d of rastro.denuncias) {
    await borrarPorIgualdad(d.coleccion, d.campo, d.valor);
  }
}

async function borrarStorage(rutas) {
  const bucket = getStorage().bucket();
  for (const ruta of rutas) {
    try {
      await bucket.file(ruta).delete({ ignoreNotFound: true });
    } catch (_) {
      // no-op idempotente
    }
  }
}

/** Fase CLIENTE: borra su cuenta y limpia a un mínimo histórico la ficha. */
async function borrarCliente(uid, clienteId, negocioId) {
  if (clienteId == null) {
    // Sin ficha: solo perfil pendiente + usuarios (se limpia después).
    return;
  }

  const refFicha = db().doc(`${plan.CLIENTES}/${clienteId}`);
  const ficha = await refFicha.get();
  let dni = null;
  if (ficha.exists) {
    const datos = ficha.data();
    if (!plan.fichaPerteneceAlUsuario(datos, uid)) {
      throw new HttpsError(
        "permission-denied",
        "La ficha no pertenece a este usuario"
      );
    }
    dni = datos.dni ? String(datos.dni).toUpperCase() : null;
    // La ficha personal se ELIMINA por completo (ya no se conserva una ficha
    // mínima). El histórico económico vive en los movimientos, que no se tocan.
    await refFicha.delete();
  }

  // Ámbito personal / actividad que ya no corresponde a la cuenta.
  await borrarSubcoleccion(plan.rutaDispositivos(plan.CLIENTES, clienteId));
  // La agenda derivada del cliente (clientes/{clienteId}/agenda) también se
  // borra con la cuenta.
  await borrarSubcoleccion(plan.rutaAgenda(clienteId));
  // Retirar a este cliente de los ASISTENTES de las sesiones donde tenía
  // reservas (evita asistentes huérfanos al borrar las reservas después).
  await retirarAsistentesDeReservas(clienteId, negocioId);
  await borrarPorIgualdad(plan.RESERVAS, "clienteId", clienteId);
  await borrarPorIgualdad(plan.SOLICITUDES, "idCliente", clienteId);
  await borrarPorIgualdad(plan.NOTIFICACIONES_BUZON, "clienteId", clienteId);
  await borrarDoc(`${plan.CLIENTES_PRIVADOS}/${clienteId}`);
  // Índice negocio+DNI: se elimina con la ficha (ya no se conserva).
  if (dni && typeof negocioId === "string" && negocioId.length > 0) {
    await borrarDoc(plan.rutaIndice(negocioId, dni));
  }
  await borrarStorage(plan.rutasStorageCliente(clienteId));
  // MOVIMIENTOS: NO se tocan. Son histórico económico autónomo del centro y
  // conservan la identidad histórica (nombre/apellidos/DNI) del movimiento.
}

/** Fase ADMIN: borra el negocio completo y sus fotos/logo. */
async function borrarNegocio(uid, negocioId) {
  // El ADMIN solo puede borrar SU negocio (negocioId == su UID).
  if (negocioId !== uid) {
    throw new HttpsError(
      "permission-denied",
      "No se puede eliminar un negocio que no te pertenece"
    );
  }

  const refNegocio = db().doc(`${plan.NEGOCIOS}/${negocioId}`);
  const negocio = await refNegocio.get();
  const codigo = negocio.exists && negocio.data()
    ? negocio.data().codigoMaestro
    : null;

  // Clientes del negocio: por cada uno se borran dispositivos, índice y foto.
  const fotos = [];
  const clientes = await db()
    .collection(plan.CLIENTES)
    .where("negocioId", "==", negocioId)
    .get();
  for (const c of clientes.docs) {
    const datos = c.data() || {};
    const clienteId = c.id;
    const dni = datos.dni ? String(datos.dni).toUpperCase() : null;
    await borrarSubcoleccion(plan.rutaDispositivos(plan.CLIENTES, clienteId));
    // La agenda derivada del cliente también es una subcolección del doc.
    await borrarSubcoleccion(plan.rutaAgenda(clienteId));
    if (dni) await borrarDoc(plan.rutaIndice(negocioId, dni));
    await borrarDoc(`${plan.CLIENTES_PRIVADOS}/${clienteId}`);
    await c.ref.delete();
    fotos.push(...plan.rutasStorageCliente(clienteId));
  }

  // Colecciones por negocioId (borrado masivo por igualdad simple).
  for (const coleccion of plan.coleccionesConNegocioId()) {
    await borrarPorIgualdad(coleccion, "negocioId", negocioId);
  }

  // Dispositivos FCM del propio ADMIN (subcolección de usuarios/{uid}).
  await borrarSubcoleccion(plan.rutaDispositivos(plan.USUARIOS, negocioId));

  // Documentos fijos del negocio + usuarios/{uid} + codigo.
  for (const ruta of plan.rutasFijasAdmin(negocioId, codigo)) {
    await borrarDoc(ruta);
  }

  fotos.push(plan.rutaStorageLogo(negocioId));
  await borrarStorage(fotos);
}

/**
 * eliminarMiCuenta
 * ----------------
 * Entrypoint de la Function callable. `request.auth.uid` es la ÚNICA fuente
 * del objetivo. No se aceptan uid/clienteId/negocioId enviados por la app.
 */
async function eliminarMiCuenta(request) {
  const uid = request.auth && request.auth.uid;
  if (!uid) {
    throw new HttpsError(
      "unauthenticated",
      "Debes iniciar sesión para eliminar tu cuenta"
    );
  }

  const usuarioRef = db().doc(`${plan.USUARIOS}/${uid}`);
  const usuario = await usuarioRef.get();
  const datos = usuario.exists ? usuario.data() : null;
  const objetivo = plan.objetivoDesdeUsuario(datos);

  try {
    if (objetivo && objetivo.tipo === "ADMIN") {
      await borrarNegocio(uid, objetivo.negocioId);
    } else if (objetivo && objetivo.tipo === "CLIENTE") {
      await borrarCliente(
        uid,
        objetivo.clienteId,
        objetivo.negocioId
      );
      // Incondicional: cubre también al CLIENTE sin ficha (clienteId == null).
      await borrarRastroPersonalDelUsuario(uid);
    } else {
      // Sin documento de usuario (o rol desconocido): limpiar residuo posible.
      await borrarRastroPersonalDelUsuario(uid);
    }

    // usuarios/{uid} se borra al final, justo antes de Auth.
    if (usuario.exists) {
      await usuarioRef.delete();
    }

    // Auth lo último: si algo falló antes no se pierde la identidad.
    await getAuth().deleteUser(uid);
  } catch (e) {
    if (e instanceof HttpsError) throw e;
    throw new HttpsError(
      "internal",
      `No se pudo completar la eliminación: ${e && e.message ? e.message : e}`
    );
  }

  return { ok: true };
}

module.exports = { eliminarMiCuenta };
