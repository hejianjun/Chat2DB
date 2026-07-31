import createRequest from './base';

export interface IMcpClientConnection {
  clientInfo?: {
    name?: string;
    version?: string;
    protocolVersion?: string;
  };
  connectedAt: string;
  lastActiveAt: string;
}

export interface IMcpStatus {
  connected: boolean;
  connectionCount: number;
  clients: IMcpClientConnection[];
}

const getStatus = createRequest<void, IMcpStatus>('/api/mcp-server/status', {
  errorLevel: false,
});

export default {
  getStatus,
};
