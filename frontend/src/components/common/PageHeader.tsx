type PageHeaderProps = {
  title: string
}

function PageHeader({ title }: PageHeaderProps) {
  return <h1>{title}</h1>
}

export default PageHeader
