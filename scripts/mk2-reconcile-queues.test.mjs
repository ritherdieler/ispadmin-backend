import test from 'node:test';
import assert from 'node:assert/strict';
import {
  QUEUE_NAME_TEMPLATE_FIBER,
  QUEUE_NAME_TEMPLATE_WIRELESS,
  buildQueueName,
  formatMaxLimit,
  isStaticIpTarget,
  normalizeTargetIp,
  normalizeMaxLimit,
  diffSubscriptionsAgainstQueues,
} from './mk2-reconcile-queues-lib.mjs';
import {
  applyActions,
  buildMysqlArgs,
  parseArgs,
  parseMysqlTabOutput,
  runReconcile,
} from './mk2-reconcile-queues.mjs';

test('buildQueueName FIBER matches backend template', () => {
  const name = buildQueueName({
    id: 42,
    first_name: 'Juan',
    last_name: 'Pérez',
    place_name: 'Centro',
    nap_code: 'NAP-01',
    plan_name: '100M',
    plan_type: 'RESIDENTIAL',
    installation_type: 'FIBER',
  });
  assert.equal(
    name,
    'id:42, usuario:Juan Pérez, lugar:Centro, nap:NAP-01, plan:100M, tipo:RESIDENTIAL',
  );
  assert.ok(name.startsWith(QUEUE_NAME_TEMPLATE_FIBER.split('%')[0]));
});

test('buildQueueName WIRELESS omits nap', () => {
  const name = buildQueueName({
    id: 7,
    first_name: 'Ana',
    last_name: 'López',
    place_name: 'Norte',
    plan_name: '50M',
    plan_type: 'BUSINESS',
    installation_type: 'WIRELESS',
  });
  assert.equal(name, 'id:7, usuario:Ana López, lugar:Norte, plan:50M, tipo:BUSINESS');
  assert.ok(!name.includes('nap:'));
  assert.ok(name.startsWith(QUEUE_NAME_TEMPLATE_WIRELESS.split('%')[0]));
});

test('formatMaxLimit uses upload/download from plan', () => {
  assert.equal(formatMaxLimit({ upload_speed: 10, download_speed: 100 }), '10M/100M');
});

test('normalizeTargetIp strips /32 suffix', () => {
  assert.equal(normalizeTargetIp('192.168.26.65/32'), '192.168.26.65');
  assert.equal(normalizeTargetIp('192.168.30.10'), '192.168.30.10');
});

test('diff detects missing queue as ADD', () => {
  const subs = [
    {
      id: 1,
      ip: '192.168.26.65',
      first_name: 'A',
      last_name: 'B',
      place_name: 'X',
      nap_code: 'N1',
      plan_name: 'P',
      plan_type: 'T',
      upload_speed: 5,
      download_speed: 50,
      installation_type: 'FIBER',
    },
  ];
  const { actions, stats } = diffSubscriptionsAgainstQueues(subs, []);
  assert.equal(stats.added, 1);
  assert.equal(actions[0].action, 'ADD');
});

test('diff detects name/limit drift as UPDATE', () => {
  const subs = [
    {
      id: 1,
      ip: '192.168.26.65',
      first_name: 'A',
      last_name: 'B',
      place_name: 'X',
      nap_code: 'N1',
      plan_name: 'P',
      plan_type: 'T',
      upload_speed: 5,
      download_speed: 50,
      installation_type: 'FIBER',
    },
  ];
  const queues = [
    {
      '.id': '*1',
      name: 'id:1, usuario:A B, lugar:X, nap:N1, plan:P, tipo:T',
      target: '192.168.26.65/32',
      'max-limit': '5M/40M',
    },
  ];
  const { actions, stats } = diffSubscriptionsAgainstQueues(subs, queues);
  assert.equal(stats.updated, 1);
  assert.equal(actions[0].action, 'UPDATE');
});

test('diff detects owner conflict', () => {
  const subs = [{ id: 2, ip: '192.168.26.65', installation_type: 'FIBER', upload_speed: 1, download_speed: 1 }];
  const queues = [
    {
      '.id': '*9',
      name: 'id:99, usuario:Otro Cliente, lugar:Y, nap:Z, plan:P, tipo:T',
      target: '192.168.26.65',
      'max-limit': '1M/1M',
    },
  ];
  const { actions, stats } = diffSubscriptionsAgainstQueues(subs, queues);
  assert.equal(stats.conflicts, 1);
  assert.equal(actions[0].action, 'CONFLICT');
});

