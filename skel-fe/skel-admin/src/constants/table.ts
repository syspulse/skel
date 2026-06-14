/** Shared vertical padding for table header/body cells (Explain, Dash, Dispatcher). */
export const TABLE_ROW_PY = 'py-2';

/** Standard horizontal + vertical padding for table cells. */
export const TABLE_CELL_PAD = `px-3 ${TABLE_ROW_PY}`;

/** Body cell typography — consistent row height across modules. */
export const TABLE_TD = `${TABLE_CELL_PAD} text-xs leading-none`;

/** Header cell typography. */
export const TABLE_TH = `${TABLE_CELL_PAD} text-xs leading-none`;

/** Icon column cell. Icon pixel size is set in styles.css via --table-icon-size (.table-icon). */
export const TABLE_ICON_CELL = `px-2 ${TABLE_ROW_PY} text-xs leading-none text-center`;
