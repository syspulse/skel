import React, { memo, useCallback, useEffect, useRef, useState } from 'react';
import { Handle, Position, NodeProps, useReactFlow, Node } from 'reactflow';
import {truncateAddr, truncateAddrChart} from '../util/Util';
import { networksMap } from '../Network';
import './CustomNode.css';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeRaw from 'rehype-raw';
import { PieChart, Pie, Cell, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { getGPTResponse } from '../services/openai';

export type NodeType = 'address' | 'service' | 'gpt' | 'chart' | 'info';

interface PieChartEntry {
  address: string;
  current_balance: number;
}

interface TimeSeriesEntry {
  block_date: string;
  total_transactions: number;
}

interface ChartData {
  style: 'pie' | 'timeseries';
  data: PieChartEntry[] | TimeSeriesEntry[];
}

interface CustomNodeData {
  nodeType: NodeType;
  title: string;
  description: string;
  icon: string;
  extraHandles?: { type: 'source' | 'target',  position: Position}[];
  tags: string;
  network: string;
  inputText?: string;
  chartData?: ChartData;
  telemetry?: {
    txCount: number;
    alertCount: number;
    detectorCount: number;
    severityCritical: number;
    severityHigh: number;
    severityMedium: number;
    severityLow: number;
    severityInfo: number;
  };
  input?: string;
  output?: string;
}

function CustomNode({ data, id, selected }: NodeProps<CustomNodeData>) {
  const { deleteElements, setNodes } = useReactFlow();
  const [inputText, setInputText] = useState(data.inputText || '');
  const [input, setInput] = useState(data.input || '');
  const [output, setOutput] = useState(data.output || '');
  const [isLoading, setIsLoading] = useState(false);

  // Update inputText when data changes
  useEffect(() => {
    if (data.inputText !== undefined) {
      setInputText(data.inputText);
    }
  }, [data.inputText]);

  // Update node data when inputText changes
  useEffect(() => {
    setNodes((nds) =>
      nds.map((node) =>
        node.id === id
          ? { ...node, data: { ...node.data, inputText } }
          : node
      )
    );
  }, [inputText, id, setNodes]);

  // --------------------------------------------------------------- Visual Effect only
  const txCountRef = useRef<HTMLSpanElement>(null);
  var counter = -1;
  useEffect(() => {    
    if (data.telemetry?.txCount !== counter && txCountRef.current) {
      txCountRef.current.classList.add('blink');
      setTimeout(() => txCountRef.current?.classList.remove('blink'), 1000);
      counter = data.telemetry?.txCount || 0;
    }
  }, [data.telemetry?.txCount, id]);
  // --------------------------------------------------------------- Visual Effect only

  const onDelete = () => {
    deleteElements({ nodes: [{ id }] });
  };

  const handleDoubleClick = useCallback(() => {
    const url = `https://app.extractor.dev.hacken.cloud/${id}/overview`;
    window.open(url, '_blank', 'noopener,noreferrer');
  }, [id]);

  const network = networksMap.get(data.network);  

  const handleInputChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value);
    data.input = e.target.value;
  };

  const handleSubmit = async () => {
    if (!input.trim()) return;
    
    setIsLoading(true);
    try {
      const response = await getGPTResponse(input);
      setOutput(response);
      data.output = response;
    } catch (error) {
      console.error('Error getting GPT response:', error);
      setOutput('Error: Failed to get response from GPT');
      data.output = 'Error: Failed to get response from GPT';
    } finally {
      setIsLoading(false);
    }
  };

  const renderGPTNode = () => {
    const handleWheelCapture = (e: React.WheelEvent) => {
      const target = e.target as HTMLElement;
      if (target.closest('.gpt-input') || target.closest('.gpt-output')) {
        e.stopPropagation();
        e.preventDefault();
        const container = target.closest('.gpt-input') || target.closest('.gpt-output');
        if (container) {
          container.scrollTop += e.deltaY;
        }
      }
    };

    return (
      <div className="gpt-node" onWheelCapture={handleWheelCapture}>
        <div className="custom-node-title">
          {data.title}
        </div>
        <div className="gpt-input-container">
          <textarea
            className="gpt-input"
            value={input}
            onChange={handleInputChange}
            placeholder="Enter your prompt..."
          />
        </div>
        <button 
          onClick={handleSubmit}
          disabled={isLoading || !input.trim()}
          className="submit-button"
        >
          {isLoading ? 'Loading...' : 'Submit'}
        </button>
        <div className="gpt-output-container">
          <div className="gpt-output">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>
              {output}
            </ReactMarkdown>
          </div>
        </div>
      </div>
    );
  };

  const renderInfoNode = () => {
    const handleWheelCapture = (e: React.WheelEvent) => {
      const target = e.target as HTMLElement;
      if (target.closest('.info-input') || target.closest('.info-output')) {
        e.stopPropagation();
        e.preventDefault();
        const container = target.closest('.info-input') || target.closest('.info-output');
        if (container) {
          container.scrollTop += e.deltaY;
        }
      }
    };

    return (
      <div className="info-node" onWheelCapture={handleWheelCapture}>
        <div className="custom-node-title">
          {data.title}
        </div>
        <div className="info-input-container">
          <textarea
            className="info-input"
            rows={3}
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            placeholder="Enter your input here..."
          />
        </div>
        <div className="info-output-container">
          <div className="info-output">
            <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeRaw]}>{inputText}</ReactMarkdown>
          </div>
        </div>
      </div>
    );
  };

  const renderChartNode = (data: CustomNodeData) => {
    const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884d8', '#82ca9d', '#ffc658', '#ff8042', '#8884d8', '#82ca9d'];

    if (!data.chartData) return null;

    const { style, data: chartData } = data.chartData;

    if (style === 'pie') {
      return (
        <div className="chart-container">
          <ResponsiveContainer>
            <PieChart>
              <Pie
                data={chartData as PieChartEntry[]}
                dataKey="current_balance"
                nameKey="address"
                cx="50%"
                cy="50%"
                innerRadius={40}
                outerRadius={60}
                label={({ address, percent, x, y }: { address: string; percent: number; x: number; y: number }) => (
                  <text
                    x={x}
                    y={y}
                    textAnchor="middle"
                    fill="#666"
                    style={{ fontSize: '6px' }}
                  >
                    {`${truncateAddrChart(address)} ${(percent * 100).toFixed(0)}%`}
                  </text>
                )}
              >
                {(chartData as PieChartEntry[]).map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
        </div>
      );
    }

    if (style === 'timeseries') {
      return (
        <div className="chart-container">
          <ResponsiveContainer>
            <LineChart 
              data={chartData as TimeSeriesEntry[]}
              margin={{ top: 5, right: 5, bottom: 5, left: 0 }}
            >
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis 
                dataKey="block_date" 
                tick={{ fontSize: 6 }}
                angle={-45}
                textAnchor="end"
                height={30}
              />
              <YAxis 
                tick={{ fontSize: 6 }}
                width={30}
              />
              <Tooltip contentStyle={{ fontSize: '6px' }} />
              <Line 
                type="monotone" 
                dataKey="total_transactions" 
                stroke="#8884d8" 
                strokeWidth={2}
                dot={{ r: 4 }}
                activeDot={{ r: 6 }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      );
    }

    return null;
  };

  return (
    <div>
      <button className="delete-button" onClick={onDelete}>×</button>
      {network && (
        <img 
          src={network.icon} 
          alt={network.name} 
          className="custom-node-network-icon"
          title={network.name}
        />
      )}
      <span className="custom-node-id-label">{id}</span>
      
      <Handle type="target" position={Position.Top} id="top-target" className="target-handle" />
      <Handle type="source" position={Position.Top} id="top-source" className="source-handle" />
      
      <Handle type="target" position={Position.Left} id="left-target" className="target-handle" />
      <Handle type="source" position={Position.Left} id="left-source" className="source-handle"  />
      
      <Handle type="target" position={Position.Right} id="right-target" className="target-handle" />
      <Handle type="source" position={Position.Right} id="right-source" className="source-handle" />
      
      <Handle type="target" position={Position.Bottom} id="bottom-target" className="target-handle" />
      <Handle type="source" position={Position.Bottom} id="bottom-source" className="source-handle" />

      {data.extraHandles?.map((handle, index) => (        
        <Handle type="source" position={Position.Top} id="top-source-10" className="source-handle" style={{transform:'translate(+50%, 0%)'}} />
      ))}
      
      {data.nodeType === 'info' ? (
        renderInfoNode()
      ) : data.nodeType === 'gpt' ? (
        renderGPTNode()
      ) : data.nodeType === 'chart' ? (
        <>
          <div className="custom-node-title">
            {data.title}
          </div>
          {renderChartNode(data)}
        </>
      ) : (
        <>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            {data.icon && (
              <img 
                src={data.icon} 
                className="custom-node-icon"
                onDoubleClick={handleDoubleClick}
                style={{ cursor: 'pointer' }}
              />
            )}

            <div>                    
              <div className="custom-node-title">
                {data.title}
              </div>

              {data.tags && (
                <div className="custom-node-tags">
                  {data.tags.split(',').map((tag, index) => (
                    <span key={index} className="custom-node-tag">{tag}</span>
                  ))}
                </div>
              )}
              <a 
                href={`${networksMap.get(data.network)?.explorer}/address/${data.description}`}
                target="_blank" 
                rel="noopener noreferrer"
                className="custom-node-description"
              >
                {truncateAddr(data.description)}
              </a>
            </div>        
          </div>

          {data.telemetry && (
            <>
              <div className="custom-node-separator"></div>
              <div className="custom-node-counts">
                <div className="count-item">
                  <span className="count-label">Tx:</span>
                  <span ref={txCountRef} className="count-value">{data.telemetry?.txCount}</span>
                </div>
                
                <div className="count-item">
                  <span className="count-label">Mo:</span>
                  <span className="count-value-detector">{data.telemetry?.detectorCount}</span>
                </div>

                <div className="count-item-alerts">
                  <span className={data.telemetry.severityCritical && data.telemetry.severityCritical != 0 ? "count-value-alert-crit" : "count-value-alert" }>{data.telemetry?.severityCritical}</span>
                  <span className={data.telemetry.severityHigh && data.telemetry.severityHigh != 0 ? "count-value-alert-major" : "count-value-alert" }>{data.telemetry?.severityHigh}</span>
                  <span className={data.telemetry.severityMedium && data.telemetry.severityMedium != 0 ? "count-value-alert-med" : "count-value-alert" }>{data.telemetry?.severityMedium}</span>
                  <span className={data.telemetry.severityLow && data.telemetry.severityLow != 0 ? "count-value-alert-low" : "count-value-alert" }>{data.telemetry?.severityLow}</span>
                </div> 
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
}

export default CustomNode;
