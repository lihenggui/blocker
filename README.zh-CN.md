## 介绍
Blocker是一款操作Android应用程序四大组件的程序。它支持多种不同的控制器控制组件，目前支持的有使用软件包管理器方式（PackageManager）和意图防火墙模式（Intent Firewall）。支持无缝切换使用模式，导入导出Blocker规则，导入导出纯IFW规则，兼容MyAndroidTools规则导入，或是将其转换为Intent Firewall规则导入。

## 优点
1. 轻量级程序，不会给系统增加负担。
2. 易于使用，界面直观。
3. 兼容多种方法禁用，无付费限制。

## 三种控制模式介绍
### PM模式
PM是Package Manager的简写，译为软件包管理器。其由Android操作系统提供，主要用于查询手机上已安装的应用详细信息、进行管理操作。其提供一个```setComponentEnabledSetting(ComponentName, int, int)```的接口，应用程序可以调用此接口来对自身组件进行状态控制。在正常模式下，调用此接口无法对第三方应用进行操控，对非本应用进行组件控制需要有signature等级的权限。

所幸Android在Shell下提供了一个pm工具，用户可以在命令行下对应用的组件进行操作。不过成功运行此命令需要有Root权限。

```
pm disable [PackageName/ComponmentName]
```

无论是调用系统API还是在命令行调用PM工具，应用组件状态变化最终写入```/data/system/users/0/package
restrictions.xml```文件下。

### IFW模式 （默认模式）
Intent Firewall（IFW， 意图防火墙）自Android 4.4.2(API 19)版本后被引入，在目前的最新版本(Android Pie,  API28)中仍然有效。Intent Firewall被集成在Android Framework中，用于过滤应用程序的启动意图(Intent)。


#### IFW能做的事
Android下发起的每一个启动应用组件的意图(Intent)， 都需要经过此防火墙的过滤。它甚至有能力过滤系统程序发出的请求。防火墙的过滤列表定义在一个XML文件
当中，当文件发生变化，防火墙会即时更新过滤规则。

#### IFW的限制
基于安全考虑，只有拥有System权限的系统应用才可以直接读取修改配置文件所在目录，第三方应用程序无法读取改写过滤规则。再者，防火墙过滤规则的时候不会考虑该意图的发送者身份，无法对意图发起方进行条件过滤。

### IFW相比于PM的优点
IFW是防火墙，新增/删除防火墙规则对组件状态无影响。程序探测的组件状态是启用，但是就是无法启动该组件。

Package Manager是直接禁用了对应组件。若是试图启动被禁用的组件，程序会抛出异常退出。开发者可以根据抛出的异常或者根据探测到的组件状态，通
过系统API重新启用相关组件。这就是为什么使用此模式，运行软件有的时候会崩溃，有些组件会自动启用的原因。使用IFW模式则无此问题，不会崩溃，也不会自动启用。
#### 更多参考
请参阅[Intent Firewall](www.cis.syr.edu/~wedu/android/IntentFirewall/)

### Shizuku模式 (无需Root权限)
Shizuku是由Rikka开发的应用，具体请参见[RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)

在Android Oreo之后，Package Manager在更改组件状态的时候新增加了一个匹配规则，当应用程序为Test-Only版本的时候，用户可以随意通过命令行的PM应用程序控制应用程序组件状态。Shizuku的API运行在Shell权限下，我们可以修改APK，将其状态位设置为Test-Only，通过Shizuku提供的高权限API控制组件。

修改应用至Test-Only模式请参考Github下的Wiki教程[[实验性功能] [开发者向]如何免Root控制应用程序组件](https://github.com/lihenggui/blocker/wiki/%5B%E5%AE%9E%E9%AA%8C%E6%80%A7%E5%8A%9F%E8%83%BD%5D-%5B%E5%BC%80%E5%8F%91%E8%80%85%E5%90%91%5D%E5%A6%82%E4%BD%95%E5%85%8DRoot%E6%8E%A7%E5%88%B6%E5%BA%94%E7%94%A8%E7%A8%8B%E5%BA%8F%E7%BB%84%E4%BB%B6)
## 计划
1. 增加在线规则下发应用
2. 支持组件评论
3. 重构现有的代码
