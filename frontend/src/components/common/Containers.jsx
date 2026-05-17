import styled, { keyframes } from 'styled-components';

const fadeIn = keyframes`
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
`;

export const MainLayout = styled.div`
  max-width: 1400px;
  margin: 0 auto;
  padding: 2rem;
  display: grid;
  grid-template-columns: 1fr 2fr;
  grid-template-rows: auto 1fr;
  gap: 1.5rem;
  min-height: 100vh;
  height: auto;
  color: var(--text);
  background-color: var(--bg);
`;

export const Header = styled.header`
  grid-column: 1 / -1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--border);
`;

export const BigContainer = styled.div`
  background: var(--card-bg);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border);
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  animation: ${fadeIn} 0.5s ease-out forwards;
  animation-delay: ${props => props.$delay || '0s'};
  opacity: 0;
`;

export const SmallContainer = styled.div`
  background: rgba(255, 255, 255, 0.05);
  padding: 1.25rem;
  border-radius: 0.75rem;
  text-align: center;
  border: 1px solid transparent;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--primary);
    background: rgba(255, 255, 255, 0.08);
    transform: translateY(-2px);
  }
`;

export const SectionTitle = styled.h2`
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 1.25rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--accent);
`;

export const Badge = styled.span`
  padding: 0.25rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 500;
  background: ${props => props.color || 'var(--primary)'};
  color: white;
  display: flex;
  align-items: center;
  gap: 4px;
`;

export const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(${props => props.min || '120px'}, 1fr));
  gap: 1rem;
`;
