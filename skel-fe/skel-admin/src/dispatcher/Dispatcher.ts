import type { DispatcherEvent, SysHandler, DispatcherStats } from './types';

const QUEUE_MAX = 100;
const HISTORY_SIZE = Math.max(
  10,
  parseInt(import.meta.env.VITE_DISPATCHER_HISTORY_SIZE ?? '1000', 10) || 1000,
);

class Dispatcher {
  private readonly handlers = new Map<string, SysHandler[]>();
  private readonly queue: DispatcherEvent[] = [];
  private history: DispatcherEvent[] = [];
  private processing = false;
  private readonly dispatcherId: string = import.meta.env.VITE_DISPATCHER_ID ?? '';
  private readonly listeners = new Set<() => void>();

  register(sys: string, handler: SysHandler): () => void {
    const list = this.handlers.get(sys) ?? [];
    this.handlers.set(sys, [...list, handler]);
    return () => {
      const current = this.handlers.get(sys) ?? [];
      this.handlers.set(sys, current.filter(h => h !== handler));
    };
  }

  dispatch(event: DispatcherEvent): void {
    // Filter by dst — if we have an id set and event targets a different id, drop silently
    if (this.dispatcherId && event.dst && event.dst !== this.dispatcherId) return;

    // Always add to history; drop oldest when at capacity
    this.history = [event, ...this.history].slice(0, HISTORY_SIZE);
    this.notifyListeners();

    // Drop newest on queue overflow
    if (this.queue.length >= QUEUE_MAX) {
      console.warn(`[Dispatcher] Queue overflow (max ${QUEUE_MAX}), dropping event id=${event.id} sys="${event.sys}"`);
      return;
    }

    this.queue.push(event);
    this.processQueue();
  }

  private async processQueue(): Promise<void> {
    if (this.processing) return;
    this.processing = true;
    while (this.queue.length > 0) {
      const event = this.queue.shift()!;
      await this.route(event);
    }
    this.processing = false;
  }

  private async route(event: DispatcherEvent): Promise<void> {
    const handlers = this.handlers.get(event.sys) ?? [];
    if (handlers.length === 0) {
      if (event.sys !== '') {
        console.warn(`[Dispatcher] No handler for sys="${event.sys}", dropping event id=${event.id}`);
      }
      return;
    }
    for (const handler of handlers) {
      try {
        await handler(event);
      } catch (err) {
        console.error(`[Dispatcher] Handler error sys="${event.sys}" id=${event.id}:`, err);
      }
    }
  }

  getHistory(): readonly DispatcherEvent[] {
    return this.history;
  }

  getStats(): DispatcherStats {
    const byTyp: Record<string, number> = {};
    const bySys: Record<string, number> = {};
    for (const e of this.history) {
      const typ = e.typ ?? '—';
      byTyp[typ] = (byTyp[typ] ?? 0) + 1;
      const sys = e.sys || '(default)';
      bySys[sys] = (bySys[sys] ?? 0) + 1;
    }
    return { total: this.history.length, byTyp, bySys };
  }

  subscribe(cb: () => void): () => void {
    this.listeners.add(cb);
    return () => this.listeners.delete(cb);
  }

  private notifyListeners(): void {
    this.listeners.forEach(cb => cb());
  }
}

export const dispatcher = new Dispatcher();
