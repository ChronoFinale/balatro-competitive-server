import { createRoot } from "react-dom/client";
import Almanac from "../src/renderer/src/Almanac";
import "../src/renderer/src/styles.css";
createRoot(document.getElementById("root")!).render(<Almanac onClose={() => {}} />);
