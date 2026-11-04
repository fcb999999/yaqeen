// اختبارات قواعد أمان Firestore (firebase/firestore.rules) على محاكي Firebase.
import { readFileSync } from 'node:fs';
import { before, after, beforeEach, describe, it } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, deleteDoc, collection, getDocs } from 'firebase/firestore';

const RULES = readFileSync(new URL('../../firebase/firestore.rules', import.meta.url), 'utf8');
let env;

const now = Date.now();
const validDoc = (uid, id, over = {}) => ({
  uid, id, updatedAt: now, deviceId: 'dev-A', opId: 'op-1', deleted: false,
  date: '2026-11-04', prayer: 'FAJR', status: 'MOSQUE', ...over,
});

before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-yaqeen', firestore: { rules: RULES } });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

const alice = () => env.authenticatedContext('alice').firestore();
const bob = () => env.authenticatedContext('bob').firestore();
const anon = () => env.unauthenticatedContext().firestore();
const path = (uid, coll, id) => `users/${uid}/${coll}/${id}`;

describe('user isolation', () => {
  it('owner can create and read own document', async () => {
    const id = '2026-11-04_FAJR';
    await assertSucceeds(setDoc(doc(alice(), path('alice', 'prayers_tracking', id)), validDoc('alice', id)));
    const snap = await assertSucceeds(getDoc(doc(alice(), path('alice', 'prayers_tracking', id))));
    if (!snap.exists()) throw new Error('should exist');
  });

  it('another user can neither read nor write', async () => {
    const id = '2026-11-04_FAJR';
    await env.withSecurityRulesDisabled(async (c) => setDoc(doc(c.firestore(), path('alice', 'prayers_tracking', id)), validDoc('alice', id)));
    await assertFails(getDoc(doc(bob(), path('alice', 'prayers_tracking', id))));
    await assertFails(setDoc(doc(bob(), path('alice', 'prayers_tracking', id)), validDoc('alice', id)));
    await assertFails(getDocs(collection(bob(), `users/alice/prayers_tracking`)));
  });

  it('unauthenticated access is denied', async () => {
    const id = 'x';
    await assertFails(getDoc(doc(anon(), path('alice', 'prayers_tracking', id))));
    await assertFails(setDoc(doc(anon(), path('alice', 'prayers_tracking', id)), validDoc('alice', id)));
  });

  it('cannot write a document claiming a different uid', async () => {
    const id = 'a1';
    await assertFails(setDoc(doc(alice(), path('alice', 'prayers_tracking', id)), validDoc('bob', id)));
  });
});

describe('document validation', () => {
  it('rejects id that does not match the document key', async () => {
    await assertFails(setDoc(doc(alice(), path('alice', 'prayers_tracking', 'k1')), validDoc('alice', 'other')));
  });
  it('rejects missing/invalid updatedAt, deviceId, opId, deleted', async () => {
    const id = 'k2';
    for (const bad of [{ updatedAt: 'x' }, { updatedAt: 0 }, { updatedAt: -5 }, { deviceId: 5 }, { opId: null }, { deleted: 'no' }]) {
      await assertFails(setDoc(doc(alice(), path('alice', 'prayers_tracking', id)), validDoc('alice', id, bad)));
    }
  });
  it('rejects oversized documents (> 40 fields)', async () => {
    const id = 'k3';
    const big = validDoc('alice', id);
    for (let i = 0; i < 45; i++) big['f' + i] = i;
    await assertFails(setDoc(doc(alice(), path('alice', 'prayers_tracking', id)), big));
  });
  it('rejects unknown collections', async () => {
    await assertFails(setDoc(doc(alice(), path('alice', 'evil_collection', 'k4')), validDoc('alice', 'k4')));
  });
  it('allows every declared collection', async () => {
    const cols = ['reading_progress', 'quran_bookmark_categories', 'quran_bookmarks', 'quran_plans', 'quran_plan_logs', 'quran_day_logs',
      'adhkar_progress', 'adhkar_favorites', 'prayers_tracking', 'rawatib_tracking', 'custom_sunnah', 'daily_tasks', 'task_completions',
      'calendar_events', 'user_settings', 'notification_settings', 'achievement_statistics'];
    for (const c of cols) await assertSucceeds(setDoc(doc(alice(), path('alice', c, 'z')), validDoc('alice', 'z')));
  });
});

describe('Last-Writer-Wins enforced by the server', () => {
  const id = 'lww';
  const ref = () => doc(alice(), path('alice', 'prayers_tracking', id));
  it('accepts newer or equal updatedAt, rejects older', async () => {
    await assertSucceeds(setDoc(ref(), validDoc('alice', id, { updatedAt: 1000, status: 'HOME' })));
    await assertSucceeds(setDoc(ref(), validDoc('alice', id, { updatedAt: 2000, status: 'MOSQUE' })));
    await assertSucceeds(setDoc(ref(), validDoc('alice', id, { updatedAt: 2000, status: 'MOSQUE' }))); // إعادة إرسال العملية نفسها
    await assertFails(setDoc(ref(), validDoc('alice', id, { updatedAt: 1500, status: 'HOME' })));
    const snap = await getDoc(ref());
    if (snap.data().status !== 'MOSQUE') throw new Error('older write must not overwrite');
  });
});

describe('no physical deletes, tombstones only', () => {
  it('delete is denied, tombstone update is allowed', async () => {
    const id = 'del';
    const ref = doc(alice(), path('alice', 'prayers_tracking', id));
    await assertSucceeds(setDoc(ref, validDoc('alice', id, { updatedAt: 10 })));
    await assertFails(deleteDoc(ref));
    await assertSucceeds(setDoc(ref, validDoc('alice', id, { updatedAt: 20, deleted: true })));
  });
});

describe('profile and public collections', () => {
  it('owner can write own profile, others cannot', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'users/alice'), { uid: 'alice', name: 'A' }));
    await assertFails(setDoc(doc(bob(), 'users/alice'), { uid: 'alice', name: 'hack' }));
    await assertFails(setDoc(doc(alice(), 'users/alice'), { uid: 'bob' }));
    await assertFails(getDoc(doc(bob(), 'users/alice')));
  });
  it('app_versions: public read, no client write', async () => {
    await env.withSecurityRulesDisabled(async (c) => setDoc(doc(c.firestore(), 'app_versions/0.1.0'), { versionName: '0.1.0' }));
    await assertSucceeds(getDoc(doc(anon(), 'app_versions/0.1.0')));
    await assertFails(setDoc(doc(alice(), 'app_versions/9.9.9'), { versionName: '9.9.9' }));
  });
  it('everything else is denied', async () => {
    await assertFails(getDoc(doc(alice(), 'secrets/key')));
    await assertFails(setDoc(doc(alice(), 'secrets/key'), { a: 1 }));
  });
});
