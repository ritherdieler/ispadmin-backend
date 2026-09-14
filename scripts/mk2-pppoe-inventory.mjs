#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { writeFile } from 'node:fs/promises';
import https from 'node:https';
import { fileURLToPath } from 'node:url';

import {
  classifySecretsAgainstDb,
  findBlockConflicts,
  findDoubleLimited,
  indexSubscriptionsByIp,
  reconcileSecretProfiles,
  subdivideBlock,
  summarizeSecrets,
} from './mk2-pppoe-inventory-lib.mjs';

const DEFAULT_BLOCK = '10.64.0.0/18';

export const PROPOSED_RANGES = [
  { name: 'PPPOE-DINAMICO', from: '10.64.0.2', to: '10.64.47.254' },
  { name: 'PPPOE-FIJAS', from: '10.64.48.1', to: '10.64.51.254' },
  { name: 'PPPOE-STG', from: '10.64.60.2', to: '10.64.60.254' },
];

export const SUBSCRIPTION_IPS_SQL = `
SELECT s.id, s.ip, s.service_status, s.installation_type,
       p.name AS plan_name, p.download_speed, p.upload_speed
FROM subscription s
LEFT JOIN plan p ON p.id = s.plan_id
WHERE s.ip IS NOT NULL AND s.ip <> ''
ORDER BY s.id
`.trim();

export function parseArgs(argv) {
  const options = { block: DEFAULT_BLOCK, jsonOut: null, insecure: false, help: false };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--insecure') options.insecure = true;
    else if (arg === '--help' || arg === '-h') options.help = true;
    else if (arg === '--block') {
      options.block = argv[i + 1];
      i += 1;
    } else if (arg === '--json-out') {
      options.jsonOut = argv[i + 1];
      i += 1;
    } else {
      throw new Error(`Argumento desconocido: ${arg}`);
    }
  }
  return options;
}

export function envConfig() {
  return {
    mysqlHost: process.env.MYSQL_HOST ?? '127.0.0.1',
    mysqlPort: process.env.MYSQL_PORT ?? '13306',
    mysqlUser: process.env.MYSQL_USER ?? 'root',
    mysqlPassword: process.env.MYSQL_PASSWORD ?? '',
    mysqlDatabase: process.env.MYSQL_DATABASE ?? 'ispadmin',
    mk2Host: process.env.MK2_HOST ?? '38.224.231.4',
    mk2User: process.env.MK2_USER ?? '',
    mk2Pass: process.env.MK2_PASS ?? '',
    mk2Port: process.env.MK2_PORT ?? '443',
  };
}

export function parseMysqlTabOutput(stdout) {
  const lines = stdout.trim().split('\n').filter(Boolean);
  if (lines.length === 0) return [];
  const headers = lines[0].split('\t');
  return lines.slice(1).map((line) => {
    const cols = line.split('\t');
    const row = {};
    headers.forEach((header, index) => {
      const value = cols[index] ?? '';
      if (header === 'id') row[header] = value === '' || value === 'NULL' ? null : Number.parseInt(value, 10);
      else row[header] = value === 'NULL' ? null : value;
    });
    return row;
  });
}

export function runMysqlQuery(config, sql) {
  const args = [
    '-h', config.mysqlHost,
    '-P', String(config.mysqlPort),
    '-u', config.mysqlUser,
    config.mysqlDatabase,
    '-e', sql,
    '--batch', '--raw', '--default-character-set=utf8mb4',
  ];
  const env = config.mysqlPassword
    ? { ...process.env, MYSQL_PWD: config.mysqlPassword }
    : process.env;
  const result = spawnSync('mysql', args, { encoding: 'utf8', env });
  if (result.status !== 0) {
    throw new Error(result.stderr || 'mysql falló al leer suscripciones');
  }
  return parseMysqlTabOutput(result.stdout);
}

