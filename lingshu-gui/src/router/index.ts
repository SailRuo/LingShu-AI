import { createRouter, createWebHashHistory } from 'vue-router';
import MainLayout from '../components/layout/MainLayout.vue';

const routes = [
  {
    path: '/',
    component: MainLayout,
    children: [
      {
        path: '',
        name: 'chat',
        component: () => import('../views/ChatView.vue'),
      },
      {
        path: 'contacts',
        name: 'contacts',
        component: () => import('../views/ContactsView.vue'),
      },
      {
        path: 'folder',
        name: 'folder',
        component: () => import('../views/FolderView.vue'),
      },
      {
        path: 'starred',
        name: 'starred',
        component: () => import('../views/StarredView.vue'),
      },
      {
        path: 'settings',
        name: 'settings',
        component: () => import('../views/SettingsView.vue'),
      },
    ],
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

export default router;
