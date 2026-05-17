import React from 'react';
import { Activity, Database, Zap, Server } from 'lucide-react';

import { BigContainer, SectionTitle, SmallContainer, Grid, Badge } from './common/Containers';

const HealthContainer = ({ health, storage }) => (
  <BigContainer $delay="0.1s">
    <SectionTitle><Activity size={20} /> System Health</SectionTitle>
    <Grid>
      {Object.entries(health).map(([name, status]) => (
        <SmallContainer key={name}>
          <div className={`status-indicator ${status === 'ACTIVE' ? 'status-active' : 'status-down'}`} />
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{name}</div>
          <div style={{ fontWeight: 'bold', fontSize: '0.9rem' }}>{status}</div>
        </SmallContainer>
      ))}
    </Grid>
    
    <SectionTitle style={{ marginTop: '2rem' }}><Database size={20} /> Storage Fabric</SectionTitle>
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
      <SmallContainer style={{ display: 'flex', justifyContent: 'space-between', textAlign: 'left', alignItems: 'center' }}>
        <span><Zap size={14} style={{ marginRight: '5px' }} color="cyan"/> Redis (L1 Cache)</span>
        <Badge color={storage?.Redis?.includes('CONNECTED') ? 'var(--success)' : 'var(--danger)'}>
          {storage?.Redis || 'OFFLINE'}
        </Badge>
      </SmallContainer>
      <SmallContainer style={{ display: 'flex', justifyContent: 'space-between', textAlign: 'left', alignItems: 'center' }}>
        <span><Server size={14} style={{ marginRight: '5px' }} color="orange"/> Legacy DB (MySQL)</span>
        <Badge color={storage?.LegacyDB?.includes('CONNECTED') ? 'var(--success)' : 'var(--danger)'}>
          {storage?.LegacyDB || 'OFFLINE'}
        </Badge>
      </SmallContainer>
    </div>
  </BigContainer>
);

export default HealthContainer;
