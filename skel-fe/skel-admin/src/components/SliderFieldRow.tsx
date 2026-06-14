import React from 'react';

/** Fixed label column widths used in slide-panel "label : control" rows. */
export type SliderLabelWidth = '10' | '20' | '24';

const LABEL_CLASS: Record<SliderLabelWidth, string> = {
  '10': 'row-label-10',
  '20': 'row-label-20',
  '24': 'row-label-24',
};

export interface SliderFieldRowProps {
  label: React.ReactNode;
  children: React.ReactNode;
  /** Label column width; default `24` (workflow / explain). Dash uses `20`. */
  labelWidth?: SliderLabelWidth;
}

/** A labeled form row inside slide-panel detail sliders. */
export function SliderFieldRow({ label, children, labelWidth = '24' }: SliderFieldRowProps) {
  return (
    <div className="field-row">
      <label className={LABEL_CLASS[labelWidth]}>{label}</label>
      {children}
    </div>
  );
}
