import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Layout from '@/components/Layout/index.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘' },
      },
      {
        path: 'demo',
        name: 'Demo',
        component: () => import('@/views/Demo.vue'),
        meta: { title: '示例页面' },
      },
      {
        path: 'resource-check',
        name: 'ResourceCheck',
        component: () => import('@/views/ResourceCheck.vue'),
        meta: { title: '资源核查报告' },
      },
      {
        path: 'performance',
        name: 'PerformanceTest',
        component: () => import('@/views/PerformanceTest.vue'),
        meta: { title: '性能测试' },
      },
      {
        path: 'meta-gen',
        name: 'MetaGen',
        component: () => import('@/views/MetaGen.vue'),
        meta: { title: '元数据生成' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
