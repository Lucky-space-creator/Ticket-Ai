import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/layout/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘' }
      },
      {
        path: 'trains',
        name: 'TrainList',
        component: () => import('@/views/TrainList.vue'),
        meta: { title: '车次管理' }
      },
      {
        path: 'ticket-stocks',
        name: 'TicketStockList',
        component: () => import('@/views/TicketStockList.vue'),
        meta: { title: '余票管理' }
      },
      {
        path: 'orders',
        name: 'OrderList',
        component: () => import('@/views/OrderList.vue'),
        meta: { title: '订单管理' }
      },
      {
        path: 'users',
        name: 'UserList',
        component: () => import('@/views/UserList.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'faq',
        name: 'FaqManage',
        component: () => import('@/views/KnowledgeList.vue'),
        meta: { title: 'FAQ问答管理' }
      },
      {
        path: 'knowledge',
        redirect: { name: 'FaqManage' }
      },
      {
        path: 'roles',
        name: 'RoleList',
        component: () => import('@/views/RoleList.vue'),
        meta: { title: '角色管理' }
      },
      {
        path: 'permissions',
        name: 'PermissionList',
        component: () => import('@/views/PermissionList.vue'),
        meta: { title: '权限管理' }
      },
      {
        path: 'system/logs',
        name: 'LogList',
        component: () => import('@/views/LogList.vue'),
        meta: { title: '操作日志' }
      },
      {
        path: 'train/stations',
        name: 'StationList',
        component: () => import('@/views/StationList.vue'),
        meta: { title: '车站管理' }
      },
      {
        path: 'order/stats',
        name: 'OrderStats',
        component: () => import('@/views/OrderStats.vue'),
        meta: { title: '订单统计' }
      },
      {
        path: 'customer-service',
        name: 'CustomerService',
        component: () => import('@/views/CustomerService.vue'),
        meta: { title: '客服工作台' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.path === '/login') {
    // 如果已经登录，跳转到首页
    if (userStore.token) {
      next('/')
    } else {
      next()
    }
  } else {
    // 需要登录
    if (!userStore.token) {
      next('/login')
    } else {
      next()
    }
  }
})

export default router