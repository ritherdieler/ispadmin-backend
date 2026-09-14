#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import https from 'node:https';
import { fileURLToPath } from 'node:url';
import {
  CANCELLED_SQL,
  SUBSCRIPTIONS_SQL,
  diffSubscriptionsAgainstQueues,
} from './mk2-reconcile-queues-lib.mjs';

const DEFAULT_HOST_DEVICE_ID = 8;
const QUEUE_PROPLIST = ['.id', 'name', 'target', 'max-limit'];

export function parseArgs(argv) {
  const flags = new Set();
  const options = {};
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--dry-run') flags.add('dryRun');
    else if (arg === '--apply') flags.add('apply');
    else if (arg === '--delete-cancelled') flags.add('deleteCancelled');
    else if (arg === '--insecure') flags.add('insecure');
    else if (arg === '--help' || arg === '-h') flags.add('help');
    else if (arg === '--subscriptions-json') {
      options.subscriptionsJson = argv[i + 1];
      i += 1;
    } else if (arg === '--only') {
      options.only = String(argv[i + 1] ?? '')
        .split(',')
        .map((value) => value.trim().toUpperCase())
        .filter(Boolean);
      i += 1;
    } else if (arg === '--skip-queue-ids') {
      options.skipQueueIds = String(argv[i + 1] ?? '')
        .split(',')
        .map((value) => value.trim())
        .filter(Boolean);
      i += 1;
    } else if (arg === '--cancelled-json') {
      options.cancelledJson = argv[i + 1];
      i += 1;
    } else if (arg === '--queues-json') {
      options.queuesJson = argv[i + 1];
      i += 1;
    } else if (arg === '--host-device-id') {
      options.hostDeviceId = Number.parseInt(argv[i + 1], 10);
      i += 1;
    } else if (arg === '--json-out') {
      options.jsonOut = argv[i + 1];
      i += 1;
    } else {
      throw new Error(`Argumento desconocido: ${arg}`);
    }
  }
  if (flags.has('dryRun') && flags.has('apply')) {
    throw new Error('Usa solo uno: --dry-run o --apply');
  }
  if (!flags.has('dryRun') && !flags.has('apply')) {
    flags.add('dryRun');
  }
  return {
    dryRun: flags.has('dryRun'),
    apply: flags.has('apply'),
    deleteCancelled: flags.has('deleteCancelled'),
    insecure: flags.has('insecure'),
    help: flags.has('help'),
    only: options.only?.length ? options.only : null,
    skipQueueIds: options.skipQueueIds ?? [],
    hostDeviceId: options.hostDeviceId ?? DEFAULT_HOST_DEVICE_ID,
    subscriptionsJson: options.subscriptionsJson ?? null,
    cancelledJson: options.cancelledJson ?? null,
    queuesJson: options.queuesJson ?? null,
    jsonOut: options.jsonOut ?? null,
  };
}

export function envConfig() {
  return {
    mysqlHost: process.env.MYSQL_HOST ?? '127.0.0.1',
    mysqlPort: process.env.MYSQL_PORT ?? '13306',
    mysqlUser: process.env.MYSQL_USER ?? 'root',
    mysqlPassword: process.env.MYSQL_PASSWORD ?? '',
    mysqlDatabase: process.env.MYSQL_DATABASE ?? 'ispadmin',
    mk2Host: process.env.MK2_HOST ?? process.env.ROUTEROS_MK2_HOST ?? '38.224.231.4',
    mk2User: process.env.MK2_USER ?? process.env.ROUTEROS_MK2_USER ?? '',
    mk2Pass: process.env.MK2_PASS ?? process.env.ROUTEROS_MK2_PASSWORD ?? '',
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
      if (header === 'id' || header === 'host_device_id' || header === 'upload_speed' || header === 'download_speed') {
        row[header] = value === '' || value === 'NULL' ? null : Number.parseInt(value, 10);
      } else if (value === 'NULL') {
        row[header] = null;
      } else {
        row[header] = value;
      }
    });
    return row;
  });
}

export function buildMysqlArgs(config, sql) {
  return [
    '-h',
    config.mysqlHost,
    '-P',
    String(config.mysqlPort),
    '-u',
    config.mysqlUser,
    config.mysqlDatabase,
    '-e',
    sql,
    '--batch',
    '--raw',
    '--default-character-set=utf8mb4',
  ];
}

