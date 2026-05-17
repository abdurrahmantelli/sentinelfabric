import React, { useState, useRef, useEffect } from 'react';
import styled from 'styled-components';
import { Send, Bot, User, Loader2 } from 'lucide-react';
import axios from 'axios';
import { BigContainer, SectionTitle } from './common/Containers';

const ChatWindow = styled.div`
  height: 250px;
  overflow-y: auto;
  padding: 1rem;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-bottom: 1rem;
  border: 1px solid var(--border);

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--border);
    border-radius: 3px;
  }
`;

const Message = styled.div`
  display: flex;
  gap: 0.75rem;
  align-self: ${props => props.$isUser ? 'flex-end' : 'flex-start'};
  max-width: 80%;
`;

const MessageBubble = styled.div`
  padding: 0.75rem 1rem;
  border-radius: 1rem;
  font-size: 0.9rem;
  background: ${props => props.$isUser ? 'var(--primary)' : 'rgba(255, 255, 255, 0.05)'};
  color: ${props => props.$isUser ? 'white' : 'var(--text)'};
  border: 1px solid ${props => props.$isUser ? 'transparent' : 'var(--border)'};
  border-bottom-right-radius: ${props => props.$isUser ? '0.25rem' : '1rem'};
  border-bottom-left-radius: ${props => props.$isUser ? '1rem' : '0.25rem'};
`;

const InputArea = styled.form`
  display: flex;
  gap: 0.5rem;
`;

const StyledInput = styled.input`
  flex: 1;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border);
  border-radius: 0.5rem;
  padding: 0.75rem 1rem;
  color: white;
  outline: none;
  transition: border-color 0.3s;

  &:focus {
    border-color: var(--primary);
  }
`;

const SendButton = styled.button`
  background: var(--primary);
  color: white;
  border: none;
  border-radius: 0.5rem;
  width: 45px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.3s;

  &:hover {
    background: var(--primary-hover);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const AgentDialogue = () => {
  const [messages, setMessages] = useState([
    { text: "Hello! I am the Sentinel Fabric Orchestrator. How can I assist you with infrastructure resiliency today?", isUser: false }
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const scrollRef = useRef(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!input.trim() || loading) return;

    const userMsg = { text: input, isUser: true };
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    try {
      const res = await axios.post('http://localhost:8080/api/chat', { query: input });
      const botMsg = { 
        text: res.data.text, 
        model: res.data.model,
        source: res.data.source,
        latency: res.data.latency,
        isUser: false 
      };
      setMessages(prev => [...prev, botMsg]);
    } catch (err) {
      setMessages(prev => [...prev, { text: "Communication error with orchestrator. Check backend status.", isUser: false, isError: true }]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <BigContainer $delay="0.4s">
      <SectionTitle><Bot size={20} /> Agent Orchestrator Dialogue</SectionTitle>
      <ChatWindow ref={scrollRef}>
        {messages.map((msg, i) => (
          <Message key={i} $isUser={msg.isUser}>
            {!msg.isUser && <Bot size={16} style={{ color: 'var(--accent)', marginTop: '4px' }} />}
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <MessageBubble $isUser={msg.isUser}>
                {msg.text}
              </MessageBubble>
              {(msg.source || msg.model) && (
                <span style={{
                  fontSize: '0.7rem',
                  color: msg.source && msg.source.includes('Semantic') ? 'var(--accent)' : 
                         msg.source && msg.source.includes('Redis') ? 'var(--primary)' : 'var(--text)',
                  opacity: 0.85,
                  marginTop: '4px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px'
                }}>
                  {msg.source && msg.source.includes('Semantic') ? '⚡' : 
                   msg.source && msg.source.includes('Redis') ? '🗄️' : 
                   msg.source && msg.source.includes('Shield') ? '🛡️' : '🤖'}
                  {msg.source || msg.model}
                  {msg.latency && <span style={{ opacity: 0.6 }}> · {msg.latency}ms</span>}
                </span>
              )}
            </div>
            {msg.isUser && <User size={16} style={{ color: 'var(--primary)', marginTop: '4px' }} />}
          </Message>
        ))}
        {loading && (
          <Message $isUser={false}>
            <Bot size={16} style={{ color: 'var(--accent)', marginTop: '4px' }} />
            <MessageBubble $isUser={false}>
              <Loader2 size={16} className="animate-spin" />
            </MessageBubble>
          </Message>
        )}
      </ChatWindow>
      <InputArea onSubmit={handleSubmit}>
        <StyledInput 
          value={input} 
          onChange={(e) => setInput(e.target.value)} 
          placeholder="Ask about inventory, system status, or run commands..."
          disabled={loading}
        />
        <SendButton type="submit" disabled={loading}>
          <Send size={18} />
        </SendButton>
      </InputArea>
    </BigContainer>
  );
};

export default AgentDialogue;
