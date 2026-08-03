/** Shared formatting so every screen renders amounts and dates the same way. */

export function formatMoney(amount: number | string): string {
  const value = Number(amount)
  if (Number.isNaN(value)) return String(amount)
  return value.toLocaleString('en-US', { style: 'currency', currency: 'USD' })
}

export function formatDate(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

export function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1)
}

export function formatType(type: string): string {
  return type === 'billpay' ? 'Bill Pay' : capitalize(type)
}
