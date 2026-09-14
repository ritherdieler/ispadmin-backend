const OCTET = /^\d{1,3}$/;

export function ipToLong(ip) {
  const raw = (ip ?? '').trim();
  if (!raw) return null;
  const parts = raw.split('.');
  if (parts.length !== 4) return null;
  let value = 0;
  for (const part of parts) {
    if (!OCTET.test(part)) return null;
    const octet = Number.parseInt(part, 10);
    if (octet > 255) return null;
    value = value * 256 + octet;
  }
  return value;
}

export function longToIp(value) {
  if (!Number.isInteger(value) || value < 0 || value > 4294967295) return null;
  return [
    Math.floor(value / 16777216) % 256,
    Math.floor(value / 65536) % 256,
    Math.floor(value / 256) % 256,
    value % 256,
  ].join('.');
}

export function parseCidr(cidr) {
  const raw = (cidr ?? '').trim();
  if (!raw) return null;
  const [ipPart, prefixPart] = raw.split('/');
  const base = ipToLong(ipPart);
  if (base === null) return null;
  const prefix = prefixPart === undefined ? 32 : Number.parseInt(prefixPart, 10);
  if (!Number.isInteger(prefix) || prefix < 0 || prefix > 32) return null;

  const size = 2 ** (32 - prefix);
  const network = Math.floor(base / size) * size;
  const broadcast = network + size - 1;
  const hasHostRange = size > 2;

  return {
    prefix,
    size,
    network,
    broadcast,
    networkIp: longToIp(network),
    broadcastIp: longToIp(broadcast),
    firstUsableIp: longToIp(hasHostRange ? network + 1 : network),
    lastUsableIp: longToIp(hasHostRange ? broadcast - 1 : broadcast),
  };
}

export function cidrContains(cidr, ip) {
  const block = parseCidr(cidr);
  const value = ipToLong(ip);
  if (!block || value === null) return false;
  return value >= block.network && value <= block.broadcast;
}

export function cidrsOverlap(a, b) {
  const left = parseCidr(a);
  const right = parseCidr(b);
  if (!left || !right) return false;
  return left.network <= right.broadcast && right.network <= left.broadcast;
}

const CONFLICT_SOURCES = ['addresses', 'routes', 'ips'];

function isDefaultRoute(cidr) {
  const block = parseCidr(cidr);
  return block !== null && block.prefix === 0;
}

export function findBlockConflicts(candidateCidr, sources = {}) {
  const conflicts = [];
  for (const source of CONFLICT_SOURCES) {
    for (const value of sources[source] ?? []) {
      const raw = (value ?? '').toString().trim();
      if (!raw) continue;
      if (!parseCidr(raw)) continue;
      if (isDefaultRoute(raw)) continue;
      if (!cidrsOverlap(candidateCidr, raw)) continue;
      conflicts.push({ source, value: raw });
    }
  }
  return { block: candidateCidr, free: conflicts.length === 0, conflicts };
}

export function indexSubscriptionsByIp(rows = []) {
  const index = new Map();
  for (const row of rows) {
    const ip = (row.ip ?? '').trim();
    if (!ip) continue;
    const existing = index.get(ip);
    if (!existing) {
      index.set(ip, row);
      continue;
    }
    const existingCancelled = (existing.service_status ?? existing.serviceStatus) === 'CANCELLED';
    const candidateCancelled = (row.service_status ?? row.serviceStatus) === 'CANCELLED';
    if (existingCancelled && !candidateCancelled) index.set(ip, row);
  }
  return index;
}

export function subdivideBlock(parentCidr, ranges = []) {
  const parent = parseCidr(parentCidr);
  const errors = [];
  const resolved = [];

  if (!parent) {
    return { valid: false, errors: [`Bloque padre invalido: ${parentCidr}`], ranges: [] };
  }

  for (const range of ranges) {
    const from = ipToLong(range.from);
    const to = ipToLong(range.to);
    const name = range.name ?? '(sin nombre)';

    if (from === null || to === null) {
      errors.push(`${name}: rango invalido ${range.from}-${range.to}`);
      continue;
    }
    if (from > to) {
      errors.push(`${name}: rango invertido ${range.from}-${range.to}`);
      continue;
    }
    if (from < parent.network || to > parent.broadcast) {
      errors.push(
        `${name}: ${range.from}-${range.to} cae fuera del bloque ${parentCidr}`,
      );
      continue;
    }
    resolved.push({ name, from, to, fromIp: range.from, toIp: range.to, size: to - from + 1 });
  }

  const sorted = [...resolved].sort((a, b) => a.from - b.from);
  for (let i = 1; i < sorted.length; i += 1) {
    const previous = sorted[i - 1];
    const current = sorted[i];
    if (current.from <= previous.to) {
      errors.push(`${current.name}: solapa con ${previous.name}`);
    }
  }

  return { valid: errors.length === 0, errors, ranges: resolved };
}

