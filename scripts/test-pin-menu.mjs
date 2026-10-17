import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

const serial = process.env.PAPAN_ANDROID_SERIAL || '', adb = process.env.PAPAN_ADB;
assert.match(serial, /^emulator-\d+$/, 'Select a disposable emulator; phones are not test targets.');
assert.ok(adb, 'Set PAPAN_ADB to the adb executable.');
const pkg = 'com.papan.share.test', out = path.resolve('artifacts/android/pin-menu');
const run = (...args) => {
  const result = spawnSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout: 90000 });
  assert.equal(result.status, 0, result.stderr); return result.stdout;
};
assert.equal(run('shell', 'getprop', 'ro.kernel.qemu').trim(), '1', 'Use a headless emulator.');
assert.equal(run('shell', 'pm', 'list', 'packages', pkg).trim(), '', 'Use a fresh disposable installation.');
const version = JSON.parse(await readFile('package.json', 'utf8')).version;
const apk = path.resolve(`dist/Papan-Android-${version}-test.apk`);
const apkSha256 = createHash('sha256').update(await readFile(apk)).digest('hex');
await mkdir(out, { recursive: true });
let installed = false;
try {
  run('install', '-r', apk); installed = true;
  const log = run('shell', 'am', 'instrument', '-w', '-r', '-e', 'viewerMenuOnly', 'true', `${pkg}/com.papan.share.MobileFlowTest`);
  await writeFile(path.join(out, 'instrumentation.txt'), log);
  assert.match(log, /INSTRUMENTATION_RESULT: status=passed/, log);
  const displayId = Number(log.match(/displayId=(\d+)/)?.[1]); assert.ok(displayId > 0);
  const frame = spawnSync(adb, ['-s', serial, 'exec-out', 'run-as', pkg, 'cat', 'files/pin-actions.png'], { maxBuffer: 16 * 1024 * 1024, timeout: 10000 });
  assert.equal(frame.status, 0, String(frame.stderr)); assert.equal(frame.stdout.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
  await writeFile(path.join(out, 'pin-actions.png'), frame.stdout);
  const report = { status: 'passed', apkSha256, device: serial, displayId, physicalPhoneUsed: false, checks: log.match(/checks=(.*)/)?.[1] };
  await writeFile(path.join(out, 'report.json'), JSON.stringify(report, null, 2) + '\n'); console.log(JSON.stringify(report, null, 2));
} finally {
  if (installed) run('uninstall', pkg);
}