test('diff ignores env-tagged staging queues', () => {
  const { actions, stats } = diffSubscriptionsAgainstQueues(
    [],
    [
      {
        '.id': '*stg',
        name: '[stg] id:9, usuario:Staging',
        target: '192.168.250.10/32',
        comment: 'env=stg',
        'max-limit': '10M/10M',
      },
    ],
  );
  assert.equal(stats.orphans, 0);
  assert.equal(actions.length, 0);
});

test('diff detects orphan MK2 queue', () => {
  const subs = [];
  const queues = [
    {
      '.id': '*55',
      name: 'legacy queue',
      target: '192.168.30.99/32',
      'max-limit': '5M/50M',
    },
  ];
  const { actions, stats } = diffSubscriptionsAgainstQueues(subs, queues);
  assert.equal(stats.orphans, 1);
  assert.equal(actions[0].action, 'ORPHAN');
});

test('isStaticIpTarget accepts any IPv4 target and rejects pppoe or empty', () => {
  assert.equal(isStaticIpTarget('192.168.30.1/32'), true);
  assert.equal(isStaticIpTarget('192.169.22.170/32'), true);
  assert.equal(isStaticIpTarget('10.20.0.5'), true);
  assert.equal(isStaticIpTarget('<pppoe-cliente>'), false);
  assert.equal(isStaticIpTarget(''), false);
  assert.equal(isStaticIpTarget('192.168.30.0/24'), true);
});

test('diff does not ADD when queue exists on a non 192.168 target', () => {
  const subs = [
    {
      id: 840,
      ip: '192.169.22.170',
      first_name: 'P',
      last_name: 'T',
      place_name: 'la villa',
      plan_name: 'w50',
      plan_type: 'T',
      upload_speed: 20,
      download_speed: 20,
      installation_type: 'WIRELESS',
    },
  ];
  const queues = [
    {
      '.id': '*4AB',
      name: 'id:840, usuario:P T, lugar:la villa, plan:w50, tipo:T',
      target: '192.169.22.170/32',
      'max-limit': '20000000/20000000',
    },
  ];
  const { stats } = diffSubscriptionsAgainstQueues(subs, queues);
  assert.equal(stats.added, 0);
  assert.equal(stats.unchanged, 1);
});

test('diff retargets existing queue when subscription changed IP', () => {
  const subs = [
    {
      id: 665,
      ip: '192.168.93.82',
      first_name: 'H',
      last_name: 'C',
      place_name: 'san jeronimo',
      plan_name: 'w70',
      plan_type: 'T',
      upload_speed: 30,
      download_speed: 30,
      installation_type: 'WIRELESS',
    },
  ];
  const queues = [
    {
      '.id': '*44F',
      name: 'id:665, usuario:H C, lugar:san jeronimo, plan:w70, tipo:T',
      target: '192.168.221.51/32',
      'max-limit': '30000000/30000000',
    },
  ];
  const { actions, stats } = diffSubscriptionsAgainstQueues(subs, queues);
  assert.equal(stats.added, 0);
  assert.equal(stats.orphans, 0);
  assert.equal(stats.retargeted, 1);
  assert.equal(actions[0].action, 'RETARGET');
  assert.equal(actions[0].queueId, '*44F');
  assert.equal(actions[0].expected.target, '192.168.93.82');
});

test('diff keeps ADD when the id queue belongs to another expected IP', () => {
  const subs = [
    { id: 10, ip: '192.168.30.10', installation_type: 'FIBER', upload_speed: 1, download_speed: 1 },
    { id: 11, ip: '192.168.30.11', installation_type: 'FIBER', upload_speed: 1, download_speed: 1 },
  ];
  const queues = [
    {
      '.id': '*A',
      name: 'id:10, usuario: , lugar:, nap:, plan:, tipo:FIBER',
      target: '192.168.30.11/32',
      'max-limit': '1M/1M',
    },
  ];
  const { stats } = diffSubscriptionsAgainstQueues(subs, queues);
  assert.equal(stats.retargeted, 0);
  assert.equal(stats.added, 1);
  assert.equal(stats.conflicts, 1);
});

test('normalizeMaxLimit equates RouterOS bits with M suffix', () => {
  assert.equal(normalizeMaxLimit('200M/200M'), normalizeMaxLimit('200000000/200000000'));
  assert.equal(normalizeMaxLimit('5M/50M'), '5000000/50000000');
  assert.notEqual(normalizeMaxLimit('5M/40M'), normalizeMaxLimit('5M/50M'));
});