export function runMysqlQuery(config, sql) {
  const args = buildMysqlArgs(config, sql);
  const env = config.mysqlPassword
    ? { ...process.env, MYSQL_PWD: config.mysqlPassword }
    : process.env;
  const result = spawnSync('mysql', args, { encoding: 'utf8', env });
  if (result.status !== 0) {
    throw new Error(result.stderr || 'mysql falló al leer suscripciones');
  }
  return parseMysqlTabOutput(result.stdout);
}

export function loadSubscriptionsFromMysql(config, hostDeviceId) {
  return runMysqlQuery(config, SUBSCRIPTIONS_SQL.replace('?', String(hostDeviceId)));
}

export function loadCancelledFromMysql(config) {
  return runMysqlQuery(config, CANCELLED_SQL);
}

export async function loadJsonFile(path) {
  const raw = await readFile(path, 'utf8');
  return JSON.parse(raw);
}

export function createMk2Client(config, { insecure = false } = {}) {
  const auth = Buffer.from(`${config.mk2User}:${config.mk2Pass}`).toString('base64');
  const agent = insecure ? new https.Agent({ rejectUnauthorized: false }) : undefined;

  async function request(method, path, body) {
    if (!config.mk2User || !config.mk2Pass) {
      throw new Error('MK2_USER y MK2_PASS (o ROUTEROS_MK2_*) son obligatorios');
    }
    const payload = body == null ? null : JSON.stringify(body);
    return new Promise((resolve, reject) => {
      const req = https.request(
        {
          hostname: config.mk2Host,
          port: Number.parseInt(config.mk2Port, 10),
          path,
          method,
          agent,
          headers: {
            Authorization: `Basic ${auth}`,
            Accept: 'application/json',
            ...(payload
              ? {
                  'Content-Type': 'application/json; charset=utf-8',
                  'Content-Length': Buffer.byteLength(payload),
                }
              : {}),
          },
        },
        (res) => {
          let data = '';
          res.on('data', (chunk) => {
            data += chunk;
          });
          res.on('end', () => {
            if (res.statusCode < 200 || res.statusCode >= 300) {
              reject(new Error(`MK2 ${method} ${path} HTTP ${res.statusCode}: ${data}`));
              return;
            }
            resolve(data);
          });
        },
      );
      req.on('error', reject);
      if (payload) req.write(payload);
      req.end();
    });
  }

  return {
    async printQueues() {
      const body = { '.proplist': QUEUE_PROPLIST };
      const raw = await request('POST', '/rest/queue/simple/print', body);
      if (!raw.trim()) return [];
      return JSON.parse(raw);
    },
    async addQueue(expected) {
      await request('PUT', '/rest/queue/simple', {
        name: expected.name,
        target: expected.target,
        'max-limit': expected.maxLimit,
      });
    },
    async patchQueue(queueId, fields) {
      const id = encodeURIComponent(queueId);
      await request('PATCH', `/rest/queue/simple/${id}`, fields);
    },
    async removeQueue(queueId) {
      const id = encodeURIComponent(queueId);
      await request('DELETE', `/rest/queue/simple/${id}`, null);
    },
  };
}

export async function applyActions(
  client,
  actions,
  { dryRun = true, deleteCancelled = false, only = null, skipQueueIds = [] } = {},
) {
  const errors = [];
  const applied = { added: 0, updated: 0, retargeted: 0, reclaimed: 0, deletedCancelled: 0 };
  const allowed = only ? new Set(only) : null;
  const skipped = new Set(skipQueueIds);

  for (const action of actions) {
    if (dryRun) continue;
    if (allowed && !allowed.has(action.action)) continue;
    if (action.queueId && skipped.has(action.queueId)) continue;
    try {
      if (action.action === 'ADD') {
        await client.addQueue(action.expected);
        applied.added += 1;
      } else if (action.action === 'UPDATE') {
        await client.patchQueue(action.queueId, {
          name: action.expected.name,
          'max-limit': action.expected.maxLimit,
        });
        applied.updated += 1;
      } else if (action.action === 'RETARGET') {
        await client.patchQueue(action.queueId, {
          name: action.expected.name,
          target: action.expected.target,
          'max-limit': action.expected.maxLimit,
        });
        applied.retargeted += 1;
      } else if (action.action === 'RECLAIM') {
        await client.removeQueue(action.queueId);
        await client.addQueue(action.expected);
        applied.reclaimed += 1;
      } else if (action.action === 'DELETE_CANCELLED' && deleteCancelled) {
        await client.removeQueue(action.queueId);
        applied.deletedCancelled += 1;
      }
    } catch (error) {
      errors.push({
        action: action.action,
        subscriptionId: action.subscriptionId ?? null,
        ip: action.ip ?? null,
        queueId: action.queueId ?? null,
        message: error.message,
      });
    }
  }

  return { applied, errors };
}

