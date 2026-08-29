<!--suppress CheckImageSize -->
# GKUIClock
基于BlackyHawkyClock的开源项目，并添加跳过节假日等本土化功能




## 目录

- [功能](#features)
  * [通病](#common-issues)
- [贡献](#contributing-)
  * [报告问题](#reporting-issues)
  * [翻译](#translation)
  * [贡献代码](#code-contribution)
- [License](#license)
- [截图](#screenshots)
- [来源](#credits)

# 功能
* 跳过节假日；
* 可设置大小周/单休工作模式；
* 翻转/摇晃手机以静音/关闭闹钟;
* 用音量/电源键以推迟/关闭闹钟;
* 部分骁龙机型可以关机后仍响铃;
  * 不幸的是, 尽管有 _“com.qualcomm.qti.poweroffalarm”_ 开头的应用，该功能可能无效，  [详情请点击](https://github.com/BlackyHawky/Clock/issues/88).
* 滑动删除闹钟;
* 复制闹钟;
* 自定义闹钟标题;
* 自定义铃声;
* 随机铃声;
* 亮/暗/跟随系统设置主题;
* AMOLED 暗色主题;
* 电子/传统时钟样式;
* 出门在外时显示家乡时区;
* 显示多个城市的时区;
* 秒表和计时器;
* 可以与你的联系人分享你的计时器;
* 可自定义界面;
* 可自定义屏保;
* 焕然一新的小组件;
* 可自定义小组件;
* 支持控制中心磁贴 (Android 7+);
* 可备份闹钟 (除自定义铃声外均可备份);
* Material design;
* 支持动态取色（Android 12+）;
* 支持 [Reproducible Builds](https://reproducible-builds.org/).  [详情请点击](https://github.com/BlackyHawky/Clock/issues/140).

## 节假日 JSON 导入

可在“设置 → 闹钟 → 节假日闹钟”中导入本地 JSON，并选择要应用的国家或地区。默认会跟随应用语言（系统语言模式下跟随系统语言和地区）自动选择对应国家；也可以手动指定。应用支持项目原有的 `Years` 格式、普通节假日数组，以及 Nager.Date、Calendarific 等常见国际数据结构。最简单的自定义格式如下：

```json
{
  "countryCode": "US",
  "holidays": [
    {
      "name": "New Year's Day",
      "date": "2026-01-01"
    },
    {
      "name": "Example holiday",
      "startDate": "2026-05-01",
      "endDate": "2026-05-03",
      "compDays": ["2026-05-09"]
    }
  ]
}
```

日期使用 `yyyy-MM-dd`。`countryCode` 为可选的 ISO 3166-1 国家代码；`compDays` 为可选的调休工作日。

快捷计时器首次使用时会提供“刷牙（2 分钟）”“泡面（3 分钟）”“蒸蛋（10 分钟）”和“敷面膜（15 分钟）”等生活化预设；预设名称会随应用语言切换。

高级轮班闹钟还可以单独同步系统日历，并指定某个日历账户作为班次来源。日历事件会作为未来 30 天的日期覆盖写入轮班计划，已删除或移动的事件会在下次同步时自动清理。可通过日期范围添加个人假期或年假，暂停这些日期的轮班闹钟；个人假期优先于日历同步。

## 通病

由于缺乏测试设备，某些问题可能在这些设备上出现.

⚠ _<b>我不是专家,一些问题没有帮助可能解决不了.</b>_ ⚠


# License

Clock is licensed under GNU General Public License v3.0.

> Permissions of this strong copyleft license are conditioned on making available complete source code of licensed works and modifications, which include larger works using a licensed work, under the same license. Copyright and license notices must be preserved. Contributors provide an express grant of patent rights.

See repo's [LICENSE](/LICENSE) file.

Since the app is based on Apache 2.0 licensed AOSP Clock, an [Apache 2.0](LICENSE-Apache-2.0) license file is provided too.

# 截图

<details>
<summary><b>查看截图</b></summary>
<br>
 <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/01.jpg" alt="Screenshot 01" width="200" />
 <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/02.jpg" alt="Screenshot 02" width="200" />
 <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/03.jpg" alt="Screenshot 03" width="200" />
 <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/04.jpg" alt="Screenshot 04" width="200" />
 <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/05.jpg" alt="Screenshot 05" width="200" />
 <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/06.jpg" alt="Screenshot 06" width="200" />
 <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/07.jpg" alt="Screenshot 07" width="200" />
 <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/08.jpg" alt="Screenshot 08" width="200" />
 <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/09.jpg" alt="Screenshot 09" width="200" />
 <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/10.jpg" alt="Screenshot 10" width="200" />
 <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/11.jpg" alt="Screenshot 11" width="200" />
 <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/12.jpg" alt="Screenshot 12" width="200" />
 <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/13.jpg" alt="Screenshot 13" width="200" />
 <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/14.jpg" alt="Screenshot 14" width="200" />
</details>

# Credits
- [qw123wh](https://github.com/qw123wh)
- [crDroid Android](https://github.com/crdroidandroid/android_packages_apps_DeskClock)
- [LineageOS](https://github.com/LineageOS/android_packages_apps_DeskClock)
- [BlackyHawkyClock](https://github.com/BlackyHawky/Clock)
