import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "@/App";
import { reportWebVitals } from "@/api/webVitals";
import "./index.css";

// eslint-disable-next-line @typescript-eslint/no-non-null-assertion
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

// Real-User Monitoring: fires after the metrics resolve (LCP after the
// largest paint, INP after the next interaction, etc.). Doesn't block
// startup.
reportWebVitals();
