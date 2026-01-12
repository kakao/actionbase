import React from "react";
import {BrowserRouter, Route, Routes} from "react-router-dom";
import {DriverProvider} from './contexts/DriverContext';
import {ToastProvider} from './contexts/ToastContext';
import Layout from "./components/layout/Layout";
import Feed from "./components/pages/Feed";
import Followers from "./components/pages/Followers";
import Followings from "./components/pages/Followings";
import NotFound from "./components/pages/NotFound";
import Post from "./components/pages/Post";
import Profile from "./components/pages/Profile";
import Search from "./components/pages/Search";
import "./styles/app.css";

function AppContent() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Feed/>}/>
        <Route path="/profile/:id" element={<Profile/>}/>
        <Route path="/search" element={<Search/>}/>
        <Route path="/post/:id" element={<Post/>}/>
        <Route path="/followers/:id" element={<Followers/>}/>
        <Route path="/followings/:id" element={<Followings/>}/>
        <Route path="*" element={<NotFound/>}/>
      </Routes>
    </Layout>
  );
}

function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <DriverProvider>
          <AppContent/>
        </DriverProvider>
      </ToastProvider>
    </BrowserRouter>
  );
}

export default App;
