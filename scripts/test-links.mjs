import { spawnSync } from 'node:child_process';
import { mkdtemp, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

const root = process.cwd();
const java = name => process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
const run = (command, args) => {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.status !== 0) throw new Error(`${path.basename(command)} failed.`);
};
const directory = await mkdtemp(path.join(os.tmpdir(), 'papan-link-test-'));
try {
  run(java('javac'), ['-encoding', 'UTF-8', '-source', '8', '-target', '8', '-d', directory,
    path.join(root, 'android/src/com/papan/share/SharedLink.java'), path.join(root, 'android/test/com/papan/share/SharedLinkTest.java')]);
  run(java('java'), ['-classpath', directory, 'com.papan.share.SharedLinkTest']);
} finally { await rm(directory, { recursive: true, force: true }); }
