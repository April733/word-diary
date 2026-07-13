import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/LoginView.vue')
    },
    {
      path: '/',
      name: 'Dashboard',
      component: () => import('../views/DashboardView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/notebooks',
      name: 'Notebooks',
      component: () => import('../views/NotebookListView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/notebooks/:id',
      name: 'NotebookDetail',
      component: () => import('../views/WordListView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/study',
      name: 'Study',
      component: () => import('../views/StudyView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/records',
      name: 'Records',
      component: () => import('../views/RecordView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/bookshelf',
      name: 'Bookshelf',
      component: () => import('../views/BookshelfView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/reader/:id',
      name: 'Reader',
      component: () => import('../views/ReaderView.vue'),
      meta: { requiresAuth: true }
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/')
  } else {
    next()
  }
})

export default router
