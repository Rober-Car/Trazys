"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");

const { esNotificacionInmediataProcesable, esAvisoAlAdmin } = require("../lib/plan_inmediata");

test("MANUAL inmediata PENDIENTE -> procesable", () => {
    assert.equal(
        esNotificacionInmediataProcesable({
            estado: "PENDIENTE",
            programada: false,
            origen: "MANUAL",
            tipo: "MANUAL",
        }),
        true
    );
});

test("PRECONFIGURADA de baja (BAJA_CONFIRMADA) -> procesable (push)", () => {
    assert.equal(
        esNotificacionInmediataProcesable({
            estado: "PENDIENTE",
            programada: false,
            origen: "PRECONFIGURADA",
            tipo: "BAJA_CONFIRMADA",
        }),
        true
    );
});

test("PRECONFIGURADA de baja rechazada (SOLICITUD_RECHAZADA) -> procesable (push)", () => {
    assert.equal(
        esNotificacionInmediataProcesable({
            estado: "PENDIENTE",
            programada: false,
            origen: "PRECONFIGURADA",
            tipo: "SOLICITUD_RECHAZADA",
        }),
        true
    );
});

test("PRECONFIGURADA de MOROSIDAD -> NO procesable aquí (la envía su barrido)", () => {
    assert.equal(
        esNotificacionInmediataProcesable({
            estado: "PENDIENTE",
            programada: false,
            origen: "PRECONFIGURADA",
            tipo: "MOROSIDAD",
        }),
        false
    );
});

test("PRECONFIGURADA desconocida -> NO procesable", () => {
    assert.equal(
        esNotificacionInmediataProcesable({
            estado: "PENDIENTE",
            programada: false,
            origen: "PRECONFIGURADA",
            tipo: "CAMBIO_HORARIO",
        }),
        false
    );
});

test("programada -> NO procesable", () => {
    assert.equal(
        esNotificacionInmediataProcesable({
            estado: "PROGRAMADA",
            programada: true,
            origen: "MANUAL",
            tipo: "PROGRAMADA",
        }),
        false
    );
});

test("ya ENVIADA -> NO procesable", () => {
    assert.equal(
        esNotificacionInmediataProcesable({
            estado: "ENVIADA",
            programada: false,
            origen: "MANUAL",
            tipo: "MANUAL",
        }),
        false
    );
});

test("datos nulos -> NO procesable", () => {
    assert.equal(esNotificacionInmediataProcesable(null), false);
    assert.equal(esNotificacionInmediataProcesable(undefined), false);
});

test("SOLICITUD_BAJA (aviso al ADMIN) PENDIENTE -> procesable y es aviso ADMIN", () => {
    const datos = {
        estado: "PENDIENTE",
        programada: false,
        origen: "AUTOMATICA",
        tipo: "SOLICITUD_BAJA",
    };
    assert.equal(esNotificacionInmediataProcesable(datos), true);
    assert.equal(esAvisoAlAdmin(datos), true);
});

test("SOLICITUD_BAJA no es aviso ADMIN si no es ese tipo", () => {
    assert.equal(esAvisoAlAdmin({ tipo: "MANUAL" }), false);
    assert.equal(esAvisoAlAdmin({ tipo: "MOROSIDAD" }), false);
    assert.equal(esAvisoAlAdmin({ tipo: "BAJA_CONFIRMADA" }), false);
    assert.equal(esAvisoAlAdmin(null), false);
});
