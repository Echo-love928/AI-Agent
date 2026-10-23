import { createApp } from "vue";
import { createRouter, createWebHashHistory } from "vue-router";
import App from "./App.vue";
import HomeView from "./views/HomeView.vue";
import "./styles.css";

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/", name: "home", component: HomeView },
    {
      path: "/mind",
      name: "mind",
      component: () => import("./views/ChatView.vue"),
      props: { kind: "mind" },
    },
    {
      path: "/agent",
      name: "agent",
      component: () => import("./views/ChatView.vue"),
      props: { kind: "agent" },
    },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
  scrollBehavior: () => ({ top: 0 }),
});

createApp(App).use(router).mount("#app");
