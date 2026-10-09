const { app, crashReporter } = require('electron');
const os = require('os');
const path = require('path');
const log = require('electron-log');

function logProcessSnapshot(event) {
  try {
    // 只记录进程资源，不采集 SQL、连接信息或页面内容。
    log.info('[desktop-diagnostics]', event, {
      freeMemoryMB: Math.round(os.freemem() / 1024 / 1024),
      processes: app.getAppMetrics().map(({ pid, type, cpu, memory }) => ({ pid, type, cpu, memory })),
    });
  } catch (error) {
    log.warn('[desktop-diagnostics] Failed to collect process metrics', error);
  }
}

function registerDiagnostics(logDirectory = path.join(os.homedir(), '.chat2db', 'logs')) {
  log.transports.file.resolvePathFn = () => path.join(logDirectory, 'electron.log');
  log.transports.file.maxSize = 10 * 1024 * 1024;
  // 崩溃转储仅保存在本机，便于定位 Chromium / GPU 原生崩溃。
  crashReporter.start({ uploadToServer: false });
  const hardwareAccelerationDisabled = process.env.CHAT2DB_DISABLE_GPU === '1';
  if (hardwareAccelerationDisabled) {
    app.disableHardwareAcceleration();
  }
  log.info('[desktop-diagnostics] Startup', {
    versions: process.versions,
    platform: process.platform,
    arch: process.arch,
    hardwareAccelerationDisabled,
    crashDumps: app.getPath('crashDumps'),
  });
  app.on('child-process-gone', (_event, details) => {
    log.error('[desktop-diagnostics] child-process-gone', details);
    logProcessSnapshot('child-process-gone');
  });
}

function registerWindowDiagnostics(window) {
  const contents = window.webContents;
  contents.on('render-process-gone', (_event, details) => {
    log.error('[desktop-diagnostics] render-process-gone', details);
    logProcessSnapshot('render-process-gone');
  });
  contents.on('preload-error', (_event, preloadPath, error) => {
    log.error('[desktop-diagnostics] preload-error', preloadPath, error);
  });
  contents.on('did-fail-load', (_event, errorCode, errorDescription, _url, isMainFrame) => {
    log.error('[desktop-diagnostics] did-fail-load', { errorCode, errorDescription, isMainFrame });
  });
  contents.on('did-finish-load', () => logProcessSnapshot('did-finish-load'));
  window.on('unresponsive', () => {
    log.warn('[desktop-diagnostics] Window unresponsive');
    logProcessSnapshot('unresponsive');
  });
  window.on('responsive', () => log.info('[desktop-diagnostics] Window responsive'));
  // 保留崩溃前的资源趋势；不自动重载，避免丢失未保存的编辑内容。
  const timer = setInterval(() => logProcessSnapshot('periodic'), 60000);
  timer.unref();
  window.once('closed', () => clearInterval(timer));
}

module.exports = { registerDiagnostics, registerWindowDiagnostics };
