import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { initSession } from "./session";
import "./styles.css";

initSession(); // wire the IPC message/status streams into the store before first render

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
