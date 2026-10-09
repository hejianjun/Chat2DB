const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const dist = path.join(root, 'dist');
const html = fs.readFileSync(path.join(dist, 'index.html'), 'utf8');
// 编译成功不代表 file:// 能加载，逐一验证 HTML 引用的本地脚本和样式。
for (const match of html.matchAll(/(?:src|href)=["']([^"']+\.(?:js|css)(?:[?#][^"']*)?)["']/g)) {
  const reference = match[1];
  if (/^(?:https?:)?\/\//.test(reference)) continue;
  const assetPath = path.resolve(dist, reference.split(/[?#]/)[0]);
  if (reference.startsWith('/') || !assetPath.startsWith(`${dist}${path.sep}`) || !fs.existsSync(assetPath)) {
    throw new Error(`Desktop asset cannot be loaded from file://: ${reference}`);
  }
}
const versionFile = path.join(root, 'versions', 'version');
const version = fs.existsSync(versionFile)
  ? fs.readFileSync(versionFile, 'utf8').trim()
  : require('../package.json').version;
if (!/^[\w.-]+$/.test(version) || version === '.' || version === '..') {
  throw new Error('Invalid desktop resource version');
}
// 保留发布流程已有的版本号与服务端资源，只更新该版本的前端构建。
const destination = path.join(root, 'versions', version, 'dist');
fs.mkdirSync(destination, { recursive: true });
fs.cpSync(dist, destination, { recursive: true });
fs.writeFileSync(versionFile, version);
console.log('Desktop resources prepared:', destination);
