import React, { ReactNode, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { ApiLogProvider } from '../../contexts/ApiLogContext';
import CliTerminal from './CliTerminal';
import MobileFooter from './MobileFooter';
import '../../styles/layout.css';
import ApiLogs from './ApiLogs';
import { BreadCrumbStep, generateBreadCrumbSteps } from '../../constants/stepsConfig';
import { useDriver } from '../../contexts/DriverContext';
import { ICONS } from '../../constants/icons';

interface SplitLayoutProps {
  children: ReactNode;
}

const Layout: React.FC<SplitLayoutProps> = ({ children }) => {
  const { stepIndex, resetStep } = useDriver();

  const initialBreadcrumbSteps = useMemo(() => generateBreadCrumbSteps(), []);
  const [breadcrumbSteps, setBreadcrumbSteps] = useState<BreadCrumbStep[]>(initialBreadcrumbSteps);
  const previousStepIndexRef = useRef<number | undefined>(undefined);

  const isStepCompleted = stepIndex !== undefined && stepIndex > 17;

  useLayoutEffect(() => {
    if (stepIndex === previousStepIndexRef.current) {
      return;
    }

    previousStepIndexRef.current = stepIndex;

    if (stepIndex === undefined || stepIndex < 0) {
      return;
    }

    const allBreadcrumbStepIndices = [
      ...initialBreadcrumbSteps.map((step) => step.stepIndex),
      ...initialBreadcrumbSteps
        .flatMap((step) => step.subSteps || [])
        .map((subStep) => subStep.stepIndex),
    ].sort((a, b) => a - b);

    const stepExists = allBreadcrumbStepIndices.includes(stepIndex);

    let targetStepIndex = stepIndex;
    if (!stepExists) {
      const closestPrevStep = allBreadcrumbStepIndices.filter((idx) => idx < stepIndex).pop();
      if (closestPrevStep !== undefined) {
        targetStepIndex = closestPrevStep;
      } else {
        return;
      }
    }

    const updatedSteps = initialBreadcrumbSteps.map((step, index) => {
      const nextMainStep = initialBreadcrumbSteps[index + 1];
      const nextMainStepIndex = nextMainStep?.stepIndex ?? Infinity;

      const isMainStepActive = step.stepIndex === targetStepIndex;
      const isMainStepCompleted = nextMainStepIndex <= stepIndex;

      const updatedSubSteps = step.subSteps?.map((subStep, subIndex) => {
        const nextSubStep = step.subSteps?.[subIndex + 1];
        const nextSubStepIndex = nextSubStep?.stepIndex ?? nextMainStepIndex;

        const isSubStepActive = subStep.stepIndex === targetStepIndex;
        const isSubStepCompleted = nextSubStepIndex <= stepIndex;
        return {
          ...subStep,
          isActive: isSubStepActive,
          isCompleted: isSubStepCompleted,
        };
      });

      const hasActiveSubStep = updatedSubSteps?.some((subStep) => subStep.isActive) || false;

      return {
        ...step,
        isActive: isMainStepActive && !hasActiveSubStep,
        hasActiveSubStep,
        isCompleted: isMainStepCompleted,
        subSteps: updatedSubSteps,
      };
    });
    setBreadcrumbSteps(updatedSteps);
  }, [stepIndex, initialBreadcrumbSteps]);

  return (
    <>
      <div className="sidebar">
        <div className="sidebar-header">
          <div className="sidebar-header-row">
            <img className="logo" src="/images/logo.svg" />
          </div>
          <div className="guide-box">
            <div className="guide-title">
              <span className="guide-label">Hands-on Guide</span>
              <span className="guide-name">Build Your Social App</span>
            </div>
            <div className="header-actions">
              <a
                href="https://kakao.github.io/actionbase/guides/build-your-social-media-app/"
                target="_blank"
                className="header-action"
                title="Documentation"
              >
                <svg
                  width="14"
                  height="14"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path>
                  <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path>
                </svg>
                Docs
              </a>
              <a
                href="https://github.com/kakao/actionbase/issues"
                target="_blank"
                className="header-action"
                title="Feedback"
              >
                <svg
                  width="14"
                  height="14"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
                </svg>
                Feedback
              </a>
              <button className="header-action" onClick={resetStep} title="Restart">
                <svg
                  width="14"
                  height="14"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" />
                  <path d="M21 3v5h-5" />
                  <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" />
                  <path d="M3 21v-5h5" />
                </svg>
                Restart
              </button>
            </div>
          </div>
        </div>

        <div className="sidebar-content">
          {breadcrumbSteps.map((step, index) => (
            <div key={index} className="breadcrumb-item-wrapper">
              <div
                className={`breadcrumb-item ${step.isActive ? 'active' : ''} ${step.hasActiveSubStep ? 'has-active-substep' : ''} ${step.isCompleted ? 'completed' : ''}`}
              >
                <span className="breadcrumb-number">{index + 1}</span>
                {step.icon && (
                  <svg
                    className="breadcrumb-icon"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    dangerouslySetInnerHTML={{
                      __html: ICONS[step.icon as keyof typeof ICONS] || '',
                    }}
                  />
                )}
                <span className="breadcrumb-title">{step.title}</span>
              </div>
              {step.subSteps && step.subSteps.length > 0 && (
                <div className="breadcrumb-substeps">
                  {step.subSteps.map((subStep, subIndex) => (
                    <div
                      key={subIndex}
                      className={`breadcrumb-item breadcrumb-substep ${subStep.isActive ? 'active' : ''} ${subStep.isCompleted ? 'completed' : ''}`}
                    >
                      <span className="breadcrumb-title">{subStep.title}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      <ApiLogProvider>
        <div className="layout">
          <div className="mobile-frame-wrapper">
            <div className="mobile-frame">
              <div className="mobile-status-bar">
                <span className="status-time">9:41</span>
                <div className="mobile-notch"></div>
                <div className="status-icons">
                  <svg width="17" height="12" viewBox="0 0 17 12" fill="currentColor">
                    <path d="M1 4.5C1 3.67 1.67 3 2.5 3h1C4.33 3 5 3.67 5 4.5v6c0 .83-.67 1.5-1.5 1.5h-1C1.67 12 1 11.33 1 10.5v-6zm5-2C6 1.67 6.67 1 7.5 1h1C9.33 1 10 1.67 10 2.5v8c0 .83-.67 1.5-1.5 1.5h-1C6.67 12 6 11.33 6 10.5v-8zm5 3c0-.83.67-1.5 1.5-1.5h1c.83 0 1.5.67 1.5 1.5v5c0 .83-.67 1.5-1.5 1.5h-1c-.83 0-1.5-.67-1.5-1.5v-5z" />
                  </svg>
                  <svg width="16" height="12" viewBox="0 0 16 12" fill="currentColor">
                    <path d="M8 2.4c2.28 0 4.35.87 5.9 2.3a.75.75 0 001.05-1.07A10.45 10.45 0 008 .9c-2.72 0-5.2 1.04-7.07 2.73A.75.75 0 102 4.7 8.95 8.95 0 018 2.4zm0 3c1.54 0 2.94.59 4 1.56a.75.75 0 001.02-1.1A7.45 7.45 0 008 3.9c-1.97 0-3.76.75-5.1 1.96A.75.75 0 104 6.96 5.95 5.95 0 018 5.4zm0 3a3.5 3.5 0 012.13.72.75.75 0 10.91-1.19A4.99 4.99 0 008 6.9c-1.2 0-2.3.42-3.16 1.03a.75.75 0 10.9 1.2A3.5 3.5 0 018 8.4zm0 2.1a1.5 1.5 0 100 3 1.5 1.5 0 000-3z" />
                  </svg>
                  <svg width="25" height="12" viewBox="0 0 25 12" fill="currentColor">
                    <rect
                      x="0.5"
                      y="0.5"
                      width="21"
                      height="11"
                      rx="2.5"
                      stroke="currentColor"
                      strokeOpacity="0.35"
                      fill="none"
                    />
                    <rect x="2" y="2" width="18" height="8" rx="1.5" fillOpacity="0.9" />
                    <path d="M23 4v4a2 2 0 000-4z" fillOpacity="0.4" />
                  </svg>
                </div>
              </div>
              <div className="mobile-content">
                {children}
                <MobileFooter />
              </div>
              <div className="mobile-home-indicator">
                <div className="home-indicator-bar"></div>
              </div>
            </div>
          </div>
          <div className="browser-frame-wrapper">
            {isStepCompleted && (
              <div className="completion-bubble">
                <h3>Now try it yourself</h3>
                <ul>
                  <li>
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      dangerouslySetInnerHTML={{ __html: ICONS.search }}
                    />
                    <span>Follow someone</span>
                  </li>
                  <li>
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      dangerouslySetInnerHTML={{ __html: ICONS.home }}
                    />
                    <span>Check your feed</span>
                  </li>
                  <li>
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      dangerouslySetInnerHTML={{ __html: ICONS.heart }}
                    />
                    <span>Like a post</span>
                  </li>
                </ul>
                <div className="bubble-arrow"></div>
              </div>
            )}
            <div className={`browser-frame ${isStepCompleted ? 'step-completed' : ''}`}>
              <div className="browser-header">
                <div className="browser-buttons">
                  <span className="browser-btn close"></span>
                  <span className="browser-btn minimize"></span>
                  <span className="browser-btn maximize"></span>
                </div>
              </div>
              <div className={`browser-content ${isStepCompleted ? 'step-completed' : ''}`}>
                {!isStepCompleted && (
                  <div className="terminal-wrapper">
                    <CliTerminal />
                  </div>
                )}
              </div>
              <div className={`api-logs-wrapper ${isStepCompleted ? 'step-completed' : ''}`}>
                <ApiLogs />
              </div>
              <div className="browser-help-notice">
                <span>Stuck? Try restarting the server</span>
                <a
                  href="https://github.com/kakao/actionbase/issues"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Share feedback
                </a>
              </div>
            </div>
          </div>
        </div>
      </ApiLogProvider>
    </>
  );
};

export default Layout;
