import React, { useMemo, useRef, useState } from 'react';
import { t } from '../../i18n/index.js';
import { languageTag } from '../../i18n/locale.js';
import TiwiIconButton from '../TiwiIconButton.jsx';
import { IconDelete, IconFilter, IconMic } from '../Icons.jsx';

export default function SearchBarSection({
  query,
  onQueryChange,
  onSubmit,
  onClear,
  onOpenFilters,
  activeFilterCount = 0,
  inputRef,
}) {
  const localRef = useRef(null);
  const [isListening, setIsListening] = useState(false);

  const speechCtor = useMemo(() => {
    if (typeof window === 'undefined') return null;
    return window.SpeechRecognition || window.webkitSpeechRecognition || null;
  }, []);

  const hasFilters = activeFilterCount > 0;
  const showClear = Boolean(query) || hasFilters;
  const filterTooltip = hasFilters
    ? t('filter_button_count', activeFilterCount)
    : t('filter_button_label');

  const handleVoiceSearch = () => {
    if (!speechCtor || isListening) return;
    try {
      const recognition = new speechCtor();
      recognition.lang = languageTag();
      recognition.interimResults = false;
      recognition.maxAlternatives = 1;
      setIsListening(true);
      recognition.onresult = (event) => {
        const transcript = event.results?.[0]?.[0]?.transcript || '';
        if (transcript.trim()) {
          onQueryChange?.(transcript.trim());
          onSubmit?.(transcript.trim());
        }
      };
      recognition.onend = () => setIsListening(false);
      recognition.onerror = () => setIsListening(false);
      recognition.start();
    } catch {
      setIsListening(false);
    }
  };

  return (
    <div className="search-bar-row">
      <div className="search-field-wrap">
        <input
          ref={(node) => {
            localRef.current = node;
            if (typeof inputRef === 'function') inputRef(node);
            else if (inputRef) inputRef.current = node;
          }}
          type="search"
          value={query}
          placeholder={t('common_search_placeholder')}
          className="search-field-input"
          data-focusable="true"
          data-focus-zone="content"
          onChange={(e) => onQueryChange?.(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              onSubmit?.(query);
            }
          }}
        />
      </div>

      {speechCtor && (
        <TiwiIconButton
          icon={<IconMic size={20} />}
          tooltip={t('voice_search_tooltip')}
          tooltipPosition="below"
          variant="surface"
          primaryTint={isListening}
          onClick={handleVoiceSearch}
        />
      )}

      {showClear && (
        <TiwiIconButton
          icon={<IconDelete size={20} />}
          tooltip={t('common_clear')}
          tooltipPosition="below"
          variant="surface"
          onClick={() => {
            onClear?.();
            localRef.current?.focus();
          }}
        />
      )}

      <TiwiIconButton
        icon={<IconFilter size={20} />}
        tooltip={filterTooltip}
        tooltipPosition="below"
        variant="surface"
        primaryTint={hasFilters}
        onClick={onOpenFilters}
      />
    </div>
  );
}
