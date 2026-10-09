import { Pill } from './Pill.jsx'

const LABEL = {
  trip: 'Trip',
  home: 'Home',
  general: 'General',
}

const TONE = {
  trip: 'coral',
  home: 'sage',
  general: 'mustard',
}

export function GroupTypeTag({ type }) {
  const label = LABEL[type] || String(type || '').replace(/^./, (c) => c.toUpperCase())
  return <Pill tone={TONE[type] || 'neutral'}>{label}</Pill>
}
