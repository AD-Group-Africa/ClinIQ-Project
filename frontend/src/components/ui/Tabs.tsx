'use client';

import { useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export interface TabItem {
  id: string;
  label: string;
  content: ReactNode;
}

export interface TabsProps {
  tabs: TabItem[];
  /** Accessible name for the tab list. */
  label: string;
  defaultTabId?: string;
}

/** Accessible tabs following the WAI-ARIA pattern: arrow keys, Home and End move between tabs. */
export function Tabs({ tabs, label, defaultTabId }: TabsProps) {
  const baseId = useId();
  const [selectedId, setSelectedId] = useState<string>(defaultTabId ?? tabs[0]?.id ?? '');
  const tabRefs = useRef<Record<string, HTMLButtonElement | null>>({});

  const selectAndFocus = (id: string) => {
    setSelectedId(id);
    tabRefs.current[id]?.focus();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const index = tabs.findIndex((tab) => tab.id === selectedId);
    let nextIndex = -1;
    if (event.key === 'ArrowRight') nextIndex = (index + 1) % tabs.length;
    else if (event.key === 'ArrowLeft') nextIndex = (index - 1 + tabs.length) % tabs.length;
    else if (event.key === 'Home') nextIndex = 0;
    else if (event.key === 'End') nextIndex = tabs.length - 1;
    if (nextIndex < 0) return;
    event.preventDefault();
    selectAndFocus(tabs[nextIndex].id);
  };

  return (
    <div>
      <div role="tablist" aria-label={label} onKeyDown={handleKeyDown} className="flex gap-1 overflow-x-auto border-b border-line">
        {tabs.map((tab) => {
          const selected = tab.id === selectedId;
          return (
            <button
              key={tab.id}
              ref={(element) => {
                tabRefs.current[tab.id] = element;
              }}
              id={`${baseId}-tab-${tab.id}`}
              type="button"
              role="tab"
              aria-selected={selected}
              aria-controls={`${baseId}-panel-${tab.id}`}
              tabIndex={selected ? 0 : -1}
              onClick={() => setSelectedId(tab.id)}
              className={cn(
                'relative min-h-11 whitespace-nowrap px-4 text-sm font-bold transition-colors',
                selected ? 'text-ink' : 'text-muted hover:text-ink'
              )}
            >
              {tab.label}
              {/* The underline is a second cue for the selected tab (not colour alone). */}
              <span aria-hidden="true" className={cn('absolute inset-x-2 bottom-0 h-0.5 rounded-full', selected ? 'bg-brand-dark' : 'bg-transparent')} />
            </button>
          );
        })}
      </div>
      {tabs.map((tab) => (
        <div
          key={tab.id}
          id={`${baseId}-panel-${tab.id}`}
          role="tabpanel"
          aria-labelledby={`${baseId}-tab-${tab.id}`}
          hidden={tab.id !== selectedId}
          className="pt-6"
        >
          {tab.content}
        </div>
      ))}
    </div>
  );
}
