import assert from 'node:assert/strict';
import { spawnSync, spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import path from 'node:path';
import { deflateSync } from 'node:zlib';

const serial = process.env.PAPAN_ANDROID_SERIAL || '', adb = process.env.PAPAN_ADB;
assert.match(serial, /^emulator-\d+$/, 'Media tests require a disposable emulator.'); assert.ok(adb);
const run = (...args) => { const r = spawnSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout: 60000, maxBuffer: 4 * 1024 * 1024 }); assert.equal(r.status, 0, r.stderr); return r.stdout; };
assert.equal(run('shell', 'getprop', 'ro.kernel.qemu').trim(), '1');
const frames = process.argv.includes('--frames');
const pkg = 'com.papan.share.test', out = path.resolve(frames ? 'artifacts/android/phone-frames' : 'artifacts/android/media-selection');
assert.equal(run('shell', 'pm', 'list', 'packages', pkg).trim(), '', 'Use a fresh disposable test installation.');
await mkdir(out, { recursive: true });
const ffmpeg = process.env.PAPAN_FFMPEG; assert.ok(ffmpeg, 'Set PAPAN_FFMPEG to a local FFmpeg.');
const movie = path.join(out, 'fixture.mp4');
const encoded = spawnSync(ffmpeg, ['-nostdin', '-v', 'error', '-y', '-f', 'lavfi', '-i', 'color=c=red:s=90x160:r=24', '-t', '3', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', movie], { encoding: 'utf8' }); assert.equal(encoded.status, 0, encoded.stderr);
const chunk = (type, data) => { const content = Buffer.concat([Buffer.from(type), data]); let crc = 0xffffffff; for (const byte of content) { crc ^= byte; for (let i = 0; i < 8; i++) crc = crc >>> 1 ^ (crc & 1 ? 0xedb88320 : 0); } const size = Buffer.alloc(4), sum = Buffer.alloc(4); size.writeUInt32BE(data.length); sum.writeUInt32BE((crc ^ 0xffffffff) >>> 0); return Buffer.concat([size, content, sum]); };
const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(2); ihdr.writeUInt32BE(2, 4); ihdr[8] = 8; ihdr[9] = 2;
const poster = Buffer.concat([Buffer.from('89504e470d0a1a0a', 'hex'), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(Buffer.from([0, 0, 0, 255, 0, 0, 255, 0, 0, 0, 255, 0, 0, 255]))), chunk('IEND', Buffer.alloc(0))]);
const video = await readFile(movie); let offline = false, videoRequests = 0, retries = 0;
const server = createServer((req, res) => {
  if (req.url === '/offline') { offline = true; res.end('offline'); return; }
  if (req.url === '/retry-gallery' && retries++ === 0) { res.writeHead(503); res.end('Unavailable'); return; }
  if (req.url === '/gallery' || req.url === '/retry-gallery') { res.setHeader('Content-Type', 'text/html'); res.end('<title>Selected media fixture</title><meta property="og:video" content="/movie.mp4"><meta property="og:image" content="/image.png"><img src="/image.png"><img src="/unselected.png">'); return; }
  if (req.url === '/movie.mp4') { videoRequests++; if (offline) { res.writeHead(403); res.end(); return; } res.setHeader('Content-Type', 'video/mp4'); res.end(video); return; }
  if (offline) { res.writeHead(403); res.end(); return; }
  res.setHeader('Content-Type', 'image/png'); res.end(poster);
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const url = `http://10.0.2.2:${server.address().port}/gallery`, apk = path.resolve('dist/Papan-Android-0.3.1-test.apk');
let installed = false;
try {
  run('install', '-r', apk); installed = true;
  if (frames) run('shell', 'pm', 'grant', pkg, 'android.permission.CAMERA');
  const args = ['-s', serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', frames ? 'framesOnly' : 'mediaOnly', 'true', '-e', 'mediaUrl', url];
  if (!frames && process.env.PAPAN_LIVE_MEDIA_URL) args.push('-e', 'liveUrl', process.env.PAPAN_LIVE_MEDIA_URL);
  args.push(`${pkg}/com.papan.share.MobileFlowTest`);
  const log = await new Promise((resolve, reject) => { const child = spawn(adb, args); let output = ''; child.stdout.on('data', data => output += data); child.stderr.on('data', data => output += data); child.on('error', reject); child.on('exit', code => code === 0 ? resolve(output) : reject(new Error(output))); });
  await writeFile(path.join(out, 'instrumentation.txt'), log);
  const passed = /INSTRUMENTATION_RESULT: status=passed/.test(log);
  const captures = frames ? ['phone-portrait-board.png', 'phone-portrait-large-text.png', 'phone-mixed-filtered.png', 'phone-mixed-board.png', 'phone-menu-paused.png'] : ['media-choice.png', 'media-selected.png', 'selected-video-offline.png', ...(process.env.PAPAN_LIVE_MEDIA_URL ? ['live-media.json'] : [])];
  for (const name of [...captures, ...(!passed ? ['failure.png'] : [])]) {
    const r = spawnSync(adb, ['-s', serial, 'exec-out', 'run-as', pkg, 'cat', `files/${name}`], { maxBuffer: 16 * 1024 * 1024 });
    if (!passed && (r.status !== 0 || !r.stdout.subarray(0, 8).equals(Buffer.from('89504e470d0a1a0a', 'hex')))) continue; assert.equal(r.status, 0, String(r.stderr)); await writeFile(path.join(out, name), r.stdout);
  }
  assert.ok(passed, log); assert.equal(videoRequests, frames ? 1 : 3, 'Video requests must match the fixture reads.');
  const report = { status: 'passed', apkSha256: createHash('sha256').update(await readFile(apk)).digest('hex'), device: serial, displayId: Number(log.match(/displayId=(\d+)/)?.[1]), physicalPhoneUsed: false, checks: log.match(/checks=(.*)/)?.[1], videoRequests };
  await writeFile(path.join(out, 'report.json'), JSON.stringify(report, null, 2) + '\n'); console.log(JSON.stringify(report, null, 2));
} finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); if (installed) run('uninstall', pkg); }