test('diff does not UPDATE when max-limit only differs in format', () => {
  const subs = [
    {
      id: 1,
      ip: '192.168.26.65',
      first_name: 'A',
      last_name: 'B',
      place_name: 'X',
      nap_code: 'N1',
      plan_name: 'P',
      plan_type: 'T',
      upload_speed: 200,
      download_speed: 200,
      installation_type: 'FIBER',
    },
  ];
  const queues = [
    {
      '.id': '*1',
      name: 'id:1, usuario:A B, lugar:X, nap:N1, plan:P, tipo:T',
      target: '192.168.26.65/32',
      'max-limit': '200000000/200000000',
    },
  ];
  const { actions, stats } = diffSubscriptionsAgainstQueues(subs, queues);
  assert.equal(stats.updated, 0);
  assert.equal(stats.unchanged, 1);
  assert.equal(actions[0].action, 'UNCHANGED');
});

test('diff marks queue of CANCELLED subscription as DELETE_CANCELLED', () => {
  const queues = [
    {
      '.id': '*70',
      name: 'id:70, usuario:Ex Cliente, lugar:X, nap:N1, plan:P, tipo:T',
      target: '192.168.30.70/32',
      'max-limit': '5M/50M',
    },
  ];
  const { actions, stats } = diffSubscriptionsAgainstQueues([], queues, {
    cancelled: [{ id: 70, ip: '192.168.30.70' }],
  });
  assert.equal(stats.cancelledQueues, 1);
  assert.equal(stats.orphans, 0);
  assert.equal(actions[0].action, 'DELETE_CANCELLED');
  assert.equal(actions[0].queueId, '*70');
  assert.equal(actions[0].subscriptionId, 70);
});

test('diff matches CANCELLED by queue id prefix even when IP moved', () => {
  const queues = [
    {
      '.id': '*71',
      name: 'id:71, usuario:Ex Cliente, lugar:X, nap:N1, plan:P, tipo:T',
      target: '192.168.30.200/32',
      'max-limit': '5M/50M',
    },
  ];
  const { actions, stats } = diffSubscriptionsAgainstQueues([], queues, {
    cancelled: [{ id: 71, ip: '192.168.30.71' }],
  });
  assert.equal(stats.cancelledQueues, 1);
  assert.equal(actions[0].action, 'DELETE_CANCELLED');
});

test('diff keeps ORPHAN when no CANCELLED match', () => {
  const queues = [
    {
      '.id': '*55',
      name: 'legacy queue',
      target: '192.168.30.99/32',
      'max-limit': '5M/50M',
    },
  ];
  const { actions, stats } = diffSubscriptionsAgainstQueues([], queues, {
    cancelled: [{ id: 70, ip: '192.168.30.70' }],
  });
  assert.equal(stats.cancelledQueues, 0);
  assert.equal(stats.orphans, 1);
  assert.equal(actions[0].action, 'ORPHAN');
});

test('diff keeps CONFLICT when active subscription reuses IP of cancelled queue', () => {
  const subs = [
    { id: 2, ip: '192.168.30.70', installation_type: 'FIBER', upload_speed: 1, download_speed: 1 },
  ];
  const queues = [
    {
      '.id': '*70',
      name: 'id:70, usuario:Ex Cliente, lugar:X, nap:N1, plan:P, tipo:T',
      target: '192.168.30.70/32',
      'max-limit': '5M/50M',
    },
  ];
  const { actions, stats } = diffSubscriptionsAgainstQueues(subs, queues, {
    cancelled: [{ id: 70, ip: '192.168.30.70' }],
  });
  assert.equal(stats.conflicts, 1);
  assert.equal(stats.cancelledQueues, 0);
  assert.equal(actions[0].action, 'CONFLICT');
});

test('diff reclaims queue without id prefix', () => {
  const subs = [
    {
      id: 3,
      ip: '192.168.26.70',
      first_name: 'C',
      last_name: 'D',
      place_name: 'Z',
      nap_code: 'N2',
      plan_name: 'P2',
      plan_type: 'T2',
      upload_speed: 2,
      download_speed: 20,
      installation_type: 'FIBER',
    },
  ];
  const queues = [
    {
      '.id': '*3',
      name: 'cliente viejo',
      target: '192.168.26.70',
      'max-limit': '2M/20M',
    },
  ];
  const { actions, stats } = diffSubscriptionsAgainstQueues(subs, queues);
  assert.equal(stats.reclaimed, 1);
  assert.equal(actions[0].action, 'RECLAIM');
});

