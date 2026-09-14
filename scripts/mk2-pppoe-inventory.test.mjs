import assert from 'node:assert/strict';
import test from 'node:test';

import {
  cidrContains,
  cidrsOverlap,
  classifySecretsAgainstDb,
  expectedProfileName,
  findBlockConflicts,
  findDoubleLimited,
  indexSubscriptionsByIp,
  ipToLong,
  longToIp,
  parseCidr,
  planRateLimit,
  reconcileSecretProfiles,
  subdivideBlock,
  summarizeSecrets,
} from './mk2-pppoe-inventory-lib.mjs';

test('ipToLong y longToIp son inversos', () => {
  assert.equal(ipToLong('0.0.0.0'), 0);
  assert.equal(ipToLong('10.64.0.1'), 171966465);
  assert.equal(longToIp(171966465), '10.64.0.1');
  assert.equal(longToIp(ipToLong('192.168.26.254')), '192.168.26.254');
});

test('ipToLong rechaza entradas invalidas', () => {
  assert.equal(ipToLong('no-una-ip'), null);
  assert.equal(ipToLong('999.1.1.1'), null);
  assert.equal(ipToLong(''), null);
  assert.equal(ipToLong(null), null);
});

test('parseCidr calcula primera y ultima direccion del bloque', () => {
  const block = parseCidr('10.64.0.0/18');
  assert.equal(block.prefix, 18);
  assert.equal(block.networkIp, '10.64.0.0');
  assert.equal(block.broadcastIp, '10.64.63.255');
  assert.equal(block.firstUsableIp, '10.64.0.1');
  assert.equal(block.lastUsableIp, '10.64.63.254');
  assert.equal(block.size, 16384);
});

test('parseCidr acepta host address y normaliza a la red', () => {
  const block = parseCidr('192.168.30.1/24');
  assert.equal(block.networkIp, '192.168.30.0');
  assert.equal(block.broadcastIp, '192.168.30.255');
});

test('parseCidr sin mascara asume /32', () => {
  const block = parseCidr('10.64.0.5');
  assert.equal(block.prefix, 32);
  assert.equal(block.networkIp, '10.64.0.5');
  assert.equal(block.size, 1);
});

test('cidrContains distingue dentro y fuera del bloque', () => {
  assert.equal(cidrContains('10.64.0.0/18', '10.64.0.1'), true);
  assert.equal(cidrContains('10.64.0.0/18', '10.64.63.255'), true);
  assert.equal(cidrContains('10.64.0.0/18', '10.64.64.0'), false);
  assert.equal(cidrContains('10.64.0.0/18', '10.64.250.1'), false);
  assert.equal(cidrContains('10.64.0.0/18', '192.168.26.1'), false);
});

test('cidrsOverlap detecta solape en ambos sentidos', () => {
  assert.equal(cidrsOverlap('10.64.0.0/18', '10.64.32.0/20'), true);
  assert.equal(cidrsOverlap('10.64.32.0/20', '10.64.0.0/18'), true);
  assert.equal(cidrsOverlap('10.64.0.0/18', '10.65.0.0/18'), false);
  assert.equal(cidrsOverlap('10.64.0.0/18', '192.168.26.0/24'), false);
});

test('findBlockConflicts marca el bloque libre cuando nada lo toca', () => {
  const report = findBlockConflicts('10.64.0.0/18', {
    addresses: ['192.168.30.1/24', '10.20.0.1/22', '38.224.231.4/27'],
    routes: ['192.168.26.0/24', '10.11.104.0/24'],
    ips: ['192.168.26.108', '192.168.30.55'],
  });
  assert.equal(report.free, true);
  assert.deepEqual(report.conflicts, []);
});

test('findBlockConflicts reporta direcciones del router que solapan', () => {
  const report = findBlockConflicts('10.64.0.0/18', {
    addresses: ['10.64.5.1/24', '192.168.30.1/24'],
    routes: [],
    ips: [],
  });
  assert.equal(report.free, false);
  assert.equal(report.conflicts.length, 1);
  assert.equal(report.conflicts[0].source, 'addresses');
  assert.equal(report.conflicts[0].value, '10.64.5.1/24');
});