export function summarize(stats, errors) {
  const lines = [
    `added=${stats.added} updated=${stats.updated} retargeted=${stats.retargeted} reclaimed=${stats.reclaimed}`,
    `cancelledQueues=${stats.cancelledQueues} conflicts=${stats.conflicts} orphans=${stats.orphans} unchanged=${stats.unchanged}`,
    `skipped=${stats.skipped} pppoeIgnored=${stats.pppoeIgnored}`,
  ];
  if (errors.length > 0) {
    lines.push(`errors=${errors.length}`);
  }
  return lines.join('\n');
}

export async function runReconcile(options, deps = {}) {
  const config = deps.config ?? envConfig();
  const loadSubs =
    deps.loadSubscriptions ??
    (async () => {
      if (options.subscriptionsJson) {
        return loadJsonFile(options.subscriptionsJson);
      }
      return loadSubscriptionsFromMysql(config, options.hostDeviceId);
    });
  const loadCancelled =
    deps.loadCancelled ??
    (async () => {
      if (options.cancelledJson) {
        return loadJsonFile(options.cancelledJson);
      }
      if (options.subscriptionsJson) {
        return [];
      }
      return loadCancelledFromMysql(config);
    });
  const loadQueues =
    deps.loadQueues ??
    (async () => {
      if (options.queuesJson) {
        return loadJsonFile(options.queuesJson);
      }
      const client = deps.client ?? createMk2Client(config, { insecure: options.insecure });
      return client.printQueues();
    });

  const subscriptions = await loadSubs();
  const cancelled = await loadCancelled();
  const queues = await loadQueues();
  const { actions, stats } = diffSubscriptionsAgainstQueues(subscriptions, queues, { cancelled });
  const client = deps.client ?? createMk2Client(config, { insecure: options.insecure });
  const { applied, errors } = await applyActions(client, actions, {
    dryRun: options.dryRun,
    deleteCancelled: options.deleteCancelled,
    only: options.only,
    skipQueueIds: options.skipQueueIds,
  });

  return {
    mode: options.dryRun ? 'dry-run' : 'apply',
    hostDeviceId: options.hostDeviceId,
    subscriptionCount: subscriptions.length,
    cancelledCount: cancelled.length,
    queueCount: queues.length,
    stats,
    applied,
    actions,
    errors,
  };
}

function printHelp() {
  console.log(`Uso: node scripts/mk2-reconcile-queues.mjs [--dry-run|--apply] [opciones]

Variables de entorno:
  MYSQL_HOST MYSQL_PORT MYSQL_USER MYSQL_PASSWORD MYSQL_DATABASE
  MK2_HOST MK2_USER MK2_PASS  (alias ROUTEROS_MK2_*)

Opciones:
  --dry-run                 Solo reporte (default)
  --apply                   Aplica ADD / PATCH / RECLAIM en MK2
  --delete-cancelled        Con --apply, borra colas de suscripciones CANCELLED
  --only ADD,UPDATE         Limita las acciones que se ejecutan en --apply
  --skip-queue-ids *1,*2    Excluye colas concretas de cualquier acción
  --insecure                TLS sin verificar certificado MK2
  --host-device-id 8        Filtra suscripciones por host_device_id
  --subscriptions-json FILE Carga suscripciones desde JSON (sin MySQL)
  --cancelled-json FILE     Carga canceladas desde JSON (sin MySQL)
  --queues-json FILE        Carga colas MK2 desde JSON (sin REST)
  --json-out FILE           Escribe reporte JSON completo
`);
}

async function main() {
  const options = parseArgs(process.argv);
  if (options.help) {
    printHelp();
    return;
  }

  const report = await runReconcile(options);
  console.log(summarize(report.stats, report.errors));
  console.log(
    JSON.stringify(
      {
        mode: report.mode,
        hostDeviceId: report.hostDeviceId,
        subscriptionCount: report.subscriptionCount,
        cancelledCount: report.cancelledCount,
        queueCount: report.queueCount,
        stats: report.stats,
        applied: report.applied,
        errors: report.errors,
        actions: report.actions,
      },
      null,
      2,
    ),
  );

  if (options.jsonOut) {
    const { writeFile } = await import('node:fs/promises');
    await writeFile(options.jsonOut, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  }

  if (report.errors.length > 0) process.exitCode = 2;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
