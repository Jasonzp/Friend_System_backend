# Jasonzp - 伙伴搭子匹配中心项目

> 作者：[Jasonzp](https://github.com/Jasonzp)

![image](https://github.com/user-attachments/assets/2d58aed8-a65a-496c-ba83-2c6b94894888)



## 项目简介

### 后端
1.用户登录:使用 Redis 实现分布式 Session，解决集群间登录态同步问题;并使用 Hash 代替 string 来存储用户信息，节约了内存并便于单字段的修改。  
2.对于项目中复杂的集合处理(比如为队伍列表关联已加入队伍的用户)，使用 Java8 Stream APl和 Lambda 表达式来简化编码。  
3.使用 Easy Excel 读取收集来的基础用户信息，并通过自定义线程池+Completablefuture 并发编程提高批量导入数据库的性能。实测导入 100 万行的时间从 xx秒缩短至 xx秒。  
4.使用 Redis 缓存首页高频访问的用户信息列表，将接口响应时长缩短。且通过自定义 Redis 序列化器来解决数据乱码、空间浪费的问题。  
5.为解决首次访问系统的用户主页加载过慢的问题，使用 Spring Scheduler 定时任务来实现缓存预热，并通过分布式锁保证多机部署时定时任务不会重复执行。  
6.为解决同一用户重复加入队伍、入队人数超限的问题，使用 Redisson 分布式锁来实现操作互斥，保证了接口幂等性。  
7.使用编辑距离算法实现了根据标签匹配最相似用户的功能，并通过优先队列来减少 TOP N 运算过程中的内存占用。  
8.自主编写 Dockerfile，并通过第三方容器托管平台实现自动化镜像构建及容器部署，提高部署上线效率。  
9.使用 Knife4j+ Swagger 自动生成后端接口文档，并通过编写 Api0peration 等注解补充接口注释，避免了人工编写维护文档的麻烦。
### 前端
1.前端使用 vant U组件库，并封装了全局通用的 Layout 组件，使主页、搜索页、组队页布局一致、并减少重复代码。  
2.基于 Vue Router 全局路由守卫实现了根据不同页面来动态切换导航栏标题， 并通过在全局路由配置文件扩展 title 字段来减少无意义的 if else 代码。  
3.使用 TypeScript 类型定义保证项目编码规范，提高项目的质量



## 技术选型

### 前端

- Vue 3
- Vant UI 组件库
- TypeScript
- Vite 脚手架
- Axios 请求库



### 后端

- Java SpringBoot 2.7.x 框架
- MySQL 数据库
- MyBatis-Plus
- MyBatis X 自动生成
- Redis 缓存（Spring Data Redis 等多种实现方式）
- Redisson 分布式锁
- Easy Excel 数据导入
- Spring Scheduler 定时任务
- Swagger + Knife4j 接口文档
- Gson：JSON 序列化库
- 相似度匹配算法



### 部署

- Serverless 服务
- 云原生容器平台