export function summarizeSecrets(secrets = [], activeSessions = []) {
  const byProfile = {};
  const addressCount = new Map();
  let disabled = 0;
  let withFixedAddress = 0;

  for (const secret of secrets) {
    const profile = secret.profile ?? '(sin perfil)';
    byProfile[profile] = (byProfile[profile] ?? 0) + 1;
    if (secret.disabled === 'true' || secret.disabled === true) disabled += 1;
    const address = (secret['remote-address'] ?? '').trim();
    if (address) {
      withFixedAddress += 1;
      addressCount.set(address, (addressCount.get(address) ?? 0) + 1);
    }
  }

  const activeByName = new Map(activeSessions.map((session) => [session.name, session]));
  const addressMismatches = [];
  for (const secret of secrets) {
    const session = activeByName.get(secret.name);
    if (!session) continue;
    const secretAddress = (secret['remote-address'] ?? '').trim();
    const sessionAddress = (session.address ?? '').trim();
    if (secretAddress && sessionAddress && secretAddress !== sessionAddress) {
      addressMismatches.push({ name: secret.name, secretAddress, sessionAddress });
    }
  }

  const withSession = secrets.filter((secret) => activeByName.has(secret.name)).length;
  const duplicatedAddresses = [...addressCount.entries()]
    .filter(([, count]) => count > 1)
    .map(([address]) => address)
    .sort();

  return {
    total: secrets.length,
    disabled,
    withFixedAddress,
    activeSessions: activeSessions.length,
    withoutSession: secrets.length - withSession,
    byProfile,
    duplicatedAddresses,
    addressMismatches,
  };
}

function speedsOf(plan) {
  if (!plan) return null;
  const download = Number(plan.download_speed ?? plan.downloadSpeed ?? 0);
  const upload = Number(plan.upload_speed ?? plan.uploadSpeed ?? 0);
  if (!Number.isFinite(download) || !Number.isFinite(upload)) return null;
  if (download <= 0 || upload <= 0) return null;
  return { download, upload };
}

export function expectedProfileName(plan) {
  const speeds = speedsOf(plan);
  if (!speeds) return null;
  return `GF-${speeds.download}-${speeds.upload}`;
}

export function planRateLimit(plan) {
  const speeds = speedsOf(plan);
  if (!speeds) return null;
  return `${speeds.upload}M/${speeds.download}M`;
}

function normalizeRate(value) {
  const raw = (value ?? '').trim();
  if (!raw) return '';
  return raw
    .split('/')
    .map((part) => {
      const match = /^(\d+(?:\.\d+)?)\s*([kKMG]?)$/.exec(part.trim());
      if (!match) return part.trim();
      const factor = { k: 1e3, K: 1e3, M: 1e6, G: 1e9 }[match[2]] ?? 1;
      return String(Math.round(Number.parseFloat(match[1]) * factor));
    })
    .join('/');
}

export function reconcileSecretProfiles(
  entries = [],
  plansBySubscription = new Map(),
  profilesByName = new Map(),
) {
  const aligned = [];
  const mismatched = [];
  const unknownProfile = [];
  const withoutPlan = [];

  for (const entry of entries) {
    const plan = plansBySubscription.get(entry.subscriptionId);
    if (!plan) {
      withoutPlan.push(entry);
      continue;
    }

    const profile = profilesByName.get(entry.profile);
    if (!profile) {
      unknownProfile.push(entry);
      continue;
    }

    const expectedRateLimit = planRateLimit(plan);
    const profileRateLimit = profile.rateLimit ?? profile['rate-limit'] ?? null;
    const row = {
      ...entry,
      expectedProfile: expectedProfileName(plan),
      expectedRateLimit,
      profileRateLimit,
    };

    if (expectedRateLimit && normalizeRate(profileRateLimit) === normalizeRate(expectedRateLimit)) {
      aligned.push(row);
    } else {
      mismatched.push(row);
    }
  }

  return { aligned, mismatched, unknownProfile, withoutPlan };
}

export function findDoubleLimited(secrets = [], queues = []) {
  const queuesByIp = new Map();
  for (const queue of queues) {
    const target = (queue.target ?? '').split('/')[0].trim();
    if (!target || target.startsWith('<')) continue;
    queuesByIp.set(target, queue);
  }

  const result = [];
  for (const secret of secrets) {
    const remoteAddress = (secret['remote-address'] ?? '').trim();
    if (!remoteAddress) continue;
    const queue = queuesByIp.get(remoteAddress);
    if (!queue) continue;
    result.push({
      name: secret.name,
      remoteAddress,
      profile: secret.profile ?? null,
      queueName: queue.name ?? null,
      queueMaxLimit: queue['max-limit'] ?? null,
    });
  }
  return result;
}

export function classifySecretsAgainstDb(secrets = [], subscriptionsByIp = new Map()) {
  const matchedActive = [];
  const matchedCancelled = [];
  const unknown = [];
  const withoutAddress = [];

  for (const secret of secrets) {
    const remoteAddress = (secret['remote-address'] ?? '').trim();
    if (!remoteAddress) {
      withoutAddress.push({ name: secret.name, profile: secret.profile ?? null });
      continue;
    }

    const subscription = subscriptionsByIp.get(remoteAddress);
    if (!subscription) {
      unknown.push({ name: secret.name, remoteAddress, profile: secret.profile ?? null });
      continue;
    }

    const entry = {
      name: secret.name,
      remoteAddress,
      profile: secret.profile ?? null,
      subscriptionId: subscription.id,
      serviceStatus: subscription.service_status ?? subscription.serviceStatus ?? null,
      installationType: subscription.installation_type ?? subscription.installationType ?? null,
    };

    if (entry.serviceStatus === 'CANCELLED') matchedCancelled.push(entry);
    else matchedActive.push(entry);
  }

  const reservedIps = unknown.map((entry) => entry.remoteAddress).sort();

  return { matchedActive, matchedCancelled, unknown, withoutAddress, reservedIps };
}
