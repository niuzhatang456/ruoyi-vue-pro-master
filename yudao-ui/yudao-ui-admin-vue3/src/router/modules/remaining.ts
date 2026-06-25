import { Layout } from '@/utils/routerHelper'

const { t } = useI18n()
/**
 * redirect: noredirect        当设置 noredirect 的时候该路由在面包屑导航中不可被点击
 * name:'router-name'          设定路由的名字，一定要填写不然使用<keep-alive>时会出现各种问题
 * meta : {
 hidden: true              当设置 true 的时候该路由不会再侧边栏出现 如404，login等页面(默认 false)

 alwaysShow: true          当你一个路由下面的 children 声明的路由大于1个时，自动会变成嵌套的模式，
 只有一个时，会将那个子路由当做根路由显示在侧边栏，
 若你想不管路由下面的 children 声明的个数都显示你的根路由，
 你可以设置 alwaysShow: true，这样它就会忽略之前定义的规则，
 一直显示根路由(默认 false)

 title: 'title'            设置该路由在侧边栏和面包屑中展示的名字

 icon: 'svg-name'          设置该路由的图标

 noCache: true             如果设置为true，则不会被 <keep-alive> 缓存(默认 false)

 breadcrumb: false         如果设置为false，则不会在breadcrumb面包屑中显示(默认 true)

 affix: true               如果设置为true，则会一直固定在tag项中(默认 false)

 noTagsView: true          如果设置为true，则不会出现在tag中(默认 false)

 activeMenu: '/dashboard'  显示高亮的路由路径

 followAuth: '/dashboard'  跟随哪个路由进行权限过滤

 canTo: true               设置为true即使hidden为true，也依然可以进行路由跳转(默认 false)
 }
 **/