test('findBlockConflicts reporta IPs sueltas dentro del bloque', () => {
  const report = findBlockConflicts('10.64.0.0/18', {
    addresses: [],
    routes: [],
    ips: ['10.64.0.9', '192.168.26.108'],
  });
  assert.equal(report.free, false);
  assert.equal(report.conflicts.length, 1);
  assert.equal(report.conflicts[0].value, '10.64.0.9');
  assert.equal(report.conflicts[0].source, 'ips');
});

test('findBlockConflicts ignora la ruta default porque solapa con todo', () => {
  const report = findBlockConflicts('10.64.0.0/18', {
    addresses: [],
    routes: ['0.0.0.0/0', '0.0.0.0/0', '192.168.26.0/24'],
    ips: [],
  });
  assert.equal(report.free, true);
  assert.deepEqual(report.conflicts, []);
});

test('findBlockConflicts sigue reportando rutas concretas que solapan', () => {
  const report = findBlockConflicts('10.64.0.0/18', {
    addresses: [],
    routes: ['0.0.0.0/0', '10.64.16.0/20'],
    ips: [],
  });
  assert.equal(report.free, false);
  assert.equal(report.conflicts.length, 1);
  assert.equal(report.conflicts[0].value, '10.64.16.0/20');
});

test('findBlockConflicts ignora valores vacios o corruptos', () => {
  const report = findBlockConflicts('10.64.0.0/18', {
    addresses: ['', null, 'basura'],
    routes: [undefined],
    ips: ['', '   '],
  });
  assert.equal(report.free, true);
});

test('subdivideBlock valida que cada rango cabe en el bloque padre', () => {
  const result = subdivideBlock('10.64.0.0/18', [
    { name: 'PPPOE-DINAMICO', from: '10.64.0.2', to: '10.64.47.254' },
    { name: 'PPPOE-FIJAS', from: '10.64.48.1', to: '10.64.51.254' },
    { name: 'PPPOE-STG', from: '10.64.60.2', to: '10.64.60.254' },
  ]);
  assert.equal(result.valid, true);
  assert.deepEqual(result.errors, []);
  assert.equal(result.ranges[0].size, 12285);
});

test('subdivideBlock detecta un rango fuera del bloque padre', () => {
  const result = subdivideBlock('10.64.0.0/18', [
    { name: 'PPPOE-DINAMICO', from: '10.64.0.2', to: '10.64.47.254' },
    { name: 'PPPOE-STG', from: '10.64.250.2', to: '10.64.250.254' },
  ]);
  assert.equal(result.valid, false);
  assert.equal(result.errors.length, 1);
  assert.match(result.errors[0], /PPPOE-STG/);
  assert.match(result.errors[0], /fuera del bloque/);
});

test('subdivideBlock detecta solape entre rangos hermanos', () => {
  const result = subdivideBlock('10.64.0.0/18', [
    { name: 'PPPOE-DINAMICO', from: '10.64.0.2', to: '10.64.47.254' },
    { name: 'PPPOE-FIJAS', from: '10.64.47.100', to: '10.64.51.254' },
  ]);
  assert.equal(result.valid, false);
  assert.equal(result.errors.length, 1);
  assert.match(result.errors[0], /solapa/);
});

test('subdivideBlock detecta rango invertido', () => {
  const result = subdivideBlock('10.64.0.0/18', [
    { name: 'MALO', from: '10.64.10.5', to: '10.64.10.1' },
  ]);
  assert.equal(result.valid, false);
  assert.match(result.errors[0], /invertido/);
});

test('summarizeSecrets agrupa por perfil y cuenta sesiones', () => {
  const secrets = [
    { name: 'a', profile: 'PLAN 50 SOLES', 'remote-address': '192.168.26.10', service: 'pppoe' },
    { name: 'b', profile: 'PLAN 50 SOLES', 'remote-address': '192.168.26.11', service: 'pppoe' },
    { name: 'c', profile: 'PLAN 70', service: 'pppoe', disabled: 'true' },
  ];
  const active = [{ name: 'a', address: '192.168.26.10' }];

  const summary = summarizeSecrets(secrets, active);

  assert.equal(summary.total, 3);
  assert.equal(summary.disabled, 1);
  assert.equal(summary.withFixedAddress, 2);
  assert.equal(summary.activeSessions, 1);
  assert.equal(summary.withoutSession, 2);
  assert.deepEqual(summary.byProfile, { 'PLAN 50 SOLES': 2, 'PLAN 70': 1 });
});

