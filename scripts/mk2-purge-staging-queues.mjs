#!/usr/bin/env node
import { readFile } from 'node:fs/promises';
import https from 'node:https';
import { fileURLToPath } from 'node:url';
import { parseArgs, selectPurgeCandidates } from './mk2-purge-staging-queues-lib.mjs';

const QUEUE_PROPLIST = ['.id', 'name', 'target', 'max-limit', 'comment'];

export { parseArgs, selectPurgeCandidates };

export function envConfig() {
  return {
    mk2Host: process.env.MK2_HOST ?? process.env.ROUTEROS_MK2_HOST ?? '38.224.231.4',
    mk2User: process.env.MK2_USER ?? process.env.ROUTEROS_MK2_USER ?? '',
    mk2Pass: process.env.MK2_PASS ?? process.env.ROUTEROS_MK2_PASSWORD ?? '',
    mk2Port: process.env.MK2_PORT ?? '443',
  };
}

function restRequest({ host, port, user, pass, insecure, path, method, body }) {
  const auth = Buffer.from(`${user}:${pass}`).toString('base64');
  const payload = body == null ? null : JSON.stringify(body);
  return new Promise((resolve, reject) => {
    const req = https.request(
      {
        host,
        port,
        path,
        method,
        rejectUnauthorized: !insecure,
        headers: {
          Authorization: `Basic ${auth}`,
          'Content-Type': 'application/json',
          ...(payload ? { 'Content-Length': Buffer.byteLength(payload) } : {}),
        },
      },
      (res) => {
        const chunks = [];
        res.on('data', (chunk) => chunks.push(chunk));
        res.on('end', () => {
          const text = Buffer.concat(chunks).toString('utf8');
          if (res.statusCode < 200 || res.statusCode >= 300) {
            reject(new Error(`REST ${method} ${path}: HTTP ${res.statusCode} ${text}`));
            return;
          }
          resolve(text ? JSON.parse(text) : null);
        });
      },
    );
    req.on('error', reject);
    if (payload) req.write(payload);
    req.end();
  });
}

export async function listSimpleQueues(config, insecure) {
  return restRequest({
    host: config.mk2Host,
    port: config.mk2Port,
    user: config.mk2User,
    pass: config.mk2Pass,
    insecure,
    path: '/rest/queue/simple/print',
    method: 'POST',
    body: { '.proplist': QUEUE_PROPLIST },
  });
}

export async function removeSimpleQueue(config, insecure, id) {
  return restRequest({
    host: config.mk2Host,
    port: config.mk2Port,
    user: config.mk2User,
    pass: config.mk2Pass,
    insecure,
    path: `/rest/queue/simple/${encodeURIComponent(id)}`,
    method: 'DELETE',
  });
}

export async function runPurge({ args, queues, remove }) {
  if (!args.tag) {
    return { selected: [], deleted: 0, dryRun: true, skipped: 'missing --tag' };
  }
  const selected = selectPurgeCandidates(queues, args.tag);
  if (args.dryRun || !args.apply) {
    return { selected, deleted: 0, dryRun: true };
  }
  let deleted = 0;
  for (const queue of selected) {
    const id = queue['.id'] ?? queue.id;
    if (!id) continue;
    await remove(id);
    deleted += 1;
  }
  return { selected, deleted, dryRun: false };
}

function printHelp() {
  console.log(`Uso: node scripts/mk2-purge-staging-queues.mjs --tag stg [--dry-run|--apply]

Lista /queue/simple y selecciona solo colas con comment env=<tag> o prefijo [<tag>].
Sin --tag no borra nada. --apply es obligatorio para borrar.
`);
}

async function main() {
  const args = parseArgs(process.argv);
  if (args.help) {
    printHelp();
    return;
  }
  let queues;
  if (args.queuesJson) {
    queues = JSON.parse(await readFile(args.queuesJson, 'utf8'));
  } else {
    const config = envConfig();
    if (!config.mk2User || !config.mk2Pass) {
      throw new Error('Faltan MK2_USER / MK2_PASS');
    }
    queues = await listSimpleQueues(config, args.insecure);
  }
  const result = await runPurge({
    args,
    queues,
    remove: (id) => removeSimpleQueue(envConfig(), args.insecure, id),
  });
  console.log(JSON.stringify(result, null, 2));
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