const remainingRouter: AppRouteRecordRaw[] = [
  {
    path: '/redirect',
    component: Layout,
    name: 'RedirectRoot',
    children: [
      {
        path: '/redirect/:path(.*)',
        name: 'Redirect',
        component: () => import('@/views/Redirect/Redirect.vue'),
        meta: {}
      }
    ],
    meta: {
      hidden: true,
      noTagsView: true
    }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/input/drag',
    name: 'Home',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'index',
        component: () => import('@/views/Home/Index.vue'),
        name: 'Index',
        meta: {
          title: t('router.home'),
          icon: 'ep:home-filled',
          noCache: false,
          affix: true
        }
      }
    ]
  },
  {
    path: '/input',
    component: Layout,
    redirect: '/input/property-group',
    name: 'JijianInput',
    meta: {
      title: '录入数据',
      icon: 'ep:upload',
      alwaysShow: true
    },
    children: [
      // ─── 分组入口（侧边栏可见）───────────────────────────────────────────
      {
        path: 'drag',
        component: () => import('@/views/jijian/input/Drag.vue'),
        name: 'JijianInputDrag',
        meta: { title: '拖拽录入', icon: 'ep:folder-add' }
      },
      {
        path: 'property-group',
        component: () => import('@/views/jijian/input/PropertyGroup.vue'),
        name: 'JijianInputPropertyGroup',
        meta: { title: '房产情况', icon: 'ep:office-building' }
      },
      {
        path: 'attendance-group',
        component: () => import('@/views/jijian/input/AttendanceGroup.vue'),
        name: 'JijianInputAttendanceGroup',
        meta: { title: '考勤情况', icon: 'ep:calendar' }
      },
      {
        path: 'canteen-supplier',
        component: () => import('@/views/jijian/input/CanteenSupplier.vue'),
        name: 'JijianInputCanteenSupplier',
        meta: { title: '食堂供应', icon: 'ep:food' }
      },
      // ─── 旧单表入口（保留路由可访问，但隐藏侧边栏）────────────────────────
      {
        path: 'ocr',
        component: () => import('@/views/jijian/input/Ocr.vue'),
        name: 'JijianInputOcr',
        meta: { title: '图片/文件识别录入', icon: 'ep:picture', hidden: true }
      },
      {
        path: 'excel',
        component: () => import('@/views/jijian/input/Excel.vue'),
        name: 'JijianInputExcel',
        meta: { title: 'Excel录入', icon: 'ep:document', hidden: true }
      },
      {
        path: 'property-info',
        component: () => import('@/views/jijian/input/PropertyInfo.vue'),
        name: 'JijianInputPropertyInfo',
        meta: { title: '房产情况表', icon: 'ep:office-building', hidden: true }
      },
      {
        path: 'lessee',
        component: () => import('@/views/jijian/input/Lessee.vue'),
        name: 'JijianInputLessee',
        meta: { title: '租赁人员表', icon: 'ep:user', hidden: true }
      },
      {
        path: 'lease-contract',
        component: () => import('@/views/jijian/input/LeaseContract.vue'),
        name: 'JijianInputLeaseContract',
        meta: { title: '租赁合同表', icon: 'ep:document', hidden: true }
      },
      {
        path: 'attendance-daily',
        component: () => import('@/views/jijian/input/AttendanceDaily.vue'),
        name: 'JijianInputAttendanceDaily',
        meta: { title: '考勤日报表', icon: 'ep:calendar', hidden: true }
      },
      {
        path: 'recuperation-leave',
        component: () => import('@/views/jijian/input/RecuperationLeave.vue'),
        name: 'JijianInputRecuperationLeave',
        meta: { title: '疗休养请假表', icon: 'ep:first-aid-kit', hidden: true }
      },
      {
        path: 'personal-leave',
        component: () => import('@/views/jijian/input/PersonalLeave.vue'),
        name: 'JijianInputPersonalLeave',
        meta: { title: '事假表', icon: 'ep:tickets', hidden: true }
      },
      {
        path: 'business-trip',
        component: () => import('@/views/jijian/input/BusinessTrip.vue'),
        name: 'JijianInputBusinessTrip',
        meta: { title: '出差表', icon: 'ep:map-location', hidden: true }
      },
      {
        path: 'compensatory-leave',
        component: () => import('@/views/jijian/input/CompensatoryLeave.vue'),
        name: 'JijianInputCompensatoryLeave',
        meta: { title: '调休表', icon: 'ep:clock', hidden: true }
      }
    ]
  },
  {
    path: '/query',
    component: Layout,
    redirect: '/query/smart',
    name: 'JijianQuery',
    meta: {
      title: '查询信息',
      icon: 'ep:search',
      alwaysShow: true
    },
    children: [
      {
        path: 'smart',
        component: () => import('@/views/jijian/query/Smart.vue'),
        name: 'JijianQuerySmart',
        meta: {
          title: '智能AI查询',
          icon: 'ep:chat-dot-round'
        }
      }
    ]
  },
  {
    path: '/me',
    component: Layout,
    redirect: '/me/history',
    name: 'JijianMe',
    meta: {
      title: '我的',
      icon: 'ep:user',
      alwaysShow: true
    },
    children: [
      {
        path: 'history',
        component: () => import('@/views/jijian/me/History.vue'),
        name: 'JijianMeHistory',
        meta: {
          title: '历史查询对话',
          icon: 'ep:clock'
        }
      },
      {
        path: 'disposal',
        component: () => import('@/views/jijian/me/Disposal.vue'),
        name: 'JijianMeDisposal',
        meta: {
          title: '处置记录',
          icon: 'ep:document-checked'
        }
      },
      {
        path: 'account',
        component: () => import('@/views/jijian/me/Account.vue'),
        name: 'JijianMeAccount',
        meta: {
          title: '当前账号信息',
          icon: 'ep:user-filled'
        }
      },
      {
        path: 'imports',
        component: () => import('@/views/jijian/me/Imports.vue'),
        name: 'JijianMeImports',
        meta: {
          title: '最近导入记录',
          icon: 'ep:files'
        }
      }
    ]
  },
  {
    path: '/user',
    component: Layout,
    name: 'UserInfo',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'profile',
        component: () => import('@/views/Profile/Index.vue'),
        name: 'Profile',
        meta: {
          canTo: true,
          hidden: true,
          noTagsView: false,
          icon: 'ep:user',
          title: t('common.profile')
        }
      },
      {
        path: 'notify-message',
        component: () => import('@/views/system/notify/my/index.vue'),
        name: 'MyNotifyMessage',
        meta: {
          canTo: true,
          hidden: true,
          noTagsView: false,
          icon: 'ep:message',
          title: '我的站内信'
        }
      }
    ]
  },
  {
    path: '/dict',
    component: Layout,
    name: 'dict',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'type/data/:dictType',
        component: () => import('@/views/system/dict/data/index.vue'),
        name: 'SystemDictData',
        meta: {
          title: '字典数据',
          noCache: true,
          hidden: true,
          canTo: true,
          icon: '',
          activeMenu: '/system/dict'
        }
      }
    ]
  },
  {
    path: '/codegen',
    component: Layout,
    name: 'CodegenEdit',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'edit',
        component: () => import('@/views/infra/codegen/EditTable.vue'),
        name: 'InfraCodegenEditTable',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: 'ep:edit',
          title: '修改生成配置',
          activeMenu: 'infra/codegen/index'
        }
      }
    ]
  },
  {
    path: '/job',
    component: Layout,
    name: 'JobL',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'job-log',
        component: () => import('@/views/infra/job/logger/index.vue'),
        name: 'InfraJobLog',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: 'ep:edit',
          title: '调度日志',
          activeMenu: 'infra/job/index'
        }
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/Login/Login.vue'),
    name: 'Login',
    meta: {
      hidden: true,
      title: t('router.login'),
      noTagsView: true
    }
  },
  {
    path: '/sso',
    component: () => import('@/views/Login/Login.vue'),
    name: 'SSOLogin',
    meta: {
      hidden: true,
      title: t('router.login'),
      noTagsView: true
    }
  },
  {
    path: '/social-login',
    component: () => import('@/views/Login/SocialLogin.vue'),
    name: 'SocialLogin',
    meta: {
      hidden: true,
      title: t('router.socialLogin'),
      noTagsView: true
    }
  },
  {
    path: '/403',
    component: () => import('@/views/Error/403.vue'),
    name: 'NoAccess',
    meta: {
      hidden: true,
      title: '403',
      noTagsView: true
    }
  },
  {
    path: '/404',
    component: () => import('@/views/Error/404.vue'),
    name: 'NoFound',
    meta: {
      hidden: true,
      title: '404',
      noTagsView: true
    }
  },
  {
    path: '/500',
    component: () => import('@/views/Error/500.vue'),
    name: 'Error',
    meta: {
      hidden: true,
      title: '500',
      noTagsView: true
    }
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/Error/404.vue'),
    name: '',
    meta: {
      title: '404',
      hidden: true,
      breadcrumb: false
    }
  }
]

export default remainingRouter
