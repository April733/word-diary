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
      path: '/terms',
      name: 'Terms',
      component: () => import('../views/TermsView.vue')
    },
    {
      path: '/report',
      name: 'Report',
      component: () => import('../views/ReportView.vue')
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
      path: '/plan',
      name: 'StudyPlan',
      component: () => import('../views/StudyPlanView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/study/settings',
      name: 'StudySettings',
      component: () => import('../views/StudySettingsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/study/browse',
      name: 'StudyBrowse',
      component: () => import('../views/StudyBrowseView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/study-duration',
      name: 'StudyDuration',
      component: () => import('../views/StudyDurationView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/shadowing',
      name: 'Shadowing',
      component: () => import('../views/ShadowingListView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/shadowing/local',
      name: 'ShadowingLocalVideo',
      component: () => import('../views/ShadowingLocalVideoView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/shadowing/:id',
      name: 'ShadowingPlayer',
      component: () => import('../views/ShadowingPlayerView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/bookshelf',
      name: 'Bookshelf',
      component: () => import('../views/BookshelfView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/writing',
      name: 'Writing',
      component: () => import('../views/WritingListView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/writing/new',
      name: 'WritingNew',
      component: () => import('../views/WritingEditView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/writing/:id/result',
      name: 'WritingResult',
      component: () => import('../views/WritingResultView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/writing/:id',
      name: 'WritingEdit',
      component: () => import('../views/WritingEditView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/translation',
      name: 'Translation',
      component: () => import('../views/TranslationListView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/translation/new',
      name: 'TranslationNew',
      component: () => import('../views/TranslationEditView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/translation/:id',
      name: 'TranslationEdit',
      component: () => import('../views/TranslationEditView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/spelling',
      name: 'Spelling',
      component: () => import('../views/SpellingPracticeView.vue'),
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
