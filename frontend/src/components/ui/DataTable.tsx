import { Fragment, type ReactNode } from 'react';

export interface DataTableColumn<T> {
  id: string;
  header: string;
  cell: (row: T) => ReactNode;
  /** The column that identifies the row (for example the patient name). Becomes the row header. */
  primary?: boolean;
}

export interface DataTableProps<T> {
  /** Describes the table for screen readers (also used as the mobile list label). */
  caption: string;
  columns: DataTableColumn<T>[];
  rows: T[];
  getRowId: (row: T) => string;
}

/**
 * Responsive table.
 *  - 561px and wider: a real <table> that scrolls sideways (keyboard-focusable) if it is too wide.
 *  - Narrower: each row becomes a stacked card with label/value pairs, so nothing is squeezed or cut off.
 * Only one of the two is visible at a time, so screen readers never read the data twice.
 */
export function DataTable<T>({ caption, columns, rows, getRowId }: DataTableProps<T>) {
  const primary = columns.find((column) => column.primary) ?? columns[0];
  const secondary = columns.filter((column) => column !== primary);

  return (
    <>
      <div role="region" aria-label={caption} tabIndex={0} className="hidden overflow-x-auto xs:block">
        <table className="w-full min-w-[520px] text-sm">
          <caption className="sr-only">{caption}</caption>
          <thead className="bg-surface-muted">
            <tr>
              {columns.map((column) => (
                <th key={column.id} scope="col" className="type-th px-6 py-3.5 text-left">
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-line-subtle">
            {rows.map((row) => (
              <tr key={getRowId(row)}>
                {columns.map((column) =>
                  column === primary ? (
                    <th key={column.id} scope="row" className="px-6 py-4 text-left font-bold text-ink">
                      {column.cell(row)}
                    </th>
                  ) : (
                    <td key={column.id} className="px-6 py-4 text-muted">
                      {column.cell(row)}
                    </td>
                  )
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ul aria-label={caption} className="divide-y divide-line-subtle xs:hidden">
        {rows.map((row) => (
          <li key={getRowId(row)} className="space-y-3 px-4 py-4">
            <p className="font-bold text-ink">{primary.cell(row)}</p>
            <dl className="grid grid-cols-[auto,1fr] items-center gap-x-4 gap-y-2 text-sm">
              {secondary.map((column) => (
                <Fragment key={column.id}>
                  <dt className="font-semibold text-muted">{column.header}</dt>
                  <dd className="text-ink">{column.cell(row)}</dd>
                </Fragment>
              ))}
            </dl>
          </li>
        ))}
      </ul>
    </>
  );
}
