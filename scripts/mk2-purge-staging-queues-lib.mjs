export function parseArgs(argv) {
  const flags = new Set();
  const options = {};
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--dry-run') flags.add('dryRun');
    else if (arg === '--apply') flags.add('apply');
    else if (arg === '--insecure') flags.add('insecure');
    else if (arg === '--help' || arg === '-h') flags.add('help');
    else if (arg === '--tag') {
      options.tag = argv[i + 1];
      i += 1;
    } else if (arg === '--queues-json') {
      options.queuesJson = argv[i + 1];
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
    insecure: flags.has('insecure'),
    help: flags.has('help'),
    tag: (options.tag ?? '').trim(),
    queuesJson: options.queuesJson ?? null,
  };
}

export function isTaggedQueue(queue, tag) {
  if (!tag) return false;
  const name = (queue.name ?? '').trim();
  const comment = (queue.comment ?? '').trim();
  const prefix = `[${tag}]`;
  return name.startsWith(`${prefix} `) || name === prefix || comment.split(/\s+/).includes(`env=${tag}`);
}

export function selectPurgeCandidates(queues, tag) {
  if (!tag) return [];
  return (queues ?? []).filter((queue) => isTaggedQueue(queue, tag));
}

export function isEnvTaggedQueue(queue) {
  const name = (queue.name ?? '').trim();
  const comment = (queue.comment ?? '').trim();
  return /^\[[a-zA-Z0-9]+\](\s|$)/.test(name) || /(?:^|\s)env=[a-zA-Z0-9]+(?:\s|$)/.test(comment);
}