test('dry-run does not mutate MK2', async () => {
  const calls = [];
  const client = {
    async addQueue(expected) {
      calls.push(['add', expected]);
    },
    async patchQueue(id, fields) {
      calls.push(['patch', id, fields]);
    },
    async removeQueue(id) {
      calls.push(['remove', id]);
    },
  };

  const actions = [
    {
      action: 'ADD',
      expected: { name: 'n', target: '192.168.26.1', maxLimit: '1M/1M' },
    },
    {
      action: 'UPDATE',
      queueId: '*1',
      expected: { name: 'n2', maxLimit: '2M/2M' },
    },
  ];

  await applyActions(client, actions, { dryRun: true });
  assert.equal(calls.length, 0);
});

test('apply executes ADD PATCH RECLAIM', async () => {
  const calls = [];
  const client = {
    async addQueue(expected) {
      calls.push(['add', expected]);
    },
    async patchQueue(id, fields) {
      calls.push(['patch', id, fields]);
    },
    async removeQueue(id) {
      calls.push(['remove', id]);
    },
  };

  const actions = [
    { action: 'ADD', expected: { name: 'n', target: '1.1.1.1', maxLimit: '1M/1M' } },
    {
      action: 'UPDATE',
      queueId: '*2',
      expected: { name: 'n2', maxLimit: '2M/2M' },
    },
    {
      action: 'RECLAIM',
      queueId: '*3',
      expected: { name: 'n3', target: '3.3.3.3', maxLimit: '3M/3M' },
    },
    { action: 'CONFLICT' },
    { action: 'ORPHAN' },
  ];

  const { applied } = await applyActions(client, actions, { dryRun: false });
  assert.equal(applied.added, 1);
  assert.equal(applied.updated, 1);
  assert.equal(applied.reclaimed, 1);
  assert.equal(calls.length, 4);
});

test('apply patches target on RETARGET instead of recreating', async () => {
  const calls = [];
  const client = {
    async addQueue() {
      calls.push(['add']);
    },
    async patchQueue(id, fields) {
      calls.push(['patch', id, fields]);
    },
    async removeQueue() {
      calls.push(['remove']);
    },
  };
  const actions = [
    {
      action: 'RETARGET',
      queueId: '*44F',
      expected: { name: 'n', target: '192.168.93.82', maxLimit: '30M/30M' },
    },
  ];
  const { applied } = await applyActions(client, actions, { dryRun: false });
  assert.equal(applied.retargeted, 1);
  assert.deepEqual(calls, [
    ['patch', '*44F', { name: 'n', target: '192.168.93.82', 'max-limit': '30M/30M' }],
  ]);
});

test('apply never removes ORPHAN queues', async () => {
  const calls = [];
  const client = {
    async addQueue() {},
    async patchQueue() {},
    async removeQueue(id) {
      calls.push(['remove', id]);
    },
  };

  const actions = [{ action: 'ORPHAN', queueId: '*55' }];

  await applyActions(client, actions, { dryRun: false, deleteCancelled: true });
  assert.equal(calls.length, 0);
});

test('apply removes DELETE_CANCELLED only with deleteCancelled', async () => {
  const calls = [];
  const client = {
    async addQueue() {},
    async patchQueue() {},
    async removeQueue(id) {
      calls.push(['remove', id]);
    },
  };
  const actions = [{ action: 'DELETE_CANCELLED', queueId: '*70', subscriptionId: 70 }];

  const skipped = await applyActions(client, actions, { dryRun: false });
  assert.equal(calls.length, 0);
  assert.equal(skipped.applied.deletedCancelled, 0);

  const done = await applyActions(client, actions, { dryRun: false, deleteCancelled: true });
  assert.deepEqual(calls, [['remove', '*70']]);
  assert.equal(done.applied.deletedCancelled, 1);
});

test('parseArgs defaults to dry-run', () => {
  const opts = parseArgs(['node', 'script.mjs']);
  assert.equal(opts.dryRun, true);
  assert.equal(opts.apply, false);
  assert.equal(opts.deleteCancelled, false);
});

test('parseArgs reads --only list', () => {
  const opts = parseArgs(['node', 'script.mjs', '--apply', '--only', 'ADD,UPDATE']);
  assert.deepEqual(opts.only, ['ADD', 'UPDATE']);
  assert.equal(parseArgs(['node', 'script.mjs']).only, null);
});

