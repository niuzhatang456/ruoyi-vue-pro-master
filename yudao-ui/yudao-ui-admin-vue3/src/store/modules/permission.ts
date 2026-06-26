import { defineStore } from 'pinia'
import { store } from '@/store'
import { cloneDeep } from 'lodash-es'
import remainingRouter from '@/router/modules/remaining'
import { flatMultiLevelRoutes, generateRoute } from '@/utils/routerHelper'
import { CACHE_KEY, useCache } from '@/hooks/web/useCache'

const { wsCache } = useCache()

const jijianMenuRouteNames = ['JijianInput', 'JijianQuery', 'JijianMe']
const jijianMenuPathMap: Record<string, string> = {
  JijianInput: '/jijian/input',
  JijianQuery: '/jijian/query',
  JijianMe: '/jijian/me'
}

const normalizeJijianMenuPath = (path?: string) => {
  if (!path) return path
  if (path.startsWith('/jijian/')) return path
  if (path.startsWith('/input')) return `/jijian${path}`
  if (path.startsWith('/query')) return `/jijian${path}`
  if (path.startsWith('/me')) return `/jijian${path}`
  return path
}

const normalizeJijianMenuRoute = (route: AppRouteRecordRaw): AppRouteRecordRaw => {
  const mappedPath = jijianMenuPathMap[route.name as string]
  route.path = mappedPath || normalizeJijianMenuPath(route.path) || route.path
  route.redirect = normalizeJijianMenuPath(route.redirect as string) || route.redirect
  route.children = route.children?.map((child) => normalizeJijianMenuRoute(child))
  return route
}

export interface PermissionState {
  routers: AppRouteRecordRaw[]
  addRouters: AppRouteRecordRaw[]
  menuTabRouters: AppRouteRecordRaw[]
}

export const usePermissionStore = defineStore('permission', {
  state: (): PermissionState => ({
    routers: [],
    addRouters: [],
    menuTabRouters: []
  }),
  getters: {
    getRouters(): AppRouteRecordRaw[] {
      return this.routers
    },
    getAddRouters(): AppRouteRecordRaw[] {
      return flatMultiLevelRoutes(cloneDeep(this.addRouters))
    },
    getMenuTabRouters(): AppRouteRecordRaw[] {
      return this.menuTabRouters
    }
  },
  actions: {
    async generateRoutes(): Promise<unknown> {
      return new Promise<void>(async (resolve) => {
        // 获得菜单列表，它在登录的时候，setUserInfoAction 方法中已经进行获取
        let res: AppCustomRouteRecordRaw[] = []
        const roleRouters = wsCache.get(CACHE_KEY.ROLE_ROUTERS)
        if (roleRouters) {
          res = roleRouters as AppCustomRouteRecordRaw[]
        }
        const routerMap: AppRouteRecordRaw[] = generateRoute(res)
        // 动态路由，404一定要放到最后面
        // preschooler：vue-router@4以后已支持静态404路由，此处可不再追加
        this.addRouters = routerMap.concat([
          {
            path: '/:path(.*)*',
            // redirect: '/404',
            component: () => import('@/views/Error/404.vue'),
            name: '404Page',
            meta: {
              hidden: true,
              breadcrumb: false
            }
          }
        ])
        // 渲染菜单的所有路由
        const jijianMenuChildren = cloneDeep(remainingRouter)
          .filter((route) => jijianMenuRouteNames.includes(route.name as string))
          .map((route) => normalizeJijianMenuRoute(route))
        this.routers = [
          {
            path: '/',
            redirect: '/jijian/input/drag',
            name: 'JijianRootMenu',
            meta: {
              title: '纪检信息系统',
              icon: 'ep:menu',
              alwaysShow: true
            },
            children: jijianMenuChildren
          }
        ] as AppRouteRecordRaw[]
        resolve()
      })
    },
    setMenuTabRouters(routers: AppRouteRecordRaw[]): void {
      this.menuTabRouters = routers
    }
  },
  persist: false
})

export const usePermissionStoreWithOut = () => {
  return usePermissionStore(store)
}
