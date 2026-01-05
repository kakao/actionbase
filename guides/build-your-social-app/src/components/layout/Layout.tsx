import React, {ReactNode, useEffect, useState} from 'react';
import CliTerminal from './CliTerminal';
import MobileFooter from "./MobileFooter";
import {TUTORIAL_FIRST_STEP, TUTORIAL_STEP_KEY} from "../../modules/DriverContext";
import {ApiLogProvider} from "../../modules/ApiLogContext";

import '../../styles/layout.css';
import {getStarsAsTag} from "../../api/github";
import DOMPurify from "dompurify";

const OWNER = "kakao"
const REPOSITORY = "actionbase"
const DEFAULT_GITHUB_START = `<svg xmlns="http://www.w3.org/2000/svg" width="76" height="20"><style>a:hover #llink{fill:url(#b);stroke:#ccc}a:hover #rlink{fill:#4183c4}</style><linearGradient id="a" x2="0" y2="100%"><stop offset="0" stop-color="#fcfcfc" stop-opacity="0"/><stop offset="1" stop-opacity=".1"/></linearGradient><linearGradient id="b" x2="0" y2="100%"><stop offset="0" stop-color="#ccc" stop-opacity=".1"/><stop offset="1" stop-opacity=".1"/></linearGradient><g stroke="#d5d5d5"><rect stroke="none" fill="#fcfcfc" x="0.5" y="0.5" width="54" height="19" rx="2"/><rect x="60.5" y="0.5" width="15" height="19" rx="2" fill="#fafafa"/><rect x="60" y="7.5" width="0.5" height="5" stroke="#fafafa"/><path d="M60.5 6.5 l-3 3v1 l3 3" fill="#fafafa"/></g><image x="5" y="3" width="14" height="14" href="data:image/svg+xml;base64,PHN2ZyBmaWxsPSIjMTgxNzE3IiByb2xlPSJpbWciIHZpZXdCb3g9IjAgMCAyNCAyNCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48dGl0bGU+R2l0SHViPC90aXRsZT48cGF0aCBkPSJNMTIgLjI5N2MtNi42MyAwLTEyIDUuMzczLTEyIDEyIDAgNS4zMDMgMy40MzggOS44IDguMjA1IDExLjM4NS42LjExMy44Mi0uMjU4LjgyLS41NzcgMC0uMjg1LS4wMS0xLjA0LS4wMTUtMi4wNC0zLjMzOC43MjQtNC4wNDItMS42MS00LjA0Mi0xLjYxQzQuNDIyIDE4LjA3IDMuNjMzIDE3LjcgMy42MzMgMTcuN2MtMS4wODctLjc0NC4wODQtLjcyOS4wODQtLjcyOSAxLjIwNS4wODQgMS44MzggMS4yMzYgMS44MzggMS4yMzYgMS4wNyAxLjgzNSAyLjgwOSAxLjMwNSAzLjQ5NS45OTguMTA4LS43NzYuNDE3LTEuMzA1Ljc2LTEuNjA1LTIuNjY1LS4zLTUuNDY2LTEuMzMyLTUuNDY2LTUuOTMgMC0xLjMxLjQ2NS0yLjM4IDEuMjM1LTMuMjItLjEzNS0uMzAzLS41NC0xLjUyMy4xMDUtMy4xNzYgMCAwIDEuMDA1LS4zMjIgMy4zIDEuMjMuOTYtLjI2NyAxLjk4LS4zOTkgMy0uNDA1IDEuMDIuMDA2IDIuMDQuMTM4IDMgLjQwNSAyLjI4LTEuNTUyIDMuMjg1LTEuMjMgMy4yODUtMS4yMy42NDUgMS42NTMuMjQgMi44NzMuMTIgMy4xNzYuNzY1Ljg0IDEuMjMgMS45MSAxLjIzIDMuMjIgMCA0LjYxLTIuODA1IDUuNjI1LTUuNDc1IDUuOTIuNDIuMzYuODEgMS4wOTYuODEgMi4yMiAwIDEuNjA2LS4wMTUgMi44OTYtLjAxNSAzLjI4NiAwIC4zMTUuMjEuNjkuODI1LjU3QzIwLjU2NSAyMi4wOTIgMjQgMTcuNTkyIDI0IDEyLjI5N2MwLTYuNjI3LTUuMzczLTEyLTEyLTEyIi8+PC9zdmc+"/><g aria-hidden="false" fill="#333" text-anchor="middle" font-family="Helvetica Neue,Helvetica,Arial,sans-serif" text-rendering="geometricPrecision" font-weight="700" font-size="110px" line-height="14px"><a target="_blank" href="https://github.com/kakao/actionbase"><text aria-hidden="true" x="355" y="150" fill="#fff" transform="scale(.1)" textLength="270">Stars</text><text x="355" y="140" transform="scale(.1)" textLength="270">Stars</text><rect id="llink" stroke="#d5d5d5" fill="url(#a)" x=".5" y=".5" width="54" height="19" rx="2"/></a><a target="_blank" href="https://github.com/kakao/actionbase/stargazers"><rect width="16" x="60" height="20" fill="rgba(0,0,0,0)"/><text aria-hidden="true" x="675" y="150" fill="#fff" transform="scale(.1)" textLength="70">0</text><text id="rlink" x="675" y="140" transform="scale(.1)" textLength="70">0</text></a></g></svg>`

interface SplitLayoutProps {
  children: ReactNode;
}

const Layout: React.FC<SplitLayoutProps> = ({children}) => {
  const [starsImage, setStarsImage] = useState<string>(DEFAULT_GITHUB_START)
  const handleRefresh = () => {
    localStorage.setItem(TUTORIAL_STEP_KEY, TUTORIAL_FIRST_STEP);
    localStorage.clear();

    window.location.href = "/"
  };

  useEffect(() => {
    const fetchData = async () => {
      try {
        const starImageTag = await getStarsAsTag(OWNER, REPOSITORY)
        setStarsImage(starImageTag)
      } catch (err) {
        console.log("Failed to get repository", err)
      }
    }
    fetchData()
  }, []);
  return (
    <>
      <div className="split-layout-gutter split-layout-gutter-left"/>
      <div className="split-layout-gutter split-layout-gutter-right"/>

      <ApiLogProvider>
        <div className="split-layout">
          <div className="split-layout-title">
            <span className="split-layout-title-text">Actionbase Hands-On: Build Your Social App</span>
            <div className="split-layout-actions">
              <a
                href="https://actionbase.io/"
                target="_blank"
                rel="noopener noreferrer"
                className="split-layout-doc-link"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path>
                  <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path>
                </svg>
                <span>actionbase.io</span>
              </a>
              <div
                className="split-layout-stars-image"
                dangerouslySetInnerHTML={{
                  __html: DOMPurify.sanitize(starsImage, {
                    ADD_ATTR: ['target', 'rel'],
                    ALLOWED_URI_REGEXP: /^(?:(?:(?:f|ht)tps?|mailto|tel|callto|sms|cid|xmpp|data):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i
                  })
                }}
              />
            </div>
          </div>
          <div className="split-layout-header-line"/>

          <div className="cli-panel">
            <CliTerminal/>
          </div>

          <div className="mobile-preview">
            <div className="mobile-frame">
              <div className="mobile-content">
                {children}
                <MobileFooter/>
              </div>
              <button
                className="tutorial-refresh-btn"
                onClick={handleRefresh}
              >
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="23 4 23 10 17 10"></polyline>
                  <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </ApiLogProvider>
    </>
  );
};

export default Layout;