test('apply honours --only filter', async () => {
  const calls = [];
  const client = {
    async addQueue() {
      calls.push('add');
    },
    async patchQueue() {
      calls.push('patch');
    },
    async removeQueue() {
      calls.push('remove');
    },
  };
  const actions = [
    { action: 'ADD', expected: { name: 'n', target: '1.1.1.1', maxLimit: '1M/1M' } },
    { action: 'RECLAIM', queueId: '*3', expected: { name: 'n3', target: '3.3.3.3', maxLimit: '3M/3M' } },
  ];
  const { applied } = await applyActions(client, actions, { dryRun: false, only: ['ADD'] });
  assert.deepEqual(calls, ['add']);
  assert.equal(applied.reclaimed, 0);
});

test('apply skips queue ids listed in --skip-queue-ids', async () => {
  const calls = [];
  const client = {
    async addQueue() {},
    async patchQueue() {},
    async removeQueue(id) {
      calls.push(id);
    },
  };
  const actions = [
    { action: 'DELETE_CANCELLED', queueId: '*113', subscriptionId: 833 },
    { action: 'DELETE_CANCELLED', queueId: '*3', subscriptionId: 1898 },
  ];
  const opts = parseArgs(['node', 'script.mjs', '--apply', '--skip-queue-ids', '*113']);
  assert.deepEqual(opts.skipQueueIds, ['*113']);
  const { applied } = await applyActions(client, actions, {
    dryRun: false,
    deleteCancelled: true,
    skipQueueIds: opts.skipQueueIds,
  });
  assert.deepEqual(calls, ['*3']);
  assert.equal(applied.deletedCancelled, 1);
});

test('parseArgs reads --delete-cancelled', () => {
  const opts = parseArgs(['node', 'script.mjs', '--apply', '--delete-cancelled']);
  assert.equal(opts.apply, true);
  assert.equal(opts.deleteCancelled, true);
});

test('buildMysqlArgs keeps user and database in order and hides password', () => {
  const args = buildMysqlArgs(
    {
      mysqlHost: '127.0.0.1',
      mysqlPort: '13306',
      mysqlUser: 'root',
      mysqlPassword: 's3cret',
      mysqlDatabase: 'ispadmin',
    },
    'SELECT 1',
  );
  assert.equal(args[args.indexOf('-u') + 1], 'root');
  assert.ok(args.includes('ispadmin'));
  assert.equal(args[args.indexOf('-e') + 1], 'SELECT 1');
  assert.ok(!args.some((arg) => arg.includes('s3cret')));
});

test('parseMysqlTabOutput maps rows', () => {
  const stdout = 'id\tip\tupload_speed\n1\t192.168.26.1\t10\n';
  const rows = parseMysqlTabOutput(stdout);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].id, 1);
  assert.equal(rows[0].ip, '192.168.26.1');
  assert.equal(rows[0].upload_speed, 10);
});

test('runReconcile offline json mode', async () => {
  const subs = [
    {
      id: 1,
      ip: '192.168.26.65',
      first_name: 'A',
      last_name: 'B',
      place_name: 'X',
      nap_code: 'N1',
      plan_name: 'P',
      plan_type: 'T',
      upload_speed: 5,
      download_speed: 50,
      installation_type: 'FIBER',
    },
  ];
  const report = await runReconcile(
    { dryRun: true, hostDeviceId: 8, subscriptionsJson: 'x', queuesJson: 'y' },
    {
      loadSubscriptions: async () => subs,
      loadQueues: async () => [],
      client: {
        async addQueue() {},
        async patchQueue() {},
        async removeQueue() {},
      },
    },
  );
  assert.equal(report.stats.added, 1);
  assert.equal(report.mode, 'dry-run');
});

test('runReconcile reports cancelled queues without touching MK2 in dry-run', async () => {
  const calls = [];
  const report = await runReconcile(
    { dryRun: true, hostDeviceId: 8, deleteCancelled: true },
    {
      loadSubscriptions: async () => [],
      loadCancelled: async () => [{ id: 70, ip: '192.168.30.70' }],
      loadQueues: async () => [
        {
          '.id': '*70',
          name: 'id:70, usuario:Ex Cliente',
          target: '192.168.30.70/32',
          'max-limit': '5M/50M',
        },
      ],
      client: {
        async addQueue() {},
        async patchQueue() {},
        async removeQueue(id) {
          calls.push(id);
        },
      },
    },
  );
  assert.equal(report.stats.cancelledQueues, 1);
  assert.equal(calls.length, 0);
});
