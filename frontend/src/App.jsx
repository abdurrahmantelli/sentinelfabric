import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Cpu } from 'lucide-react';
import HealthContainer from './components/HealthContainer';
import SalesContainer from './components/SalesContainer';
import ResiliencyContainer from './components/ResiliencyContainer';
import AgentDialogue from './components/AgentDialogue';

const API_BASE = 'http://localhost:8080/api/monitor';

import { MainLayout, Header, Badge } from './components/common/Containers';

function App() {
  const [data, setData] = useState({
    health: {},
    sales: [],
    logs: [],
    storage: {}
  });
  const [loading, setLoading] = useState(true);

  const fetchDashboard = async () => {
    try {
      const res = await axios.get(`${API_BASE}/dashboard`);
      setData(res.data);
      setLoading(false);
    } catch (err) {
      console.error("Fetch error:", err);
    }
  };

  useEffect(() => {
    fetchDashboard();
    const interval = setInterval(fetchDashboard, 5000);
    return () => clearInterval(interval);
  }, []);

  if (loading) {
    return (
      <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: 'var(--bg)', color: 'white' }}>
        <div className="animate-pulse">Initializing Sentinel Fabric...</div>
      </div>
    );
  }

  return (
    <MainLayout>
      <Header>
        <div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: '800', letterSpacing: '-0.025em' }}>
            SENTINEL <span style={{ color: 'var(--primary)' }}>FABRIC</span>
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>AI Gateway Resiliency & Monitoring</p>
        </div>
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
          <Badge color="rgba(99, 102, 241, 0.2)" style={{ border: '1px solid var(--primary)', color: 'var(--primary)' }}>
            <Cpu size={12} /> V4.2-STABLE
          </Badge>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            Last Update: {new Date().toLocaleTimeString()}
          </div>
        </div>
      </Header>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
        <HealthContainer health={data.health} storage={data.storage} />
        <SalesContainer sales={data.sales} />
      </div>

      <AgentDialogue />

      <ResiliencyContainer logs={data.logs} />
    </MainLayout>
  );
}

export default App;
