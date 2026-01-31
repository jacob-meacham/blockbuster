import React from 'react'
import ReactDOM from 'react-dom/client'
import { CssBaseline, Container, Tabs, Tab, Box } from '@mui/material'
import { SearchView } from './search/SearchView'
import { LibraryView } from './library/LibraryView'

function App() {
  const [tab, setTab] = React.useState(0)
  return (
    <React.StrictMode>
      <CssBaseline />
      <Container maxWidth="lg" sx={{ py: 3 }}>
        <Box sx={{ typography: 'h4', mb: 2 }}>🎬 Blockbuster</Box>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
          <Tab label="Search" />
          <Tab label="Library" />
        </Tabs>
        {tab === 0 && <SearchView />}
        {tab === 1 && <LibraryView />}
      </Container>
    </React.StrictMode>
  )
}

ReactDOM.createRoot(document.getElementById('root')!).render(<App />)




