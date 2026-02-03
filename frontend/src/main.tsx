import React from 'react'
import ReactDOM from 'react-dom/client'
import { CssBaseline, Box, Tabs, Tab, createTheme, ThemeProvider } from '@mui/material'
import { BrowserRouter, Routes, Route, useLocation, useNavigate } from 'react-router-dom'
import { SearchView } from './search/SearchView'
import { LibraryView } from './library/LibraryView'

const darkTheme = createTheme({
  palette: {
    mode: 'dark',
    primary: {
      main: '#E50914'
    },
    background: {
      default: '#141414',
      paper: '#1a1a1a'
    }
  },
  typography: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif'
  }
})

function AppContent() {
  const location = useLocation()
  const navigate = useNavigate()

  // Determine current tab from path
  const currentTab = location.pathname === '/' ? 1 : 0

  const handleTabChange = (_: React.SyntheticEvent, newValue: number) => {
    if (newValue === 0) {
      navigate('/search')
    } else {
      navigate('/')
    }
  }

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: '#000' }}>
      {/* Tab Navigation */}
      <Box sx={{
        borderBottom: 1,
        borderColor: '#333',
        position: 'sticky',
        top: 0,
        bgcolor: 'rgba(0, 0, 0, 0.95)',
        backdropFilter: 'blur(10px)',
        zIndex: 1000,
        px: 3
      }}>
        <Tabs
          value={currentTab}
          onChange={handleTabChange}
          sx={{
            '& .MuiTab-root': {
              color: '#999',
              fontWeight: 600,
              textTransform: 'none',
              fontSize: '1rem',
              minWidth: 120,
              '&.Mui-selected': {
                color: '#fff'
              }
            },
            '& .MuiTabs-indicator': {
              backgroundColor: '#E50914',
              height: 3
            }
          }}
        >
          <Tab label="Search" />
          <Tab label="Library" />
        </Tabs>
      </Box>

      {/* Routes */}
      <Routes>
        <Route path="/search" element={<SearchView />} />
        <Route path="/" element={<LibraryView />} />
      </Routes>
    </Box>
  )
}

function App() {
  return (
    <React.StrictMode>
      <ThemeProvider theme={darkTheme}>
        <CssBaseline />
        <BrowserRouter>
          <AppContent />
        </BrowserRouter>
      </ThemeProvider>
    </React.StrictMode>
  )
}

ReactDOM.createRoot(document.getElementById('root')!).render(<App />)
