import { Clock, Droplet, PersonStanding, ShoppingBag, Users, Wrench } from 'lucide-react'
import type { IncidentCategory } from '../data/organizer'
import type { Icon } from './components'

/** Icon and tile colours of each participant report category. */

export const categoryIcon: Record<IncidentCategory, Icon> = {
  crowd: Users,
  queue: Clock,
  accessibility: PersonStanding,
  venue: Wrench,
  lost: ShoppingBag,
}

export const categoryTile: Record<IncidentCategory, string> = {
  crowd: 'bg-warning-soft text-warning',
  queue: 'bg-warning-soft text-warning',
  accessibility: 'bg-primary-soft text-primary',
  venue: 'bg-primary-soft text-primary',
  lost: 'bg-background text-ink',
}

export function incidentIcon(i: { category: IncidentCategory; icon?: 'water' }): Icon {
  return i.icon === 'water' ? Droplet : categoryIcon[i.category]
}
