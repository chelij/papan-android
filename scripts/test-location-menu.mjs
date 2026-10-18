import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

const serial = process.env.PAPAN_ANDROID_SERIAL || '', adb = process.env.PAPAN_ADB;
assert.match(serial, /^emulator-\d+$/, 'Select a disposable emulator; personal phones are not test targets.');
assert.ok(adb, 'Set PAPAN_ADB to the adb executable.');
const pkg = 'com.papan.share.test', out = path.resolve('artifacts/android/location-menu');
const run = (...args) => {
  const result = spawnSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout: 60000 });
  assert.equal(result.status, 0, result.stderr); return result.stdout;
};
assert.equal(run('shell', 'getprop', 'ro.kernel.qemu').trim(), '1', 'Location tests require an emulator.');
assert.equal(run('shell', 'pm', 'list', 'packages', pkg).trim(), '', 'Use a fresh disposable test installation.');
const version = JSON.parse(await readFile('package.json', 'utf8')).version;
const apk = path.resolve(`dist/Papan-Android-${version}-test.apk`);
const apkSha256 = createHash('sha256').update(await readFile(apk)).digest('hex');
await mkdir(out, { recursive: true });
let installed = false;
try {
  run('install', '-r', apk); installed = true;
  const log = run('shell', 'am', 'instrument', '-w', '-r', '-e', 'locationOnly', 'true', `${pkg}/com.papan.share.MobileFlowTest`);
  await writeFile(path.join(out, 'instrumentation.txt'), log);
  const passed = /INSTRUMENTATION_RESULT: status=passed/.test(log);
  for (const name of ['menu-motion-before.png', 'menu-motion-first.png', 'menu-motion-last.png', 'menu-motion.json', 'location-phone-menu.png', 'location-desktop-menu.png', 'location-activity-menu.png', 'location-desktop-button.png']) {
    const result = spawnSync(adb, ['-s', serial, 'exec-out', 'run-as', pkg, 'cat', `files/${name}`], { timeout: 10000, maxBuffer: 16 * 1024 * 1024 });
    if (!passed && (result.status !== 0 || name.endsWith('.png') && result.stdout.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a' || name.endsWith('.json') && !result.stdout.toString().trim().startsWith('{'))) continue;
    assert.equal(result.status, 0, String(result.stderr));
    if (name.endsWith('.png')) assert.equal(result.stdout.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
    await writeFile(path.join(out, name), result.stdout);
  }
  assert.ok(passed, log);
  const motion = JSON.parse(await readFile(path.join(out, 'menu-motion.json'), 'utf8'));
  assert.ok(motion.status === 'passed' && motion.displayId > 0 && motion.buttonUncovered);
  const report = { status: 'passed', apkSha256, device: serial, physicalPhoneUsed: false, motion, checks: log.match(/INSTRUMENTATION_RESULT: checks=(.*)/)?.[1] };
  await writeFile(path.join(out, 'report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
} finally {
  if (installed) run('uninstall', pkg);
}
