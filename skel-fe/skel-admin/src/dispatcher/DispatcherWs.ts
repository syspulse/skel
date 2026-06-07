import { dispatcher } from './Dispatcher';
import type { DispatcherEvent } from './types';

export interface WsConfig {
  url:              string;
  token?:           string;
  onConnected?:     () => void;
  onDisconnected?:  () => void;
  onMessage?:       () => void;
  onReconnectAttempt?: () => void;
}

const RETRY_MIN  = 1_000;
const RETRY_MAX  = 30_000;

export class DispatcherWs {
  private ws: WebSocket | null = null;
  private retryDelay = RETRY_MIN;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private destroyed = false;

  constructor(private readonly config: WsConfig) {}

  connect(): void {
    if (this.destroyed) return;

    // Browsers cannot set custom headers on WebSocket — pass token as query param
    const { url, token } = this.config;
    const wsUrl = token
      ? `${url}${url.includes('?') ? '&' : '?'}token=${encodeURIComponent(token)}`
      : url;

    try {
      this.ws = new WebSocket(wsUrl);
    } catch (err) {
      console.error('[DispatcherWs] Failed to create WebSocket:', err);
      this.scheduleReconnect();
      return;
    }

    this.ws.onopen = () => {
      this.retryDelay = RETRY_MIN;
      this.config.onConnected?.();
    };

    this.ws.onmessage = (e: MessageEvent) => {
      this.config.onMessage?.();
      try {
        const event = JSON.parse(e.data as string) as DispatcherEvent;
        dispatcher.dispatch(event);
      } catch (err) {
        console.error('[DispatcherWs] Failed to parse event:', err, e.data);
      }
    };

    this.ws.onclose = () => {
      this.config.onDisconnected?.();
      if (!this.destroyed) this.scheduleReconnect();
    };

    this.ws.onerror = (e) => {
      console.error('[DispatcherWs] WebSocket error:', e);
    };
  }

  private scheduleReconnect(): void {
    this.config.onReconnectAttempt?.();
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      this.connect();
    }, this.retryDelay);
    this.retryDelay = Math.min(this.retryDelay * 2, RETRY_MAX);
  }

  destroy(): void {
    this.destroyed = true;
    if (this.retryTimer !== null) clearTimeout(this.retryTimer);
    this.ws?.close();
    this.ws = null;
  }
}
