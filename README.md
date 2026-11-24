# AI 语音聊天（Android）

和一位 AI 虚拟角色打语音电话。形象由 Seedream 5.0 生成，聊天用火山方舟对话模型。

## 工作流程
说话 → 手机语音识别（zh-CN） → 方舟对话模型生成回复 → 手机语音合成朗读
Seedream 是图像模型，只负责生成角色形象照，不负责对话和声音。

## 运行
1. 用 Android Studio 打开本目录，等 Gradle 同步完成，连手机运行
2. 到火山方舟控制台：创建 API Key；开通一个对话模型和 Seedream 5.0；复制它们的模型 ID 或接入点 ID
3. 打开应用，在设置（右上角 ⚙）里填：API Key、聊天模型 ID、Seedream 模型 ID
4. 保存后会自动生成形象，也可点「生成 / 更换形象」；点「开始语音通话」开聊

## 注意
- 接口地址默认是火山方舟国内版 `https://ark.cn-beijing.volces.com/api/v3`；BytePlus 国际版请改成 `https://ark.ap-southeast.bytepluses.com/api/v3`
- 语音识别用系统自带服务，部分国产手机没有该服务，此时可用文字聊天；语音合成同理，音色取决于系统语音包
- API Key 保存在手机本地，仅适合自用；若要上架或分发，请改成通过你自己的后端转发请求
- 想要更好听的声音，可把 `MainActivity.speak()` 换成火山引擎语音合成接口
- 角色设定在 `Settings.kt` 的 `systemPrompt()`，默认是温柔健谈、保持得体的虚构 AI 女生，并会如实承认自己是 AI
