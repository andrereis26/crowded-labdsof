import { SquareKanban, type LucideProps } from 'lucide-react'

/** Icons of the mockups that lucide-react does not have under the same name. */

/** "icon/poll": a square with bars growing from the bottom. */
export function PollIcon(props: LucideProps) {
  return <SquareKanban {...props} style={{ transform: 'rotate(180deg)', ...props.style }} />
}
