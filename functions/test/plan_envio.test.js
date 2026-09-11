"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");

const { construirMensajeMulticast } = require("../lib/plan_envio");

const base = {
    tokens: ["t1", "t2"],
    notificacionId: "morosidad_7_1700000000000",
    clienteId: 7,
    tipo: "MOROSIDAD",
    negocioId: "negocio-a",
    origen: "PRECONFIGURADA",
    titulo: "Pago vencido",
    mensaje: "Se ha detectado un periodo de pago vencido en tu cuenta.",
};

test("normal: incluye notification, sin android y data sin texto", () => {
    const m = construirMensajeMulticast({ ...base, tipo: "MANUAL", origen: "MANUAL" });
    assert.deepEqual(m.notification, { title: base.titulo, body: base.mensaje });
    assert.equal(m.android, undefined);
    assert.equal(m.data.titulo, undefined);
    assert.equal(m.data.mensaje, undefined);
    assert.equal(m.data.tituloEn, undefined);
    assert.equal(m.data.clienteId, "7");
    assert.deepEqual(m.tokens, ["t1", "t2"]);
});

test("morosidad data-only: sin notification, prioridad alta y texto en data", () => {
    const m = construirMensajeMulticast({
        ...base,
        tituloEn: "Payment overdue",
        soloDatos: true,
    });
    assert.equal(m.notification, undefined);
    assert.deepEqual(m.android, { priority: "high" });
    assert.equal(m.data.titulo, "Pago vencido");
    assert.equal(m.data.tituloEn, "Payment overdue");
    assert.equal(m.data.mensaje, base.mensaje);
});

test("data-only sin tituloEn (compatibilidad): tituloEn ausente", () => {
    const m = construirMensajeMulticast({ ...base, soloDatos: true });
    assert.equal(m.data.tituloEn, undefined);
    assert.equal(m.data.titulo, "Pago vencido");
    assert.deepEqual(m.android, { priority: "high" });
});

test("normal con tituloEn: notification intacta y tituloEn en data", () => {
    const m = construirMensajeMulticast({ ...base, tituloEn: "Payment overdue" });
    assert.deepEqual(m.notification, { title: base.titulo, body: base.mensaje });
    assert.equal(m.data.tituloEn, "Payment overdue");
    assert.equal(m.android, undefined);
});

test("data-only de baja rechazada: titulo/tituloEn/mensaje/mensajeEn en data", () => {
    const m = construirMensajeMulticast({
        ...base,
        tipo: "SOLICITUD_RECHAZADA",
        origen: "PRECONFIGURADA",
        titulo: "Solicitud de baja rechazada",
        tituloEn: "Cancellation request rejected",
        mensaje: "Tu solicitud de baja ha sido rechazada.",
        mensajeEn: "Your cancellation request has been rejected.",
        soloDatos: true,
    });
    assert.equal(m.notification, undefined);
    assert.deepEqual(m.android, { priority: "high" });
    assert.equal(m.data.titulo, "Solicitud de baja rechazada");
    assert.equal(m.data.tituloEn, "Cancellation request rejected");
    assert.equal(m.data.mensaje, "Tu solicitud de baja ha sido rechazada.");
    assert.equal(m.data.mensajeEn, "Your cancellation request has been rejected.");
});
