type PageHeaderProps = {
  title: string
}

function PageHeader({ title }: PageHeaderProps) {
  return (
    <h1 className="text-[34px] font-extrabold leading-tight tracking-normal text-slate-950">
      {title}
    </h1>
  )
}

export default PageHeader
