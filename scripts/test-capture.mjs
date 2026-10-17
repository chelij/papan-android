import { spawnSync } from 'node:child_process';
import { access, mkdtemp, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

const root = process.cwd(), sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
if (!sdk) throw new Error('Set ANDROID_HOME to an Android SDK containing platform 35.');
const platform = path.join(sdk, 'platforms', 'android-35', 'android.jar');
await access(platform);
const java = name => process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
const run = (command, args) => {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.status !== 0) throw new Error(`${path.basename(command)} failed.`);
};
const directory = await mkdtemp(path.join(os.tmpdir(), 'papan-capture-test-'));
try {
  run(java('javac'), ['-encoding', 'UTF-8', '-source', '8', '-target', '8', '-classpath', platform, '-d', directory,
    ...['SharedLink.java', 'LocalCapture.java', 'MobileLibrary.java'].map(name => path.join(root, 'android/src/com/papan/share', name)),
    path.join(root, 'test/com/papan/share/MediaExtractor.java'), path.join(root, 'test/com/papan/share/LocalCaptureTest.java')]);
  run(java('java'), ['-classpath', [directory, platform].join(path.delimiter), 'com.papan.share.LocalCaptureTest']);
} finally { await rm(directory, { recursive: true, force: true }); }
