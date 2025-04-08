# RaceCondition V4.5
- 一款burp插件，用于测试不同数据包不同线程下的条件竞争问题

- 不同数据包之间的并发操作可能会引发条件竞争的问题

- 在常规的肾透测试中，不同的数据包之间并发测试可能会带来意想不到的效果，**尤其是涉及到支付和修改的逻辑漏洞**

- 版本功能迭代已从最初的1.0升级到了4.5，放心食用，如有问题，请提交Issues

### 插件功能 
- **延迟**，为了更容易实现条件竞争，在不同线程之间加了一个小延迟，可根据实际情况填写，默认为0（有时候两个数据包并发可能会导致报错），一般设置在0-1之间就足够测试了。比如两个数据包，延迟设定为1，请求-1 发包间隔1ms 请求-2再发

- **开始攻击**，即所有数据包并发

- **停止**，即攻击时可以提前停止结束

- **清空**，即把所有数据清空

- **↑**，选择数据包，**点击↑**可调整数据包位置顺序

- **×**，选择数据包，**点击×**即可删除对应的数据包

- 支持修改编辑数据包 


### 食用方法
- burp添加扩展（此处省略......一万字长篇大论）

- 插件界面
![image-20250408092512991](https://github.com/user-attachments/assets/5fd48cd3-4892-4236-8b75-94e2dcd7f080)

- 在**Proxy**和**Repeater**模块都可以右键发送到该插件

![image-20250408092548720](https://github.com/user-attachments/assets/e5531547-cdc5-4e0e-83bf-52f64c6d386d)

- 不同数据包之间的条件竞争测试，点击开始攻击按钮即可。**Tips：有时候给个小延时条件竞争成功的概率更高，一般在0-1之间，默认是0**
![image-20250408092955295](https://github.com/user-attachments/assets/5340e912-92b9-4399-b74d-d5393113e817)

- 尽情食用吧


### 新增功能 ➕

- 添加了请求超时机制（30秒超时限制）

- 添加了 dispose() 方法用于资源清理

- 添加了延迟输入的合法性验证方法 isValidDelay()


### 修改功能 🔄

- 将 rawRequests 列表改为线程安全的集合：

- 增强了错误处理机制，添加了更详细的错误提示


### 删除功能 ➖

没有删除任何现有功能


### 优化效果 💡

- 提高了线程安全性

- 改善了资源管理

- 增强了错误处理的健壮性

- 防止请求永久挂起

- 提供了更好的用户体验（更清晰的错误提示）
