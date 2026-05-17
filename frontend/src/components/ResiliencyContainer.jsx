import React from 'react';
import { ShieldCheck } from 'lucide-react';

import { BigContainer, SectionTitle } from './common/Containers';

const ResiliencyContainer = ({ logs }) => (
  <BigContainer $delay="0.3s" style={{ gridColumn: '1 / -1' }}>
    <SectionTitle><ShieldCheck size={20} /> Resiliency Orchestrator Logs</SectionTitle>
    <div className="log-stream" style={{ height: '200px' }}>
      {logs.map((log) => (
        <div key={log.id} className={`log-entry log-${log.action}`}>
          <span style={{ color: 'var(--text-muted)' }}>[{new Date(log.timestamp).toLocaleTimeString()}]</span>
          <strong style={{ margin: '0 8px' }}>{log.model}</strong>
          <span style={{ marginRight: '8px' }}>[{log.action}]</span>
          <span>{log.message}</span>
          {log.latency > 0 && <span style={{ marginLeft: 'auto', color: 'cyan', fontSize: '0.7rem' }}> {log.latency}ms</span>}
        </div>
      ))}
    </div>
  </BigContainer>
);

export default ResiliencyContainer;