test('summarizeSecrets detecta remote-address duplicada', () => {
  const secrets = [
    { name: 'a', 'remote-address': '192.168.26.10' },
    { name: 'b', 'remote-address': '192.168.26.10' },
  ];
  const summary = summarizeSecrets(secrets, []);
  assert.deepEqual(summary.duplicatedAddresses, ['192.168.26.10']);
});

test('summarizeSecrets detecta sesion con IP distinta a la del secret', () => {
  const secrets = [{ name: 'a', 'remote-address': '192.168.26.10' }];
  const active = [{ name: 'a', address: '192.168.26.99' }];
  const summary = summarizeSecrets(secrets, active);
  assert.deepEqual(summary.addressMismatches, [
    { name: 'a', secretAddress: '192.168.26.10', sessionAddress: '192.168.26.99' },
  ]);
});

test('classifySecretsAgainstDb separa conocidos de invisibles', () => {
  const secrets = [
    { name: 'a', 'remote-address': '192.168.26.10' },
    { name: 'b', 'remote-address': '192.168.26.11' },
    { name: 'c', 'remote-address': '192.168.26.99' },
    { name: 'sinip' },
  ];
  const subscriptionsByIp = new Map([
    ['192.168.26.10', { id: 100, service_status: 'ACTIVE', installation_type: 'FIBER' }],
    ['192.168.26.11', { id: 200, service_status: 'CANCELLED', installation_type: 'WIRELESS' }],
  ]);

  const result = classifySecretsAgainstDb(secrets, subscriptionsByIp);

  assert.equal(result.matchedActive.length, 1);
  assert.equal(result.matchedActive[0].subscriptionId, 100);
  assert.equal(result.matchedCancelled.length, 1);
  assert.equal(result.matchedCancelled[0].subscriptionId, 200);
  assert.equal(result.unknown.length, 1);
  assert.equal(result.unknown[0].remoteAddress, '192.168.26.99');
  assert.equal(result.withoutAddress.length, 1);
  assert.equal(result.withoutAddress[0].name, 'sinip');
});

test('indexSubscriptionsByIp prefiere la suscripcion no cancelada cuando la IP se repite', () => {
  const rows = [
    { id: 700, ip: '192.168.26.50', service_status: 'CANCELLED' },
    { id: 900, ip: '192.168.26.50', service_status: 'ACTIVE' },
    { id: 950, ip: '192.168.26.51', service_status: 'CANCELLED' },
  ];
  const index = indexSubscriptionsByIp(rows);
  assert.equal(index.get('192.168.26.50').id, 900);
  assert.equal(index.get('192.168.26.51').id, 950);
});

test('indexSubscriptionsByIp no depende del orden de las filas', () => {
  const rows = [
    { id: 900, ip: '192.168.26.50', service_status: 'ACTIVE' },
    { id: 700, ip: '192.168.26.50', service_status: 'CANCELLED' },
  ];
  assert.equal(indexSubscriptionsByIp(rows).get('192.168.26.50').id, 900);
});

test('classifySecretsAgainstDb expone las IPs invisibles para bloquear su reuso', () => {
  const secrets = [
    { name: 'a', 'remote-address': '192.168.26.99' },
    { name: 'b', 'remote-address': '192.168.26.98' },
  ];
  const result = classifySecretsAgainstDb(secrets, new Map());
  assert.deepEqual(result.reservedIps, ['192.168.26.98', '192.168.26.99']);
});

test('expectedProfileName deriva el nombre del plan y no del nombre comercial', () => {
  assert.equal(expectedProfileName({ download_speed: 200, upload_speed: 200 }), 'GF-200-200');
  assert.equal(expectedProfileName({ download_speed: 80, upload_speed: 40 }), 'GF-80-40');
  assert.equal(expectedProfileName({ downloadSpeed: 600, uploadSpeed: 600 }), 'GF-600-600');
});

