[中文README](https://github.com/lihenggui/blocker/blob/master/README.zh-CN.md)

## Introduction
Blocker is a component controller for Android applications. Currently it supports using PackageManager and Intent Firewall to control the state of componets, and it can be extended in the future. You could switch between these controllers seamlessly. For the application rules, you could export & import it. It is compatible with the backup files generated by MyAndroidTools, and you could convert it to Intent Firewall rules without any effort.

## Advantages
1. Lightwight, don't burden the system
2. Easy to use
3. Supports multiple control types
4. FREE

## Introduction to different component controllers
### Package Manager
Android system provide us a tool called PackageManager, for managing the applications installed on the phone or getting informations of the applications. It has a method called ```setComponentEnabledSetting(ComponentName, int, int)```, application can call this API to control the component state for itself. Call to this API to control other applications will fail unless you have signature permission.

Fortunately Android has another tool called pm，users could control the component state in the command line mode。But it needs Root permission to run with.

```
pm disable [PackageName/ComponmentName]
```

No matter using PackageManager in the code or using pm in the command line mode, the configurations will be written to ```/data/system/users/0/package
restrictions.xml```.

### Intent Firewall Mode
Starting from Android 4.4.2(API 19), the Intent Firewall was introduced. It is still has effects in the latest Android system (Pie, API 28)。It was intergrated into Android Framework，for filtering the intents sent by applications or system.

#### What Intent Firewall can do
Each intent sent by application will be filtered by Intent firewall. The rules are stored in xml files. If there are changes in the configuration file has changed, the Intent Firewall will update the rules immediately. 

#### Limitations of Intent Firewall
Based on security considerations, only system applications can read & write the directory directly where the configuration file is placed, third-party applications does not have any permissions to get the configuration file. Furthermore, when the firewall filters the rules, the sender identity of the intent will not be considered, so we cannot filter intents by sender identity.

#### Differences between Intent Firewall and Package Manager
Intent Firewall, indeed it is a firewall, it has no impact on component status. The applications detects the component is on, but it just cannot start the component.

For the components disabled by PackageManager, if application starts it, an exception will be thrown。Developers can catch this exception to know whether the component is disabled or not, so they could re-enable this component。That's the reason why the components will be enabled unexpectly. If you are using Intent Firewall controller, there will be no problems.
#### References
[Intent Firewall](www.cis.syr.edu/~wedu/android/IntentFirewall/)

### Shizuku Mode (No Root Permission Required)
Shizuku is an application by Rikka，[RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)

Starting from Android O，if we install a Test-Only application，users could use pm command to control the command status. ，我们We could modify the install package to set it into Test-Only mode，using APIs provided by Shizuku to control the component status.

Tutorial for modifying APKs (Chinese Only) [[实验性功能] [开发者向]如何免Root控制应用程序组件](https://github.com/lihenggui/blocker/wiki/%5B%E5%AE%9E%E9%AA%8C%E6%80%A7%E5%8A%9F%E8%83%BD%5D-%5B%E5%BC%80%E5%8F%91%E8%80%85%E5%90%91%5D%E5%A6%82%E4%BD%95%E5%85%8DRoot%E6%8E%A7%E5%88%B6%E5%BA%94%E7%94%A8%E7%A8%8B%E5%BA%8F%E7%BB%84%E4%BB%B6)
## Plan
1. Support online rules
2. Comments for componets
3. Refactor existing code