export function createMk2Client(config, { insecure = false } = {}) {
  const auth = Buffer.from(`${config.mk2User}:${config.mk2Pass}`).toString('base64');
  const agent = insecure ? new https.Agent({ rejectUnauthorized: false }) : undefined;

  function print(path, body = {}) {
    if (!config.mk2User || !config.mk2Pass) {
      throw new Error('MK2_USER y MK2_PASS son obligatorios');
    }
    const payload = JSON.stringify(body);
    return new Promise((resolve, reject) => {
      const req = https.request(
        {
          hostname: config.mk2Host,
          port: Number.parseInt(config.mk2Port, 10),
          path,
          method: 'POST',
          agent,
          headers: {
            Authorization: `Basic ${auth}`,
            Accept: 'application/json',
            'Content-Type': 'application/json; charset=utf-8',
            'Content-Length': Buffer.byteLength(payload),
          },
        },
        (res) => {
          let data = '';
          res.on('data', (chunk) => {
            data += chunk;
          });
          res.on('end', () => {
            if (res.statusCode < 200 || res.statusCode >= 300) {
              reject(new Error(`MK2 POST ${path} HTTP ${res.statusCode}: ${data}`));
              return;
            }
            resolve(data.trim() ? JSON.parse(data) : []);
          });
        },
      );
      req.on('error', reject);
      req.write(payload);
      req.end();
    });
  }

  return {
    secrets: () => print('/rest/ppp/secret/print'),
    profiles: () => print('/rest/ppp/profile/print'),
    active: () => print('/rest/ppp/active/print'),
    servers: () => print('/rest/interface/pppoe-server/server/print'),
    pools: () => print('/rest/ip/pool/print'),
    vlans: () => print('/rest/interface/vlan/print'),
    addresses: () => print('/rest/ip/address/print'),
    routes: () => print('/rest/ip/route/print', { '.proplist': 'dst-address,gateway,active' }),
    arp: () => print('/rest/ip/arp/print', { '.proplist': 'address,mac-address,interface' }),
    queues: () => print('/rest/queue/simple/print', { '.proplist': 'name,target,max-limit' }),
  };
}

function usage() {
  return `Uso: mk2-pppoe-inventory.mjs [--block CIDR] [--json-out FILE] [--insecure]

Inventario de solo lectura del PPPoE y el direccionamiento de MK2.
Verifica que el bloque propuesto para PPPoE dinamico este libre y clasifica
los ppp secrets existentes contra la base de datos.

Variables: MK2_HOST, MK2_USER, MK2_PASS, MYSQL_* (tunel en 127.0.0.1:13306).`;
}

export async function buildReport(client, config, block) {
  const [secrets, profiles, active, servers, pools, vlans, addresses, routes, arp, queues] =
    await Promise.all([
      client.secrets(),
      client.profiles(),
      client.active(),
      client.servers(),
      client.pools(),
      client.vlans(),
      client.addresses(),
      client.routes(),
      client.arp(),
      client.queues(),
    ]);

  const dbRows = runMysqlQuery(config, SUBSCRIPTION_IPS_SQL);
  const subscriptionsByIp = indexSubscriptionsByIp(dbRows);
  const plansBySubscription = new Map(
    dbRows.map((row) => [
      row.id,
      {
        name: row.plan_name,
        download_speed: Number(row.download_speed ?? 0),
        upload_speed: Number(row.upload_speed ?? 0),
      },
    ]),
  );

  const queueTargets = queues
    .map((queue) => (queue.target ?? '').split('/')[0].trim())
    .filter((target) => /^\d/.test(target));

  const conflicts = findBlockConflicts(block, {
    addresses: addresses.map((entry) => entry.address),
    routes: routes.map((entry) => entry['dst-address']),
    ips: [
      ...arp.map((entry) => entry.address),
      ...queueTargets,
      ...dbRows.map((row) => row.ip),
      ...pools.flatMap((pool) => (pool.ranges ?? '').split(',').flatMap((r) => r.split('-'))),
    ],
  });

  const classification = classifySecretsAgainstDb(secrets, subscriptionsByIp);
  const profileRows = profiles.map((profile) => ({
    name: profile.name,
    rateLimit: profile['rate-limit'] ?? null,
    localAddress: profile['local-address'] ?? null,
    remoteAddress: profile['remote-address'] ?? null,
  }));
  const profilesByName = new Map(profileRows.map((profile) => [profile.name, profile]));

  return {
    generatedAt: new Date().toISOString(),
    block,
    blockConflicts: conflicts,
    subdivision: subdivideBlock(block, PROPOSED_RANGES),
    secrets: summarizeSecrets(secrets, active),
    classification,
    profileReconciliation: reconcileSecretProfiles(
      [...classification.matchedActive, ...classification.matchedCancelled],
      plansBySubscription,
      profilesByName,
    ),
    doubleLimited: findDoubleLimited(secrets, queues),
    profiles: profileRows,
    servers: servers.map((server) => ({
      interface: server.interface,
      serviceName: server['service-name'],
      disabled: server.disabled,
      maxMtu: server['max-mtu'],
      maxMru: server['max-mru'],
    })),
    pools: pools.map((pool) => ({ name: pool.name, ranges: pool.ranges })),
    vlans: vlans.map((vlan) => ({
      name: vlan.name,
      vlanId: vlan['vlan-id'],
      interface: vlan.interface,
    })),
  };
}

