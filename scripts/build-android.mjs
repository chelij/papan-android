import { spawnSync } from 'node:child_process';
import { mkdir, readdir, rm, copyFile, cp, access, writeFile, readFile, chmod } from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';

// Native Android UI/Camera2 plus ZXing's standalone QR decoder; no Gradle.
// Keep the signing key across rebuilds/updates.
const root = process.cwd(), sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
if (!sdk) throw new Error('Set ANDROID_HOME to an Android SDK containing platform 35 and build-tools 35.0.0.');
const java = name => process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
const tool = name => path.join(sdk, 'build-tools', '35.0.0', name + (process.platform === 'win32' ? ['d8', 'apksigner'].includes(name) ? '.bat' : '.exe' : ''));
const env = { ...process.env, PATH: [process.env.JAVA_HOME && path.join(process.env.JAVA_HOME, 'bin'), process.env.PATH].filter(Boolean).join(path.delimiter) };
const run = (command, args) => { const result = spawnSync(command, args, { stdio: 'inherit', env, shell: process.platform === 'win32' && command.endsWith('.bat') }); if (result.status !== 0) throw new Error(`${path.basename(command)} failed.`); };
const test = process.argv.includes('--test'), debug = test || process.argv.includes('--debug');
const output = path.join(root, 'artifacts', 'android'), work = path.join(output, test ? 'build-test' : 'build');
await mkdir(output, { recursive: true }); await rm(work, { recursive: true, force: true });
for (const dir of ['classes', 'dex', 'generated']) await mkdir(path.join(work, dir), { recursive: true });
const dependencies = path.join(output, 'dependencies'); await mkdir(dependencies, { recursive: true });
for (const [name, url, digest] of [
  ['core-3.5.4.jar', 'https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.4/core-3.5.4.jar', '71de5d89341b5fcf5dd89da7f44e84d825d0e084cdf3ec77c9abe26b0f0ceb13'],
  ['core-3.5.4-sources.jar', 'https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.4/core-3.5.4-sources.jar', 'a611e3b63b661aeb2a81b8b4f4bde2daace1e2976adbb37917a674a0019c8fac'],
  ['ZXing-LICENSE.txt', 'https://raw.githubusercontent.com/zxing/zxing/zxing-3.5.4/LICENSE', '3f62881f0566227a24b12e5a754cc79f39aaa94883038e95c94812e1f50af42f'],
]) {
  const target = path.join(dependencies, name);
  try { await access(target); } catch {
    const response = await fetch(url, { signal: AbortSignal.timeout(30000) });
    if (!response.ok) throw new Error(`Could not download ${name}: HTTP ${response.status}.`);
    await writeFile(target, Buffer.from(await response.arrayBuffer()));
  }
  if (createHash('sha256').update(await readFile(target)).digest('hex') !== digest) throw new Error(`Checksum mismatch for ${name}. Remove that cached dependency and retry.`);
}
const decoder = path.join(dependencies, 'core-3.5.4.jar');
const assets = path.join(work, 'assets'); await mkdir(path.join(assets, 'papan-source'), { recursive: true });
await cp('android/src', path.join(assets, 'papan-source/android/src'), { recursive: true });
await cp('android/res', path.join(assets, 'papan-source/android/res'), { recursive: true });
await copyFile('android/AndroidManifest.xml', path.join(assets, 'papan-source/android/AndroidManifest.xml'));
for (const file of ['LICENSE', 'THIRD-PARTY.md', 'scripts/build-android.mjs']) await copyFile(file, path.join(assets, 'papan-source', path.basename(file)));
for (const file of ['core-3.5.4-sources.jar', 'ZXing-LICENSE.txt']) await copyFile(path.join(dependencies, file), path.join(assets, file));
const platform = path.join(sdk, 'platforms', 'android-35', 'android.jar');
await access(platform); await access(tool('aapt2'));
const resources = path.join(work, 'resources.zip'), unsigned = path.join(work, 'unsigned.apk');
run(tool('aapt2'), ['compile', '--dir', path.join(root, 'android/res'), '-o', resources]);
let manifest = path.join(root, 'android/AndroidManifest.xml');
if (test) {
  manifest = path.join(work, 'AndroidManifest.xml');
  await writeFile(manifest, (await readFile('android/AndroidManifest.xml', 'utf8'))
    .replace('package="com.papan.share"', 'package="com.papan.share.test"')
    .replace('android:label="Papan"', 'android:label="Papan UI Test"')
    .replaceAll('android:name=".', 'android:name="com.papan.share.')
    .replace(/^\s*<intent-filter><action android:name="android.intent.action.(?:SEND|VIEW)"[^\n]*\n/gm, '')
    .replace('</manifest>', '<instrumentation android:name="com.papan.share.MobileFlowTest" android:targetPackage="com.papan.share.test" />\n</manifest>'));
}
run(tool('aapt2'), ['link', '-o', unsigned, '--manifest', manifest, '-I', platform, '-A', assets, '--java', path.join(work, 'generated'), ...(debug ? ['--debug-mode'] : []), resources]);
const source = path.join(root, 'android/src/com/papan/share');
run(java('javac'), ['-encoding', 'UTF-8', '-source', '8', '-target', '8', '-classpath', [platform, decoder].join(path.delimiter), '-d', path.join(work, 'classes'), ...(await readdir(source)).filter(file => file.endsWith('.java')).map(file => path.join(source, file)), ...(test ? [path.join(root, 'android/test/com/papan/share/MobileFlowTest.java')] : [])]);
const jar = path.join(work, 'classes.jar'); run(java('jar'), ['cf', jar, '-C', path.join(work, 'classes'), '.']);
run(tool('d8'), ['--min-api', '26', '--lib', platform, '--output', path.join(work, 'dex'), jar, decoder]);
run(java('jar'), ['uf', unsigned, '-C', path.join(work, 'dex'), 'classes.dex']);
const aligned = path.join(work, 'aligned.apk'); run(tool('zipalign'), ['-f', '4', unsigned, aligned]);
const key = process.env.PAPAN_ANDROID_KEYSTORE || path.join(output, 'papan-local.keystore');
try { await access(key); } catch {
  if (process.env.PAPAN_ANDROID_KEYSTORE) throw new Error('The specified signing key does not exist.');
  run(java('keytool'), ['-genkeypair', '-keystore', key, '-storepass', 'papan-local-build', '-keypass', 'papan-local-build', '-alias', 'papan', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Papan local build']);
}
if (!process.env.PAPAN_ANDROID_KEYSTORE) await chmod(key, 0o600);
const version = JSON.parse(await readFile('package.json', 'utf8')).version;
await mkdir('dist', { recursive: true });
const apk = path.join(root, 'dist', `Papan-Android-${version}${test ? '-test' : debug ? '-debug' : ''}.apk`);
await copyFile(aligned, apk);
run(tool('apksigner'), ['sign', '--ks', key, '--ks-key-alias', process.env.PAPAN_ANDROID_KEY_ALIAS || 'papan', '--ks-pass', process.env.PAPAN_ANDROID_KEY_PASSWORD ? 'env:PAPAN_ANDROID_KEY_PASSWORD' : 'pass:papan-local-build', apk]);
run(tool('apksigner'), ['verify', '--verbose', apk]);
await writeFile(`${apk}.sha256`, `${createHash('sha256').update(await readFile(apk)).digest('hex')}  ${path.basename(apk)}\n`);
console.log(`Built ${apk}\nKeep ${key} to sign compatible updates.`);
