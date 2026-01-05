import React from "react";
import {BrowserRouter, Route, Routes} from "react-router-dom";
import Feed from "./components/pages/Feed";
import Profile from "./components/pages/Profile";
import Search from "./components/pages/Search";
import Post from "./components/pages/Post";
import Followings from "./components/pages/Followings";
import NotFound from "./components/pages/NotFound";
import Layout from "./components/layout/Layout";
import {DriverProvider} from "./modules/DriverContext";

import "./styles/app.css";
import Followers from "./components/pages/Followers";

function App() {
  return (
    <BrowserRouter>
      <DriverProvider>
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
      </DriverProvider>
    </BrowserRouter>
  );
}

export default App;
