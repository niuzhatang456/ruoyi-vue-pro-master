import {resolve} from 'path'
import type {ConfigEnv, UserConfig} from 'vite'
import {loadEnv, normalizePath} from 'vite'
import {createVitePlugins} from './build/vite'
import {exclude, include} from "./build/vite/optimize"
// 当前执行node命令时文件夹的地址(工作目录)
const root = process.cwd()

// 路径查找
function pathResolve(dir: string) {
    return resolve(root, '.', dir)
}

// https://vitejs.dev/config/
export default ({command, mode}: ConfigEnv): UserConfig => {
    let env = {} as any
    const isBuild = command === 'build'
    if (!isBuild) {
        env = loadEnv((process.argv[3] === '--mode' ? process.argv[4] : process.argv[3]), root)
    } else {
        env = loadEnv(mode, root)
    }
    const variablesScssPath = normalizePath(pathResolve('src/styles/variables.scss'))
    return {
        base: env.VITE_BASE_PATH,
        root: root,
        // 服务端渲染
        server: {
            port: env.VITE_PORT, // 端口号
            host: "0.0.0.0",
            open: env.VITE_OPEN === 'true',
            // 本地跨域代理. 目前注释的原因：暂时没有用途，server 端已经支持跨域
            // proxy: {
            //   ['/admin-api']: {
            //     target: env.VITE_BASE_URL,
            //     ws: false,
            //     changeOrigin: true,
            //     rewrite: (path) => path.replace(new RegExp(`^/admin-api`), ''),
            //   },
            // },
        },
        // 项目使用的vite插件。 单独提取到build/vite/plugin中管理
        plugins: createVitePlugins(),
        css: {
            lightningcss: {
                // Preserve legacy star-hack declarations by stripping invalid syntax during minification.
                errorRecovery: true
            },
            preprocessorOptions: {
                scss: {
                    additionalData: (source: string, filename: string) => {
                        const normalizedFilename = normalizePath(filename)
                        // Windows 下更容易触发重复注入：定义或显式转导变量的文件，不能再次注入同一个
                        // `@use ... as *`，否则 Sass 会报 duplicate global variables。
                        if (
                            normalizedFilename.endsWith('/src/styles/variables.scss') ||
                            normalizedFilename.endsWith('/src/styles/global.module.scss')
                        ) {
                            return source
                        }
                        return `@use "${variablesScssPath}" as *;\n${source}`
                    },
                    api: 'modern-compiler'
                }
            }
        },
        resolve: {
            extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.scss', '.css'],
            alias: [
                {
                    find: /\@\//,
                    replacement: `${pathResolve('src')}/`
                }
            ]
        },
        build: {
            // 改用 esbuild 替代 terser：terser 在当前 Node v24 + WSL 环境下会 SIGSEGV（本地环境 OOM/native crash）。
            // esbuild 已内置于 Vite，输出质量与 terser 基本相当，且速度快、稳定。
            // TODO: 如需 drop_console/drop_debugger，可在 esbuildOptions 中配置。
            minify: 'esbuild',
            esbuildOptions: {
                drop: [
                    ...(env.VITE_DROP_DEBUGGER === 'true' ? ['debugger'] as const : []),
                    ...(env.VITE_DROP_CONSOLE === 'true' ? ['console'] as const : []),
                ],
            },
            outDir: env.VITE_OUT_DIR || 'dist',
            sourcemap: env.VITE_SOURCEMAP === 'true' ? 'inline' : false,
            // brotliSize: false,
            rollupOptions: {
                output: {
                    codeSplitting: {
                        groups: [
                            { name: 'echarts', test: /node_modules[\\/]echarts[\\/]/ }, // 将 echarts 单独打包，参考 https://gitee.com/yudaocode/yudao-ui-admin-vue3/issues/IAB1SX 讨论
                            { name: 'form-create', test: /node_modules[\\/]@form-create[\\/]element-ui[\\/]/ }, // 参考 https://github.com/yudaocode/yudao-ui-admin-vue3/issues/148 讨论
                            { name: 'form-designer', test: /node_modules[\\/]@form-create[\\/]designer[\\/]/ }
                        ]
                    }
                },
            },
        },
        optimizeDeps: {include, exclude}
    }
}
