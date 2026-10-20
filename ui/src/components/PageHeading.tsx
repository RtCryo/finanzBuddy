import type { ReactNode } from 'react'

type PageHeadingProps = {
  title: string
  description: string
  action?: ReactNode
  className?: string
}

export function PageHeading({ title, description, action, className }: PageHeadingProps) {
  return (
    <section className={['page-heading', className].filter(Boolean).join(' ')}>
      <div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action}
    </section>
  )
}
