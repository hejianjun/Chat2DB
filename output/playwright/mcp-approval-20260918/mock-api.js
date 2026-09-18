async (page) => {
  const state = {
    config: {
      authType: 'bearer',
      token: 'ui-acceptance-token',
      sql: { allowWrite: true, allowDdl: true, retainedSqlFlag: 'keep-sql' },
      unknownTopLevel: { marker: 'keep-top-level', values: [1, 2] },
    },
    saves: [],
    requests: [],
    blocked: [],
    errors: [],
  };
  await page.exposeFunction('__mcpAcceptanceRead', () => JSON.parse(JSON.stringify(state)));
  await page.exposeFunction('__mcpAcceptanceSeed', (config) => {
    state.config = config;
    return state.config;
  });
  page.on('pageerror', (error) => state.errors.push(error.message));
  await page.context().route('**/*', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const pathname = url.pathname;
    const api = pathname.startsWith('/api/') || pathname.startsWith('/client/') ||
      ['fetch', 'xhr', 'eventsource'].includes(request.resourceType());
    if (api) {
      const entry = { method: request.method(), pathname, mocked: true };
      state.requests.push(entry);
      const respond = (data) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data }),
      });
      if (pathname === '/api/config/system_config/mcp.server.config' && request.method() === 'GET') {
        return respond({ code: 'mcp.server.config', content: JSON.stringify(state.config) });
      }
      if (pathname === '/api/config/system_config' && request.method() === 'POST') {
        const payload = request.postDataJSON();
        if (payload.code !== 'mcp.server.config' || typeof payload.content !== 'string') {
          throw new Error('Unexpected system config save contract');
        }
        state.config = JSON.parse(payload.content);
        state.saves.push(JSON.parse(JSON.stringify(state.config)));
        entry.payload = payload;
        return respond(null);
      }
      if (pathname === '/api/mcp-server/status') {
        return respond({ connected: false, connectionCount: 0, clients: [] });
      }
      if (pathname === '/api/oauth/user_a') return respond(null);
      if (pathname === '/api/system/get_latest_version') return respond(null);
      if (pathname.startsWith('/api/config/system_config/')) {
        return respond({ code: decodeURIComponent(pathname.split('/').pop()), content: '' });
      }
      return respond(null);
    }
    if (url.origin === 'http://localhost:8000' && ['GET', 'HEAD'].includes(request.method())) {
      return route.continue();
    }
    state.blocked.push({ method: request.method(), url: request.url(), type: request.resourceType() });
    return route.abort('blockedbyclient');
  });
  return { mockInstalled: true, originAllowedForStaticAssets: 'http://localhost:8000', seed: state.config };
}
