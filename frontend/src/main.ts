import { createApp } from "vue";
import { createPinia } from "pinia";
import "@fontsource/inter/400.css";
import "@fontsource/inter/500.css";
import "@fontsource/inter/600.css";
import "@fontsource/ibm-plex-mono/400.css";
import "@fontsource/ibm-plex-mono/500.css";
import App from "./App.vue";
import router from "./router";
import "./styles.css";

createApp(App).use(createPinia()).use(router).mount("#app");
