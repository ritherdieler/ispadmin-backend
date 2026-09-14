export const QUEUE_NAME_TEMPLATE_FIBER =
  'id:%d, usuario:%s %s, lugar:%s, nap:%s, plan:%s, tipo:%s';
export const QUEUE_NAME_TEMPLATE_WIRELESS =
  'id:%d, usuario:%s %s, lugar:%s, plan:%s, tipo:%s';

const ID_PREFIX = /^id:(\d+)/;

export function subscriptionIdFromQueueName(name) {
  const trimmed = (name ?? '').trim();
  if (!trimmed) return null;
  const match = ID_PREFIX.exec(trimmed);
  return match ? Number.parseInt(match[1], 10) : null;
}

export function normalizeTargetIpCompat(target) {
  const value = (target ?? '').trim();
  if (!value) return null;
  return value.split('/')[0].trim() || null;
}

export function normalizeTargetIp(target) {
  return normalizeTargetIpCompat(target);
}

export function isStaticIpTarget(target) {
  const ip = normalizeTargetIpCompat(target);
  if (!ip) return false;
  if (ip.startsWith('<')) return false;
  return /^\d{1,3}(\.\d{1,3}){3}$/.test(ip);
}

const RATE_SUFFIX = { k: 1e3, K: 1e3, M: 1e6, G: 1e9 };

function rateToBits(value) {
  const raw = (value ?? '').trim();
  if (!raw) return '';
  const match = /^(\d+(?:\.\d+)?)\s*([kKMG]?)$/.exec(raw);
  if (!match) return raw;
  const factor = RATE_SUFFIX[match[2]] ?? 1;
  return String(Math.round(Number.parseFloat(match[1]) * factor));
}

export function normalizeMaxLimit(value) {
  const raw = (value ?? '').trim();
  if (!raw) return '';
  return raw.split('/').map(rateToBits).join('/');
}

export function isEnvTaggedQueue(queue) {
  const name = (queue?.name ?? '').trim();
  const comment = (queue?.comment ?? '').trim();
  return /^\[[a-zA-Z0-9]+\](\s|$)/.test(name) || /(?:^|\s)env=[a-zA-Z0-9]+(?:\s|$)/.test(comment);
}

export function buildQueueName(sub) {
  const id = sub.id ?? 0;
  const first = sub.first_name ?? sub.firstName ?? '';
  const last = sub.last_name ?? sub.lastName ?? '';
  const place = sub.place_name ?? sub.placeName ?? '';
  const nap = sub.nap_code ?? sub.napCode ?? '';
  const planName = sub.plan_name ?? sub.planName ?? '';
  const planType = sub.plan_type ?? sub.planType ?? '';
  const installationType = sub.installation_type ?? sub.installationType ?? '';

  if (installationType === 'FIBER') {
    return formatTemplate(QUEUE_NAME_TEMPLATE_FIBER, [
      id,
      first,
      last,
      place,
      nap,
      planName,
      planType,
    ]);
  }
  return formatTemplate(QUEUE_NAME_TEMPLATE_WIRELESS, [
    id,
    first,
    last,
    place,
    planName,
    planType,
  ]);
}

function formatTemplate(template, values) {
  let index = 0;
  return template.replace(/%[ds]/g, () => {
    const value = values[index++];
    return value == null ? '' : String(value);
  });
}

export function formatMaxLimit(sub) {
  const upload = sub.upload_speed ?? sub.uploadSpeed ?? 0;
  const download = sub.download_speed ?? sub.downloadSpeed ?? 0;
  return `${upload}M/${download}M`;
}

export function buildExpectedQueue(sub) {
  return {
    subscriptionId: sub.id,
    ip: (sub.ip ?? '').trim(),
    name: buildQueueName(sub),
    maxLimit: formatMaxLimit(sub),
    target: (sub.ip ?? '').trim(),
  };
}

