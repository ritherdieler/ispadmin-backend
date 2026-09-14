import test from 'node:test';
import assert from 'node:assert/strict';
import {
  isTaggedQueue,
  parseArgs,
  selectPurgeCandidates,
} from './mk2-purge-staging-queues-lib.mjs';

test('parseArgs defaults to dry-run', () => {
  const args = parseArgs(['node', 'script']);
  assert.equal(args.dryRun, true);
  assert.equal(args.apply, false);
  assert.equal(args.tag, '');
});

test('selectPurgeCandidates requires explicit tag', () => {
  const queues = [
    { '.id': '*1', name: '[stg] id:1, usuario:A', comment: 'env=stg' },
    { '.id': '*2', name: 'id:2, usuario:B', comment: 'FIBER' },
  ];
  assert.deepEqual(selectPurgeCandidates(queues, ''), []);
  assert.deepEqual(selectPurgeCandidates(queues, null), []);
});

test('selects only env=stg or [stg] prefix', () => {
  const queues = [
    { '.id': '*1', name: '[stg] id:1, usuario:A', comment: '' },
    { '.id': '*2', name: 'id:2, usuario:B', comment: 'env=stg' },
    { '.id': '*3', name: 'id:3, usuario:C', comment: 'FIBER' },
    { '.id': '*4', name: '[prod] id:4, usuario:D', comment: 'env=prod' },
  ];
  const selected = selectPurgeCandidates(queues, 'stg').map((q) => q['.id']);
  assert.deepEqual(selected, ['*1', '*2']);
});

test('does not select untagged production queues', () => {
  assert.equal(
    isTaggedQueue({ name: 'id:744, usuario:Judith', comment: 'FIBER' }, 'stg'),
    false,
  );
});
