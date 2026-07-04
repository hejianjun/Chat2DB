import createRequest from './base';

export interface IMcpStatus {
  connected: boolean;
  connectionCount: number;
}

const getStatus = createRequest<void, IMcpStatus>('/api/mcp-server/status', {
  errorLevel: false,
});

export default {
  getStatus,
};
