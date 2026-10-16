import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

const serial = process.env.PAPAN_ANDROID_SERIAL || '', adb = process.env.PAPAN_ADB;
assert.match(serial, /^emulator-\d+$/, 'Select a disposable emulator; personal phones are not test targets.');
assert.ok(adb, 'Set PAPAN_ADB to the adb executable.');
const pkg = 'com.papan.share.test', out = path.resolve('artifacts/android/scroll-controls');
const run = (...args) => {
  const result = spawnSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout: 120000 });
  assert.equal(result.status, 0, result.stderr); return result.stdout;
};
assert.equal(run('shell', 'getprop', 'ro.kernel.qemu').trim(), '1', 'Scroll tests require an emulator.');
assert.equal(run('shell', 'pm', 'list', 'packages', pkg).trim(), '', 'Use a fresh disposable test installation.');
const version = JSON.parse(await readFile('package.json', 'utf8')).version;
const apk = path.resolve(`dist/Papan-Android-${version}-test.apk`);
const apkSha256 = createHash('sha256').update(await readFile(apk)).digest('hex');
await mkdir(out, { recursive: true });
let installed = false;
try {
  run('install', '-r', apk); installed = true;
  const log = run('shell', 'am', 'instrument', '-w', '-r', '-e', 'scrollOnly', 'true', `${pkg}/com.papan.share.MobileFlowTest`);
  await writeFile(path.join(out, 'instrumentation.txt'), log);
  assert.match(log, /INSTRUMENTATION_RESULT: status=passed/, log);
  for (const name of ['scroll-controls-before.png', 'scroll-controls-hidden.png', 'scroll-controls-returned.png']) {
    const result = spawnSync(adb, ['-s', serial, 'exec-out', 'run-as', pkg, 'cat', `files/${name}`], { timeout: 10000, maxBuffer: 16 * 1024 * 1024 });
    assert.equal(result.status, 0, String(result.stderr)); assert.equal(result.stdout.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
    await writeFile(path.join(out, name), result.stdout);
  }
  const report = { status: 'passed', device: serial, displayId: Number(log.match(/INSTRUMENTATION_RESULT: displayId=(\d+)/)?.[1]), apkSha256, checks: log.match(/INSTRUMENTATION_RESULT: checks=(.*)/)?.[1], physicalPhoneUsed: false };
  assert.ok(report.displayId > 0 && report.checks); await writeFile(path.join(out, 'report.json'), JSON.stringify(report, null, 2) + '\n');
  process.stdout.write('Native scroll direction, refresh, keyboard and responsive checks passed on the private emulator display.\n');
} finally { if (installed) run('uninstall', pkg); }
