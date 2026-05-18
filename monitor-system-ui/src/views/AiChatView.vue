<script setup lang="ts">
import { askAiStream } from '../api/ai'
import { ref, nextTick } from 'vue'
import { ChatLineRound, UserFilled, Opportunity } from '@element-plus/icons-vue'
import { marked } from 'marked' // 引入 Markdown 解析神器

interface Message {
  role: 'user' | 'assistant'
  content: string
}

const inputMsg = ref('')
const loading = ref(false)
const messages = ref<Message[]>([
  { role: 'assistant', content: '您好！我是 AI 智能排障专家。请描述您遇到的设备故障现象或输入故障码，我将为您检索内部知识库并提供方案。' }
])
const scrollRef = ref<any>(null)

// 自动滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (scrollRef.value) {
    scrollRef.value.setScrollTop(scrollRef.value.wrapRef.scrollHeight)
  }
}

// 核心工具：将文本解析为 Markdown HTML
const parseMarkdown = (text: string) => {
  if (!text) return ''
  // marked.parse 返回的是 HTML 字符串
  return marked.parse(text) as string 
}

// 发送消息
const handleSend = async () => {
  if (!inputMsg.value.trim()) return
  
  const userMsg = inputMsg.value
  // 1. 用户消息入列
  messages.value.push({ role: 'user', content: userMsg })
  inputMsg.value = ''
  
  // 2. 开启 loading 动画（此时界面只会显示“正在检索...”的气泡）
  loading.value = true
  scrollToBottom()

  // 记录 AI 回复在数组中的索引位置，初始为 -1
  let assistantMsgIndex = -1

  try {
    // 3. 调用流式接口
    await askAiStream(userMsg, (chunkText) => {
      // 【完美解决双气泡问题】：只有收到第一块数据时，才关掉 loading 并推入真实的气泡
      if (loading.value) {
        loading.value = false // 关掉“正在检索”的气泡
        assistantMsgIndex = messages.value.length
        messages.value.push({ role: 'assistant', content: chunkText })
      } else {
        // 后续的数据，直接拼接到现有的气泡中
        const assistantMsg = messages.value[assistantMsgIndex]
        if (assistantMsg) {
          assistantMsg.content += chunkText
        } else {
          assistantMsgIndex = messages.value.length
          messages.value.push({ role: 'assistant', content: chunkText })
        }
      }
      scrollToBottom()
    })
  } catch (error) {
    // 异常兜底：如果报错了还没建出气泡，就建一个报错气泡
    if (loading.value) {
      loading.value = false
      messages.value.push({ role: 'assistant', content: '❌ 抱歉，服务器开小差了或网络连接中断。' })
    } else {
      const assistantMsg = messages.value[assistantMsgIndex]
      if (assistantMsg) {
        assistantMsg.content += '\n\n*[网络连接中断]*'
      } else {
        messages.value.push({ role: 'assistant', content: '❌ 抱歉，服务器开小差了或网络连接中断。' })
      }
    }
    scrollToBottom()
  }
}
</script>

<template>
  <div class="chat-container">
    <el-scrollbar ref="scrollRef" class="chat-list">
      
      <div v-for="(msg, index) in messages" :key="index" :class="['msg-wrapper', msg.role]">
        <el-avatar :icon="msg.role === 'user' ? UserFilled : Opportunity" 
                   :size="40" 
                   :style="{ backgroundColor: msg.role === 'user' ? '#409EFF' : '#67C23A' }" />
        <div class="msg-content">
          <div class="role-name">{{ msg.role === 'user' ? '运维员' : 'AI 专家' }}</div>
          
          <div v-if="msg.role === 'user'" class="bubble">{{ msg.content }}</div>
          
          <div v-else class="bubble markdown-body" v-html="parseMarkdown(msg.content)"></div>
        </div>
      </div>

      <div v-if="loading" class="msg-wrapper assistant">
        <el-avatar :icon="Opportunity" :size="40" style="background-color: #67C23A" />
        <div class="msg-content">
          <div class="role-name">AI 专家</div>
          <div class="bubble loading-dots">
            <span class="dot">.</span><span class="dot">.</span><span class="dot">.</span> 正在检索核心知识库
          </div>
        </div>
      </div>

    </el-scrollbar>

    <div class="input-area">
      <el-input v-model="inputMsg" 
                placeholder="请输入故障码（如 T-994）或故障现象..." 
                @keyup.enter="handleSend"
                :disabled="loading">
        <template #append>
          <el-button type="primary" @click="handleSend" :loading="loading">发送</el-button>
        </template>
      </el-input>
    </div>
  </div>
</template>

<style scoped>
/* 原有基础样式保持不变 */
.chat-container {
  height: calc(100vh - 220px);
  display: flex;
  flex-direction: column;
}
.chat-list {
  flex: 1;
  padding: 20px;
  background-color: #f9f9f9;
  border-radius: 8px;
}
.msg-wrapper {
  display: flex;
  margin-bottom: 25px;
}
.msg-wrapper.user {
  flex-direction: row-reverse;
}
.msg-content {
  margin: 0 15px;
  max-width: 75%; /* 稍微加宽一点，让排版更好看 */
}
.role-name {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.user .role-name {
  text-align: right;
}
.bubble {
  padding: 12px 16px;
  border-radius: 8px;
  background-color: #fff;
  line-height: 1.6;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
  font-size: 14px;
  /* 去掉这里的 pre-wrap，因为 Markdown 解析后已经是 HTML 了 */
}
.user .bubble {
  background-color: #ecf5ff;
  color: #409EFF;
  white-space: pre-wrap; /* 用户的文本还是保留换行符 */
}
.assistant .bubble {
  background-color: #f0f9eb;
  color: #303133; /* 改为深灰色，让大段文字阅读更护眼 */
}

/* =================================================== */
/* 【新增】：为 Markdown 渲染后的标签专门定制的企业级 CSS */
/* =================================================== */
:deep(.markdown-body p) {
  margin: 0 0 10px 0; /* 段落间距 */
}
:deep(.markdown-body p:last-child) {
  margin-bottom: 0;
}
:deep(.markdown-body strong) {
  color: #409EFF; /* 把加粗的字体变成主题蓝，极其提神 */
  font-weight: 600;
}
:deep(.markdown-body ol), :deep(.markdown-body ul) {
  margin: 0 0 10px 0;
  padding-left: 20px;
}
:deep(.markdown-body li) {
  margin-bottom: 5px;
}

/* Loading 呼吸灯特效 */
.loading-dots {
  color: #909399;
  font-style: italic;
}
.dot {
  animation: blink 1.4s infinite both;
  font-weight: bold;
}
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink {
  0% { opacity: 0.2; }
  20% { opacity: 1; }
  100% { opacity: 0.2; }
}
.input-area {
  margin-top: 20px;
}
</style>