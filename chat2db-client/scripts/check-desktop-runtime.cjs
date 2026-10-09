const fs = require('node:fs');
const path = require('node:path');

const root = process.argv[2] ? path.resolve(process.argv[2]) : path.resolve(__dirname, '..');
const version = fs.readFileSync(path.join(root, 'versions', 'version'), 'utf8').trim();
if (!/^[\w.-]+$/.test(version) || version === '.' || version === '..') {
  throw new Error('Invalid desktop resource version');
}
// 完整安装包必须包含页面、服务端和 Java，不能只验证 Electron 能打包。
for (const resource of [
  path.join('versions', version, 'dist', 'index.html'),
  path.join('versions', version, 'static', 'chat2db-server-start.jar'),
  path.join('static', 'jre', 'bin', process.platform === 'win32' ? 'java.exe' : 'java'),
]) {
  if (!fs.existsSync(path.join(root, resource))) {
    throw new Error(`Missing desktop runtime resource: ${resource}. Prepare Java and server resources before packaging.`);
  }
}
console.log('Desktop runtime resources verified:', version);
