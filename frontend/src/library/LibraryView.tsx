import React from 'react'
import { Box } from '@mui/material'
import { DataGrid, GridColDef, GridPaginationModel } from '@mui/x-data-grid'

type LibraryRow = { id: string; uuid: string; plugin: string; configJson: string; updatedAt: string }

export function LibraryView() {
  const [rows, setRows] = React.useState<LibraryRow[]>([])
  const [rowCount, setRowCount] = React.useState(0)
  const [pagination, setPagination] = React.useState<GridPaginationModel>({ page: 0, pageSize: 25 })
  const [loading, setLoading] = React.useState(false)

  const columns: GridColDef[] = [
    { field: 'plugin', headerName: 'Plugin', flex: 1 },
    { field: 'uuid', headerName: 'UUID', flex: 2 },
    { field: 'updatedAt', headerName: 'Updated', flex: 1 }
  ]

  React.useEffect(() => {
    let ignore = false
    async function load() {
      setLoading(true)
      try {
        const page = pagination.page + 1
        const resp = await fetch(`/library?page=${page}&pageSize=${pagination.pageSize}`)
        const data = await resp.json()
        if (ignore) return
        const items = (data.items || []).map((it: any) => ({ id: it.uuid, ...it }))
        setRows(items)
        setRowCount(data.total || items.length)
      } finally {
        setLoading(false)
      }
    }
    load()
    return () => { ignore = true }
  }, [pagination])

  return (
    <Box sx={{ height: 600, width: '100%' }}>
      <DataGrid
        columns={columns}
        rows={rows}
        loading={loading}
        paginationMode="server"
        rowCount={rowCount}
        paginationModel={pagination}
        onPaginationModelChange={setPagination}
        pageSizeOptions={[10, 25, 50, 100]}
      />
    </Box>
  )
}