export function indexQueuesByIp(queues) {
  const byIp = new Map();
  for (const queue of queues) {
    if (isEnvTaggedQueue(queue)) continue;
    if (!isStaticIpTarget(queue.target)) continue;
    const ip = normalizeTargetIpCompat(queue.target);
    if (!ip) continue;
    if (!byIp.has(ip)) byIp.set(ip, []);
    byIp.get(ip).push(queue);
  }
  return byIp;
}

export function indexCancelledSubscriptions(cancelled) {
  const byId = new Map();
  const byIp = new Map();
  for (const sub of cancelled ?? []) {
    if (sub?.id != null) byId.set(Number(sub.id), sub);
    const ip = normalizeTargetIpCompat(sub?.ip);
    if (ip && !byIp.has(ip)) byIp.set(ip, sub);
  }
  return { byId, byIp };
}

export function indexQueuesByOwnerId(queuesByIp) {
  const byOwner = new Map();
  for (const [ip, matches] of queuesByIp.entries()) {
    for (const queue of matches) {
      const ownerId = subscriptionIdFromQueueName(queue.name);
      if (ownerId == null) continue;
      if (!byOwner.has(ownerId)) byOwner.set(ownerId, []);
      byOwner.get(ownerId).push({ ip, queue });
    }
  }
  return byOwner;
}

export function diffSubscriptionsAgainstQueues(subscriptions, queues, options = {}) {
  const queuesByIp = indexQueuesByIp(queues);
  const queuesByOwner = indexQueuesByOwnerId(queuesByIp);
  const cancelled = indexCancelledSubscriptions(options.cancelled);
  const expectedIps = new Set();
  const actions = [];
  const stats = {
    added: 0,
    updated: 0,
    retargeted: 0,
    reclaimed: 0,
    conflicts: 0,
    cancelledQueues: 0,
    orphans: 0,
    skipped: 0,
    unchanged: 0,
    pppoeIgnored: 0,
  };

  for (const queue of queues) {
    if (queue.target && !isStaticIpTarget(queue.target)) {
      stats.pppoeIgnored += 1;
    }
  }

  for (const sub of subscriptions) {
    const ip = (sub.ip ?? '').trim();
    if (ip) expectedIps.add(ip);
  }

  const retargetedQueueIds = new Set();

  for (const sub of subscriptions) {
    const expected = buildExpectedQueue(sub);
    const ip = expected.ip;
    if (!ip) {
      stats.skipped += 1;
      actions.push({
        action: 'SKIP',
        subscriptionId: sub.id,
        ip: '',
        message: 'Sin IP en BD',
      });
      continue;
    }

    const matches = queuesByIp.get(ip) ?? [];
    if (matches.length === 0) {
      const movable = (queuesByOwner.get(sub.id) ?? []).filter(
        (entry) => entry.ip !== ip && !expectedIps.has(entry.ip),
      );
      if (movable.length === 1) {
        const { queue, ip: oldIp } = movable[0];
        const queueId = queue['.id'] ?? queue.id;
        retargetedQueueIds.add(queueId);
        stats.retargeted += 1;
        actions.push({
          action: 'RETARGET',
          subscriptionId: sub.id,
          ip,
          queueId,
          before: { name: queue.name, maxLimit: queue['max-limit'], target: queue.target },
          expected,
          message: `Cola del abonado en IP anterior ${oldIp}`,
        });
        continue;
      }
      stats.added += 1;
      actions.push({
        action: 'ADD',
        subscriptionId: sub.id,
        ip,
        expected,
        message: 'Cola no existe en MK2',
      });
      continue;
    }

    const queue = matches[matches.length - 1];
    const ownerId = subscriptionIdFromQueueName(queue.name);
    const queueId = queue['.id'] ?? queue.id;

    if (ownerId != null && ownerId !== sub.id) {
      stats.conflicts += 1;
      actions.push({
        action: 'CONFLICT',
        subscriptionId: sub.id,
        ip,
        queueId,
        ownerId,
        before: { name: queue.name, maxLimit: queue['max-limit'] },
        expected,
        message: `IP en cola de suscripción ${ownerId}`,
      });
      continue;
    }

    const nameDrift = (queue.name ?? '') !== expected.name;
    const limitDrift =
      normalizeMaxLimit(queue['max-limit']) !== normalizeMaxLimit(expected.maxLimit);

    if (ownerId == null) {
      stats.reclaimed += 1;
      actions.push({
        action: 'RECLAIM',
        subscriptionId: sub.id,
        ip,
        queueId,
        before: { name: queue.name, maxLimit: queue['max-limit'] },
        expected,
        message: 'Cola huérfana de nombre (sin id:)',
      });
      continue;
    }

    if (nameDrift || limitDrift) {
      stats.updated += 1;
      actions.push({
        action: 'UPDATE',
        subscriptionId: sub.id,
        ip,
        queueId,
        before: { name: queue.name, maxLimit: queue['max-limit'] },
        expected,
        message: [nameDrift ? 'name' : null, limitDrift ? 'max-limit' : null]
          .filter(Boolean)
          .join(', '),
      });
      continue;
    }

    stats.unchanged += 1;
    actions.push({
      action: 'UNCHANGED',
      subscriptionId: sub.id,
      ip,
      queueId,
    });
  }

  for (const [ip, matches] of queuesByIp.entries()) {
    if (expectedIps.has(ip)) continue;
    for (const queue of matches) {
      const queueId = queue['.id'] ?? queue.id;
      if (retargetedQueueIds.has(queueId)) continue;
      const before = { name: queue.name, maxLimit: queue['max-limit'], target: queue.target };
      const ownerId = subscriptionIdFromQueueName(queue.name);
      const cancelledSub =
        (ownerId != null ? cancelled.byId.get(ownerId) : null) ?? cancelled.byIp.get(ip) ?? null;

      if (cancelledSub) {
        stats.cancelledQueues += 1;
        actions.push({
          action: 'DELETE_CANCELLED',
          subscriptionId: Number(cancelledSub.id),
          ip,
          queueId,
          before,
          message:
            ownerId != null && cancelled.byId.has(ownerId)
              ? `Cola de suscripción CANCELLED ${ownerId}`
              : `IP de suscripción CANCELLED ${cancelledSub.id}`,
        });
        continue;
      }

      stats.orphans += 1;
      actions.push({
        action: 'ORPHAN',
        ip,
        queueId,
        before,
        message: 'Cola en MK2 sin suscripción en BD',
      });
    }
  }

  return { actions, stats };
}

