[中文README](https://github.com/lihenggui/blocker/blob/main/README.zh-CN.md)

## Blocker
[![release](https://img.shields.io/github/v/release/lihenggui/blocker?label=release&color=red)](https://github.com/lihenggui/blocker/releases)
[![download](https://shields.io/github/downloads/lihenggui/blocker/total?label=download)](https://github.com/lihenggui/blocker/releases/latest)
[![translation](https://weblate.sanmer.app/widget/blocker/svg-badge.svg)](https://weblate.sanmer.app/engage/blocker/)
[![follow](https://img.shields.io/badge/follow-Telegram-blue.svg?label=follow)](https://t.me/blockerandroid) 
[![license](https://img.shields.io/github/license/lihenggui/blocker)](LICENSE) 


Blocker là một công cụ kiểm soát thành phần dành cho các ứng dụng Android. Hiện tại, ứng dụng này hỗ trợ sử dụng PackageManager và Intent Firewall để quản lý trạng thái của các thành phần.

Đối với các ứng dụng nặng, nhiều thành phần bên trong thường bị dư thừa. Blocker cung cấp một nút điều khiển thuận tiện để quản lý các thành phần tương ứng này, cho phép vô hiệu hóa các chức năng không cần thiết và tiết kiệm tài nguyên khi ứng dụng hoạt động.

Blocker có thể chuyển đổi linh hoạt giữa các bộ điều khiển ở trên. Bạn cũng có thể xuất và nhập luật dành cho ứng dụng. Ngoài ra, nó còn tương thích với các tệp sao lưu được tạo bởi MyAndroidTools, và bạn có thể dễ dàng chuyển đổi chúng thành các quy tắc Intent Firewall. Ứng dụng cũng có tiềm năng được mở rộng thêm tính năng trong tương lai.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.merxury.blocker/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.merxury.blocker)

## Advantages
1. Nhẹ, không làm hại đến hệ thống
2. Dễ sử dụng
3. Hỗ trợ nhiều kiểu quản lý

## Giới thiệu về các cách quản lý thành phần
### Trình cài đặt gói

Android cung cấp công cụ tên gọi là PackageManager được dùng để quản lý hoặc cung cấp thông tin ứng dụng đã được cài đặt trong máy, một số cách thức của nó là, `setComponentEnabledSetting(ComponentName, int, int)`, cho phép quản lý trạng thái các thành phần trong ứng dụng .Nhưng nếu sử dụng phương pháp này để kiểm soát các thành phần trong các ứng dụng khác, yêu cầu phải có quyền thay đổi chữ ký, nếu không lệnh gọi sẽ thất bại. Nhưng, Android cung cấp một công cụ khác có tên là `pm`, cho phép người dùng kiểm soát trạng thái của các thành phần ở chế độ dòng lệnh. Tuy nhiên, công cụ `pm` yêu cầu quyền root để chạy. Câu lệnh sau đây có thể được sử dụng để vô hiệu hóa thành phần cụ thể:"

```
pm disable [PackageName/ComponentName]
```

Khi dùng PackageManager ở code hoặc lệnh `pm` dưới dạng cli, Cấu hình sẽ được lưu trữ tại  ```/data/system/users/0/package_restrictions.xml```.

### Chế độ tường lửa nội bộ
Chế độ tường lửa được ra đời từ phiên bản Android 4.4.2 (API 19) và vẫn có tác dụng ở phiên bản mới nhất. Được nhúng vào Framework của Android để lọc nội dụng được gửi từ ứng dụng hoặc hệ thống. 

#### Tưởng lửa nội bộ làm được gì?
Từng nội dung của ứng dụng được gửi thông qua hệ thống được lưu dưới dạng file XML và được cập nhật ngay tức thì khi mà có sự thay đổi.

#### Giới hạn của tường lửa
Chỉ có ứng dụng hệ thống mới có quyền RW đường dẫn của nó và ứng dụng thứ 3 không thể chỉnh sửa được.

#### Sự khác biệt giữa tường lửa và trình quản lý gói
Tường lửa nội bộ không gây ảnh hưởng đến trạng thái của thành phần. Ứng dụng vẫn phát hiện ra thành phần đang được kích hoạt, nhưng chỉ là không thể chạy thành phần đó.

Về trình quản lý gói, khi mà có ứng dụng khởi chạy và phát hiện ra thành phần bị vô hiệu hóa, nhà phát triển có thể thêm tính năng tự động kích hoạt thành phần đấy hoặc từ chối chạy. Nhưng nếu bạn sử dụng tường lửa thì có thể không có gì xảy ra cả.
#### Ví dụ
[Tưởng lửa nội bộ](https://carteryagemann.com/pages/android-intent-firewall.html)

### Chế độ Shizuku/Sui
Shizuku là ứng dụng được phát triển bởi Rikka :3, [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)

Bắt đầu từ Android O (ừ), Nếu cài đặt một ứng dụng bản Test-Only, người dùng có thể sử dụng lệnh pm để kiểm soát trạng thái. Chúng ta có thể sửa đổi gói cài đặt để thiết lập nó sang chế độ Test-Only, đồng thời sử dụng các API do Shizuku cung cấp để kiểm soát trạng thái của các thành phần.

Hướng dẫn chỉnh sửa ứng dụng (Tiếng Trung) [[实验性功能] [开发者向]如何免Root控制应用程序组件](https://github.com/lihenggui/blocker/wiki/%5B%E5%AE%9E%E9%AA%8C%E6%80%A7%E5%8A%9F%E8%83%BD%5D-%5B%E5%BC%80%E5%8F%91%E8%80%85%E5%90%91%5D%E5%A6%82%E4%BD%95%E5%85%8DRoot%E6%8E%A7%E5%88%B6%E5%BA%94%E7%94%A8%E7%A8%8B%E5%BA%8F%E7%BB%84%E4%BB%B6)

LƯU Ý: Đối với các ứng dụng thông thường, quyền Shell trong chế độ Shizuku là không đủ để thay đổi trạng thái bật/tắt của các thành phần.

Cách khác, các APK chưa qua chỉnh sửa sẽ không hỗ trợ việc thay đổi này nếu máy chưa Root. Nếu bạn muốn sử dụng Shizuku để sửa đổi trạng thái thành phần của các ứng dụng thường, vui lòng khởi chạy Shizuku với quyền Root.

Sự giới hạn này được thêm vào từ nguồn 
AOSP: [frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java;l=3750;drc=02a77ed61cbeec253a1b49e732d1f27a9ff4b303;bpv=0;bpt=1)

## Kiểm tra chụp màn hình

**Blocker** sử dụng [Roborazzi](https://github.com/takahirom/roborazzi) để kiểm tra xem bạn có đang chụp màn hình toàn phần hoặc thành phần không. để chạy kiểm tra, chạy `verifyRoborazziFossDebug` hoặc
`recordRoborazziFossDebug` tiến trình. Nhớ rằng bản ghi chụp màn hình được ghi lại trong CI sử dụng Linux và sử dụng một vài nền tảng khác có thể tạo ra hình ảnh khác biệt, gây ra lỗi.

## Kiến trúc
Kiến trúc của ứng dụng Blocker được miêu tả trong [Now in Android](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md), và cũng được nói trong [official architecture guidance](https://developer.android.com/topic/architecture).  
Để tìm hiểu thêm, vui lòng đọc: [Modularization Learning Journey](https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md).

## UI
Ứng dụng được thiết kế bởi [Material 3 guidelines](https://m3.material.io/). Tìm hiểu quá trình thiết kế qua [design files in Figma](https://www.figma.com/file/T903MNmXtahDVf1yoOgXoI/Blocker).
Cảm ơn vi da den từ nhà thiết kế: [@COPtimer](https://github.com/COPtimer)  
Hình ảnh và giao diện được làm từ Jetpack Compose.

Ứng dụng có 2 màu:

* Màu linh hoạt - sử dụng cơ chế material để chọn màu (nếu có)
* Màu đang cso - sử dụng màu mặc định

Cả hai theme đều có chế độ tối.

## Hỗ trợ dịch
string mặc định được lưu trữ tại:

`app-compose/src/main/res/values/strings.xml`  
`core/[module]/src/main/res/values/strings.xml`  
`sync/[module]/src/main/res/values/strings.xml`  
`feature/[module]/src/main/res/values/strings.xml`  

Dịch từng cái và để tại ([module]/src/main/res/values-[lang]/strings.xml).  

Bạn có thể dùng [Weblate](https://weblate.sanmer.app/projects/blocker/) để dịch. (Cảm ơn [@SanmerDev](https://github.com/SanmerDev) đã làm nhà cung cấp)

## Top những câu hỏi hỏi nhiều nhất

1.  Khi mà bấm nút ở chế độ Shizuku, thành phần không thể điều chỉnh được và bám lỗi: SecurityException: Shell cannot change component state for 'xx' to state 'xx'.

* Shizuku mà chạy dưới dạng shell không có tác dụng. Vui lòng chạy Shizuku dưới quyền root hoặc chỉnh sửa lại ứng dụng.

2. Sự khác biệt giữa bản `foss` và bản `market` ?

* Bạn có thể xem chi tiết tại [#619 Build & Check F-Droid version](https://github.com/lihenggui/blocker/pull/619/files). Bản `foss` không có các thành phần mã đóng của GMS và Firebase, bản `market` bao gồm Firebase Crashlytics và Firebase Analytics , cho phép nhà phát triển theo dõi thông tin lỗi và người dùng. Những phản hồi này giúp nhà phát triển dễ dàng sửa lỗi và phát triển hơn. The version available on Google Play is the market version. Nếu bạn không muốn có các thành phần theo dõi, bạn có thể tải bản `foss` tại [GitHub release page](https://github.com/lihenggui/blocker/releases) hoặc [F-Droid](https://f-droid.org/packages/com.merxury.blocker/).
