import { spawnSync } from 'node:child_process';
import { mkdir, readdir, rm, copyFile, cp, access, writeFile, readFile, chmod } from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';

const root = process.cwd(), sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
if (!sdk) throw new Error('Set ANDROID_HOME to an Android SDK containing platform 35 and build-tools 35.0.0.');
const java = name => process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
const tool = name => path.join(sdk, 'build-tools', '35.0.0', name + (process.platform === 'win32' ? name === 'apksigner' ? '.bat' : '.exe' : ''));
const env = { ...process.env, PATH: [process.env.JAVA_HOME && path.join(process.env.JAVA_HOME, 'bin'), process.env.PATH].filter(Boolean).join(path.delimiter) };
const run = (command, args) => { const result = spawnSync(command, args, { stdio: 'inherit', env, shell: process.platform === 'win32' && command.endsWith('.bat') }); if (result.status !== 0) throw new Error(`${path.basename(command)} failed.`); };
const test = process.argv.includes('--test'), debug = test || process.argv.includes('--debug');
const output = path.join(root, 'artifacts', 'android'); await mkdir(output, { recursive: true });
const assets = path.join(output, 'gradle-assets'); await rm(path.join(assets, 'papan-source'), { recursive: true, force: true });
for (const source of ['android/src', 'android/res', 'android/test', 'android/gradle', 'android/gradlew', 'android/gradlew.bat', 'android/build.gradle', 'android/settings.gradle', 'android/gradle.properties', 'android/gradle.lockfile', 'android/AndroidManifest.xml', 'android/isolated-manifest.xml', 'LICENSE', 'THIRD-PARTY.md', 'package.json', 'scripts/build-android.mjs', 'scripts/test-location-menu.mjs', 'scripts/test-scroll-controls.mjs']) {
  const target = path.join(assets, 'papan-source', source); await mkdir(path.dirname(target), { recursive: true }); await cp(source, target, { recursive: true });
}
const license = path.join(assets, 'Apache-2.0.txt');
try { await access(license); } catch {
  const response = await fetch('https://raw.githubusercontent.com/zxing/zxing/zxing-3.5.4/LICENSE', { signal: AbortSignal.timeout(30000) });
  if (!response.ok) throw new Error(`Could not download Apache license: HTTP ${response.status}.`);
  await writeFile(license, Buffer.from(await response.arrayBuffer()));
}
if (createHash('sha256').update(await readFile(license)).digest('hex') !== '3f62881f0566227a24b12e5a754cc79f39aaa94883038e95c94812e1f50af42f') throw new Error('Apache license checksum mismatch.');
await access(path.join(sdk, 'platforms', 'android-35', 'android.jar')); await access(tool('apksigner'));
const variant = test ? 'isolated' : 'personal', buildType = debug ? 'qa' : 'release';
const task = `assemble${variant[0].toUpperCase()}${variant.slice(1)}${buildType[0].toUpperCase()}${buildType.slice(1)}`;
run(java('java'), ['-classpath', path.join(root, 'android/gradle/wrapper/gradle-wrapper.jar'), 'org.gradle.wrapper.GradleWrapperMain', '-p', path.join(root, 'android'), task, '--console=plain']);
const built = path.join(output, 'gradle-build/outputs/apk', variant, buildType);
const candidates = (await readdir(built)).filter(name => name.endsWith('.apk'));
if (candidates.length !== 1) throw new Error('Expected one unsigned APK from Gradle.');
const key = process.env.PAPAN_ANDROID_KEYSTORE || path.join(output, 'papan-local.keystore');
try { await access(key); } catch {
  if (process.env.PAPAN_ANDROID_KEYSTORE) throw new Error('The specified signing key does not exist.');
  run(java('keytool'), ['-genkeypair', '-keystore', key, '-storepass', 'papan-local-build', '-keypass', 'papan-local-build', '-alias', 'papan', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Papan local build']);
}
if (!process.env.PAPAN_ANDROID_KEYSTORE) await chmod(key, 0o600);
const version = JSON.parse(await readFile('package.json', 'utf8')).version;
await mkdir('dist', { recursive: true });
const apk = path.join(root, 'dist', `Papan-Android-${version}${test ? '-test' : debug ? '-debug' : ''}.apk`);
await copyFile(path.join(built, candidates[0]), apk);
run(tool('apksigner'), ['sign', '--ks', key, '--ks-key-alias', process.env.PAPAN_ANDROID_KEY_ALIAS || 'papan', '--ks-pass', process.env.PAPAN_ANDROID_KEY_PASSWORD ? 'env:PAPAN_ANDROID_KEY_PASSWORD' : 'pass:papan-local-build', apk]);
run(tool('apksigner'), ['verify', '--verbose', apk]);
await writeFile(`${apk}.sha256`, `${createHash('sha256').update(await readFile(apk)).digest('hex')}  ${path.basename(apk)}\n`);
console.log(`Built ${apk}\nKeep ${key} to sign compatible updates.`);
