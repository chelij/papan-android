import { spawnSync } from 'node:child_process';
import { access, readFile } from 'node:fs/promises';
import path from 'node:path';

const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
const serial = process.env.PAPAN_ANDROID_SERIAL;
if (!sdk || !/^emulator-\d+$/.test(serial || '')) throw new Error('Set ANDROID_HOME and PAPAN_ANDROID_SERIAL to a disposable headless emulator. Phones are not supported.');
const adb = path.join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
const run = args => {
  const result = spawnSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout: 30000 });
  if (result.status !== 0) throw new Error(result.error?.message || result.stderr || 'The emulator command failed.');
  return result.stdout.trim();
};
if (run(['shell', 'getprop', 'ro.kernel.qemu']) !== '1' || run(['shell', 'getprop', 'sys.boot_completed']) !== '1') throw new Error('The selected disposable emulator must be booted.');
const { version } = JSON.parse(await readFile('package.json', 'utf8'));
const apk = path.resolve('dist', `Papan-Android-${version}-test.apk`);
await access(apk);
console.log(run(['install', '-r', apk]));
if (run(['shell', 'pm', 'clear', 'com.papan.share.test']) !== 'Success') throw new Error('Could not reset the isolated emulator test package.');
const output = run(['shell', 'am', 'instrument', '-w', '-r', 'com.papan.share.test/com.papan.share.MobileLibraryStorageTest']);
console.log(output);
if (!output.includes('Mobile storage checks passed:') || !/^INSTRUMENTATION_CODE: -1$/m.test(output)) throw new Error('The native storage checks failed.');
