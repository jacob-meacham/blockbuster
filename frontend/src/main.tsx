import React from 'react'
import ReactDOM from 'react-dom/client'
import { CssBaseline, createTheme, ThemeProvider } from '@mui/material'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { UnifiedView } from './UnifiedView'
import { LibraryView } from './LibraryView'

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

function App() {
  return (
    <React.StrictMode>
      <ThemeProvider theme={darkTheme}>
        <CssBaseline />
        <BrowserRouter>
          <Routes>
            <Route path="/search" element={<UnifiedView />} />
            <Route path="/library" element={<LibraryView />} />
            <Route path="/" element={<UnifiedView />} />
          </Routes>
        </BrowserRouter>
      </ThemeProvider>
    </React.StrictMode>
  )
}

ReactDOM.createRoot(document.getElementById('root')!).render(<App />)
