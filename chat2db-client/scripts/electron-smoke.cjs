// 使用真实 Electron 验证 preload、IPC 和渲染进程崩溃日志，不启动 Java 服务。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { once } = require('node:events');
const { createRequire } = require('node:module');
const { app, BrowserWindow, ipcMain } = require('electron');
const mainDirectory = process.env.CHAT2DB_SMOKE_MAIN_DIRECTORY || path.resolve(__dirname, '../src/main');
const mainRequire = createRequire(path.join(mainDirectory, 'index.js'));
const { registerDiagnostics, registerWindowDiagnostics } = mainRequire('./diagnostics');

const testDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'chat2db-electron-smoke-'));
app.setPath('userData', testDirectory);
app.setPath('crashDumps', testDirectory);
process.env.NODE_ENV = 'development';
registerDiagnostics(testDirectory);
const logPath = path.join(testDirectory, 'electron.log');

const timeout = setTimeout(() => {
  console.error('Desktop smoke test timed out:', testDirectory);
  app.exit(1);
}, 30000);

app.whenReady().then(async () => {
  const window = new BrowserWindow({
    show: false,
    webPreferences: {
      webSecurity: false,
      spellcheck: false,
      nodeIntegration: true,
      contextIsolation: true,
      preload: path.join(mainDirectory, 'preload.js'),
    },
  });
  registerWindowDiagnostics(window);
  const preloadErrors = [];
  window.webContents.on('preload-error', (_event, _path, error) => preloadErrors.push(error));
  await window.loadURL('data:text/html,<html><body>Desktop smoke test</body></html>');
  assert.deepEqual(preloadErrors, []);
  const platform = await window.webContents.executeJavaScript('window.electronApi.getPlatform()');
  assert.equal(platform.isWin, process.platform === 'win32');
  assert.equal(platform.isMac, process.platform === 'darwin');
  assert.equal(platform.isLinux, process.platform === 'linux');
  const ipcMessage = once(ipcMain, 'set-base-url');
  await window.webContents.executeJavaScript("window.electronApi.setBaseURL('http://127.0.0.1:10821')");
  assert.equal((await ipcMessage)[1], 'http://127.0.0.1:10821');

  // 真实加载失败与真实 renderer 崩溃，检查诊断信息确实落盘。
  await window.loadFile(path.join(testDirectory, 'missing.html')).catch(() => {});
  await window.loadURL('data:text/html,<html><body>Crash probe</body></html>');
  const rendererGone = once(window.webContents, 'render-process-gone');
  window.webContents.forcefullyCrashRenderer();
  const [, details] = await rendererGone;
  assert.equal(details.reason, 'crashed');
  const output = fs.readFileSync(logPath, 'utf8');
  assert.match(output, /Startup/);
  assert.ok(output.includes(process.versions.electron));
  assert.ok(output.includes(`hardwareAccelerationDisabled: ${process.env.CHAT2DB_DISABLE_GPU === '1'}`));
  assert.match(output, /did-fail-load/);
  assert.match(output, /render-process-gone/);
  assert.match(output, /exitCode/);
  assert.match(output, /freeMemoryMB/);
  console.log('Desktop smoke test passed:', { electron: process.versions.electron, logPath });
  clearTimeout(timeout);
  window.destroy();
  app.exit(0);
}).catch((error) => {
  console.error(error);
  clearTimeout(timeout);
  app.exit(1);
});