export const SUBSCRIPTIONS_SQL = `
SELECT
  s.id,
  s.ip,
  s.first_name,
  s.last_name,
  s.installation_type,
  s.service_status,
  s.host_device_id,
  p.name AS plan_name,
  p.type AS plan_type,
  p.upload_speed,
  p.download_speed,
  pl.name AS place_name,
  n.code AS nap_code
FROM subscription s
LEFT JOIN plan p ON p.id = s.plan_id
LEFT JOIN place pl ON pl.id = s.place_id
LEFT JOIN nap_box n ON n.id = s.napbox_id
WHERE s.host_device_id = ?
  AND s.service_status IN ('ACTIVE', 'CUT_OFF', 'SUSPENDED')
  AND s.ip IS NOT NULL AND s.ip <> ''
  AND (s.installation_type IS NULL OR s.installation_type <> 'ONLY_TV_FIBER')
ORDER BY s.id
`;

export const CANCELLED_SQL = `
SELECT
  s.id,
  s.ip,
  s.first_name,
  s.last_name,
  s.service_status,
  s.host_device_id
FROM subscription s
WHERE s.service_status = 'CANCELLED'
  AND s.ip IS NOT NULL AND s.ip <> ''
  AND NOT EXISTS (
    SELECT 1 FROM subscription o
    WHERE o.ip = s.ip AND o.service_status <> 'CANCELLED'
  )
ORDER BY s.id
`;