function printReport(report) {
  const { blockConflicts, subdivision, secrets, classification } = report;

  console.log(`Bloque propuesto: ${report.block}`);
  console.log(`  libre: ${blockConflicts.free ? 'SI' : 'NO'}`);
  for (const conflict of blockConflicts.conflicts) {
    console.log(`  CONFLICTO [${conflict.source}] ${conflict.value}`);
  }

  console.log(`Subdivision valida: ${subdivision.valid ? 'SI' : 'NO'}`);
  for (const range of subdivision.ranges) {
    console.log(`  ${range.name}: ${range.fromIp}-${range.toIp} (${range.size} direcciones)`);
  }
  for (const error of subdivision.errors) console.log(`  ERROR ${error}`);

  console.log('PPP secrets');
  console.log(`  total ${secrets.total} | deshabilitados ${secrets.disabled} | con IP fija ${secrets.withFixedAddress}`);
  console.log(`  sesiones activas ${secrets.activeSessions} | sin sesion ${secrets.withoutSession}`);
  console.log(`  por perfil: ${JSON.stringify(secrets.byProfile)}`);
  if (secrets.duplicatedAddresses.length) {
    console.log(`  IP duplicadas: ${secrets.duplicatedAddresses.join(', ')}`);
  }
  if (secrets.addressMismatches.length) {
    console.log(`  sesiones con IP distinta al secret: ${secrets.addressMismatches.length}`);
  }

  console.log('Cruce con base de datos');
  console.log(`  con suscripcion activa: ${classification.matchedActive.length}`);
  console.log(`  con suscripcion cancelada: ${classification.matchedCancelled.length}`);
  console.log(`  sin suscripcion (IP a reservar): ${classification.unknown.length}`);
  console.log(`  sin remote-address: ${classification.withoutAddress.length}`);
  if (classification.reservedIps.length) {
    console.log(`  reservar: ${classification.reservedIps.join(', ')}`);
  }

  const { profileReconciliation, doubleLimited } = report;
  console.log('Perfil PPPoE contra plan contratado');
  console.log(`  alineados: ${profileReconciliation.aligned.length}`);
  console.log(`  desalineados: ${profileReconciliation.mismatched.length}`);
  console.log(`  perfil inexistente en el router: ${profileReconciliation.unknownProfile.length}`);
  console.log(`  sin plan en la base: ${profileReconciliation.withoutPlan.length}`);
  for (const row of profileReconciliation.mismatched.slice(0, 15)) {
    console.log(
      `  sub ${row.subscriptionId} ${row.name}: perfil ${row.profile} da ${row.profileRateLimit}, el plan pide ${row.expectedRateLimit}`,
    );
  }

  console.log(`Doble limitacion (secret PPPoE + simple queue por IP): ${doubleLimited.length}`);

  console.log('Servidores PPPoE');
  for (const server of report.servers) {
    console.log(`  ${server.interface} | ${server.serviceName} | mtu ${server.maxMtu}`);
  }

  console.log('VLANs');
  for (const vlan of report.vlans) {
    console.log(`  ${vlan.name} id=${vlan.vlanId} sobre ${vlan.interface}`);
  }
}

async function main() {
  const options = parseArgs(process.argv);
  if (options.help) {
    console.log(usage());
    return;
  }

  const config = envConfig();
  const client = createMk2Client(config, { insecure: options.insecure });
  const report = await buildReport(client, config, options.block);

  printReport(report);

  if (options.jsonOut) {
    await writeFile(options.jsonOut, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
    console.log(`Reporte JSON: ${options.jsonOut}`);
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
