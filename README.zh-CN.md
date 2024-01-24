## Blocker
[![release](https://img.shields.io/github/v/release/lihenggui/blocker?label=release&color=red)](https://github.com/lihenggui/blocker/releases)
[![download](https://shields.io/github/downloads/lihenggui/blocker/total?label=download)](https://github.com/lihenggui/blocker/releases/latest)
[![translation](https://weblate.sanmer.dev/widget/blocker/svg-badge.svg)](https://weblate.sanmer.dev/engage/blocker/)
[![follow](https://img.shields.io/badge/follow-Telegram-blue.svg?label=follow)](https://t.me/blockerandroid) 
[![license](https://img.shields.io/github/license/lihenggui/blocker)](LICENSE) 

Blocker是一款操作Android应用程序四大组件的程序。对于臃肿的应用来说，应用中的许多组件都是冗余的。Blocker提供了一个快捷的控制按钮来控制对应的组件，实现禁用无用功能，节约应用运行资源的功能。
Blocker支持多种不同的控制器控制组件，目前支持的有使用软件包管理器方式（PackageManager）和意图防火墙模式（Intent
Firewall）。支持无缝切换使用模式，导入导出Blocker规则，导入导出纯IFW规则，兼容MyAndroidTools规则导入，或是将其转换为Intent
Firewall规则导入。

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.merxury.blocker/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.merxury.blocker)

## Telegram讨论群
https://t.me/blockerandroid

## 优点
1. 轻量级程序，不会给系统增加负担。
2. 易于使用，界面直观。
3. 兼容多种方法禁用。

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
Intent Firewall（IFW， 意图防火墙）自Android 4.4.2(API 19)版本后被引入，在目前的最新版本中仍然有效。Intent Firewall被集成在Android Framework中，用于过滤应用程序的启动意图(Intent)。

#### IFW能做的事
Android下发起的每一个启动应用组件的意图(Intent)， 都需要经过此防火墙的过滤。它甚至有能力过滤系统程序发出的请求。防火墙的过滤列表定义在一个XML文件
当中，当文件发生变化，防火墙会即时更新过滤规则。

#### IFW的限制
基于安全考虑，只有拥有System权限的系统应用才可以直接读取修改配置文件所在目录，第三方应用程序无法读取改写过滤规则。

#### IFW相比于PM的优点
IFW是防火墙，新增/删除防火墙规则对组件状态无影响。程序探测的组件状态是启用，但是就是无法启动该组件。

Package Manager是直接禁用了对应组件。若是试图启动被禁用的组件，程序会抛出异常退出。开发者可以根据抛出的异常或者根据探测到的组件状态，通
过系统API重新启用相关组件。这就是为什么使用此模式，运行软件有的时候会崩溃，有些组件会自动启用的原因。使用IFW模式则无此问题，不会崩溃，也不会自动启用。
#### 更多参考
请参阅[Intent Firewall](https://carteryagemann.com/pages/android-intent-firewall.html)

### Shizuku/Sui模式
Shizuku是由Rikka开发的应用，具体请参见[RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)

在Android Oreo之后，Package
Manager在更改组件状态的时候新增加了一个匹配规则，当应用程序为Test-Only版本的时候，用户可以随意通过命令行的PM应用程序控制应用程序组件状态。Shizuku的API运行在Shell权限下，我们可以修改APK，将其状态位设置为Test-Only，通过Shizuku提供的API控制组件。

修改应用至Test-Only模式请参考Github下的Wiki教程[[实验性功能] [开发者向]如何免Root控制应用程序组件](https://github.com/lihenggui/blocker/wiki/%5B%E5%AE%9E%E9%AA%8C%E6%80%A7%E5%8A%9F%E8%83%BD%5D-%5B%E5%BC%80%E5%8F%91%E8%80%85%E5%90%91%5D%E5%A6%82%E4%BD%95%E5%85%8DRoot%E6%8E%A7%E5%88%B6%E5%BA%94%E7%94%A8%E7%A8%8B%E5%BA%8F%E7%BB%84%E4%BB%B6)

请注意：对于正常应用，Shizuku模式下的Shell权限不足以更改组件的开关状态。换言之就是没有修改过的APK是不支持Shizuku的免root修改。如果你想要使用Shizuku修改正常应用的组件状态，请使用Root身份启动Shizuku。
AOSP中关于此限制的实现：[frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java;l=3750;drc=02a77ed61cbeec253a1b49e732d1f27a9ff4b303;bpv=0;bpt=1)

## 截图测试

**Blocker** 使用 [Roborazzi](https://github.com/takahirom/roborazzi) 进行截图测试某些屏幕和组件。
要运行这些测试，请运行“verifyRoborazziFossDebug”或`recordRoborazziFossDebug`任务。
请注意，屏幕截图是在CI上记录的，使用Linux等平台可能会生成略有不同的图像，从而导致测试失败。


## 架构
Blocker应用架构参照了[Now in Android的软件架构](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md)， 它也同时遵循了[官方架构指南](https://developer.android.com/topic/architecture)。   
关于模块化设计，请参照这篇文章： [Modularization Learning Journey](https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md)。  

## 用户界面

本应用遵循[Material 3 界面规范](https://m3.material.io/)开发，并完全使用Jetpack
Compose构建UI元素。你可以从 [Figma](https://www.figma.com/file/T903MNmXtahDVf1yoOgXoI/Blocker)
上获取设计源文件.

非常感谢我们的UI设计师: [@COPtimer](https://github.com/COPtimer)

应用程序有两个预定义主题：
动态颜色 - 根据用户当前的颜色主题使用颜色；默认主题 - 在不支持动态颜色时使用预定义的颜色。
同时每个主题还支持暗模式。

## 贡献翻译
Blocker app的默认字符串资源在工程以下几个位置：

`app-compose/src/main/res/values/strings.xml`  
`core/[module]/src/main/res/values/strings.xml`  
`sync/[module]/src/main/res/values/strings.xml`  
`feature/[module]/src/main/res/values/strings.xml`  

对应的翻译文件需要放在对应工程的对应资源目录中 ([module]/src/main/res/values-[lang]/strings.xml)。 

你也可以使用 [Weblate](https://weblate.sanmer.dev/projects/blocker/) 来翻译此项目。 (感谢 [@SanmerDev](https://github.com/SanmerDev) 提供的服务)

## 常见问题

1. 在Shizuku模式下点击按钮，组件状态无法控制，弹出SecurityException: Shell cannot change component
   state for 'xx' to state 'xx'。

* Shizuku的Shell权限无法禁用未修改的应用组件，请用Root身份重启Shizuku，或者修改APK之后尝试。
