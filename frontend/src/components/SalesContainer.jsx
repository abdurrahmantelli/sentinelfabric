import React from 'react';
import { TrendingUp, Globe } from 'lucide-react';

import { BigContainer, SectionTitle } from './common/Containers';

const SalesContainer = ({ sales }) => (
  <BigContainer $delay="0.2s">
    <SectionTitle><TrendingUp size={20} /> Enterprise Sales Feed</SectionTitle>
    <table className="sales-table">
      <thead>
        <tr>
          <th>Product</th>
          <th>Region</th>
          <th>Amount</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        {sales.map((sale) => (
          <tr key={sale.id}>
            <td>{sale.productName}</td>
            <td><Globe size={12} style={{ marginRight: '4px' }}/> {sale.region}</td>
            <td style={{ fontWeight: 'bold' }}>${sale.amount.toLocaleString()}</td>
            <td><span style={{ color: 'var(--success)', fontSize: '0.75rem' }}>● Settled</span></td>
          </tr>
        ))}
      </tbody>
    </table>
  </BigContainer>
);

export default SalesContainer;
