export const CATEGORY_GROUPS = [
  {
    key: 'food-drink',
    label: 'Food & Drink',
    items: [
      { key: 'Groceries',     label: 'Groceries',     emoji: '🛒' },
      { key: 'Dining Out',    label: 'Dining Out',    emoji: '🍽' },
      { key: 'Liquor / Bars', label: 'Liquor / Bars', emoji: '🍸' },
    ],
  },
  {
    key: 'housing-utilities',
    label: 'Housing & Utilities',
    items: [
      { key: 'Rent',                label: 'Rent',                emoji: '🏠' },
      { key: 'Electricity',         label: 'Electricity',         emoji: '💡' },
      { key: 'Internet / Wi-Fi',    label: 'Internet / Wi-Fi',    emoji: '📶' },
      { key: 'Water & Trash',       label: 'Water & Trash',       emoji: '💧' },
      { key: 'Gas / Heating',       label: 'Gas / Heating',       emoji: '🔥' },
      { key: 'Household Supplies',  label: 'Household Supplies',  emoji: '🧻' },
      { key: 'Furniture & Decor',   label: 'Furniture & Decor',   emoji: '🛋' },
    ],
  },
  {
    key: 'transportation',
    label: 'Transportation',
    items: [
      { key: 'Rideshare / Taxis', label: 'Rideshare / Taxis', emoji: '🚕' },
      { key: 'Fuel / Gas',        label: 'Fuel / Gas',        emoji: '⛽' },
      { key: 'Public Transit',    label: 'Public Transit',    emoji: '🚆' },
    ],
  },
  {
    key: 'travel',
    label: 'Travel',
    items: [
      { key: 'Accommodation', label: 'Accommodation', emoji: '🏨' },
      { key: 'Flights',       label: 'Flights',       emoji: '✈️' },
      { key: 'Car Rental',    label: 'Car Rental',    emoji: '🚗' },
    ],
  },
  {
    key: 'entertainment-lifestyle',
    label: 'Entertainment & Lifestyle',
    items: [
      { key: 'Movies & Entertainment', label: 'Movies & Entertainment', emoji: '🎬' },
      { key: 'Events & Concerts',      label: 'Events & Concerts',      emoji: '🎫' },
      { key: 'Subscriptions',          label: 'Subscriptions',          emoji: '📺' },
      { key: 'Gifts',                  label: 'Gifts',                  emoji: '🎁' },
    ],
  },
]

export const CATEGORIES = [
  ...CATEGORY_GROUPS.flatMap((g) => g.items),
  { key: 'Other', label: 'Other', emoji: '✎' },
]

export const CATEGORY_KEYS = CATEGORIES.map((c) => c.key)

// Older expenses were stored under the legacy category names below. Keep this
// map so existing records still render a meaningful icon after the taxonomy
// change.
const LEGACY_EMOJI = {
  'Food & drink': '🍽',
  'Food & Drink': '🍽',
  'Transport': '🚕',
  'Stay': '🏨',
  'Fun': '🎬',
  'Shopping': '🎁',
  'Health': '💊',
  'Bills': '📄',
  'Payment': '↔',
}

export function categoryEmoji(name) {
  if (!name) return '·'
  const found = CATEGORIES.find((c) => c.key === name)
  if (found) return found.emoji
  return LEGACY_EMOJI[name] || '·'
}