test('expectedProfileName devuelve null si el plan no tiene velocidades', () => {
  assert.equal(expectedProfileName({ download_speed: 0, upload_speed: 0 }), null);
  assert.equal(expectedProfileName({}), null);
  assert.equal(expectedProfileName(null), null);
});

test('planRateLimit respeta el orden rx/tx de RouterOS', () => {
  assert.equal(planRateLimit({ download_speed: 200, upload_speed: 200 }), '200M/200M');
  assert.equal(planRateLimit({ download_speed: 80, upload_speed: 40 }), '40M/80M');
  assert.equal(planRateLimit({ download_speed: 100, upload_speed: 0 }), null);
});

test('reconcileSecretProfiles detecta perfil que no corresponde al plan', () => {
  const entries = [
    { name: 'a', subscriptionId: 10, profile: 'PLAN 50 SOLES', remoteAddress: '192.168.26.10' },
    { name: 'b', subscriptionId: 20, profile: 'PLAN 70', remoteAddress: '192.168.26.11' },
  ];
  const plansBySubscription = new Map([
    [10, { download_speed: 200, upload_speed: 200 }],
    [20, { download_speed: 400, upload_speed: 400 }],
  ]);
  const profilesByName = new Map([
    ['PLAN 50 SOLES', { name: 'PLAN 50 SOLES', rateLimit: '200M/200M' }],
    ['PLAN 70', { name: 'PLAN 70', rateLimit: '300M/300M' }],
  ]);

  const result = reconcileSecretProfiles(entries, plansBySubscription, profilesByName);

  assert.equal(result.aligned.length, 1);
  assert.equal(result.aligned[0].name, 'a');
  assert.equal(result.mismatched.length, 1);
  assert.equal(result.mismatched[0].name, 'b');
  assert.equal(result.mismatched[0].profileRateLimit, '300M/300M');
  assert.equal(result.mismatched[0].expectedRateLimit, '400M/400M');
});

test('reconcileSecretProfiles marca perfil inexistente en el router', () => {
  const entries = [{ name: 'a', subscriptionId: 10, profile: 'FANTASMA' }];
  const plansBySubscription = new Map([[10, { download_speed: 200, upload_speed: 200 }]]);

  const result = reconcileSecretProfiles(entries, plansBySubscription, new Map());

  assert.equal(result.unknownProfile.length, 1);
  assert.equal(result.unknownProfile[0].profile, 'FANTASMA');
});

test('reconcileSecretProfiles ignora entradas sin plan conocido', () => {
  const entries = [{ name: 'a', subscriptionId: 999, profile: 'PLAN 70' }];
  const profilesByName = new Map([['PLAN 70', { name: 'PLAN 70', rateLimit: '300M/300M' }]]);

  const result = reconcileSecretProfiles(entries, new Map(), profilesByName);

  assert.equal(result.withoutPlan.length, 1);
  assert.equal(result.mismatched.length, 0);
});

test('findDoubleLimited cruza secrets con colas por IP', () => {
  const secrets = [
    { name: 'a', 'remote-address': '192.168.26.10' },
    { name: 'b', 'remote-address': '192.168.26.11' },
  ];
  const queues = [
    { name: 'id:100, usuario:Ana', target: '192.168.26.10/32', 'max-limit': '200000000/200000000' },
    { name: 'id:200, usuario:Beto', target: '192.168.26.99' },
  ];

  const result = findDoubleLimited(secrets, queues);

  assert.equal(result.length, 1);
  assert.equal(result[0].name, 'a');
  assert.equal(result[0].remoteAddress, '192.168.26.10');
  assert.equal(result[0].queueName, 'id:100, usuario:Ana');
});

test('findDoubleLimited ignora las colas dinamicas de pppoe', () => {
  const secrets = [{ name: 'a', 'remote-address': '192.168.26.10' }];
  const queues = [{ name: '<pppoe-a>', target: '<pppoe-a>' }];

  assert.deepEqual(findDoubleLimited(secrets, queues), []);
});
