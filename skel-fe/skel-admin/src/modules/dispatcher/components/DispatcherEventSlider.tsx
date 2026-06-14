import React from 'react';
import { useTranslation } from 'react-i18next';
import type { DispatcherEvent } from '../types';
import { IconClose } from '../../../components/Icons';
import { FormattedTimestamp } from '../../../components/FormattedTimestamp';
import { DEFAULT_TIMEZONE } from '../../../components/timezone';
import { JsonViewer } from '../../../components/JsonViewer';
import { fmtSev } from '../formatEvent';

interface DetailRowProps {
  label: string;
  children: React.ReactNode;
}

function DetailRow({ label, children }: DetailRowProps) {
  return (
    <div>
      <span className="text-foreground">{label}:</span> {children}
    </div>
  );
}

interface DispatcherEventSliderProps {
  open: boolean;
  event: DispatcherEvent | null;
  onClose: () => void;
}

export function DispatcherEventSlider({ open, event, onClose }: DispatcherEventSliderProps) {
  const { t } = useTranslation();
  const sev = fmtSev(event?.sev);

  return (
    <>
      {open && <div className="slider-backdrop" onClick={onClose} />}

      <div
        className={`slide-panel w-[640px]
          transition-transform duration-300 ease-in-out pointer-events-none
          ${open ? 'translate-x-0 shadow-2xl pointer-events-auto' : 'translate-x-full shadow-none'}`}
      >
        <div className="slide-header shrink-0">
          <h2 className="text-sm font-normal text-foreground flex items-center gap-2 min-w-0">
            {t('common.details')}
            {event?.id && (
              <span className="font-mono text-xs font-normal text-muted-foreground truncate">{event.id}</span>
            )}
          </h2>
          <button
            onClick={onClose}
            className="text-muted-foreground hover:text-foreground p-1 rounded transition-colors shrink-0"
            aria-label={t('common.close')}
          >
            <IconClose size={18} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {event ? (
            <>
              <div className="text-[11px] leading-5 text-muted-foreground space-y-0.5 font-normal">
                <DetailRow label="id">{event.id}</DetailRow>
                <DetailRow label="ts">
                  <FormattedTimestamp ts={event.ts} timezone={DEFAULT_TIMEZONE} />
                  <span className="ml-1">({event.ts})</span>
                </DetailRow>
                {event.auth !== undefined && (
                  <DetailRow label="auth">{event.auth ?? ''}</DetailRow>
                )}
                <DetailRow label={t('dispatcher.fields.sev')}>
                  <span className={sev.cls}>{sev.label}</span>
                </DetailRow>
                <DetailRow label={t('dispatcher.fields.src')}>{event.src ?? ''}</DetailRow>
                <DetailRow label={t('dispatcher.fields.dst')}>{event.dst ?? ''}</DetailRow>
                <DetailRow label={t('dispatcher.fields.sys')}>{event.sys ?? ''}</DetailRow>
                <DetailRow label={t('dispatcher.fields.typ')}>{event.typ ?? ''}</DetailRow>
                <DetailRow label={t('dispatcher.fields.cmd')}>{event.cmd ?? ''}</DetailRow>
              </div>

              <div>
                <div className="text-[11px] text-muted-foreground mb-1 font-normal">data</div>
                <JsonViewer value={event.data} />
              </div>
            </>
          ) : (
            <p className="text-sm text-muted-foreground text-center mt-16">{t('dispatcher.noEvents')}</p>
          )}
        </div>
      </div>
    </>
  );
}
